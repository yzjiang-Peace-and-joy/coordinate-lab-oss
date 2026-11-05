@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.coordinate.lab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import app.coordinate.lab.core.GeoPoint
import app.coordinate.lab.core.Geo
import app.coordinate.lab.core.SessionClock
import app.coordinate.lab.core.InjectionStatus
import app.coordinate.lab.core.RoutePrimaryAction
import app.coordinate.lab.core.RoutePrimaryButton
import app.coordinate.lab.core.RoutePrimaryButtons
import app.coordinate.lab.core.SimulationButtonStatus
import kotlinx.coroutines.launch
import java.util.Locale

/** The map owns the canvas; controls live in a persistent, two-position bottom sheet. */
@Composable internal fun MapHome(
    vm: AppViewModel,state: UiState,runtime: SimulationState,now: Long,
    onSearch: ()->Unit,onSetup: ()->Unit,onManual: ()->Unit,onStart: ()->Unit,
    modifier: Modifier=Modifier,focused: Boolean=false,onFocusChange: (Boolean)->Unit={}
) {
    val systemDark=isSystemInDarkTheme()
    val dark=when(state.appearance.theme){ThemeMode.SYSTEM->systemDark;ThemeMode.LIGHT->false;ThemeMode.DARK->true}
    val controller=rememberMapUiController(vm.store,dark)
    var layerPicker by remember {mutableStateOf(false)}
    val sheet=rememberStandardBottomSheetState(initialValue=SheetValue.PartiallyExpanded,skipHiddenState=true)
    val scaffold=rememberBottomSheetScaffoldState(bottomSheetState=sheet)
    val scope=rememberCoroutineScope()
    var workspaceTool by remember {mutableStateOf(MapWorkspaceTool.BROWSE)}
    val strokes=state.workspaceStrokes
    val sketch=remember(strokes){strokes.flatten()}
    var picked by remember {mutableStateOf<Place?>(null)}
    var nameRequest by remember {mutableStateOf<WorkspaceNameRequest?>(null)}
    var exitPrompt by remember {mutableStateOf(false)}
    var clearSketchPrompt by remember {mutableStateOf(false)}
    val exitFocus: ()->Unit={
        if(sketch.isNotEmpty())exitPrompt=true
        else {picked=null;workspaceTool=MapWorkspaceTool.BROWSE;vm.clearSavedRoutePreview();onFocusChange(false)}
    }
    val pickWorkspacePoint: (GeoPoint)->Unit={point->
        picked=state.mapMarkers.firstOrNull {Geo.distance(it.point,point)<3.0}
            ?: Place("选定位置",state.city,point,"")
    }
    // Workspace addresses are metadata only; never change the normal selected place or route draft.
    LaunchedEffect(picked?.id,focused,state.mapMarkers.any {it.id==picked?.id}) {
        val pending=picked
        if(focused && pending!=null && state.mapEnabled && state.mapMarkers.none {it.id==pending.id}) {
            kotlinx.coroutines.delay(450)
            var request: SearchCancellation?=null
            try {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> {continuation->
                    request=vm.gateway.pointAddress(pending.point){result->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if(continuation.isActive) {
                                if(picked?.id==pending.id && vm.state.value.mapMarkers.none {it.id==pending.id})result.onSuccess {picked=it}
                                continuation.resumeWith(Result.success(Unit))
                            }
                        }
                    }
                    continuation.invokeOnCancellation {request?.cancel()}
                }
            } finally {request?.cancel()}
        }
    }
    val addMapPoint: ()->Unit={vm.beginRouteEdit();scope.launch {sheet.partialExpand()};Unit}
    val expanded=!focused && sheet.targetValue==SheetValue.Expanded
    val toggle: ()->Unit={scope.launch {if(expanded)sheet.partialExpand() else sheet.expand()};Unit}
    BackHandler(enabled=expanded) {scope.launch {sheet.partialExpand()}}
    BackHandler(enabled=focused) {
        if(picked!=null)picked=null else exitFocus()
    }
    val density=LocalDensity.current
    val primary=RoutePrimaryButtons.resolve(runtime.active,state.routeMode,state.routeEditing,state.planning,state.routeError,state.routeWaypoints.size,state.route.size).let {
        if(!runtime.active && !state.routeMode && !state.locationReady)it.copy(enabled=false) else it
    }

    BoxWithConstraints(modifier) {
        val peek=(124.dp*density.fontScale.coerceIn(1f,1.4f)).coerceAtMost(maxHeight)
        // Both modes use identical sheet anchors as well as identical summary row heights.
        val targetHeight=maxHeight*0.64f
        val minimumEditorHeight=peek+240.dp*density.fontScale.coerceIn(1f,1.4f)
        val expandedHeight=targetHeight.coerceAtMost(560.dp).coerceAtLeast(minimumEditorHeight).coerceAtMost(maxHeight)
        val topOverlayHeight=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+if(focused)64.dp else 84.dp
        val focusBottomPadding=with(density){WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().roundToPx()}
        val totalHeightPx=with(density){maxHeight.toPx()}
        val coveredPx by remember(sheet,totalHeightPx,peek) {derivedStateOf {
            val offset=runCatching {sheet.requireOffset()}.getOrNull()
            if(offset==null || !offset.isFinite())with(density){peek.roundToPx()}
            else (totalHeightPx-offset).coerceIn(0f,totalHeightPx).toInt()
        }}
        BottomSheetScaffold(
            modifier=Modifier.fillMaxSize(),scaffoldState=scaffold,
            sheetPeekHeight=if(focused)0.dp else peek,sheetShape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),
            sheetContainerColor=if(focused)Color.Transparent else MaterialTheme.colorScheme.surface,
            sheetShadowElevation=if(focused)0.dp else 14.dp,sheetTonalElevation=0.dp,sheetDragHandle=null,
            sheetSwipeEnabled=!focused,
            containerColor=MaterialTheme.colorScheme.background,
            sheetContent={
                if(!focused) {
                Column(Modifier.fillMaxWidth().height(expandedHeight).semantics {paneTitle="地点与模拟控制"}) {
                    SheetSummary(state,runtime,now,peek,primary,toggle,onPrimary={
                        when (primary.action) {
                            RoutePrimaryAction.CONFIRM -> {
                                vm.confirmRoutePreview()
                            }
                            RoutePrimaryAction.START, RoutePrimaryAction.STOP -> onStart()
                            RoutePrimaryAction.PLANNING -> Unit
                        }
                    },onSwitchMode={
                        if(!runtime.active)vm.setRouteMode(!state.routeMode)
                    },onReorder=vm::reorderRoutePoint,onUndo=vm::undoRouteEdit,onClear=vm::clearRoute,expanded=expanded,routeDetails={
                        RouteEditorHeader(state,onAdd=addMapPoint,onManual={vm.beginRouteEdit();onManual()},onReverse=vm::reverseRoute,onRoad=vm::road,onClosed=vm::closedRoute,
                            onSaveRoute={nameRequest=WorkspaceNameRequest(WorkspaceSaveTarget.CURRENT_ROUTE)})
                    })
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    if(state.routeMode && !runtime.active) {
                        RouteEditorPanel(state,onMove=vm::reorderRoutePoint,onRemove=vm::removeRoutePoint,onSpeed=vm::speed,modifier=Modifier.weight(1f))
                    } else {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=14.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            if(!state.routeMode)SelectedPlaceCard(state,vm,runtime,onManual)
                            if(runtime.active && runtime.routePoints.size>=2)RuntimeDetails(runtime,now)
                        }
                    }
                }
                } else Spacer(Modifier.fillMaxWidth().height(0.dp))
            }
        ) { _ ->
            // Do not apply the sheet's content padding to the map: it must remain full size.
            Box(Modifier.fillMaxSize()) {
                if(state.mapEnabled)key(state.mapGeneration) {
                    val workspace=MapWorkspaceState(focused=focused,tool=workspaceTool,
                        markers=state.mapMarkers,pickedPlace=picked,
                        sketch=sketch,previewPath=state.previewSavedRoute?.points.orEmpty(),previewName=state.previewSavedRoute?.name.orEmpty())
                    LiveMap(state,runtime,vm::mapPick,controller,bottomPadding=if(focused)focusBottomPadding else coveredPx,topPadding=with(density){topOverlayHeight.roundToPx()},
                        workspace=workspace,onWorkspacePick=pickWorkspacePoint,onStrokeComplete={stroke->
                            if(vm.appendWorkspaceStroke(stroke))picked=null
                        })
                } else HomeMapPlaceholder(state.keyConfigured,onSetup)

                if(!focused)Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal=16.dp).padding(top=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Surface(onClick=onSearch,enabled=!runtime.active && !state.planning,shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=5.dp) {
                        Row(Modifier.fillMaxWidth().heightIn(min=58.dp).padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(AppIcons.Search,null,tint=MaterialTheme.colorScheme.primary)
                            Text(if(state.routeMode)"搜索地点，添加路线点" else "搜索地点、地标或地址",Modifier.weight(1f).padding(horizontal=12.dp),style=MaterialTheme.typography.bodyLarge,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if(state.searchScope==SearchScope.NATIONWIDE)"全国" else state.city.ifBlank {"附近"},style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,maxLines=1,modifier=Modifier.widthIn(max=72.dp),overflow=TextOverflow.Ellipsis)
                        }
                    }
                }

                if(!focused && !expanded) {
                    Column(Modifier.align(Alignment.CenterEnd).padding(end=16.dp,bottom=peek),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.End) {
                        MapTool(AppIcons.MyLocation,when {runtime.active->"回到当前模拟点";state.routeMode->"回到末个路线点";!state.hasPickedPoint->"回到我的位置";else->"回到已选地点"},state.mapEnabled,controller::center)
                        MapTool(AppIcons.Layers,"地图类型：${controller.style.label}${if(controller.traffic)"，实时路况已开启" else ""}",state.mapEnabled,{layerPicker=true})
                        MapTool(AppIcons.Lock,"锁定为全屏地图",state.mapEnabled,{onFocusChange(true)})
                    }
                }
                if(focused)MapFocusOverlay(workspaceTool,sketch.size,strokes.size,picked,
                    marked=picked?.let {point->state.mapMarkers.any {it.id==point.id}}==true,preview=state.previewSavedRoute,
                    onExit=exitFocus,onTool={workspaceTool=it;picked=null;if(it==MapWorkspaceTool.DRAW)vm.clearSavedRoutePreview()},
                    onUndo=vm::undoWorkspaceStroke,onClear={clearSketchPrompt=true},
                    onSaveSketch={nameRequest=WorkspaceNameRequest(WorkspaceSaveTarget.SKETCH)},
                    onClearPreview=vm::clearSavedRoutePreview,
                    onSavePlace={picked?.let {nameRequest=WorkspaceNameRequest(WorkspaceSaveTarget.FAVORITE,it)}},
                    onMark={picked?.let {nameRequest=WorkspaceNameRequest(WorkspaceSaveTarget.MARKER,it)}},
                    onRemoveMark={picked?.let {vm.removeMapMarker(it.id);picked=null}},onDismissPick={picked=null})
                // Map layers are independent of app theme. Protect system icons over either layer.
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha=.94f)))
                if(focused)Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha=.94f)))
            }
        }
    }
    if(layerPicker)MapLayerSheet(controller.style,controller.traffic,onStyle={style->runCatching {controller.selectStyle(style)}.onFailure {vm.notice("地图类型切换失败，请重试")}},onTraffic={value->runCatching {controller.selectTraffic(value)}.onFailure {vm.notice("路况切换失败，请重试")}},onDismiss={layerPicker=false})
    if(clearSketchPrompt)AlertDialog(onDismissRequest={clearSketchPrompt=false},title={Text("清空描线？")},
        confirmButton={TextButton(onClick={vm.clearWorkspaceSketch();clearSketchPrompt=false}){Text("清空")}},
        dismissButton={TextButton(onClick={clearSketchPrompt=false}){Text("保留")}})
    if(exitPrompt)AlertDialog(onDismissRequest={exitPrompt=false},title={Text("保存这条描线？")},
        confirmButton={TextButton(onClick={exitPrompt=false;nameRequest=WorkspaceNameRequest(WorkspaceSaveTarget.SKETCH,exitAfter=true)}){Text("命名收藏")}},
        dismissButton={TextButton(onClick={exitPrompt=false;vm.clearWorkspaceSketch();picked=null;workspaceTool=MapWorkspaceTool.BROWSE;vm.clearSavedRoutePreview();onFocusChange(false)}){Text("舍弃并退出")}})
    nameRequest?.let {request->
        val title=when(request.target){
            WorkspaceSaveTarget.CURRENT_ROUTE,WorkspaceSaveTarget.SKETCH->"收藏路线"
            WorkspaceSaveTarget.FAVORITE->"收藏地点"
            WorkspaceSaveTarget.MARKER->if(state.mapMarkers.any {it.id==request.place?.id})"重命名标记" else "添加标记"
        }
        val initial=when(request.target){
            WorkspaceSaveTarget.CURRENT_ROUTE->state.routeWaypoints.let {if(it.size>=2)"${it.first().name} → ${it.last().name}".take(40) else "我的路线"}
            WorkspaceSaveTarget.SKETCH->"手绘路线 ${state.savedRoutes.count {it.kind==SavedRouteKind.DRAWN}+1}"
            else->request.place?.name?.takeUnless {it=="选定位置"}?.take(40).orEmpty()
        }
        RouteNameDialog(title,initial,onDismiss={nameRequest=null},onSave={name->
            val success=when(request.target){
                WorkspaceSaveTarget.CURRENT_ROUTE->vm.saveCurrentRoute(name)
                WorkspaceSaveTarget.SKETCH->vm.saveDrawnRoute(name,sketch)
                WorkspaceSaveTarget.FAVORITE->request.place?.let {vm.saveFavoritePlace(it.copy(name=name))}==true
                WorkspaceSaveTarget.MARKER->request.place?.let {vm.addMapMarker(it.copy(name=name))}==true
            }
            if(success) {
                nameRequest=null
                if(request.target==WorkspaceSaveTarget.SKETCH) {
                    vm.clearWorkspaceSketch();workspaceTool=MapWorkspaceTool.BROWSE
                    if(!request.exitAfter)vm.state.value.savedRoutes.firstOrNull()?.let {vm.previewSavedRoute(it.id)}
                }
                if(request.target==WorkspaceSaveTarget.MARKER)picked=request.place?.copy(name=name)
                vm.notice("已保存")
                if(request.exitAfter){picked=null;workspaceTool=MapWorkspaceTool.BROWSE;vm.clearSavedRoutePreview();onFocusChange(false)}
            }
            success
        })
    }
}

private enum class WorkspaceSaveTarget { CURRENT_ROUTE, SKETCH, FAVORITE, MARKER }
private data class WorkspaceNameRequest(val target: WorkspaceSaveTarget,val place: Place?=null,val exitAfter: Boolean=false)

@Composable private fun SheetSummary(state: UiState,runtime: SimulationState,now: Long,height: androidx.compose.ui.unit.Dp,primary: RoutePrimaryButton,onToggle: ()->Unit,onPrimary: ()->Unit,onSwitchMode: ()->Unit,onReorder: (Int,Int)->Unit,onUndo: ()->Unit,onClear: ()->Unit,expanded: Boolean,routeDetails: @Composable ()->Unit) {
    val routeMode=if(runtime.active)runtime.routePoints.size>=2 else state.routeMode
    val viewConfiguration=LocalViewConfiguration.current
    val compactModeTarget=remember(viewConfiguration) {
        object: ViewConfiguration by viewConfiguration {
            // Only this inline switch opts out of invisible touch expansion into the drag area.
            override val minimumTouchTargetSize=DpSize.Zero
        }
    }
    Column(Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxWidth().height(22.dp).semantics {
            contentDescription="拖动抽屉"
            customActions=listOf(CustomAccessibilityAction(if(expanded)"收起抽屉" else "展开抽屉"){onToggle();true})
        },contentAlignment=Alignment.Center) {Box(Modifier.size(34.dp,4.dp).background(MaterialTheme.colorScheme.outlineVariant,CircleShape))}
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal=18.dp).padding(bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalViewConfiguration provides compactModeTarget) {
                        Row(Modifier.clickable(enabled=!runtime.active,onClickLabel="切换定位模式",onClick=onSwitchMode),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(if(routeMode)"多点路线" else "单点定位",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Icon(AppIcons.Swap,null,Modifier.size(18.dp),tint=if(runtime.active)MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if(routeMode && !runtime.active){
                        IconButton(onClick=onUndo,enabled=!runtime.active && state.canUndoRoute,modifier=Modifier.size(44.dp)){Icon(AppIcons.Undo,"撤销路线修改",Modifier.size(21.dp))}
                        IconButton(onClick=onClear,enabled=!runtime.active && state.routeWaypoints.isNotEmpty(),modifier=Modifier.size(44.dp)){Icon(AppIcons.ClearAll,"清空所有路线点",Modifier.size(21.dp))}
                    }
                }
                Box(Modifier.fillMaxWidth().height(44.dp),contentAlignment=Alignment.CenterStart) {
                    if(routeMode && expanded && !runtime.active)routeDetails()
                    else if(routeMode && state.loadedFavoriteRoute?.kind==SavedRouteKind.DRAWN)Text(state.loadedFavoriteRoute.name,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    else if(routeMode)RoutePointStrip(state.routeWaypoints,enabled=!runtime.active,onMove=onReorder)
                    else Text(if(runtime.active)runtime.name else if(state.resolvingPlace)"获取位置…" else state.selected.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
            SimulationPrimaryControl(primary,runtime,now,onPrimary)
        }
    }
}

/** A single action target expands for real session state; never optimistically claims successful writes. */
@Composable internal fun SimulationPrimaryControl(primary: RoutePrimaryButton,runtime: SimulationState,now: Long,onClick: ()->Unit) {
    val status=SimulationButtonStatus.resolve(runtime.active,runtime.health.status(runtime.active,now),runtime.paused,runtime.arrived)
    val label=SessionClock.format(SessionClock.seconds(runtime.active,runtime.startedAtElapsedMs,now))
    val colors=MaterialTheme.colorScheme
    val container=when {
        status==SimulationButtonStatus.STALE->colors.errorContainer
        runtime.active->colors.secondaryContainer
        else->colors.primary
    }
    val foreground=when {
        status==SimulationButtonStatus.STALE->colors.onErrorContainer
        runtime.active->colors.onSecondaryContainer
        else->colors.onPrimary
    }
    val labelWidth=80.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)
    Button(onClick=onClick,enabled=primary.enabled,modifier=Modifier.animateContentSize(animationSpec=tween(200)).height(52.dp).widthIn(min=52.dp).semantics {
        if(runtime.active){contentDescription="停止模拟并恢复真实定位";stateDescription=if(status==SimulationButtonStatus.STALE)"更新中断，已运行 $label" else "已运行 $label"}
    },shape=RoundedCornerShape(17.dp),contentPadding=if(runtime.active)PaddingValues(horizontal=14.dp) else PaddingValues(0.dp),colors=ButtonDefaults.buttonColors(containerColor=container,contentColor=foreground)) {
        when(primary.action){
            RoutePrimaryAction.PLANNING->CircularProgressIndicator(Modifier.size(24.dp).semantics {contentDescription="正在规划路线"},color=foreground,strokeWidth=2.dp)
            RoutePrimaryAction.CONFIRM->Icon(AppIcons.Check,"确认路线",Modifier.size(24.dp))
            RoutePrimaryAction.START->Icon(AppIcons.PlayArrow,"开始模拟",Modifier.size(24.dp))
            RoutePrimaryAction.STOP->Icon(AppIcons.Pause,null,Modifier.size(24.dp))
        }
        if(runtime.active) {
            Spacer(Modifier.width(8.dp))
            Text(label,Modifier.width(labelWidth).clearAndSetSemantics {},fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun SimulationStatusChip(runtime: SimulationState,now: Long) {
    val status=runtime.health.status(runtime.active,now)
    Surface(shape=CircleShape,color=when(status){InjectionStatus.STALE->MaterialTheme.colorScheme.errorContainer;InjectionStatus.FRESH->MaterialTheme.colorScheme.primaryContainer;else->MaterialTheme.colorScheme.surface.copy(alpha=.96f)},shadowElevation=2.dp) {
        Text(if(runtime.active)"${if(status==InjectionStatus.STALE)"! " else ""}${SessionClock.format(SessionClock.seconds(true,runtime.startedAtElapsedMs,now))}" else "○ 未模拟",Modifier.padding(horizontal=12.dp,vertical=8.dp),style=MaterialTheme.typography.labelMedium,color=if(status==InjectionStatus.STALE)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable private fun MapTool(icon: ImageVector,label: String,enabled: Boolean,onClick: ()->Unit,tint: Color=MaterialTheme.colorScheme.onSurfaceVariant) {
    // Compact 40dp visual, but retain a 48dp target so tapping does not accidentally pick the map.
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp)) {
        Surface(Modifier.size(40.dp),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.96f),shadowElevation=1.dp) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Icon(icon,label,Modifier.size(20.dp),tint=if(enabled)tint else tint.copy(alpha=.38f))
            }
        }
    }
}

@Composable private fun SelectedPlaceCard(state: UiState,vm: AppViewModel,runtime: SimulationState,onManual: ()->Unit) {
    if(!state.locationReady && !runtime.active) {
        Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer) {
            Text(state.selected.name,Modifier.padding(18.dp),style=MaterialTheme.typography.titleMedium)
        }
        return
    }
    val point=if(runtime.active)runtime.point ?: state.selected.point else state.selected.point
    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(runtime.active)runtime.name else if(state.resolvingPlace)"获取位置…" else state.selected.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onPrimaryContainer,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                val saved=state.favorites.any{it.id==state.selected.id}
                if(!runtime.active)IconButton(onClick=onManual){Icon(AppIcons.Edit,"编辑坐标",tint=MaterialTheme.colorScheme.onPrimaryContainer)}
                IconButton(onClick={vm.favorite(state.selected)}){Icon(if(saved)AppIcons.Bookmark else AppIcons.BookmarkBorder,if(saved)"取消收藏地点" else "收藏地点",tint=MaterialTheme.colorScheme.onPrimaryContainer)}
            }
            Row(Modifier.fillMaxWidth().clickable(enabled=!runtime.active,onClickLabel="编辑坐标",onClick=onManual),verticalAlignment=Alignment.CenterVertically) {
                CoordinateValue("纬度",point.latitude,Modifier.weight(1f))
                CoordinateValue("经度",point.longitude,Modifier.weight(1f))
            }
            val address=state.selected.detail.takeUnless {it.startsWith("地图点选") || it.startsWith("离线城市示例")}.orEmpty()
            if(address.isNotBlank() && !runtime.active)Text(address,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onPrimaryContainer,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun CoordinateValue(label: String,value: Double,modifier: Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.7f))
        Text(String.format(Locale.US,"%.6f°",value),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onPrimaryContainer,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable private fun RuntimeDetails(runtime: SimulationState,now: Long) {
    val context=LocalContext.current
    var diagnostics by remember {mutableStateOf(false)}
    val remaining=kotlin.math.ceil((runtime.durationSeconds-runtime.motionSeconds).coerceAtLeast(0.0)).toLong()
    val speed=if(runtime.paused || runtime.arrived)0.0 else runtime.speedKph
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            RuntimeMetric("全程",homeDistance(runtime.distanceMeters))
            RuntimeMetric("速度",String.format(Locale.US,"%.0f km/h",speed))
            RuntimeMetric("剩余",homeDuration(remaining))
        }
        LinearProgressIndicator(progress={runtime.progress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(runtime.arrived)"已到达" else "${(runtime.progress*100).toInt().coerceIn(0,100)}%",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            IconButton(onClick={diagnostics=true}){Icon(AppIcons.Tune,"查看运行诊断")}
        }
        if(runtime.health.status(true,now)==InjectionStatus.STALE)Text("定位更新中断",color=MaterialTheme.colorScheme.error)
        if(!runtime.arrived)FilledTonalButton(onClick={SimulationService.command(context,SimulationService.PAUSE)},modifier=Modifier.fillMaxWidth()){Text(if(runtime.paused)"继续" else "暂停")}
    }
    if(diagnostics)AlertDialog(onDismissRequest={diagnostics=false},title={Text("运行诊断")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("定位通道：${runtime.providers}/3")
            Text("写入：${runtime.health.writes} 次")
            Text("最近更新：${runtime.health.ageMs(now)?.let {"${it/1000} 秒前"} ?: "等待中"}")
            Text("最长间隔：${runtime.health.longestGapMs} ms")
            runtime.point?.let {Text(homePoint(it))}
        }
    },confirmButton={TextButton(onClick={diagnostics=false}){Text("关闭")}})
}

@Composable private fun RuntimeMetric(label: String,value: String) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HomeMapPlaceholder(keyConfigured: Boolean,onSetup: ()->Unit) {
    val road=MaterialTheme.colorScheme.surface
    val field=MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxSize().background(field),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w=size.width;val h=size.height
            listOf(.12f,.46f,.8f).forEach{x->drawLine(road,Offset(w*x,0f),Offset(w*(x+.12f),h),22f)}
            listOf(.2f,.44f,.72f).forEach{y->drawLine(road,Offset(0f,h*y),Offset(w,h*(y-.08f)),24f)}
        }
        Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.padding(horizontal=32.dp)) {
            Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Map,null,tint=MaterialTheme.colorScheme.primary)
                Text(if(keyConfigured)"地图待启用" else "地图暂不可用",style=MaterialTheme.typography.titleMedium)
                Text("此处为离线示意，并非真实地图",style=MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick=onSetup){Text(if(keyConfigured)"启用地图" else "查看说明")}
            }
        }
    }
}

private fun homePoint(point: GeoPoint)=String.format(Locale.US,"%.6f, %.6f · WGS84",point.latitude,point.longitude)
private fun homeDistance(meters: Double)=if(meters<1000)"${meters.toInt()} m" else String.format(Locale.US,"%.2f km",meters/1000)
private fun homeDuration(seconds: Long)=if(seconds>=3600)"${seconds/3600} 时 ${(seconds%3600)/60} 分" else if(seconds>=60)"${seconds/60} 分 ${seconds%60} 秒" else "$seconds 秒"
