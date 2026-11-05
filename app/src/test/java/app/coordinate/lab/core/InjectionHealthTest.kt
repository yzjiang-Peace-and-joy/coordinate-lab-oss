package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class InjectionHealthTest {
    @Test fun distinguishesStartupFromAnActualSuccessfulWrite() {
        val health=InjectionHealth()
        assertEquals(InjectionStatus.STOPPED,health.status(false,10_000))
        assertEquals(InjectionStatus.STARTING,health.status(true,10_000))
        assertNull(health.ageMs(10_000))
    }
    @Test fun flagsStaleEvenThoughTheServiceRemainsActive() {
        val health=InjectionHealth().success(1_000,100_000)
        assertEquals(InjectionStatus.FRESH,health.status(true,6_000))
        assertEquals(InjectionStatus.STALE,health.status(true,6_001))
        assertEquals(InjectionStatus.STOPPED,health.status(false,6_001))
    }
    @Test fun recordsInterruptionEvenAfterForegroundRefreshResumes() {
        val health=InjectionHealth().success(1_000,100_000).success(2_000,101_000).success(182_000,281_000)
        assertEquals(3L,health.writes)
        assertEquals(180_000L,health.longestGapMs)
        assertEquals(InjectionStatus.FRESH,health.status(true,182_000))
    }
    @Test fun wallClockChangesDoNotAffectFreshness() {
        val health=InjectionHealth().success(1_000,100_000).success(2_000,10_000)
        assertEquals(1_000L,health.longestGapMs)
        assertEquals(InjectionStatus.STALE,health.status(true,8_000))
    }
    @Test fun elapsedZeroIsStillASuccessfulWrite() {
        val health=InjectionHealth().success(0,100)
        assertEquals(InjectionStatus.FRESH,health.status(true,0))
        assertEquals(1L,health.writes)
    }
}
