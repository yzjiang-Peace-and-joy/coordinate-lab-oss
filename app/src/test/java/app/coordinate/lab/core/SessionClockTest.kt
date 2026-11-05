package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class SessionClockTest {
    @Test fun startsAtZero() { assertEquals("00:00:00", SessionClock.format(SessionClock.seconds(true,1000,1000))) }
    @Test fun advancesIndependentlyOfWrites() { assertEquals(65L,SessionClock.seconds(true,1000,66000)) }
    @Test fun stopsResetAndNewSessionRestarts() {
        assertEquals(0L,SessionClock.seconds(false,1000,66000))
        assertEquals(0L,SessionClock.seconds(true,66000,66000))
    }
    @Test fun monotonicAndMissingStartSafe() {
        assertEquals(0L,SessionClock.seconds(true,1000,999))
        assertEquals(0L,SessionClock.seconds(true,0,66000))
    }
    @Test fun hoursMinutesSeconds() {
        assertEquals("01:01:01",SessionClock.format(3661))
        assertEquals("100:00:00",SessionClock.format(360000))
    }
    @Test fun rejectsMockAndStaleFixes() {
        assertFalse(RealFixPolicy.accepts(true,0,30.0,110.0))
        assertFalse(RealFixPolicy.accepts(false,30001,30.0,110.0))
        assertFalse(RealFixPolicy.accepts(false,-1,30.0,110.0))
    }
    @Test fun acceptsFreshRealFix() {assertTrue(RealFixPolicy.accepts(false,1000,30.0,110.0))}
    @Test fun rejectsInvalidCoordinates() {
        assertFalse(RealFixPolicy.accepts(false,0,Double.NaN,110.0))
        assertFalse(RealFixPolicy.accepts(false,0,91.0,110.0))
    }
}
