package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class DrawingStrokeSamplerTest {
    private fun north(meters: Double) = GeoPoint(30.0 + meters / 111_195.0, 120.0)

    @Test fun samplesByScreenDistanceOrGeographicDistance() {
        val sampler = DrawingStrokeSampler(2f)
        assertTrue(sampler.append(0f, 0f, north(0.0)))
        assertFalse(sampler.append(1f, 0f, north(0.5)))
        assertTrue(sampler.append(2f, 0f, north(0.6)))
        assertTrue(sampler.append(2.1f, 0f, north(2.0)))
        assertEquals(listOf(north(0.0), north(0.6), north(2.0)), sampler.points)
    }

    @Test fun forcePreservesExactEndpointAndIgnoresExactDuplicates() {
        val sampler = DrawingStrokeSampler(2f)
        sampler.append(0f, 0f, north(0.0))
        sampler.append(5f, 0f, north(10.0))
        assertTrue(sampler.append(5.1f, 0f, north(10.1), force = true))
        assertFalse(sampler.append(5.2f, 0f, north(10.1), force = true))
        assertEquals(north(10.1), sampler.complete()!!.last())
    }

    @Test fun tapAndAccidentalTinyStrokeAreNotCommitted() {
        val sampler = DrawingStrokeSampler(2f)
        sampler.append(0f, 0f, north(0.0))
        assertNull(sampler.complete())
        sampler.append(1f, 0f, north(0.2), force = true)
        assertNull(sampler.complete())
    }

    @Test fun closedStrokeAndReturnTripRemainIntact() {
        val sampler = DrawingStrokeSampler(2f)
        sampler.append(0f, 0f, north(0.0))
        sampler.append(10f, 0f, north(10.0))
        sampler.append(0f, 0f, north(0.0))
        assertEquals(listOf(north(0.0), north(10.0), north(0.0)), sampler.complete())
    }

    @Test fun completionAndJoiningDefensivelyCopyAndOnlyRemoveAdjacentDuplicates() {
        val sampler = DrawingStrokeSampler(2f)
        sampler.append(0f, 0f, north(0.0))
        sampler.append(10f, 0f, north(10.0))
        val snapshot = sampler.complete()!!
        val existing = mutableListOf(north(-10.0), north(0.0))
        val joined = sampler.appendTo(existing)
        sampler.append(20f, 0f, north(0.0))
        existing.clear()
        assertEquals(listOf(north(0.0), north(10.0)), snapshot)
        assertEquals(listOf(north(-10.0), north(0.0), north(10.0)), joined)
    }

    @Test fun strokeLimitRejectsInsteadOfSilentlyTruncating() {
        val sampler = DrawingStrokeSampler(2f, maximumPoints = 3)
        repeat(3) { sampler.append(it * 3f, 0f, north(it * 2.0)) }
        expectFailure { sampler.append(9f, 0f, north(8.0)) }
        assertEquals(3, sampler.points.size)
    }

    @Test fun combinedLimitIsExactAndDoesNotMutateExistingDraftOnFailure() {
        val sampler = DrawingStrokeSampler(2f)
        sampler.append(0f, 0f, north(20_000.0))
        sampler.append(10f, 0f, north(20_010.0))
        val allowed = (0 until DrawingStrokeSampler.MAX_DRAWING_POINTS - 2).map { north(it.toDouble()) }
        assertEquals(DrawingStrokeSampler.MAX_DRAWING_POINTS, sampler.appendTo(allowed).size)
        val tooLong = allowed + north(15_000.0)
        expectFailure { sampler.appendTo(tooLong) }
        assertEquals(DrawingStrokeSampler.MAX_DRAWING_POINTS - 1, tooLong.size)
    }

    @Test fun invalidScreenPositionIsRejected() {
        expectFailure { DrawingStrokeSampler(0f) }
        expectFailure { DrawingStrokeSampler(2f).append(Float.NaN, 0f, north(0.0)) }
    }

    private fun expectFailure(block: () -> Unit) {
        try { block(); fail("Expected invalid drawing") } catch (_: IllegalArgumentException) { }
    }
}
