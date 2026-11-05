package app.coordinate.lab.core

data class NearbyName(val name: String,val distance: Double)
data class PointAddressLabel(val title: String,val address: String)

object PointAddressLabels {
    fun make(province: String,city: String,district: String,township: String,neighborhood: String,
        formatted: String,pois: List<NearbyName>,roads: List<NearbyName>): PointAddressLabel? {
        fun nearest(items: List<NearbyName>,limit: Double)=items.filter {it.name.isNotBlank() && it.distance.isFinite() && it.distance in 0.0..limit}.minByOrNull {it.distance}?.name?.trim()
        val full=formatted.trim().ifBlank {listOf(province,city,district,township,neighborhood).map{it.trim()}.filter{it.isNotEmpty()}.distinct().joinToString("")}
        val local=full.removePrefix(province.trim()).removePrefix(city.trim()).removePrefix(district.trim()).trim()
        val title=nearest(pois,80.0)?.let {"${it}附近"} ?: nearest(roads,100.0)?.let {"${it}附近"}
            ?: neighborhood.trim().ifBlank {township.trim()}.ifBlank {local}.ifBlank {district.trim()}.ifBlank {city.trim()}.ifBlank {province.trim()}
        if(title.isBlank())return null
        return PointAddressLabel(title,full)
    }
}
