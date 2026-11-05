@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.coordinate.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.coordinate.lab.core.RouteGeometry
import app.coordinate.lab.core.RoutePlayer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Uses the same second-line slot as collapsed point blocks: no extra toolbar or height change. */
@Composable internal fun RouteEditorHeader(state: UiState,onAdd: ()->Unit,onManual: ()->Unit,onReverse: ()->Unit,onRoad: (Boolean)->Unit,onClosed: (Boolean)->Unit,onSaveRoute: ()->Unit) {
    var menu by remember {mutableStateOf(false)}
    var routeStyle by remember {mutableStateOf(false)}
    val drawn=state.loadedFavoriteRoute?.kind==SavedRouteKind.DRAWN
    val preview=remember(state.route,state.routeEditing,state.routeError,state.speed) {
        if(state.routeEditing || state.routeError!=null || state.route.size<2)null
        else runCatching {RoutePlayer(state.route,state.speed)}.getOrNull()
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        val summary=preview?.let {
            val distance=if(it.distanceMeters<1000)"${it.distanceMeters.toInt()} m" else String.format(Locale.US,"%.2f km",it.distanceMeters/1000)
            "$distance · ${ceil(it.durationSeconds/60).toInt().coerceAtLeast(1)} 分"
        } ?: "${state.routeWaypoints.size} 个地点"
        Text(summary,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        if(state.routeClosed)Icon(AppIcons.Loop,"闭环路线已开启",Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary)
        if(!drawn)IconButton(onClick=onAdd,enabled=state.routeWaypoints.size<RouteGeometry.MAX_WAYPOINTS,modifier=Modifier.size(44.dp)){Icon(AppIcons.Add,"添加路线点",Modifier.size(21.dp))}
        Box {
            IconButton(onClick={menu=true},modifier=Modifier.size(44.dp)){Icon(AppIcons.More,"更多路线操作",Modifier.size(21.dp))}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                DropdownMenuItem(text={Text("收藏路线")},enabled=state.routeWaypoints.size>=2,onClick={menu=false;onSaveRoute()},leadingIcon={Icon(AppIcons.BookmarkBorder,null)})
                DropdownMenuItem(text={Text("反转路线")},enabled=state.routeWaypoints.size>=2,onClick={menu=false;onReverse()},leadingIcon={Icon(AppIcons.Swap,null)})
                if(!drawn)DropdownMenuItem(text={Text("输入坐标")},onClick={menu=false;onManual()},leadingIcon={Icon(AppIcons.Edit,null)})
                if(!drawn)DropdownMenuItem(text={Text("路线方式")},onClick={menu=false;routeStyle=true},leadingIcon={Icon(AppIcons.Route,null)})
                DropdownMenuItem(text={Text("闭环路线")},onClick={menu=false;onClosed(!state.routeClosed)},modifier=Modifier.semantics {stateDescription=if(state.routeClosed)"已开启" else "已关闭"},leadingIcon={Icon(AppIcons.Loop,null)},trailingIcon={Switch(checked=state.routeClosed,onCheckedChange=null)})
            }
        }
    }
    if(routeStyle)RouteStyleDialog(road=state.road,onDismiss={routeStyle=false},onSelect={routeStyle=false;onRoad(it)})
}

@Composable private fun RouteStyleDialog(road: Boolean,onDismiss: ()->Unit,onSelect: (Boolean)->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("路线方式")},text={
        Column(Modifier.selectableGroup()) {
            listOf(false,true).forEach {followRoads->
                Row(Modifier.fillMaxWidth().selectable(selected=road==followRoads,role=Role.RadioButton,onClick={onSelect(followRoads)}).padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected=road==followRoads,onClick=null)
                    Column {
                        Text(if(followRoads)"沿道路 · 步行" else "直线",style=MaterialTheme.typography.bodyLarge)
                        if(followRoads)Text("离道路的地点以直线接驳",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("取消")}})
}

/** The waypoint list owns vertical scrolling; speed stays within thumb reach. */
@Composable internal fun RouteEditorPanel(
    state: UiState,onMove: (Int,Int)->Unit,onRemove: (Int)->Unit,
    onSpeed: (Double)->Unit,modifier: Modifier=Modifier
) {
    // Only shown when no session is running. Draft edits can cancel planning, like the header.
    var speed by remember(state.speed) {mutableFloatStateOf(state.speed.toFloat())}
    val scale=LocalDensity.current.fontScale.coerceIn(1f,1.4f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scrollFallback=maxHeight<200.dp*scale
        val panelModifier=if(scrollFallback)Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()
        Column(panelModifier.padding(horizontal=18.dp)) {
            if(state.planning) {
                LinearProgressIndicator(progress={state.planningLeg.toFloat()/state.planningTotalLegs.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().padding(vertical=6.dp).semantics {contentDescription="路线规划，已完成 ${state.planningLeg}/${state.planningTotalLegs} 段"})
            } else if(state.routeError!=null) {
                // The shared confirmation button retries; do not duplicate its action here.
                Text(state.routeError,Modifier.padding(vertical=6.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error,maxLines=2,overflow=TextOverflow.Ellipsis)
            } else if(state.route.size>=2 && !state.routeEditing && state.routeConnectors>0) {
                Text("含 ${state.routeConnectors} 段直线接驳",Modifier.padding(vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(state.loadedFavoriteRoute?.kind==SavedRouteKind.DRAWN) {
                Column((if(scrollFallback)Modifier.height(180.dp*scale) else Modifier.weight(1f)).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(state.loadedFavoriteRoute.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text("手绘轨迹 · ${state.route.size} 个轨迹点",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else RouteWaypointList(state.routeWaypoints,enabled=true,onMove=onMove,onRemove=onRemove,modifier=if(scrollFallback)Modifier.height(180.dp*scale) else Modifier.weight(1f))
            HorizontalDivider(Modifier.padding(top=6.dp),color=MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(top=8.dp,bottom=8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("移动速度",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${speed.roundToInt()} km/h",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary,modifier=Modifier.semantics {contentDescription="移动速度 ${speed.roundToInt()} 千米每小时"})
                }
                Slider(value=speed,onValueChange={speed=it},onValueChangeFinished={onSpeed(speed.roundToInt().toDouble())},valueRange=1f..30f,steps=28,modifier=Modifier.fillMaxWidth().semantics {contentDescription="调整移动速度"})
            }
        }
    }
}
