package app.coordinate.lab

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.coordinate.lab.core.AssembledRoute
import app.coordinate.lab.core.Coordinates
import app.coordinate.lab.core.GeoPoint
import app.coordinate.lab.core.Geo
import app.coordinate.lab.core.NearbyName
import app.coordinate.lab.core.PointAddressLabels
import app.coordinate.lab.core.RouteGeometry
import app.coordinate.lab.core.SearchCompletionGate
import app.coordinate.lab.core.SearchPolicy
import app.coordinate.lab.core.MapConsentPolicy
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.geocoder.GeocodeAddress
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.amap.api.services.route.*

/** A bundled key does not grant consent: AMap is invoked only after explicit user agreement. */
class AmapGateway(private val context: Context, private val store: AppStore) {
    /** Address metadata only: the clicked WGS84 point is never snapped to a returned POI. */
    fun pointAddress(point: GeoPoint,done: (Result<Place>)->Unit): SearchCancellation {
        val operation=SearchOperation<Place>(12_000L,done)
        operation.dispatch {
            initialize()
            val mapped=Coordinates.toMap(point)
            GeocodeSearch(context).apply {
                setOnGeocodeSearchListener(object: GeocodeSearch.OnGeocodeSearchListener {
                    override fun onGeocodeSearched(result: GeocodeResult?,code: Int)=Unit
                    override fun onRegeocodeSearched(result: RegeocodeResult?,code: Int) {
                        operation.dispatch {
                            check(AmapSearchStatus.isSuccess(code)){AmapSearchStatus.explain(code)}
                            val address=checkNotNull(result?.regeocodeAddress){"未返回附近地址"}
                            val pois=address.pois.orEmpty().mapNotNull {poi->
                                val p=fromMapOrNull(poi.latLonPoint) ?: return@mapNotNull null
                                NearbyName(poi.title.orEmpty(),Geo.distance(point,p))
                            }
                            val roads=address.roads.orEmpty().map {NearbyName(it.name.orEmpty(),it.distance.toDouble())}
                            val label=checkNotNull(PointAddressLabels.make(address.province.orEmpty(),address.city.orEmpty(),address.district.orEmpty(),address.township.orEmpty(),address.neighborhood.orEmpty(),address.formatAddress.orEmpty(),pois,roads)){"附近没有可用地址"}
                            operation.finish(Result.success(Place(label.title,address.city.orEmpty().ifBlank {address.province.orEmpty()},point,label.address)))
                        }
                    }
                })
                getFromLocationAsyn(RegeocodeQuery(LatLonPoint(mapped.latitude,mapped.longitude),200f,GeocodeSearch.AMAP).apply {extensions="all"})
            }
        }
        return operation
    }
    fun initialize() {
        check(MapConsentPolicy.mayInitialize(store.apiKey,store.consent)) { "请先同意地图隐私政策并启用地图服务" }
        MapsInitializer.updatePrivacyShow(context,true,true)
        MapsInitializer.updatePrivacyAgree(context,true)
        MapsInitializer.setApiKey(store.apiKey)
        ServiceSettings.updatePrivacyShow(context,true,true)
        ServiceSettings.updatePrivacyAgree(context,true)
        ServiceSettings.getInstance().setApiKey(store.apiKey)
    }
    fun search(
        request: PlaceSearchRequest,
        cursor: PlaceSearchCursor? = null,
        done: (Result<PlaceSearchPage>) -> Unit
    ): SearchCancellation {
        val operation = SearchOperation(40_000L, done)
        operation.dispatch {
            val normalized = request.copy(keyword = request.keyword.trim(), city = request.city.trim())
            require(normalized.keyword.isNotEmpty()) { "请输入地点、地址或城市名称" }
            require(normalized.scope != SearchScope.CITY || normalized.city.isNotEmpty()) { "请先填写要搜索的城市，或切换为智能／全国搜索" }
            val first = cursor ?: PlaceSearchCursor(normalized, when (normalized.scope) {
                SearchScope.SMART -> SearchArea.NEARBY
                SearchScope.CITY -> SearchArea.CITY
                SearchScope.NATIONWIDE -> SearchArea.NATIONWIDE
            }, if (normalized.scope == SearchScope.SMART) SearchPolicy.INITIAL_RADIUS_METERS else 0)
            require(first.request == normalized && first.page >= 1) { "搜索条件已改变，请重新搜索" }
            require(when (normalized.scope) {
                SearchScope.SMART -> first.area == SearchArea.NEARBY || first.area == SearchArea.NATIONWIDE
                SearchScope.CITY -> first.area == SearchArea.CITY
                SearchScope.NATIONWIDE -> first.area == SearchArea.NATIONWIDE
            }) { "搜索范围已改变，请重新搜索" }
            require(if (first.area == SearchArea.NEARBY) first.radiusMeters in listOf(SearchPolicy.INITIAL_RADIUS_METERS, SearchPolicy.EXPANDED_RADIUS_METERS) else first.radiusMeters == 0) { "搜索范围无效，请重新搜索" }
            initialize()
            searchPois(first, operation, linkedSetOf())
        }
        return operation
    }

    fun suggest(
        keyword: String,
        city: String,
        cityLimit: Boolean,
        done: (Result<List<PlaceSuggestion>>) -> Unit
    ): SearchCancellation {
        val operation = SearchOperation(12_000L, done)
        operation.dispatch {
            val text = keyword.trim()
            if (text.isEmpty()) {
                operation.finish(Result.success(emptyList()))
                return@dispatch
            }
            require(!cityLimit || city.isNotBlank()) { "请先填写要搜索的城市" }
            initialize()
            val query = InputtipsQuery(text, city.trim()).apply { setCityLimit(cityLimit) }
            Inputtips(context, query).apply {
                setInputtipsListener { tips, code ->
                    operation.dispatch {
                        if (!AmapSearchStatus.isSuccess(code)) {
                            operation.finish(Result.failure(IllegalStateException(AmapSearchStatus.explain(code))))
                        } else {
                            val suggestions = tips.orEmpty().mapNotNull { tip ->
                                val name = tip.name.orEmpty().trim()
                                if (name.isEmpty()) return@mapNotNull null
                                val district = tip.district.orEmpty().trim()
                                val point = fromMapOrNull(tip.point)
                                val cityName = suggestionCity(district).ifBlank { if (cityLimit) city.trim() else "" }
                                PlaceSuggestion(name, cityName, listOf(district, tip.address.orEmpty()).filter { it.isNotBlank() }.distinct().joinToString(" · "), point)
                            }.distinctBy { listOf(it.name, it.city, it.detail, it.point) }
                            operation.finish(Result.success(suggestions))
                        }
                    }
                }
                requestInputtipsAsyn()
            }
        }
        return operation
    }

    private fun searchPois(
        cursor: PlaceSearchCursor,
        operation: SearchOperation<PlaceSearchPage>,
        cities: LinkedHashSet<String>
    ) {
        if (!operation.isActive) return
        val request = cursor.request
        val query = PoiSearch.Query(request.keyword, "", if (cursor.area == SearchArea.CITY) request.city else "").apply {
            pageSize = SearchPolicy.PAGE_SIZE
            // Since Search SDK 5.2.1 the documented SDK page number starts at 1.
            pageNum = cursor.page
            cityLimit = cursor.area == SearchArea.CITY
            isDistanceSort = cursor.area == SearchArea.NEARBY
            requireSubPois(true)
            if (cursor.area == SearchArea.NEARBY) isSpecial = false
        }
        PoiSearch(context, query).apply {
            if (cursor.area == SearchArea.NEARBY) {
                val mapped = Coordinates.toMap(request.center)
                bound = PoiSearch.SearchBound(LatLonPoint(mapped.latitude, mapped.longitude), cursor.radiusMeters, true)
            }
            setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                override fun onPoiSearched(result: PoiResult?, code: Int) {
                    operation.dispatch {
                        if (!AmapSearchStatus.isSuccess(code)) {
                            operation.finish(Result.failure(IllegalStateException(AmapSearchStatus.explain(code))))
                            return@dispatch
                        }
                        // A missing result is a protocol failure, not evidence that an area has no POIs.
                        checkNotNull(result) { "高德未返回有效搜索结果，请重试" }
                        result.searchSuggestionCitys.orEmpty().map { it.cityName.orEmpty().trim() }.filter { it.isNotEmpty() }.forEach(cities::add)
                        val raw = result.pois.orEmpty()
                        val places = SearchPolicy.distinctPlaces(raw.mapNotNull(::placeFromPoi), { it.name }, { it.point })
                        when {
                            cursor.area == SearchArea.NEARBY && SearchPolicy.shouldExpandNearby(cursor.radiusMeters, places.size, cursor.page) ->
                                searchPois(cursor.copy(radiusMeters = SearchPolicy.EXPANDED_RADIUS_METERS), operation, cities)
                            cursor.area == SearchArea.NEARBY && SearchPolicy.shouldFallbackNationwide(cursor.radiusMeters, places.size, cursor.page) ->
                                searchPois(cursor.copy(area = SearchArea.NATIONWIDE, radiusMeters = 0), operation, cities)
                            places.isEmpty() && cursor.page == 1 && cursor.area != SearchArea.NEARBY ->
                                searchAddresses(cursor, operation, cities.toList())
                            else -> {
                                val next = SearchPolicy.nextPage(cursor.page, result.pageCount, raw.size)?.let { cursor.copy(page = it) }
                                operation.finish(Result.success(PlaceSearchPage(places, next, describe(cursor), citySuggestions = cities.toList())))
                            }
                        }
                    }
                }
                override fun onPoiItemSearched(item: PoiItem?, code: Int) = Unit
            })
            searchPOIAsyn()
        }
    }

    private fun searchAddresses(cursor: PlaceSearchCursor, operation: SearchOperation<PlaceSearchPage>, cities: List<String>) {
        if (!operation.isActive) return
        val request = cursor.request
        GeocodeSearch(context).apply {
            setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, code: Int) = Unit
                override fun onGeocodeSearched(result: GeocodeResult?, code: Int) {
                    operation.dispatch {
                        if (!AmapSearchStatus.isSuccess(code)) {
                            operation.finish(Result.failure(IllegalStateException(AmapSearchStatus.explain(code))))
                            return@dispatch
                        }
                        checkNotNull(result) { "高德未返回有效地址结果，请重试" }
                        val matches = result.geocodeAddressList.orEmpty().filter { address ->
                            cursor.area != SearchArea.CITY || SearchPolicy.addressMatchesCity(request.city, address.province.orEmpty(), address.city.orEmpty(), address.district.orEmpty(), address.adcode.orEmpty())
                        }.mapNotNull { address -> placeFromAddress(address, request.keyword) }
                        val places = SearchPolicy.distinctPlaces(matches, { it.name }, { it.point })
                        val suffix = if (places.isEmpty()) "未找到匹配地点，可尝试完整地址或更换关键词" else "POI 无结果，以下为地址／城市匹配（精度依地址级别而定）"
                        operation.finish(Result.success(PlaceSearchPage(places, null, "${describe(cursor)} · $suffix", citySuggestions = cities)))
                    }
                }
            })
            getFromLocationNameAsyn(GeocodeQuery(request.keyword, if (cursor.area == SearchArea.CITY) request.city else ""))
        }
    }

    private fun placeFromPoi(poi: PoiItem): Place? {
        val point = fromMapOrNull(poi.latLonPoint) ?: return null
        return Place(poi.title.orEmpty().ifBlank { "搜索地点" }, poi.cityName.orEmpty(), point,
            listOf(poi.provinceName.orEmpty(), poi.cityName.orEmpty(), poi.adName.orEmpty(), poi.snippet.orEmpty()).filter { it.isNotBlank() }.distinct().joinToString(" · "))
    }

    private fun placeFromAddress(address: GeocodeAddress, keyword: String): Place? {
        val point = fromMapOrNull(address.latLonPoint) ?: return null
        val name = address.formatAddress.orEmpty().ifBlank { keyword }
        return Place(name, address.city.orEmpty().ifBlank { address.province.orEmpty() }, point,
            listOf("地址匹配", address.level.orEmpty(), address.district.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
    }

    private fun fromMapOrNull(point: LatLonPoint?): GeoPoint? {
        if (point == null || !SearchPolicy.usableCoordinate(point.latitude, point.longitude)) return null
        return runCatching { Coordinates.fromMap(GeoPoint(point.latitude, point.longitude)) }.getOrNull()
    }

    private fun suggestionCity(district: String): String {
        val local = district.substringAfter("省").substringAfter("自治区")
        return when {
            "市" in local -> local.substringBefore("市") + "市"
            "自治州" in local -> local.substringBefore("自治州") + "自治州"
            else -> ""
        }
    }

    private fun describe(cursor: PlaceSearchCursor): String = when (cursor.area) {
        SearchArea.NEARBY -> if (cursor.radiusMeters == SearchPolicy.INITIAL_RADIUS_METERS) "以所选点为中心 · 附近 20 km" else "附近 20 km 结果较少，已扩大至 50 km"
        SearchArea.CITY -> "仅搜索 ${cursor.request.city}"
        SearchArea.NATIONWIDE -> if (cursor.request.scope == SearchScope.SMART) "附近 50 km 无结果，已切换全国搜索" else "全国搜索 · 不限制城市"
    }

    /** SDK listeners may arrive late or off-main; neither can mutate a cancelled/newer search. */
    private class SearchOperation<T>(timeoutMillis: Long, private val done: (Result<T>) -> Unit) : SearchCancellation {
        private val handler = Handler(Looper.getMainLooper())
        private val gate = SearchCompletionGate()
        val isActive: Boolean get() = gate.isOpen
        private val timeout = Runnable { finish(Result.failure(IllegalStateException("高德搜索超时，请检查网络后重试"))) }
        init { handler.postDelayed(timeout, timeoutMillis) }

        fun dispatch(block: () -> Unit) {
            val task = Runnable {
                if (gate.isOpen) runCatching(block).onFailure { finish(Result.failure(AmapSearchStatus.failure(it))) }
            }
            if (Looper.myLooper() == handler.looper) task.run() else handler.post(task)
        }

        fun finish(result: Result<T>) {
            if (Looper.myLooper() != handler.looper) {
                handler.post { finish(result) }
                return
            }
            if (gate.tryFinish()) {
                handler.removeCallbacks(timeout)
                done(result)
            }
        }

        override fun cancel() {
            gate.cancel()
            handler.removeCallbacks(timeout)
        }
    }
    fun walkingRoute(origin: GeoPoint, end: GeoPoint, done: (Result<List<GeoPoint>>) -> Unit) {
        walkingRoute(listOf(origin, end), { _, _ -> }) { result -> done(result.map { it.points }) }
    }

    /** The SDK accepts only two walking endpoints, so waypoints are planned serially and atomically. */
    fun walkingRoute(
        waypoints: List<GeoPoint>,
        onProgress: (completed: Int, total: Int) -> Unit,
        closed: Boolean=false,
        done: (Result<AssembledRoute>) -> Unit
    ): SearchCancellation {
        val operation = WalkingRouteOperation(waypoints.toList(),closed,onProgress,done)
        operation.start()
        return operation
    }

    private inner class WalkingRouteOperation(
        private val waypoints: List<GeoPoint>,
        private val closed: Boolean,
        private val onProgress: (Int, Int) -> Unit,
        private val done: (Result<AssembledRoute>) -> Unit
    ) : SearchCancellation {
        private val handler = Handler(Looper.getMainLooper())
        private val gate = SearchCompletionGate()
        private val legs = mutableListOf<List<GeoPoint>>()
        private var planned=waypoints
        private val total get()=(planned.size-1).coerceAtLeast(0)
        private val totalDeadline = SystemClock.elapsedRealtime() + 120_000L
        private val totalTimeout = Runnable { fail(IllegalStateException("总规划时间超过 120 秒，请减少地点或稍后重试")) }
        private var legTimeout: Runnable? = null
        private var legVersion = 0
        private var currentLeg = -1
        private var nextLeg: Runnable? = null

        init { handler.postDelayed(totalTimeout, 120_000L) }

        fun start() = dispatch {
            planned=RouteGeometry.planningWaypoints(waypoints,closed)
            initialize()
            onProgress(0, total)
            if (gate.isOpen) planLeg(0)
        }

        private fun planLeg(index: Int) {
            if (!gate.isOpen) return
            currentLeg = index
            val version = ++legVersion
            val legDeadline = SystemClock.elapsedRealtime() + 20_000L
            legTimeout?.let(handler::removeCallbacks)
            legTimeout = Runnable {
                if (gate.isOpen && version == legVersion) fail(IllegalStateException("该段步行规划超过 20 秒，请检查网络后重试"))
            }.also { handler.postDelayed(it, 20_000L) }
            // A sub-metre synthetic closing edge needs no remote walking request.
            if(closed && index==total-1 && Geo.distance(planned[index],planned[index+1])<RouteGeometry.MIN_WAYPOINT_GAP_METERS) {
                legTimeout?.let(handler::removeCallbacks);legTimeout=null
                acceptLeg(listOf(planned[index],planned[index+1]),index)
                return
            }
            val a = Coordinates.toMap(planned[index])
            val b = Coordinates.toMap(planned[index + 1])
            val fromTo = RouteSearch.FromAndTo(LatLonPoint(a.latitude, a.longitude), LatLonPoint(b.latitude, b.longitude))
            RouteSearch(context).apply {
                setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
                    override fun onWalkRouteSearched(result: WalkRouteResult?, code: Int) {
                        dispatch {
                            if (version != legVersion || currentLeg != index) return@dispatch
                            check(SystemClock.elapsedRealtime() < legDeadline) { "该段步行规划超过 20 秒，请检查网络后重试" }
                            legTimeout?.let(handler::removeCallbacks)
                            legTimeout = null
                            check(AmapSearchStatus.isSuccess(code)) { AmapSearchStatus.explain(code) }
                            val path = checkNotNull(result?.paths?.firstOrNull()) { "高德未返回可用步行路线，请调整此段地点" }
                            val points = mutableListOf<GeoPoint>()
                            for (step in path.steps.orEmpty()) {
                                for (point in step.polyline.orEmpty()) {
                                    // Invalid coordinates fail this leg; skipping them could silently join disconnected geometry.
                                    check(SearchPolicy.usableCoordinate(point.latitude, point.longitude)) { "高德返回无效的步行轨迹坐标，请调整地点后重试" }
                                    val converted = Coordinates.fromMap(GeoPoint(point.latitude, point.longitude))
                                    if (points.lastOrNull() != converted) {
                                        check(points.size < RouteGeometry.MAX_PATH_POINTS) { "步行轨迹超过 ${RouteGeometry.MAX_PATH_POINTS} 个点，请缩短路线" }
                                        points.add(converted)
                                    }
                                }
                            }
                            acceptLeg(points,index)
                        }
                    }
                    override fun onBusRouteSearched(result: BusRouteResult?, code: Int) = Unit
                    override fun onDriveRouteSearched(result: DriveRouteResult?, code: Int) = Unit
                    override fun onRideRouteSearched(result: RideRouteResult?, code: Int) = Unit
                })
                // The mode-less constructor is the supported walking query API since SDK 5.0.0.
                calculateWalkRouteAsyn(RouteSearch.WalkRouteQuery(fromTo))
            }
        }

        private fun acceptLeg(points: List<GeoPoint>,index: Int) {
            legs.add(points)
            val assembled=RouteGeometry.walkingPrefix(waypoints,legs,closed)
            onProgress(index+1,total)
            if(!gate.isOpen)return
            if(index+1==total)finish(Result.success(assembled)) else {
                nextLeg=Runnable {nextLeg=null;dispatch {planLeg(index+1)}}.also {handler.postDelayed(it,1_200L)}
            }
        }

        private fun dispatch(block: () -> Unit) {
            val task = Runnable {
                if (gate.isOpen) runCatching {
                    check(SystemClock.elapsedRealtime() < totalDeadline) { "总规划时间超过 120 秒，请减少地点或稍后重试" }
                    block()
                }.onFailure(::fail)
            }
            if (Looper.myLooper() == handler.looper) task.run() else handler.post(task)
        }

        private fun fail(error: Throwable) {
            val normalized = AmapSearchStatus.failure(error)
            val prefix = if (currentLeg >= 0) "第 ${currentLeg + 1}/$total 段路线规划失败" else "路线规划失败"
            finish(Result.failure(IllegalStateException("$prefix：${normalized.message ?: "未知错误"}；整条路线未更新", normalized)))
        }

        private fun finish(result: Result<AssembledRoute>) {
            if (Looper.myLooper() != handler.looper) {
                handler.post { finish(result) }
                return
            }
            if (gate.tryFinish()) {
                clearTimeouts()
                done(result)
            }
        }

        private fun clearTimeouts() {
            handler.removeCallbacks(totalTimeout)
            nextLeg?.let(handler::removeCallbacks)
            nextLeg=null
            legTimeout?.let(handler::removeCallbacks)
            legTimeout = null
        }

        override fun cancel() {
            gate.cancel()
            if (Looper.myLooper() == handler.looper) clearTimeouts() else handler.post { clearTimeouts() }
        }
    }
}
