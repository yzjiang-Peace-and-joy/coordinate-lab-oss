package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class RouteTimelineTest {
    @Test fun tickAccumulatesExactElapsedRealtimeMilliseconds() {
        val timeline=RouteTimeline(1_000)
        assertEquals(0L,timeline.tick(1_000).motionMillis)
        val state=timeline.tick(2_234)
        assertEquals(1_234L,state.motionMillis)
        assertEquals(1.234,state.motionSeconds,0.0)
        assertFalse(state.paused)
    }
    @Test fun pauseDoesNotAccumulateAndResumeDoesNotJump() {
        val timeline=RouteTimeline(1_000)
        assertEquals(1_500L,timeline.tick(2_500).motionMillis)
        val paused=timeline.togglePause(3_000)
        assertTrue(paused.paused);assertEquals(2_000L,paused.motionMillis)
        assertEquals(2_000L,timeline.tick(9_000).motionMillis)
        val resumed=timeline.togglePause(9_000)
        assertFalse(resumed.paused);assertEquals(2_000L,resumed.motionMillis)
        assertEquals(2_250L,timeline.tick(9_250).motionMillis)
    }
    @Test fun rejectsClockRegression() {
        val timeline=RouteTimeline(100)
        timeline.tick(200)
        try { timeline.tick(199);fail("elapsedRealtime must be monotonic") } catch (_: IllegalArgumentException) {}
    }
}
