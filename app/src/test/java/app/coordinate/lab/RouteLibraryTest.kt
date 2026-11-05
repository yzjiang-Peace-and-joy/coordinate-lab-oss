package app.coordinate.lab

import app.coordinate.lab.core.GeoPoint
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RouteLibraryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun north(index: Int) = GeoPoint(30.0 + index * 0.00001, 120.0)
    private fun trace(size: Int) = List(size) { north(it) }
    private fun places() = listOf(Place("起点", "杭州", north(0)), Place("终点", "杭州", north(20)))
    private fun route(name: String = "晨跑") = RouteLibraryPolicy.current(name, places(), emptyList(), false, false, 5.0, false, 100L)
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun trimsNamesWithoutChangingIdentity() {
        val saved = route("  晨跑  ")
        val renamed = RouteLibraryPolicy.rename(listOf(saved), saved.id, "  河边路线  ").single()
        assertEquals("晨跑", saved.name); assertEquals("河边路线", renamed.name)
        assertEquals(saved.id, renamed.id); assertEquals(saved.createdAt, renamed.createdAt)
        assertEquals(saved.points, renamed.points); assertEquals(saved.waypoints, renamed.waypoints)
    }

    @Test fun validatesEmptyLongAndControlNames() {
        rejects { route("   ") }; rejects { route("名".repeat(41)) }; rejects { route("第一行\n第二行") }
        assertEquals(40, route("名".repeat(40)).name.length)
    }

    @Test fun renameUnknownIdDoesNotModifyLibrary() {
        val saved = route(); val all = listOf(saved)
        rejects { RouteLibraryPolicy.rename(all, "missing", "名字") }
        assertEquals(listOf(saved), all)
    }

    @Test fun deleteOnlyRemovesRequestedIdNotSameName() {
        val first = route(); val second = route()
        assertEquals(listOf(second), RouteLibraryPolicy.delete(listOf(first, second), first.id))
        assertEquals(listOf(first, second), RouteLibraryPolicy.delete(listOf(first, second), "missing"))
    }

    @Test fun addDoesNotEvictExistingRoutesAtLimit() {
        val routes = List(RouteLibraryPolicy.MAX_ROUTES) { route() }
        rejects { RouteLibraryPolicy.add(routes, route()) }
        assertEquals(50, routes.size)
        rejects { RouteLibraryPolicy.add(listOf(routes.first()), routes.first()) }
    }

    @Test fun rejectsTotalTraceBudgetWithoutSilentlyReducingPoints() {
        val routes = List(10) { RouteLibraryPolicy.drawn("路线", trace(10_000)) }
        assertEquals(100_000, routes.sumOf { it.points.size })
        rejects { RouteLibraryPolicy.add(routes, route()) }
    }

    @Test fun handDrawnRoutePreservesEveryExactPointIncludingDuplicates() {
        val original = (trace(51) + north(50) + trace(50).reversed()).toMutableList()
        val saved = RouteLibraryPolicy.drawn("手绘", original)
        assertEquals(original, saved.points); assertTrue(saved.waypoints.isEmpty())
        assertEquals(SavedRouteKind.DRAWN, saved.kind); assertTrue(saved.previewGeometryReady)
        original.clear(); assertEquals(102, saved.points.size)
    }

    @Test fun denseSubDecimeterSketchStillValidByTotalLength() {
        val points = List(100) { GeoPoint(30.0 + it * 0.0000001, 120.0) }
        assertEquals(points, RouteLibraryPolicy.drawn("密集手绘", points).points)
    }

    @Test fun supportsTenThousandDrawnPointsButRejectsOverflowAndEmptyTrace() {
        assertEquals(10_000, RouteLibraryPolicy.drawn("长路线", trace(10_000)).points.size)
        rejects { RouteLibraryPolicy.drawn("过长", trace(10_001)) }
        rejects { RouteLibraryPolicy.drawn("空", emptyList()) }
        rejects { RouteLibraryPolicy.drawn("单点", trace(1)) }
        rejects { RouteLibraryPolicy.drawn("重复点", List(10) { north(0) }) }
    }

    @Test fun waypointCountLimitDoesNotApplyToDrawnTrace() {
        assertEquals(300, RouteLibraryPolicy.drawn("手绘", trace(300)).points.size)
        val tooMany = List(21) { Place("点$it", "", north(it)) }
        rejects { RouteLibraryPolicy.current("选点", tooMany, emptyList(), false, false, 5.0, false) }
    }

    @Test fun roadDraftKeepsIntentButNeverClaimsRoadGeometryReady() {
        val saved = RouteLibraryPolicy.current("待规划", places(), trace(100), true, true, 6.0, false)
        assertTrue(saved.road); assertTrue(saved.closed); assertFalse(saved.previewGeometryReady)
        assertEquals(listOf(north(0), north(20), north(0)), saved.points)
        assertEquals(6.0, saved.speedKph, 0.0)
    }

    @Test fun readyRoadSnapshotKeepsExactGeometryAndSettings() {
        val geometry = trace(21)
        val saved = RouteLibraryPolicy.current("沿路", places(), geometry, true, false, 8.0, true)
        assertEquals(geometry, saved.points); assertTrue(saved.previewGeometryReady); assertTrue(saved.road)
        assertEquals(places(), saved.waypoints); assertEquals(8.0, saved.speedKph, 0.0)
    }

    @Test fun currentDraftRejectsInvalidEndpointsOrSpeed() {
        rejects { RouteLibraryPolicy.current("坏路线", places(), trace(4), true, false, 5.0, true) }
        rejects { RouteLibraryPolicy.current("坏速度", places(), emptyList(), false, false, Double.NaN, false) }
        rejects { RouteLibraryPolicy.current("坏速度", places(), emptyList(), false, false, 31.0, false) }
    }

    @Test fun codecPreservesDrawnDoublePrecisionAndAllTracePoints() {
        val points = List(10_000) { GeoPoint(30.123456789012 + it * 0.0000001, 120.987654321012) }
        val saved = RouteLibraryPolicy.drawn("精度测试", points)
        val decoded = RouteLibraryCodec.decodeRoute(RouteLibraryCodec.encodeRoute(saved))
        assertEquals(saved, decoded); assertEquals(points, decoded.points)
    }

    @Test fun codecPreservesRoadIntentAndReadyFlag() {
        val saved = RouteLibraryPolicy.current("待规划", places(), emptyList(), true, true, 5.0, false)
        assertEquals(saved, RouteLibraryCodec.decodeRoute(RouteLibraryCodec.encodeRoute(saved)))
    }

    @Test fun codecRejectsCorruptionTruncationAndWrongRecordType() {
        val bytes = RouteLibraryCodec.encodeRoute(route())
        rejects { RouteLibraryCodec.decodeRoute(bytes.copyOf(bytes.size - 1)) }
        rejects { RouteLibraryCodec.decodeRoute(bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }) }
        rejects { RouteLibraryCodec.decodeMarker(bytes) }
        rejects { RouteLibraryCodec.decodeRoute(ByteArray(RouteLibraryCodec.MAX_RECORD_BYTES + 1)) }
    }

    @Test fun markerCodecRoundTripsUnicodeAndExactPoint() {
        val marker = Place("桥边 · 标记", "杭州", GeoPoint(30.1234567890123, 120.111111111111), "河岸")
        assertEquals(marker, RouteLibraryCodec.decodeMarker(RouteLibraryCodec.encodeMarker(marker)))
    }

    @Test fun corruptRecordDoesNotHideOrDeleteOtherValidRoutes() {
        val folder = temporary.newFolder(); val store = RouteLibraryStore(folder)
        val first = route("一"); val second = route("二")
        store.writeRoute(first); store.writeRoute(second)
        File(folder, "routes/${first.id}.bin").writeBytes(byteArrayOf(1, 2, 3))
        val loaded = store.load()
        assertEquals(listOf(second), loaded.routes); assertEquals(1, loaded.unreadableRecords)
        assertTrue(File(folder, "routes/${first.id}.bin").exists())
        assertEquals(second, RouteLibraryStore(folder).load().routes.single())
    }

    @Test fun atomicRenameRetainsIdAndOtherRoutesAcrossReload() {
        val folder = temporary.newFolder(); val store = RouteLibraryStore(folder)
        val first = route("一"); val second = route("二")
        store.writeRoute(first); store.writeRoute(second)
        store.writeRoute(first.copy(name = "重命名"))
        val loaded = RouteLibraryStore(folder).load().routes
        assertEquals(2, loaded.size); assertEquals("重命名", loaded.first { it.id == first.id }.name)
        assertEquals(second, loaded.first { it.id == second.id })
        assertTrue(File(folder, "routes").listFiles()!!.none { it.extension == "tmp" })
    }

    @Test fun storeDeleteDoesNotTouchOtherRecordsAndRejectsPathTraversal() {
        val folder = temporary.newFolder(); val store = RouteLibraryStore(folder)
        val first = route("一"); val second = route("二")
        store.writeRoute(first); store.writeRoute(second)
        rejects { store.deleteRoute("../anything") }
        store.deleteRoute(first.id)
        assertEquals(listOf(second), store.load().routes)
    }

    @Test fun markerUpsertDoesNotDuplicateAndPersistsIndependentFromRoute() {
        val folder = temporary.newFolder(); val store = RouteLibraryStore(folder)
        val marker = places().first(); val saved = route()
        store.writeRoute(saved); store.writeMarker(marker); store.writeMarker(marker.copy(name = "改名标记"))
        val loaded = RouteLibraryStore(folder).load()
        assertEquals(listOf(saved), loaded.routes)
        assertEquals(listOf(marker.copy(name = "改名标记")), loaded.markers)
        store.deleteMarker(marker.id); assertTrue(store.load().markers.isEmpty())
        assertEquals(listOf(saved), store.load().routes)
    }

    @Test fun markerLimitRejectsNewRecordButAllowsRenamingExistingOne() {
        val folder = temporary.newFolder(); val store = RouteLibraryStore(folder)
        val markers = List(100) { Place("标记$it", "", north(it)) }
        markers.forEach { store.writeMarker(it) }
        rejects { store.writeMarker(Place("超额", "", north(101))) }
        store.writeMarker(markers.first().copy(name = "新名称"))
        assertEquals(100, store.load().markers.size)
        assertEquals("新名称", store.load().markers.first { it.id == markers.first().id }.name)
    }
}
