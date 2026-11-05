package app.coordinate.lab

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.*
import android.util.Log
import app.coordinate.lab.core.GeoPoint
import app.coordinate.lab.core.InjectionHealth
import app.coordinate.lab.core.RoutePlayer
import app.coordinate.lab.core.RouteSample
import app.coordinate.lab.core.RouteTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class SimulationState(
    val active: Boolean=false,val paused: Boolean=false,val arrived: Boolean=false,
    val name: String="",val point: GeoPoint?=null,val progress: Float=0f,
    val providers: Int=0,val message: String="",val elapsedSeconds: Long=0,
    val startedAtElapsedMs: Long=0,
    val health: InjectionHealth=InjectionHealth(),
    /** Immutable route snapshot for the active session; fixed-point sessions deliberately expose none. */
    val routePoints: List<GeoPoint> = emptyList(),
    val speedKph: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    /** Route motion only: paused wall time and time spent holding the destination are excluded. */
    val motionSeconds: Double = 0.0
)

object SimulationBus {
    internal val mutable=MutableStateFlow(SimulationState())
    val state=mutable.asStateFlow()
}

/** Public mock providers only. The writer is independent of the Activity and map SDK main looper. */
class SimulationService: Service() {
    private val main=Handler(Looper.getMainLooper())
    private val worker=ScheduledThreadPoolExecutor(1) { job ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);job.run() },"coordinate-location")
    }.apply { removeOnCancelPolicy=true;setExecuteExistingDelayedTasksAfterShutdownPolicy(false) }
    private lateinit var locations: LocationManager
    private lateinit var store: AppStore
    // All session fields below are exclusively owned by the single worker.
    private val providers=mutableListOf<String>()
    private var ticker: ScheduledFuture<*>?=null
    private var route: RoutePlayer?=null
    private var routeSnapshot: List<GeoPoint> = emptyList()
    private var timeline: RouteTimeline?=null
    private var fixed: GeoPoint?=null
    private var label=""
    private var paused=false
    private var startedAt=0L
    private var lastNotification=0L
    private var wake: PowerManager.WakeLock?=null
    private var lastWake=0L
    private var sessionActive=false
    private var health=InjectionHealth()
    @Volatile private var destroyed=false
    @Volatile private var latestStartId=0

    override fun onCreate() {
        super.onCreate();alive=true
        locations=getSystemService(LocationManager::class.java)
        store=AppStore(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"模拟定位运行状态",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?)=null

    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        latestStartId=startId
        when(intent?.action) {
            STOP->worker.execute { finish("已停止模拟，定位源已释放；其他应用可能需要刷新缓存",startId) }
            PAUSE->worker.execute {
                if(sessionActive && !destroyed) {
                    val now=SystemClock.elapsedRealtime()
                    // The single writer owns the timeline; it first accounts for motion through this
                    // instant, then flips state so paused wall time can never be added on resume.
                    paused=timeline?.togglePause(now)?.paused ?: !paused
                    writeLocation(startId,now)
                } else finish("当前没有运行中的模拟会话",startId)
            }
            START->{
                // Enter foreground promptly before asynchronous setup, as required by Android.
                runCatching { startForeground(NOTIFICATION,notification("正在启用模拟定位")) }
                    .onSuccess { worker.execute { startSession(intent,startId) } }
                    .onFailure { error -> worker.execute { finish(error.message ?: "无法启动前台服务",startId) } }
            }
            else->worker.execute { finish("未恢复模拟，请手动开始",startId) }
        }
        return START_NOT_STICKY
    }

    private fun startSession(intent: Intent,startId: Int) {
        if(destroyed)return
        runCatching {
            check(DeviceAccess.mockAllowed(this)){"请在开发者选项中选择「坐标」作为模拟位置信息应用"}
            check(DeviceAccess.preciseAllowed(this)){"请允许精确位置权限后再开始"}
            check(locations.isLocationEnabled){"请先开启手机系统的定位开关"}
            val values=intent.getDoubleArrayExtra(POINTS) ?: error("缺少地点")
            require(values.isNotEmpty() && values.size%2==0 && values.size<=20000){"路线点数不可用"}
            val points=values.toList().chunked(2).map { GeoPoint(it[0],it[1]) }
            val player=if(points.size>1)RoutePlayer(points,intent.getDoubleExtra(SPEED,5.0))else null
            releaseSession()
            route=player;routeSnapshot=player?.points ?: emptyList();fixed=points.first();label=intent.getStringExtra(NAME).orEmpty().take(100)
            paused=false;startedAt=SystemClock.elapsedRealtime();timeline=player?.let { RouteTimeline(startedAt) }
            health=InjectionHealth();lastNotification=0;lastWake=0
            listOf("gps","network","fused").forEach { name ->
                runCatching {
                    if(Build.VERSION.SDK_INT>=31) {
                        locations.addTestProvider(name,ProviderProperties.Builder()
                            .setHasSatelliteRequirement(name==LocationManager.GPS_PROVIDER)
                            .setHasNetworkRequirement(name==LocationManager.NETWORK_PROVIDER)
                            .setHasAltitudeSupport(true).setHasSpeedSupport(true).setHasBearingSupport(true)
                            .setPowerUsage(if(name==LocationManager.GPS_PROVIDER)ProviderProperties.POWER_USAGE_HIGH else ProviderProperties.POWER_USAGE_LOW)
                            .setAccuracy(if(name==LocationManager.NETWORK_PROVIDER)ProviderProperties.ACCURACY_COARSE else ProviderProperties.ACCURACY_FINE).build())
                    } else {
                        @Suppress("DEPRECATION")
                        locations.addTestProvider(name,name==LocationManager.NETWORK_PROVIDER,name==LocationManager.GPS_PROVIDER,false,false,true,true,true,
                            if(name==LocationManager.GPS_PROVIDER)ProviderProperties.POWER_USAGE_HIGH else ProviderProperties.POWER_USAGE_LOW,
                            if(name==LocationManager.NETWORK_PROVIDER)ProviderProperties.ACCURACY_COARSE else ProviderProperties.ACCURACY_FINE)
                    }
                    providers.add(name)
                    locations.setTestProviderEnabled(name,true)
                }.onFailure { Log.w(TAG,"Provider setup failed: $name (${it.javaClass.simpleName})") }
            }
            check(providers.isNotEmpty()){ "系统拒绝了模拟定位源，请检查开发者选项" }
            store.previousSession=true;sessionActive=true
            SimulationBus.mutable.value=SimulationState(
                active=true,name=label,message="等待首次写入",startedAtElapsedMs=startedAt,
                routePoints=routeSnapshot,
                speedKph=player?.speedKph ?: 0.0,
                distanceMeters=player?.distanceMeters ?: 0.0,
                durationSeconds=player?.durationSeconds ?: 0.0
            )
            wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"coordinate:simulation").apply { setReferenceCounted(false) }
            Log.i(TAG,"Session started; providers=${providers.size}; writer=${Thread.currentThread().name}")
            ticker=worker.scheduleWithFixedDelay({writeLocation(latestStartId)},0,1,TimeUnit.SECONDS)
        }.onFailure { finish(it.message ?: "无法启动模拟定位",startId) }
    }

    private fun writeLocation(startId: Int, tickElapsedRealtimeMs: Long?=null) {
        if(destroyed || !sessionActive)return
        runCatching {
            check(DeviceAccess.mockAllowed(this)){"模拟定位授权已被切换，当前会话已停止"}
            check(locations.isLocationEnabled){"系统定位已关闭，当前会话已停止"}
            val now=tickElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
            val timelineState=timeline?.tick(now)
            if(timelineState!=null) paused=timelineState.paused
            if(now-lastWake>300_000 || wake?.isHeld!=true){
                if(wake?.isHeld==true)wake?.release()
                wake?.acquire(600_000L);lastWake=now
            }
            val activeRoute=route
            // Once at the endpoint, expose the exact duration rather than ever-growing wall-clock motion.
            val motionSeconds=activeRoute?.let { timelineState?.motionSeconds?.coerceAtMost(it.durationSeconds) ?: 0.0 } ?: 0.0
            val moving=activeRoute?.at(motionSeconds)
            val sample=moving ?: RouteSample(fixed ?: error("没有定位点"),0f,0f,0f,false)
            var successful=0
            providers.forEach { name -> runCatching {
                val location=Location(name).apply {
                    latitude=sample.point.latitude;longitude=sample.point.longitude
                    accuracy=5f;altitude=0.0;verticalAccuracyMeters=5f
                    time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
                    speed=if(paused)0f else sample.speedMps;speedAccuracyMetersPerSecond=0.1f
                    if(!paused && sample.speedMps>0){bearing=sample.bearing;bearingAccuracyDegrees=1f}
                    // Legacy GPS consumers read this documented Location extra as fix metadata.
                    // This is simulated test data, NOT real satellite observations; Android's mock flag remains intact.
                    if(name==LocationManager.GPS_PROVIDER)extras=Bundle().apply {putInt("satellites",8)}
                }
                locations.setTestProviderLocation(name,location)
                successful++
            }}
            check(successful>0){"所有定位通道均未能写入，已停止模拟"}
            val completedAt=SystemClock.elapsedRealtime()
            val gap=health.ageMs(completedAt) ?: 0
            if(gap>InjectionHealth.STALE_AFTER_MS)Log.w(TAG,"Writer resumed after ${gap}ms gap")
            health=health.success(completedAt,System.currentTimeMillis())
            SimulationBus.mutable.value=SimulationState(
                active=true,
                paused=paused,
                arrived=moving?.finished==true,
                name=label,
                point=sample.point,
                progress=sample.progress,
                providers=successful,
                message=when { moving?.finished==true->"已到达，持续保持终点";paused->"移动已暂停，持续保持当前位置";successful<3->"部分通道可用（$successful/3）";activeRoute!=null->"正在沿已选路线移动";else->"正在保持固定地点" },
                elapsedSeconds=(completedAt-startedAt).coerceAtLeast(0)/1000,
                startedAtElapsedMs=startedAt,
                health=health,
                routePoints=routeSnapshot,
                speedKph=activeRoute?.speedKph ?: 0.0,
                distanceMeters=activeRoute?.distanceMeters ?: 0.0,
                durationSeconds=activeRoute?.durationSeconds ?: 0.0,
                motionSeconds=motionSeconds
            )
            if(completedAt-lastNotification>10000){
                updateNotification();lastNotification=completedAt
                Log.i(TAG,"Heartbeat writes=${health.writes} providers=$successful maxGapMs=${health.longestGapMs}")
            }
        }.onFailure { finish(it.message ?: "模拟定位已停止",startId) }
    }

    private fun updateNotification(){
        val state=SimulationBus.state.value
        val stamp=java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.CHINA).format(java.util.Date(state.health.lastSuccessWallMs))
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification("${state.message} · 最近写入 $stamp"))
    }
    private fun notification(text: String): Notification {
        val activity=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,SimulationService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("坐标 · 模拟定位服务").setContentText(text).setContentIntent(activity).setOngoing(true).setOnlyAlertOnce(true).addAction(Notification.Action.Builder(null,"停止并恢复",stop).build()).build()
    }
    private fun releaseSession(){
        sessionActive=false;ticker?.cancel(false);ticker=null
        providers.toList().forEach { name->runCatching { locations.removeTestProvider(name) } };providers.clear()
        if(wake?.isHeld==true)wake?.release();wake=null
    }
    private fun finish(message: String,startId: Int) {
        releaseSession();store.previousSession=false
        SimulationBus.mutable.value=SimulationState(message=message,health=health)
        Log.i(TAG,"Session stopped; writes=${health.writes}")
        main.post {
            // An earlier stop/error must not tear down a newer start request.
            if(!destroyed && latestStartId==startId){stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(startId)}
        }
    }
    override fun onDestroy(){
        destroyed=true;alive=false
        worker.execute {
            releaseSession();store.previousSession=false
            if(SimulationBus.state.value.active)SimulationBus.mutable.value=SimulationState(message="服务已结束，定位源已释放",health=health)
        }
        worker.shutdown();super.onDestroy()
    }
    override fun dump(fd: FileDescriptor,writer: PrintWriter,args: Array<out String>) {
        val state=SimulationBus.state.value
        writer.println("Coordinate ${BuildConfig.VERSION_NAME}: active=${state.active} paused=${state.paused} providers=${state.providers}")
        writer.println("writer=coordinate-location writes=${state.health.writes} ageMs=${state.health.ageMs(SystemClock.elapsedRealtime())} maxGapMs=${state.health.longestGapMs}")
        writer.println("status=${state.health.status(state.active,SystemClock.elapsedRealtime())}")
    }
    companion object {
        private const val TAG="CoordinateSimulation"
        private const val CHANNEL="simulation"
        private const val NOTIFICATION=1101
        private const val START="app.coordinate.lab.START"
        const val STOP="app.coordinate.lab.STOP"
        const val PAUSE="app.coordinate.lab.PAUSE"
        private const val POINTS="points"
        private const val SPEED="speed"
        private const val NAME="name"
        @Volatile private var alive=false
        fun start(context: Context,name: String,points: List<GeoPoint>,speed: Double) {
            require(points.isNotEmpty() && points.size<=10000){"路线点数超过首版支持范围"}
            context.startForegroundService(Intent(context,SimulationService::class.java).setAction(START).putExtra(NAME,name).putExtra(POINTS,points.flatMap { listOf(it.latitude,it.longitude) }.toDoubleArray()).putExtra(SPEED,speed))
        }
        fun command(context: Context,action: String){if(alive)context.startService(Intent(context,SimulationService::class.java).setAction(action))}
        fun recoverAbandonedSession(context: Context,store: AppStore){
            if(store.previousSession && !alive){
                if(DeviceAccess.mockAllowed(context)) {
                    val manager=context.getSystemService(LocationManager::class.java)
                    listOf("gps","network","fused").forEach { runCatching { manager.removeTestProvider(it) } }
                }
                store.previousSession=false
                SimulationBus.mutable.value=SimulationState(message="已清理上次未正常结束的模拟会话")
            }
        }
    }
}
