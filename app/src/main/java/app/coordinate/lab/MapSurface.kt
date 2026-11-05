package app.coordinate.lab

import android.graphics.Color as AndroidColor
import android.graphics.Point
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.coordinate.lab.core.Coordinates
import app.coordinate.lab.core.DrawingStrokeSampler
import app.coordinate.lab.core.GeoPoint
import app.coordinate.lab.core.RouteGeometry
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.*
import kotlin.math.roundToInt

/** Controls the existing map without tying its lifetime to an overlay or bottom sheet. */
@Stable
class MapUiController(initialStyle: MapStyle=MapStyle.NORMAL,initialTraffic: Boolean=false,private val saveStyle: (MapStyle)->Unit={},private val saveTraffic: (Boolean)->Unit={},initialDark: Boolean=false) {
    private val themePolicy=MapThemePolicy(initialStyle,initialDark)
    var style by mutableStateOf(themePolicy.style)
        private set
    var traffic by mutableStateOf(initialTraffic)
        private set

    private var map: AMap? = null
    private var centerAction: (() -> Unit)? = null

    /** Recenter on the simulated point, or the selected point when no session is active. */
    fun center() { centerAction?.invoke() }

    fun selectStyle(value: MapStyle) {
        themePolicy.selectStyle(value)
        showStyle(value)
        saveStyle(value)
    }

    /** Theme-derived layers are transient; do not persist them or disturb the traffic toggle. */
    fun applyTheme(dark: Boolean) {
        if(themePolicy.applyTheme(dark))showStyle(themePolicy.style)
    }

    private fun showStyle(value: MapStyle) {
        map?.mapType=value.sdkType
        map?.isTrafficEnabled=traffic
        style=value
    }

    fun selectTraffic(value: Boolean) {
        map?.isTrafficEnabled=value
        traffic=value
        saveTraffic(value)
    }

    internal fun attach(map: AMap, onCenter: () -> Unit) {
        this.map = map
        centerAction = onCenter
        map.mapType=style.sdkType
        map.isTrafficEnabled=traffic
    }

    internal fun detach(map: AMap) {
        if (this.map === map) {
            this.map = null
            centerAction = null
        }
    }
}

@Composable
fun rememberMapUiController(store: AppStore,dark: Boolean=false): MapUiController {
    val controller=remember(store) {
        MapUiController(store.mapStyle,store.mapTraffic,{store.mapStyle=it},{store.mapTraffic=it},initialDark=dark)
    }
    SideEffect {controller.applyTheme(dark)}
    return controller
}

/** Full-size background; overlay [bottomPadding] and [topPadding] are measured in pixels. */
@Composable
internal fun LiveMap(
    state: UiState,
    runtime: SimulationState,
    onPick: (GeoPoint) -> Unit,
    controller: MapUiController,
    bottomPadding: Int = 0,
    topPadding: Int = 0,
    workspace: MapWorkspaceState = MapWorkspaceState(),
    onWorkspacePick: (GeoPoint) -> Unit = {},
    onStrokeComplete: (List<GeoPoint>) -> Unit = {}
) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val onPickNow by rememberUpdatedState(onPick)
    val workspaceNow by rememberUpdatedState(workspace)
    val onWorkspacePickNow by rememberUpdatedState(onWorkspacePick)
    val onStrokeCompleteNow by rememberUpdatedState(onStrokeComplete)
    val mapAccent=remember(state.appearance.accentHex){AppearanceColors.mapAccent(state.appearance.accentHex)}
    // Overlay and sheet state must not recreate the SDK view.
    val view=remember(state.mapGeneration) { runCatching { WorkspaceMapView(context).apply { onCreate(null) } }.getOrNull() }
    if(view==null){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("地图初始化失败，请检查配置后重试")};return}
    val renderer=remember(view){MapRenderer(view)}
    DisposableEffect(view,controller) {
        val attachedMap=view.map
        controller.attach(attachedMap,renderer::center)
        onDispose { controller.detach(attachedMap) }
    }
    DisposableEffect(view,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause()
            else -> Unit
        }}
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))view.onResume() else view.onPause()
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view) {
        onDispose { view.onPause();renderer.close();view.onDestroy() }
    }
    AndroidView(factory={
        view.apply {
            map.setOnMapClickListener { p->
                val point=Coordinates.fromMap(GeoPoint(p.latitude,p.longitude))
                if(workspaceNow.focused) {
                    if(workspaceNow.tool==MapWorkspaceTool.BROWSE)onWorkspacePickNow(point)
                } else onPickNow(point)
            }
            map.setOnMarkerClickListener { marker->
                if(workspaceNow.focused && workspaceNow.tool==MapWorkspaceTool.BROWSE) {
                    val p=marker.position
                    onWorkspacePickNow(Coordinates.fromMap(GeoPoint(p.latitude,p.longitude)))
                    true
                } else false
            }
            previewStroke=renderer::showStroke
            commitStroke={ onStrokeCompleteNow(it) }
        }
    },modifier=Modifier.fillMaxSize(),update={
        it.setWorkspaceDrawing(workspace.focused && workspace.tool==MapWorkspaceTool.DRAW,workspace.sketch)
        renderer.render(state,runtime,bottomPadding,topPadding,workspace,mapAccent)
    })
}

/** Capture before SDK child views. Browse mode retains the SDK's normal pan/pinch/marker handling. */
private class WorkspaceMapView(context: Context): MapView(context) {
    var previewStroke: (List<GeoPoint>) -> Unit = {}
    var commitStroke: (List<GeoPoint>) -> Unit = {}
    private var drawingEnabled=false
    private var consumingGesture=false
    private var stroke: DrawingStrokeSampler?=null
    private var existingSketch=emptyList<GeoPoint>()
    private var pointerId=-1
    private val density=resources.displayMetrics.density

    fun setWorkspaceDrawing(enabled: Boolean,sketch: List<GeoPoint>) {
        existingSketch=sketch
        if(drawingEnabled==enabled)return
        drawingEnabled=enabled
        // A changed tool or disappearing screen must not commit the finger's unfinished stroke.
        cancelStroke()
    }

    private fun cancelStroke() {
        stroke=null
        pointerId=-1
        previewStroke(emptyList())
    }

    private fun projected(x: Float,y: Float): GeoPoint {
        val p=map.projection.fromScreenLocation(Point(x.roundToInt(),y.roundToInt()))
        return Coordinates.fromMap(GeoPoint(p.latitude,p.longitude))
    }

    private fun appendSample(event: MotionEvent,index: Int,history: Int?=null,force: Boolean=false) {
        val sampler=stroke ?: return
        val x=if(history==null)event.getX(index) else event.getHistoricalX(index,history)
        val y=if(history==null)event.getY(index) else event.getHistoricalY(index,history)
        // An exiting finger contributes its last visible position, never an off-screen coordinate.
        val boundedX=x.coerceIn(0f,(width-1).coerceAtLeast(0).toFloat())
        val boundedY=y.coerceIn(0f,(height-1).coerceAtLeast(0).toFloat())
        sampler.append(boundedX,boundedY,projected(boundedX,boundedY),force)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {
            consumingGesture=drawingEnabled
            if(consumingGesture) {
                cancelStroke()
                pointerId=event.getPointerId(0)
                stroke=DrawingStrokeSampler(2f*density)
            }
        }
        val end=event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL
        parent?.requestDisallowInterceptTouchEvent(!end)
        if(!consumingGesture)return super.dispatchTouchEvent(event)
        try {
            when {
                event.actionMasked==MotionEvent.ACTION_CANCEL || event.pointerCount!=1 ||
                    event.actionMasked==MotionEvent.ACTION_POINTER_DOWN || event.actionMasked==MotionEvent.ACTION_POINTER_UP -> cancelStroke()
                event.actionMasked==MotionEvent.ACTION_DOWN || event.actionMasked==MotionEvent.ACTION_MOVE ||
                    event.actionMasked==MotionEvent.ACTION_UP -> {
                    val index=event.findPointerIndex(pointerId)
                    if(index<0)cancelStroke() else if(stroke!=null) {
                        for(history in 0 until event.historySize)appendSample(event,index,history)
                        appendSample(event,index,force=event.actionMasked==MotionEvent.ACTION_UP)
                        previewStroke(stroke?.points.orEmpty())
                        if(event.actionMasked==MotionEvent.ACTION_UP) {
                            val completed=stroke?.complete()
                            // Validate the combined draft before handing even a single stroke to UI state.
                            if(completed!=null)stroke?.appendTo(existingSketch)
                            cancelStroke()
                            if(completed!=null)commitStroke(completed)
                        }
                    }
                }
            }
        } catch(error: IllegalArgumentException) {
            cancelStroke()
            Toast.makeText(context,error.message ?: "描绘未保存，请重试",Toast.LENGTH_SHORT).show()
        } catch(_: Exception) {
            cancelStroke()
            Toast.makeText(context,"地图坐标暂不可用，请重试",Toast.LENGTH_SHORT).show()
        }
        if(end)consumingGesture=false
        // Never performClick or forward the UP from a stroke into the map's point picker.
        return true
    }

    override fun performClick(): Boolean=super.performClick()
}

private class MapRenderer(private val view: MapView) {
    private val map=view.map
    private val density=view.resources.displayMetrics.density
    private var key=""
    private var selected=""
    private var current: Marker?=null
    private var centerPoint: GeoPoint?=null
    private var coveredHeight=0
    private var coveredTop=0
    private var viewport: Viewport?=null
    private var routeBounds: LatLngBounds?=null
    private var previewBounds: LatLngBounds?=null
    private var fitBounds: LatLngBounds?=null
    private var pendingRouteFit=false
    private var visibleRouteKey=""
    private var visiblePreviewKey=""
    private var fittedRouteKey=""
    private var fittingPreview=false
    private var fitKey=""
    private var strokePoints=emptyList<GeoPoint>()
    private var strokeLine: Polyline?=null
    private data class MarkerIcon(val descriptor: BitmapDescriptor,val anchorX: Float,val anchorY: Float)
    private var accentColor=0xFF176349.toInt()
    private val markerIcons=mutableMapOf<Triple<MapMarkerRole,String,Int>,MarkerIcon>()
    private val routeFit=Runnable {if(pendingRouteFit)fitRoute()}
    private data class Viewport(val width: Int,val height: Int,val top: Int,val bottom: Int) {
        val visibleHeight: Int get()=height-top-bottom
    }
    private val layoutListener=View.OnLayoutChangeListener { _,_,_,_,_,_,_,_,_->
        if(positionViewport() && pendingRouteFit)scheduleRouteFit()
    }
    init {
        map.uiSettings.isZoomControlsEnabled=false
        map.uiSettings.isMyLocationButtonEnabled=false
        map.uiSettings.isScaleControlsEnabled=false
        map.uiSettings.setLogoLeftMargin(pixels(16))
        // The ViewModel supplies verified real/selected points; the SDK does not subscribe itself.
        map.isMyLocationEnabled=false
        view.addOnLayoutChangeListener(layoutListener)
    }
    private fun pixels(dp: Int): Int=(dp*density).roundToInt()
    private fun positionViewport(): Boolean {
        if(view.width<=0 || view.height<=0)return false
        val minimumHeight=pixels(72).coerceAtMost(view.height)
        val top=coveredTop.coerceIn(0,view.height-minimumHeight)
        val bottom=coveredHeight.coerceIn(0,view.height-top-minimumHeight)
        val next=Viewport(view.width,view.height,top,bottom)
        if(viewport==next)return false
        // Cache before SDK calls: its layout requests must not re-enter unchanged setters.
        viewport=next
        map.setPointToCenter(next.width/2,next.top+next.visibleHeight/2)
        map.uiSettings.setLogoBottomMargin(next.bottom+pixels(8))
        return true
    }
    private fun fitRoute() {
        val bounds=fitBounds ?: return
        val visible=viewport ?: return
        val padding=minOf(80,(visible.width-1)/2,(visible.visibleHeight-1)/2).coerceAtLeast(0)
        pendingRouteFit=runCatching {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,visible.width,visible.visibleHeight,padding))
        }.isFailure
        if(!pendingRouteFit && !fittingPreview)fittedRouteKey=fitKey
    }
    private fun scheduleRouteFit() {view.removeCallbacks(routeFit);view.postDelayed(routeFit,280)}
    private fun location(point: GeoPoint): LatLng { val p=Coordinates.toMap(point);return LatLng(p.latitude,p.longitude) }
    private fun bounds(points: List<GeoPoint>): LatLngBounds?=if(points.size<2)null else runCatching {
        LatLngBounds.Builder().also { b->points.forEach { b.include(location(it)) } }.build()
    }.getOrNull()
    fun showStroke(points: List<GeoPoint>) {
        strokePoints=points
        if(points.size<2) {strokeLine?.remove();strokeLine=null;return}
        val positions=points.map(::location)
        if(strokeLine==null)strokeLine=map.addPolyline(PolylineOptions().addAll(positions).width(pixels(4).toFloat()).color(AndroidColor.rgb(126,76,172)).zIndex(12f))
        else strokeLine?.points=positions
    }
    private fun markerOptions(point: GeoPoint,role: MapMarkerRole,label: String=""): MarkerOptions {
        val art=markerIcons.getOrPut(Triple(role,label,accentColor)) {
            MapMarkerArt.create(role,label,density,accentColor).let {
                MarkerIcon(BitmapDescriptorFactory.fromBitmap(it.bitmap),it.anchorX,it.anchorY)
            }
        }
        return MarkerOptions().position(location(point)).anchor(art.anchorX,art.anchorY).icon(art.descriptor)
    }
    private fun routeMarker(point: GeoPoint,index: Int,last: Int,name: String,numbered: Boolean=true,closed: Boolean=false) {
        val role=when {index==0 && closed->"起终点";index==0->"起点";index==last && !closed->"终点";else->"途经点"}
        val label=if(numbered)"${index+1}" else ""
        map.addMarker(markerOptions(point,MapMarkerPolicy.route(index,last,closed),label)
            .title(if(numbered)"${index+1} · $role" else role).snippet(name).zIndex(20f))
    }
    fun center() {
        positionViewport()
        centerPoint?.let { map.animateCamera(CameraUpdateFactory.newLatLng(location(it))) }
    }
    fun render(state: UiState,runtime: SimulationState,bottomPadding: Int,topPadding: Int,workspace: MapWorkspaceState,accent: Int) {
        accentColor=accent
        centerPoint=if(!runtime.active && !state.routeMode && !state.locationReady)null else if(runtime.active)runtime.point ?: runtime.routePoints.firstOrNull() ?: state.selected.point
            else if(state.routeMode)state.routeWaypoints.lastOrNull()?.point ?: state.selected.point else state.selected.point
        coveredHeight=bottomPadding.coerceAtLeast(0)
        coveredTop=topPadding.coerceAtLeast(0)
        val viewportChanged=positionViewport()
        // The active route is a service-owned snapshot; edits never replace its line.
        val path=if(runtime.active)runtime.routePoints else if(state.routeMode && !state.routeEditing)state.route else emptyList()
        val pathKey=if(path.size>=2)path.hashCode().toString() else ""
        val previewKey=if(workspace.previewPath.size>=2)"${workspace.previewPath.hashCode()}|${workspace.previewName}" else ""
        val routeChanged=pathKey!=visibleRouteKey
        val previewChanged=previewKey!=visiblePreviewKey
        // A late road-planning response must not move the canvas under a drawing finger.
        if(workspace.focused && pendingRouteFit && !fittingPreview) {
            pendingRouteFit=false;fitBounds=null;view.removeCallbacks(routeFit)
        }
        val baseKey=if(runtime.active)"session|$pathKey|${runtime.name}" else "${state.locationReady}|${state.hasPickedPoint}|${state.selected.hashCode()}|${state.routeMode}|${state.routeEditing}|${state.routeClosed}|${state.routeWaypoints.hashCode()}|$pathKey"
        val next="$baseKey|$accentColor|${workspace.markers.hashCode()}|${workspace.pickedPlace.hashCode()}|${workspace.sketch.hashCode()}|$previewKey"
        val redraw=key!=next
        if(redraw) {
            map.clear();current=null;routeBounds=null;previewBounds=null;strokeLine=null
            if(runtime.active) {
                if(path.size>=2) {
                    if(path.first()==path.last())routeMarker(path.first(),0,0,"本次会话起终点",numbered=false,closed=true)
                    else {
                        routeMarker(path.first(),0,1,"本次会话起点",numbered=false)
                        routeMarker(path.last(),1,1,"本次会话终点",numbered=false)
                    }
                }
            } else if(state.routeMode) {
                state.routeWaypoints.forEachIndexed { index,place->routeMarker(place.point,index,state.routeWaypoints.lastIndex,place.name,closed=state.routeClosed) }
                if(path.isEmpty() && state.routeWaypoints.size>=2) {
                    val draft=state.routeWaypoints.map {it.point}
                    val outline=runCatching {RouteGeometry.planningWaypoints(draft,state.routeClosed)}.getOrDefault(draft)
                    map.addPolyline(PolylineOptions().addAll(outline.map(::location)).width(pixels(3).toFloat()).color(AndroidColor.rgb(105,131,143)).setDottedLine(true))
                }
            } else {
                MapMarkerPolicy.single(state.locationReady,state.hasPickedPoint)?.let {role->
                    map.addMarker(markerOptions(state.selected.point,role).title(state.selected.name).zIndex(18f))
                }
            }
            if(path.size>=2) {
                val points=path.map(::location)
                map.addPolyline(PolylineOptions().addAll(points).width(pixels(4).toFloat()).color(accentColor))
                routeBounds=bounds(path)
            }
            workspace.markers.filterNot {it.id==workspace.pickedPlace?.id}.forEach {place->
                map.addMarker(markerOptions(place.point,MapMarkerRole.ANNOTATION)
                    .title(place.name).snippet(place.detail).zIndex(22f))
            }
            workspace.pickedPlace?.let {place->
                map.addMarker(markerOptions(place.point,MapMarkerRole.SELECTED)
                    .title(place.name).snippet(place.detail).zIndex(30f))
            }
            if(workspace.sketch.size>=2) {
                map.addPolyline(PolylineOptions().addAll(workspace.sketch.map(::location)).width(pixels(4).toFloat())
                    .color(AndroidColor.rgb(126,76,172)).zIndex(9f))
                routeMarker(workspace.sketch.first(),0,1,"描绘起点",numbered=false)
                routeMarker(workspace.sketch.last(),1,1,"描绘终点",numbered=false)
            }
            if(workspace.previewPath.size>=2) {
                map.addPolyline(PolylineOptions().addAll(workspace.previewPath.map(::location)).width(pixels(5).toFloat())
                    .color(AndroidColor.rgb(45,104,182)).zIndex(10f))
                val closed=workspace.previewPath.first()==workspace.previewPath.last()
                routeMarker(workspace.previewPath.first(),0,1,workspace.previewName,numbered=false,closed=closed)
                if(!closed)routeMarker(workspace.previewPath.last(),1,1,workspace.previewName,numbered=false)
                previewBounds=bounds(workspace.previewPath)
            }
            // Base geometry can redraw while a finger is down. Preserve its uncommitted live overlay.
            showStroke(strokePoints)
            // Only single-point selection recenters automatically. Adding a route waypoint
            // must preserve the user's pan/zoom so consecutive taps remain predictable.
            if(!workspace.focused && !runtime.active && !state.routeMode && state.locationReady && selected!=state.selected.id)map.moveCamera(CameraUpdateFactory.newLatLngZoom(location(state.selected.point),15f))
            selected=if(state.locationReady)state.selected.id else "";key=next
        }
        // Fit an explicitly chosen favorite only when the preview changes, never when panels hide/show.
        if(previewChanged) {
            if(previewKey.isNotEmpty()) {
                fitBounds=previewBounds;fitKey=previewKey;fittingPreview=true;pendingRouteFit=fitBounds!=null
            } else {
                pendingRouteFit=false;fitBounds=null;view.removeCallbacks(routeFit)
            }
        }
        if(routeChanged && previewKey.isEmpty() && !workspace.focused) {
            fitBounds=routeBounds;fitKey=pathKey;fittingPreview=false
            pendingRouteFit=fitBounds!=null && fitKey!=fittedRouteKey
        }
        visibleRouteKey=pathKey;visiblePreviewKey=previewKey
        if(pendingRouteFit && (viewportChanged || redraw))scheduleRouteFit()
        if(runtime.active && runtime.point!=null) {
            val p=location(runtime.point)
            if(current==null)current=map.addMarker(markerOptions(runtime.point,MapMarkerRole.MOVING).title("当前模拟位置").zIndex(40f)) else current?.position=p
        } else {current?.remove();current=null}
    }
    fun close() {
        view.removeCallbacks(routeFit);view.removeOnLayoutChangeListener(layoutListener)
        strokeLine?.remove();strokeLine=null
        // Cached assets live exactly as long as this map, never one new bitmap per location tick.
        runCatching {map.clear()}
        markerIcons.values.forEach {runCatching {it.descriptor.recycle()}}
        markerIcons.clear()
    }
}
