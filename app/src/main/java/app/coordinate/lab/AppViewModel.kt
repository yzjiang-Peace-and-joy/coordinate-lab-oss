package app.coordinate.lab

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.coordinate.lab.core.GeoPoint
import app.coordinate.lab.core.Geo
import app.coordinate.lab.core.AssembledRoute
import app.coordinate.lab.core.RouteGeometry
import app.coordinate.lab.core.RoutePlayer
import app.coordinate.lab.core.RoutePointOrder
import app.coordinate.lab.core.WaypointHistory
import app.coordinate.lab.core.SearchHistoryPolicy
import app.coordinate.lab.core.SearchPolicy
import app.coordinate.lab.core.MapConsentPolicy
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UiState(
    val appearance: AppearanceSettings=AppearanceSettings(),
    val locationReady: Boolean=false,val hasPickedPoint: Boolean=false,
    val selected: Place=Examples.places.first(), val favorites: List<Place> = emptyList(),
    val savedRoutes: List<SavedRoute> = emptyList(), val mapMarkers: List<Place> = emptyList(),
    val previewSavedRoute: SavedRoute? = null,
    val loadedFavoriteRoute: SavedRoute? = null,
    val workspaceStrokes: List<List<GeoPoint>> = emptyList(),
    val city: String="北京",val searchResults: List<Place> = emptyList(),val searching: Boolean=false,
    val searchText: String="",val searchScope: SearchScope=SearchScope.SMART,
    val searchSuggestions: List<PlaceSuggestion> = emptyList(),val suggesting: Boolean=false,
    val searchCompleted: Boolean=false,val searchDescription: String="",val searchError: String?=null,
    val loadingMore: Boolean=false,val hasMore: Boolean=false,val searchMoreError: String?=null,
    val recentSearches: List<String> = emptyList(),val suggestedCities: List<String> = emptyList(),
    val message: String?=null,val keyConfigured: Boolean=false,val mapEnabled: Boolean=false,val mapGeneration: Int=0,
    val routeMode: Boolean=false,val routeEditing: Boolean=false,val routeWaypoints: List<Place> = emptyList(),
    val routeError: String?=null,val planningLeg: Int=0,val planningTotalLegs: Int=0,
    val routeConnectors: Int=0,val canUndoRoute: Boolean=false,
    val road: Boolean=false,val routeClosed: Boolean=false,val speed: Double=5.0,val route: List<GeoPoint> = emptyList(),val planning: Boolean=false,
    val mockAllowed: Boolean=false,val preciseAllowed: Boolean=false,val resolvingPlace: Boolean=false
)

class AppViewModel(application: Application): AndroidViewModel(application) {
    val store=AppStore(application)
    val gateway=AmapGateway(application,store)
    private val routeLibrary=RouteLibraryStore(File(application.filesDir,"map_library_v1"))
    private val librarySnapshot=routeLibrary.load()
    private val appearanceStore=AppearanceStore(application)
    private val main=Handler(Looper.getMainLooper())
    private val mutable=MutableStateFlow(UiState(selected=store.selected.copy(name="正在获取真实位置",detail=""),favorites=store.favorites,city=store.selected.city.ifBlank { "北京" },keyConfigured=store.apiKey.isNotBlank(),recentSearches=store.searchHistory,
        routeWaypoints=store.routeWaypoints,road=store.routeRoad,routeClosed=store.routeClosed,speed=store.routeSpeed,
        savedRoutes=librarySnapshot.routes,mapMarkers=librarySnapshot.markers,appearance=appearanceStore.load()))
    val state=mutable.asStateFlow()
    private var searchVersion=0
    private var suggestionVersion=0
    private var searchOpen=false
    private var searchCall: SearchCancellation?=null
    private var suggestionCall: SearchCancellation?=null
    private var suggestionJob: Job?=null
    private var searchCursor: PlaceSearchCursor?=null
    private var routeVersion=0
    private var routeCall: SearchCancellation?=null
    private var pointLabelVersion=0
    private var pointLabelCall: SearchCancellation?=null
    private var pointLabelJob: Job?=null
    private val waypointHistory=WaypointHistory(mutable.value.routeWaypoints,RouteGeometry.MAX_WAYPOINTS)
    private var clearedTrace: SavedRoute?=null
    init {
        refreshAccess()
        if(MapConsentPolicy.mayInitialize(store.apiKey,store.consent)) {
            runCatching { gateway.initialize() }.onSuccess { mutable.update { it.copy(mapEnabled=true) } }.onFailure { notice(it.message ?: "地图初始化失败") }
        }
        SimulationService.recoverAbandonedSession(application,store)
    }
    fun refreshAccess(){mutable.update { it.copy(mockAllowed=DeviceAccess.mockAllowed(getApplication()),preciseAllowed=DeviceAccess.preciseAllowed(getApplication())) }}
    fun notice(message: String?){mutable.update { it.copy(message=message) }}
    fun setTheme(theme: ThemeMode) {saveAppearance(mutable.value.appearance.copy(theme=theme))}
    fun setAccent(hex: String): Boolean {
        val normalized=AppearanceColors.normalizeHex(hex) ?: run {notice("请输入 6 位颜色值");return false}
        return saveAppearance(mutable.value.appearance.copy(accentHex=normalized))
    }
    fun resetAppearance() {saveAppearance(AppearanceSettings())}
    private fun saveAppearance(settings: AppearanceSettings): Boolean {
        if(settings==mutable.value.appearance)return true
        if(!appearanceStore.save(settings)){notice("外观保存失败，请重试");return false}
        mutable.update {it.copy(appearance=settings)}
        return true
    }
    fun initialRealLocation(point: GeoPoint) {
        if(mutable.value.hasPickedPoint || SimulationBus.state.value.active)return
        mutable.update {it.copy(selected=Place("我的位置","",point),locationReady=true)}
    }
    fun initialLocationUnavailable(message: String) {
        if(!mutable.value.hasPickedPoint && !mutable.value.locationReady && !SimulationBus.state.value.active)
            mutable.update {it.copy(selected=it.selected.copy(name=message))}
    }
    fun select(place: Place){
        cancelPointLabel()
        invalidateSearch()
        mutable.update { it.copy(searchResults=emptyList(),searchCompleted=false,searchDescription="",searchError=null,searchMoreError=null,suggestedCities=emptyList()) }
        if(mutable.value.routeMode){
            addRoutePoint(place)
            mutable.update {it.copy(hasPickedPoint=true,locationReady=true,selected=if(it.locationReady)it.selected else place)}
            return
        }
        mutable.update {it.copy(selected=place,city=place.city.ifBlank {it.city},hasPickedPoint=true,locationReady=true)}
        store.selected=mutable.value.selected
    }
    fun selectFavoritePlace(place: Place): Boolean {
        if(!routeEditable())return false
        cancelPointLabel();invalidateSearch();invalidateRoutePlan()
        mutable.update {FavoriteRouteLoader.selectPlace(it,place)}
        store.selected=place
        return true
    }

    fun selectFavoriteRoute(id: String): Boolean {
        if(!routeEditable())return false
        val saved=mutable.value.savedRoutes.firstOrNull {it.id==id} ?: run {notice("路线不存在");return false}
        return loadFavoriteRoute(saved)
    }

    private fun loadFavoriteRoute(saved: SavedRoute): Boolean {
        val loaded=runCatching {FavoriteRouteLoader.load(saved)}.getOrElse {notice(it.message ?: "路线载入失败");return false}
        cancelPointLabel();invalidateSearch();invalidateRoutePlan()
        clearedTrace=null
        waypointHistory.reset(loaded.handles)
        // Drawn display handles must never replace the user's persisted waypoint draft.
        if(saved.kind==SavedRouteKind.WAYPOINTS)store.routeWaypoints=loaded.handles
        store.routeRoad=saved.road;store.routeClosed=saved.closed;store.routeSpeed=saved.speedKph
        mutable.update {FavoriteRouteLoader.selectRoute(it,loaded)}
        if(loaded.needsPlanning)plan()
        return true
    }
    fun mapPick(point: GeoPoint){
        if(mutable.value.routeMode && mutable.value.loadedFavoriteRoute?.kind==SavedRouteKind.DRAWN){waypointsEditable();return}
        if(mutable.value.routeMode && !mutable.value.routeEditing){notice("点击路线抽屉中的加号，再在地图上选点");return}
        select(Place(if(mutable.value.routeMode)"地图路线点" else "选定位置",mutable.value.city,point,""))
        if(!mutable.value.routeMode && mutable.value.mapEnabled)resolvePointLabel(point)
    }
    private fun cancelPointLabel(){
        pointLabelVersion++;pointLabelCall?.cancel();pointLabelCall=null;pointLabelJob?.cancel();pointLabelJob=null
        mutable.update {it.copy(resolvingPlace=false)}
    }
    private fun resolvePointLabel(point: GeoPoint){
        val version=pointLabelVersion
        mutable.update {it.copy(resolvingPlace=true)}
        pointLabelJob=viewModelScope.launch {
            delay(450)
            if(version!=pointLabelVersion)return@launch
            pointLabelCall=gateway.pointAddress(point){result->main.post {
                if(version!=pointLabelVersion || mutable.value.routeMode || mutable.value.selected.point!=point || !mutable.value.mapEnabled)return@post
                mutable.update {it.copy(resolvingPlace=false)}
                result.onSuccess {place->
                    mutable.update {it.copy(selected=place,city=place.city.ifBlank {it.city})};store.selected=place
                    // A favorite created while loading receives the same label, without adding it back after removal.
                    if(mutable.value.favorites.any {it.id==place.id}){
                        val favorites=mutable.value.favorites.map {if(it.id==place.id)place else it}
                        store.favorites=favorites;mutable.update {it.copy(favorites=favorites)}
                    }
                }.onFailure {notice("地址获取失败：${it.message ?: "请稍后再试"}")}
            }}
        }
    }
    fun city(city: String){
        val normalized=city.trim().take(50)
        val nationwide=normalized in listOf("全国","全部","中国","中国大陆")
        invalidateSearch()
        mutable.update { it.copy(city=if(nationwide)"" else normalized,searchScope=if(nationwide)SearchScope.NATIONWIDE else it.searchScope,
            searchResults=emptyList(),searchCompleted=false,searchDescription="",searchError=null,searchMoreError=null,hasMore=false,suggestedCities=emptyList()) }
        queueSuggestions()
    }
    fun favorite(place: Place){
        val old=mutable.value.favorites
        val next=if(old.any { it.id==place.id }) old.filterNot { it.id==place.id } else listOf(place)+old.take(199)
        store.favorites=next;mutable.update { it.copy(favorites=next) }
    }
    /** Unlike the existing toggle, this is idempotent and never removes a saved place. */
    fun saveFavoritePlace(place: Place): Boolean = runCatching {
        RouteLibraryPolicy.validatePlace(place)
        val old=mutable.value.favorites
        require(old.any {it.id==place.id} || old.size<200) {"最多收藏 200 个地点"}
        val next=listOf(place)+old.filterNot {it.id==place.id}
        store.favorites=next;mutable.update {it.copy(favorites=next)}
    }.fold(onSuccess={true},onFailure={notice(it.message ?: "地点收藏失败");false})

    fun saveCurrentRoute(name: String): Boolean = runCatching {
        val s=mutable.value
        require(s.routeMode) {"请先选择一条多点路线"}
        val ready=!s.routeEditing && !s.planning && s.routeError==null && s.route.size>=2
        val trace=s.loadedFavoriteRoute?.takeIf {it.kind==SavedRouteKind.DRAWN}
        val route=if(trace!=null)RouteLibraryPolicy.validate(trace.copy(id=java.util.UUID.randomUUID().toString(),
            name=name,points=s.route,speedKph=s.speed,closed=s.routeClosed,createdAt=System.currentTimeMillis()))
        else RouteLibraryPolicy.current(name,s.routeWaypoints,s.route,s.road,s.routeClosed,s.speed,ready)
        val next=RouteLibraryPolicy.add(s.savedRoutes,route)
        routeLibrary.writeRoute(route)
        mutable.update {it.copy(savedRoutes=next)}
    }.fold(onSuccess={true},onFailure={notice(it.message ?: "路线收藏失败");false})

    fun saveDrawnRoute(name: String,points: List<GeoPoint>): Boolean = runCatching {
        val route=RouteLibraryPolicy.drawn(name,points)
        val next=RouteLibraryPolicy.add(mutable.value.savedRoutes,route)
        routeLibrary.writeRoute(route)
        mutable.update {it.copy(savedRoutes=next)}
    }.fold(onSuccess={true},onFailure={notice(it.message ?: "手绘路线收藏失败");false})

    fun renameSavedRoute(id: String,name: String): Boolean = runCatching {
        val next=RouteLibraryPolicy.rename(mutable.value.savedRoutes,id,name)
        val route=next.first {it.id==id}
        routeLibrary.writeRoute(route)
        mutable.update {it.copy(savedRoutes=next,previewSavedRoute=if(it.previewSavedRoute?.id==id)route else it.previewSavedRoute,
            loadedFavoriteRoute=if(it.loadedFavoriteRoute?.id==id)it.loadedFavoriteRoute.copy(name=route.name) else it.loadedFavoriteRoute)}
    }.fold(onSuccess={true},onFailure={notice(it.message ?: "重命名失败");false})

    fun deleteSavedRoute(id: String){
        runCatching {routeLibrary.deleteRoute(id)}.onSuccess {
            mutable.update {it.copy(savedRoutes=RouteLibraryPolicy.delete(it.savedRoutes,id),
                previewSavedRoute=it.previewSavedRoute?.takeUnless {route->route.id==id})}
        }.onFailure {notice(it.message ?: "删除路线失败")}
    }

    /** Library preview does not replace draft, change mode/settings, request road data, or start a service. */
    fun previewSavedRoute(id: String){
        val route=mutable.value.savedRoutes.firstOrNull {it.id==id} ?: return
        val preview=runCatching {
            if(route.kind==SavedRouteKind.WAYPOINTS && !route.previewGeometryReady)
                route.copy(points=RouteGeometry.straight(route.waypoints.map {it.point},route.closed).points)
            else route
        }.getOrElse {notice(it.message ?: "路线预览失败");return}
        mutable.update {it.copy(previewSavedRoute=preview)}
    }
    fun clearSavedRoutePreview(){mutable.update {it.copy(previewSavedRoute=null)}}

    /** Ephemeral workspace draft lives with the ViewModel, not in the activity's Bundle. */
    fun appendWorkspaceStroke(points: List<GeoPoint>): Boolean = runCatching {
        val next=WorkspaceSketchPolicy.append(mutable.value.workspaceStrokes,points)
        mutable.update {it.copy(workspaceStrokes=next)}
    }.fold(onSuccess={true},onFailure={notice(it.message ?: "描线未保存，请重试");false})
    fun undoWorkspaceStroke(){mutable.update {it.copy(workspaceStrokes=WorkspaceSketchPolicy.undo(it.workspaceStrokes))}}
    fun clearWorkspaceSketch(){mutable.update {it.copy(workspaceStrokes=emptyList())}}

    fun addMapMarker(place: Place): Boolean = runCatching {
        RouteLibraryPolicy.validatePlace(place)
        val old=mutable.value.mapMarkers
        require(old.any {it.id==place.id} || old.size<RouteLibraryPolicy.MAX_MARKERS) {"最多保存 ${RouteLibraryPolicy.MAX_MARKERS} 个地图标记"}
        routeLibrary.writeMarker(place)
        mutable.update {it.copy(mapMarkers=listOf(place)+old.filterNot {marker->marker.id==place.id})}
    }.fold(onSuccess={true},onFailure={notice(it.message ?: "地图标记保存失败");false})
    fun removeMapMarker(id: String){
        runCatching {routeLibrary.deleteMarker(id)}.onSuccess {
            mutable.update {it.copy(mapMarkers=it.mapMarkers.filterNot {marker->marker.id==id})}
        }.onFailure {notice(it.message ?: "删除地图标记失败")}
    }
    fun openSearch(){searchOpen=true;if(!mutable.value.searchCompleted)queueSuggestions()}
    fun closeSearch(){
        searchOpen=false;searchVersion++;searchCall?.cancel();searchCall=null;cancelSuggestions()
        mutable.update {it.copy(searching=false,loadingMore=false)}
    }
    private fun cancelSuggestions(){
        suggestionVersion++;suggestionJob?.cancel();suggestionJob=null;suggestionCall?.cancel();suggestionCall=null
        mutable.update {it.copy(suggesting=false,searchSuggestions=emptyList())}
    }
    private fun invalidateSearch(){
        searchVersion++;searchCall?.cancel();searchCall=null;searchCursor=null;cancelSuggestions()
        mutable.update {it.copy(searching=false,loadingMore=false,hasMore=false)}
    }
    fun searchText(text: String){
        if(text==mutable.value.searchText)return
        invalidateSearch()
        mutable.update {it.copy(searchText=text.take(120),searchResults=emptyList(),searchCompleted=false,searchError=null,searchMoreError=null,searchDescription="",suggestedCities=emptyList())}
        queueSuggestions()
    }
    fun searchScope(scope: SearchScope){
        if(scope==mutable.value.searchScope)return
        invalidateSearch()
        mutable.update {it.copy(searchScope=scope,searchResults=emptyList(),searchCompleted=false,searchError=null,searchMoreError=null,searchDescription="",suggestedCities=emptyList())}
        queueSuggestions()
    }
    private fun queueSuggestions(){
        cancelSuggestions()
        val s=mutable.value;val keyword=s.searchText.trim()
        if(!searchOpen || !s.mapEnabled || keyword.isBlank())return
        if(s.searchScope==SearchScope.CITY && s.city.isBlank())return
        val version=suggestionVersion
        suggestionJob=viewModelScope.launch {
            delay(350)
            if(version!=suggestionVersion || !searchOpen)return@launch
            mutable.update {it.copy(suggesting=true)}
            suggestionCall=gateway.suggest(keyword,if(s.searchScope==SearchScope.NATIONWIDE)"" else s.city,s.searchScope==SearchScope.CITY){result->main.post {
                if(version!=suggestionVersion || !searchOpen)return@post
                mutable.update {it.copy(suggesting=false,searchSuggestions=result.getOrDefault(emptyList()))}
                // Suggestion failure must not block manual POI/address search or spam a toast per keystroke.
            }}
        }
    }
    private fun rememberQuery(keyword: String){
        val next=SearchHistoryPolicy.record(store.searchHistory,keyword)
        store.searchHistory=next;mutable.update {it.copy(recentSearches=next)}
    }
    fun clearSearchHistory(){store.searchHistory=emptyList();mutable.update {it.copy(recentSearches=emptyList())}}
    fun selectSearchResult(place: Place){
        rememberQuery(mutable.value.searchText)
        select(place)
        searchText("")
    }
    fun search(keyword: String=mutable.value.searchText){
        val normalized=keyword.trim().take(120)
        if(normalized.isBlank()){searchText("");notice("请输入地点、城市或完整地址");return}
        invalidateSearch()
        val version=searchVersion
        rememberQuery(normalized)
        mutable.update {it.copy(searchText=normalized,searching=true,searchCompleted=false,searchResults=emptyList(),searchError=null,searchMoreError=null,searchDescription="",suggestedCities=emptyList())}
        val s=mutable.value
        if(s.searchScope==SearchScope.CITY && s.city.isBlank()){
            mutable.update {it.copy(searching=false,searchCompleted=true,searchError="请先填写搜索城市，或切换智能 / 全国范围")};return
        }
        if(!s.mapEnabled){
            val matches=(s.favorites+Examples.places).filter {place->
                (s.searchScope!=SearchScope.CITY || SearchPolicy.addressMatchesCity(s.city,"",place.city,"","")) &&
                    listOf(place.name,place.city,place.detail).any {text->text.contains(normalized,ignoreCase=true)}
            }.distinctBy {it.id+it.name}
            mutable.update {it.copy(searching=false,searchCompleted=true,searchResults=matches,searchDescription="仅匹配本机收藏与离线示例；尚未启用高德搜索")}
            return
        }
        val center=if(s.routeMode)s.routeWaypoints.lastOrNull()?.point ?: s.selected.point else s.selected.point
        val request=PlaceSearchRequest(normalized,s.city,center,s.searchScope)
        searchCall=gateway.search(request){result->main.post {
            if(version!=searchVersion || !searchOpen)return@post
            result.onSuccess {page->
                searchCursor=page.next
                mutable.update {it.copy(searching=false,searchCompleted=true,searchResults=page.places,hasMore=page.next!=null,searchDescription=page.description,suggestedCities=page.citySuggestions)}
            }.onFailure {e->mutable.update {it.copy(searching=false,searchCompleted=true,searchError=e.message ?: "搜索失败，请重试")}}
        }}
    }
    fun searchNationwide(){searchScope(SearchScope.NATIONWIDE);search()}
    fun loadMore(){
        val cursor=searchCursor ?: return
        if(!searchOpen || mutable.value.searching || mutable.value.loadingMore)return
        val version=searchVersion
        mutable.update {it.copy(loadingMore=true,searchMoreError=null)}
        searchCall=gateway.search(cursor.request,cursor){result->main.post {
            if(version!=searchVersion || !searchOpen)return@post
            result.onSuccess {page->
                searchCursor=page.next
                mutable.update {it.copy(loadingMore=false,hasMore=page.next!=null,searchResults=(it.searchResults+page.places).distinctBy {place->place.id+place.name})}
            }.onFailure {e->mutable.update {it.copy(loadingMore=false,searchMoreError=e.message ?: "加载更多失败，请重试")}}
        }}
    }
    private fun routeEditable(): Boolean {
        if(SimulationBus.state.value.active){notice("本次会话的路线和速度已锁定，请先停止模拟再编辑");return false}
        return true
    }
    private fun invalidateRoutePlan(){
        routeVersion++;routeCall?.cancel();routeCall=null
        mutable.update {it.copy(route=emptyList(),planning=false,planningLeg=0,planningTotalLegs=0,routeError=null,routeConnectors=0)}
    }
    private fun publishDraft(){
        invalidateRoutePlan();invalidateSearch()
        val points=waypointHistory.points
        store.routeWaypoints=points
        mutable.update {it.copy(routeMode=true,routeEditing=true,loadedFavoriteRoute=null,routeWaypoints=points,canUndoRoute=waypointHistory.canUndo || clearedTrace!=null,
            searchResults=emptyList(),searchCompleted=false,searchError=null,searchMoreError=null,searchDescription="",suggestedCities=emptyList())}
    }
    private fun changeDraft(points: List<Place>){if(waypointHistory.change(points))publishDraft()}
    private fun addRoutePoint(place: Place){
        if(!routeEditable() || !waypointsEditable())return
        val points=waypointHistory.points
        if(points.size>=RouteGeometry.MAX_WAYPOINTS){notice("路线最多支持 ${RouteGeometry.MAX_WAYPOINTS} 个选点，请删除或调整已有点");return}
        if(points.lastOrNull()?.let {Geo.distance(it.point,place.point)<RouteGeometry.MIN_WAYPOINT_GAP_METERS}==true){notice("这个点与上一点太近，请选择至少相隔 1 米的位置");return}
        changeDraft(points+place)
        if(place.city.isNotBlank())mutable.update {it.copy(city=place.city)}
    }
    fun setRouteMode(value: Boolean){
        if(value==mutable.value.routeMode || !routeEditable())return
        if(value && mutable.value.loadedFavoriteRoute!=null){loadFavoriteRoute(mutable.value.loadedFavoriteRoute!!);return}
        cancelPointLabel()
        invalidateRoutePlan()
        if(value && waypointHistory.points.isEmpty() && mutable.value.locationReady)waypointHistory.change(listOf(mutable.value.selected))
        if(mutable.value.loadedFavoriteRoute?.kind!=SavedRouteKind.DRAWN)store.routeWaypoints=waypointHistory.points
        mutable.update {it.copy(routeMode=value,routeEditing=value,routeWaypoints=waypointHistory.points,canUndoRoute=waypointHistory.canUndo || clearedTrace!=null)}
        invalidateSearch()
        mutable.update {it.copy(searchResults=emptyList(),searchCompleted=false,searchError=null,searchMoreError=null,searchDescription="",suggestedCities=emptyList())}
    }
    fun beginRouteEdit(){
        if(!routeEditable() || !waypointsEditable())return
        mutable.update {it.copy(loadedFavoriteRoute=null)}
        invalidateRoutePlan()
        mutable.update {it.copy(routeMode=true,routeEditing=true)}
    }
    fun finishRouteEdit(){
        if(!routeEditable())return
        runCatching {RouteGeometry.validateWaypoints(waypointHistory.points.map {it.point})}.onFailure {error->
            mutable.update {it.copy(routeError=error.message ?: "请至少选择两个不同的点")};notice(error.message ?: "请至少选择两个不同的点")
        }.onSuccess {mutable.update {it.copy(routeEditing=false)};plan()}
    }
    fun confirmRoutePreview(){
        if(mutable.value.planning)return
        if(mutable.value.routeEditing)finishRouteEdit() else plan()
    }
    private fun waypointsEditable(): Boolean {
        if(mutable.value.loadedFavoriteRoute?.kind==SavedRouteKind.DRAWN){notice("手绘轨迹可调整速度、反转或闭环；清空后可重新选点");return false}
        return true
    }
    fun undoRouteEdit(){
        if(!routeEditable() || !waypointsEditable())return
        if(waypointHistory.undo())publishDraft()
        else clearedTrace?.let {loadFavoriteRoute(it)}
    }
    fun clearRoute(){
        if(!routeEditable())return
        val trace=mutable.value.loadedFavoriteRoute?.takeIf {it.kind==SavedRouteKind.DRAWN}
        if(trace!=null){clearedTrace=trace;waypointHistory.reset(emptyList());publishDraft()}
        else changeDraft(emptyList())
    }
    fun removeRoutePoint(index: Int){
        if(!routeEditable() || !waypointsEditable() || index !in waypointHistory.points.indices)return
        changeDraft(waypointHistory.points.filterIndexed {i,_->i!=index})
    }
    fun moveRoutePoint(index: Int,delta: Int){
        if(!routeEditable() || !waypointsEditable() || delta !in listOf(-1,1))return
        val points=waypointHistory.points.toMutableList();val target=index+delta
        if(index !in points.indices || target !in points.indices)return
        val item=points.removeAt(index);points.add(target,item);changeDraft(points)
    }
    fun reverseRoute(){
        if(!routeEditable())return
        val trace=mutable.value.loadedFavoriteRoute?.takeIf {it.kind==SavedRouteKind.DRAWN}
        if(trace!=null){loadFavoriteRoute(trace.copy(points=trace.points.reversed(),speedKph=mutable.value.speed));return}
        changeDraft(waypointHistory.points.reversed())
    }
    fun reorderRoutePoint(from: Int,to: Int){
        if(!routeEditable() || !waypointsEditable() || from==to || from !in waypointHistory.points.indices || to !in waypointHistory.points.indices)return
        changeDraft(RoutePointOrder.move(waypointHistory.points,from,to))
    }
    fun speed(value: Double){
        if(!value.isFinite() || !routeEditable())return
        val next=value.coerceIn(1.0,30.0)
        store.routeSpeed=next;mutable.update {it.copy(speed=next,loadedFavoriteRoute=it.loadedFavoriteRoute?.copy(speedKph=next))}
    }
    fun road(value: Boolean){
        if(value==mutable.value.road || !routeEditable() || !waypointsEditable())return
        mutable.update {it.copy(loadedFavoriteRoute=null)}
        invalidateRoutePlan();store.routeRoad=value;mutable.update {it.copy(road=value)}
        if(mutable.value.routeMode && !mutable.value.routeEditing)plan()
    }
    fun closedRoute(value: Boolean){
        if(value==mutable.value.routeClosed || !routeEditable())return
        val trace=mutable.value.loadedFavoriteRoute?.takeIf {it.kind==SavedRouteKind.DRAWN}
        if(trace!=null){
            runCatching {FavoriteRouteLoader.closeTrace(trace,value)}.onSuccess {loadFavoriteRoute(it)}.onFailure {notice(it.message ?: "闭环调整失败")}
            return
        }
        mutable.update {it.copy(loadedFavoriteRoute=null)}
        invalidateRoutePlan();store.routeClosed=value;mutable.update {it.copy(routeClosed=value)}
        if(mutable.value.routeMode && !mutable.value.routeEditing)plan()
    }
    fun plan(){
        if(!routeEditable())return
        val s=mutable.value
        if(!s.routeMode || s.routeEditing){notice("请先完成地图选点，再预览路线");return}
        if(s.loadedFavoriteRoute?.kind==SavedRouteKind.DRAWN){loadFavoriteRoute(s.loadedFavoriteRoute);return}
        invalidateRoutePlan()
        val version=routeVersion
        val points=s.routeWaypoints.map {it.point}
        val valid=runCatching {RouteGeometry.validateWaypoints(points)}
        if(valid.isFailure){mutable.update {it.copy(routeError=valid.exceptionOrNull()?.message ?: "路线点不可用",routeEditing=true)};return}
        fun deliver(result: Result<AssembledRoute>){
            if(version!=routeVersion)return
            routeCall=null
            result.mapCatching {assembled->RoutePlayer(assembled.points,s.speed);assembled}.onSuccess {assembled->
                mutable.update {it.copy(route=assembled.points,routeConnectors=assembled.connectorCount,planning=false,planningLeg=RouteGeometry.planningWaypoints(points,s.routeClosed).size-1,routeError=null,
                    loadedFavoriteRoute=it.loadedFavoriteRoute?.copy(points=assembled.points,previewGeometryReady=true))}
            }.onFailure {error->mutable.update {it.copy(route=emptyList(),planning=false,routeError=error.message ?: "路线规划失败，请重试")}}
        }
        if(!s.road){deliver(runCatching {RouteGeometry.straight(points,s.routeClosed)});return}
        if(!s.mapEnabled){mutable.update {it.copy(routeError="沿道路规划需要启用高德服务；也可以明确选择“按点连线”模式")};return}
        mutable.update {it.copy(planning=true,planningTotalLegs=RouteGeometry.planningWaypoints(points,s.routeClosed).size-1)}
        routeCall=gateway.walkingRoute(points,onProgress={completed,total->main.post {
            if(version==routeVersion)mutable.update {it.copy(planningLeg=completed,planningTotalLegs=total)}
        }},closed=s.routeClosed,done={result->main.post {deliver(result)}})
    }
    fun setMapConsent(consent: Boolean): Boolean {
        if(consent && store.apiKey.isBlank()){notice("此安装包的地图服务暂不可用，请联系提供安装包的朋友");return false}
        // Consent withdrawal keeps the installed client configuration, favorites and settings intact.
        store.consent=consent
        cancelPointLabel();invalidateSearch();invalidateRoutePlan()
        val result=if(MapConsentPolicy.mayInitialize(store.apiKey,consent))runCatching {gateway.initialize()} else Result.success(Unit)
        val enabled=consent && result.isSuccess
        mutable.update { it.copy(keyConfigured=store.apiKey.isNotBlank(),mapEnabled=enabled,mapGeneration=it.mapGeneration+1,route=emptyList(),planning=false,routeError=if(it.routeMode)"地图授权已变化，请重新预览路线" else null,searching=false,searchResults=emptyList(),searchCompleted=false,searchError=null,searchMoreError=null,searchDescription="",suggestedCities=emptyList()) }
        notice(when {result.isFailure->"地图服务暂未启用，请检查网络后重试";enabled->"已启用地图服务";else->"已停用地图联网服务，离线功能仍可使用"})
        return result.isSuccess
    }
    override fun onCleared(){searchOpen=false;cancelPointLabel();invalidateSearch();invalidateRoutePlan();super.onCleared()}
}
