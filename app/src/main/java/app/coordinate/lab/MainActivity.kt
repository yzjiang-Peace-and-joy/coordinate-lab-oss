package app.coordinate.lab

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.coordinate.lab.core.RouteStartPolicy

class MainActivity: ComponentActivity() {
    private lateinit var model: AppViewModel
    private var pendingStart=false
    private lateinit var realLocation: ForegroundRealLocation
    private val initialPermissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.refreshAccess()
        if(canReadLocation())locateInitialPosition()
        else model.initialLocationUnavailable("定位未授权，请在地图上选点")
    }
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.refreshAccess()
        if(pendingStart && DeviceAccess.preciseAllowed(this)) { pendingStart=false;begin() }
        else if(pendingStart){pendingStart=false;model.notice("未开始模拟：需要精确位置权限；地图与收藏仍可使用")}
        else if(canReadLocation())locateInitialPosition()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        model=ViewModelProvider(this)[AppViewModel::class.java]
        realLocation=ForegroundRealLocation(this)
        setContent {
            val appearance=model.state.collectAsStateWithLifecycle().value.appearance
            CoordinateTheme(appearance) { CoordinateApp(model,::begin,::requestLocation,::openDeveloperSettings) }
        }
        if(savedInstanceState==null && !canReadLocation() && !SimulationBus.state.value.active)
            initialPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    override fun onResume(){super.onResume();if(::model.isInitialized){model.refreshAccess();if(canReadLocation())locateInitialPosition()}}
    override fun onPause(){if(::realLocation.isInitialized)realLocation.stop();super.onPause()}
    private fun canReadLocation()=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    private fun locateInitialPosition(){
        if(!canReadLocation() || model.state.value.hasPickedPoint || SimulationBus.state.value.active)return
        realLocation.start(model::initialRealLocation){model.initialLocationUnavailable("暂未获取真实位置，可直接选点")}
    }
    private fun openDeveloperSettings(){runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }.onFailure { model.notice("请手动打开系统设置 → 开发者选项 → 选择模拟位置信息应用 → 坐标") }}
    private fun requestLocation(){
        val requested=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        if(Build.VERSION.SDK_INT>=33)requested.add(Manifest.permission.POST_NOTIFICATIONS)
        permissions.launch(requested.toTypedArray())
    }
    private fun begin(){
        model.refreshAccess()
        if(SimulationBus.state.value.active){SimulationService.command(this,SimulationService.STOP);return}
        if(!DeviceAccess.mockAllowed(this)){model.notice("请在开发者选项中选择「坐标」作为模拟位置信息应用，再返回点击开始");openDeveloperSettings();return}
        if(!DeviceAccess.preciseAllowed(this)){pendingStart=true;requestLocation();return}
        val state=model.state.value
        if(!state.routeMode && !state.locationReady){model.notice("请等待真实位置，或先在地图上选点");return}
        if(state.routeMode && !RouteStartPolicy.canStart(state.routeEditing,state.planning,state.routeError,state.routeWaypoints.size,state.route.size)){
            model.notice("请先完成多点选线并等待整条路线预览成功，再开始模拟");return
        }
        runCatching {
            realLocation.stop()
            SimulationService.start(this,if(state.routeMode)state.loadedFavoriteRoute?.name ?: "${state.routeWaypoints.first().name} → ${state.routeWaypoints.last().name}（${state.routeWaypoints.size} 点）" else state.selected.name,
                if(state.routeMode)state.route else listOf(state.selected.point),state.speed)
        }.onFailure { model.notice(it.message ?: "无法开始模拟") }
    }
}
