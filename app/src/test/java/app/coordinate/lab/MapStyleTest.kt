package app.coordinate.lab

import org.junit.Assert.*
import org.junit.Test

class MapStyleTest {
    @Test fun sixSdkStylesHaveUniqueMappings() {assertEquals((1..6).toList(),MapStyle.entries.map {it.sdkType})}
    @Test fun savedStyleRestoresExactly() {MapStyle.entries.forEach {assertEquals(it,MapStyle.fromStored(it.name))}}
    @Test fun unknownOrMissingStyleUsesStandard() {listOf(null,"","unsupported").forEach {assertEquals(MapStyle.NORMAL,MapStyle.fromStored(it))}}
}
