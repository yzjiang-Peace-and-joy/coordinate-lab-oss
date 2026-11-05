package app.coordinate.lab.core
import org.junit.Assert.*
import org.junit.Test

class PointAddressLabelsTest {
    private fun label(pois: List<NearbyName> = emptyList(),roads: List<NearbyName> = emptyList(),formatted: String="浙江省杭州市西湖区文三路100号")=
        PointAddressLabels.make("浙江省","杭州市","西湖区","","",formatted,pois,roads)!!
    @Test fun choosesNearestUsefulPoiAndMarksItNearby() {
        assertEquals("图书馆附近",label(listOf(NearbyName("商场",70.0),NearbyName("图书馆",20.0))).title)
    }
    @Test fun distantPoiDoesNotPretendToBeSelectedBuilding() {
        assertEquals("文三路附近",label(listOf(NearbyName("商场",90.0)),listOf(NearbyName("文三路",10.0))).title)
    }
    @Test fun invalidOrMissingDistancesAreIgnored() {
        assertEquals("文三路100号",label(listOf(NearbyName("错误",Double.NaN),NearbyName("错误",-1.0))).title)
    }
    @Test fun keepsFullAddressWhileTitleIsCompact() {
        val value=label();assertEquals("文三路100号",value.title);assertEquals("浙江省杭州市西湖区文三路100号",value.address)
    }
    @Test fun municipalityDoesNotRequireRepeatedCityName() {
        assertEquals("中山路",PointAddressLabels.make("上海市","上海市","黄浦区","","","上海市黄浦区中山路",emptyList(),emptyList())!!.title)
    }
    @Test fun emptyResponseIsNotFabricated() {
        assertNull(PointAddressLabels.make("","","","","","",emptyList(),emptyList()))
    }
}
