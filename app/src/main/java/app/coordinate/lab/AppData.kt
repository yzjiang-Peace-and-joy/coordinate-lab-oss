package app.coordinate.lab

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import app.coordinate.lab.core.GeoPoint
import app.coordinate.lab.core.MapKeyConfig
import app.coordinate.lab.core.SearchHistoryPolicy
import app.coordinate.lab.core.RouteGeometry
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

data class Place(val name: String, val city: String, val point: GeoPoint, val detail: String = "") {
    val id: String get() = String.format(Locale.US,"%.6f,%.6f",point.latitude,point.longitude)
}

object Examples {
    val places = listOf(
        Place("北京 · 示例点","北京",GeoPoint(39.9042,116.4074),"离线城市示例，非精确地标"),
        Place("上海 · 示例点","上海",GeoPoint(31.2304,121.4737),"离线城市示例，非精确地标"),
        Place("杭州 · 示例点","杭州",GeoPoint(30.2741,120.1551),"离线城市示例，非精确地标"),
        Place("深圳 · 示例点","深圳",GeoPoint(22.5431,114.0579),"离线城市示例，非精确地标"),
        Place("成都 · 示例点","成都",GeoPoint(30.5728,104.0668),"离线城市示例，非精确地标")
    )
}

class AppStore(context: Context) {
    private val prefs=context.getSharedPreferences("coordinate_private",Context.MODE_PRIVATE)
    var apiKey: String
        get()=MapKeyConfig.resolve(prefs.getString("amap_key",null),BuildConfig.AMAP_ANDROID_KEY)
        set(value){prefs.edit().putString("amap_key",value).apply()}
    var consent: Boolean
        get()=prefs.getBoolean("amap_consent",false)
        set(value){prefs.edit().putBoolean("amap_consent",value).apply()}
    var previousSession: Boolean
        get()=prefs.getBoolean("session_active",false)
        set(value){prefs.edit().putBoolean("session_active",value).apply()}
    var selected: Place
        get()=runCatching { decode(JSONObject(prefs.getString("selected","").orEmpty())) }.getOrDefault(Examples.places.first())
        set(value){prefs.edit().putString("selected",encode(value).toString()).apply()}
    var favorites: List<Place>
        get()=runCatching {
            val items=JSONArray(prefs.getString("favorites","[]"))
            (0 until items.length()).mapNotNull { index -> runCatching { decode(items.getJSONObject(index)) }.getOrNull() }.distinctBy { it.id }.take(200)
        }.getOrDefault(emptyList())
        set(value){prefs.edit().putString("favorites",JSONArray().apply { value.take(200).forEach { put(encode(it)) } }.toString()).apply()}
    var searchHistory: List<String>
        get()=runCatching {
            val items=JSONArray(prefs.getString("search_history","[]"))
            SearchHistoryPolicy.filter((0 until items.length()).mapNotNull { index -> items.opt(index) as? String })
        }.getOrDefault(emptyList())
        set(value){
            val items=JSONArray().apply { SearchHistoryPolicy.filter(value).forEach { put(it) } }
            prefs.edit().putString("search_history",items.toString()).apply()
        }
    var routeWaypoints: List<Place>
        get()=runCatching {
            val items=JSONArray(prefs.getString("route_waypoints","[]"))
            (0 until items.length().coerceAtMost(RouteGeometry.MAX_WAYPOINTS)).mapNotNull { index ->
                runCatching { decode(items.getJSONObject(index)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
        set(value){
            require(value.size<=RouteGeometry.MAX_WAYPOINTS)
            prefs.edit().putString("route_waypoints",JSONArray().apply {value.forEach {put(encode(it))}}.toString()).apply()
        }
    var mapStyle: MapStyle
        get()=MapStyle.fromStored(prefs.getString("map_style",null))
        set(value){prefs.edit().putString("map_style",value.name).apply()}
    var mapTraffic: Boolean
        get()=prefs.getBoolean("map_traffic",false)
        set(value){prefs.edit().putBoolean("map_traffic",value).apply()}
    var routeClosed: Boolean
        get()=prefs.getBoolean("route_closed",false)
        set(value){prefs.edit().putBoolean("route_closed",value).apply()}
    var routeRoad: Boolean
        // v2 deliberately resets the old road-first default once; later explicit choices persist.
        get()=prefs.getBoolean("route_road_optional_v2",false)
        set(value){prefs.edit().putBoolean("route_road_optional_v2",value).apply()}
    var routeSpeed: Double
        get()=prefs.getString("route_speed",null)?.toDoubleOrNull()?.takeIf {it.isFinite()}?.coerceIn(1.0,30.0) ?: 5.0
        set(value){require(value.isFinite() && value in 1.0..30.0);prefs.edit().putString("route_speed",value.toString()).apply()}
    private fun encode(p: Place)=JSONObject().put("name",p.name).put("city",p.city).put("lat",p.point.latitude).put("lon",p.point.longitude).put("detail",p.detail)
    private fun decode(o: JSONObject)=Place(o.getString("name"),o.optString("city"),GeoPoint(o.getDouble("lat"),o.getDouble("lon")),o.optString("detail"))
}

object DeviceAccess {
    fun mockAllowed(context: Context): Boolean = runCatching {
        context.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,Process.myUid(),context.packageName)==AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
    fun preciseAllowed(context: Context)=context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    fun sha1(context: Context): String = runCatching {
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        val signature=info.signingInfo!!.apkContentsSigners.first().toByteArray()
        MessageDigest.getInstance("SHA-1").digest(signature).joinToString(":") { "%02X".format(it) }
    }.getOrDefault("无法读取签名")
}
