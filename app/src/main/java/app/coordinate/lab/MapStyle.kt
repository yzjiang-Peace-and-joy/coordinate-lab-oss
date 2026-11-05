package app.coordinate.lab

import com.amap.api.maps.AMap

enum class MapStyle(val label: String,val sdkType: Int) {
    NORMAL("标准",AMap.MAP_TYPE_NORMAL),
    SATELLITE("卫星",AMap.MAP_TYPE_SATELLITE),
    NIGHT("夜景",AMap.MAP_TYPE_NIGHT),
    NAVIGATION("导航",AMap.MAP_TYPE_NAVI),
    TRANSIT("公交",AMap.MAP_TYPE_BUS),
    NAVIGATION_NIGHT("夜间导航",AMap.MAP_TYPE_NAVI_NIGHT);

    companion object {
        fun fromStored(value: String?): MapStyle=entries.firstOrNull {it.name==value} ?: NORMAL
    }
}
