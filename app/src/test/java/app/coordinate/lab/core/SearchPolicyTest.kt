package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class SearchPolicyTest {
    @Test fun sparseFirstNearbyPageExpandsOnlyOnce() {
        assertTrue(SearchPolicy.shouldExpandNearby(20_000, 0, 1))
        assertTrue(SearchPolicy.shouldExpandNearby(20_000, 2, 1))
        assertFalse(SearchPolicy.shouldExpandNearby(20_000, 3, 1))
        assertFalse(SearchPolicy.shouldExpandNearby(20_000, 0, 2))
        assertFalse(SearchPolicy.shouldExpandNearby(50_000, 0, 1))
    }

    @Test fun nationwideFallbackRequiresEmptyExpandedFirstPage() {
        assertTrue(SearchPolicy.shouldFallbackNationwide(50_000, 0, 1))
        assertFalse(SearchPolicy.shouldFallbackNationwide(20_000, 0, 1))
        assertFalse(SearchPolicy.shouldFallbackNationwide(50_000, 1, 1))
        assertFalse(SearchPolicy.shouldFallbackNationwide(50_000, 0, 2))
    }

    @Test fun paginationIsOneBasedAndStopsAtReportedEndOrEmptyPage() {
        assertEquals(2, SearchPolicy.nextPage(1, 4, 20))
        assertEquals(4, SearchPolicy.nextPage(3, 4, 1))
        assertNull(SearchPolicy.nextPage(4, 4, 20))
        assertNull(SearchPolicy.nextPage(1, 4, 0))
        assertNull(SearchPolicy.nextPage(1, 0, 20))
        try { SearchPolicy.nextPage(0, 4, 20); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsMissingSentinelNonfiniteAndOutOfRangeCoordinates() {
        assertFalse(SearchPolicy.usableCoordinate(0.0, 0.0))
        assertFalse(SearchPolicy.usableCoordinate(Double.NaN, 120.0))
        assertFalse(SearchPolicy.usableCoordinate(30.0, Double.POSITIVE_INFINITY))
        assertFalse(SearchPolicy.usableCoordinate(90.01, 120.0))
        assertFalse(SearchPolicy.usableCoordinate(30.0, 180.01))
        assertTrue(SearchPolicy.usableCoordinate(0.0, 120.0))
        assertTrue(SearchPolicy.usableCoordinate(30.0, 0.0))
        assertTrue(SearchPolicy.usableCoordinate(31.23, 121.47))
    }

    @Test fun dedupKeepsDifferentNamesOrBranchesWithoutLocaleDependence() {
        data class Candidate(val name: String, val point: GeoPoint)
        val a = Candidate(" Shop ", GeoPoint(31.0, 121.0))
        val duplicate = Candidate("shop", GeoPoint(31.00000001, 121.0))
        val otherBranch = Candidate("shop", GeoPoint(31.1, 121.0))
        val otherPoi = Candidate("cafe", a.point)
        assertEquals(listOf(a, otherBranch, otherPoi), SearchPolicy.distinctPlaces(listOf(a, duplicate, otherBranch, otherPoi), { it.name }, { it.point }))
    }

    @Test fun strictCityAddressFallbackCannotLeakAnotherCity() {
        assertTrue(SearchPolicy.addressMatchesCity("上海", "上海市", "上海市", "黄浦区", "310101"))
        assertTrue(SearchPolicy.addressMatchesCity("杭州市", "浙江省", "杭州市", "西湖区", "330106"))
        assertTrue(SearchPolicy.addressMatchesCity("330100", "浙江省", "杭州市", "西湖区", "330106"))
        assertTrue(SearchPolicy.addressMatchesCity("310000", "上海市", "上海市", "黄浦区", "310101"))
        assertFalse(SearchPolicy.addressMatchesCity("北京", "上海市", "上海市", "黄浦区", "310101"))
        assertFalse(SearchPolicy.addressMatchesCity("330100", "浙江省", "宁波市", "海曙区", "330203"))
        assertFalse(SearchPolicy.addressMatchesCity("", "上海市", "上海市", "黄浦区", "310101"))
    }

    @Test fun completionAndTimeoutCanWinOnlyOnce() {
        val gate = SearchCompletionGate()
        val start = CountDownLatch(1)
        val winners = AtomicInteger()
        val contenders = (1..8).map {
            Thread { start.await(); if (gate.tryFinish()) winners.incrementAndGet() }.apply { start() }
        }
        start.countDown()
        contenders.forEach { it.join() }
        assertEquals(1, winners.get())
        assertFalse(gate.isOpen)
    }

    @Test fun cancelledSearchCannotDeliverLaterCallbackOrTimeout() {
        val gate = SearchCompletionGate()
        gate.cancel()
        assertFalse(gate.isOpen)
        assertFalse(gate.tryFinish())
        gate.cancel()
        assertFalse(gate.tryFinish())
    }
}
