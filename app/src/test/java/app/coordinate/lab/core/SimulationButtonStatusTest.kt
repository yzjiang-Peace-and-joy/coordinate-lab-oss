package app.coordinate.lab.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationButtonStatusTest {
    @Test fun inactiveAlwaysCollapsesRegardlessOfOldStatus() {
        InjectionStatus.entries.forEach {assertEquals(SimulationButtonStatus.IDLE,SimulationButtonStatus.resolve(false,it,true,true))}
    }
    @Test fun startingDoesNotClaimSuccessfulWrites() {assertEquals(SimulationButtonStatus.STARTING,SimulationButtonStatus.resolve(true,InjectionStatus.STARTING,false,false))}
    @Test fun activeWithStoppedHealthStillWaitsForWriter() {assertEquals(SimulationButtonStatus.STARTING,SimulationButtonStatus.resolve(true,InjectionStatus.STOPPED,false,false))}
    @Test fun freshSessionShowsWriting() {assertEquals(SimulationButtonStatus.WRITING,SimulationButtonStatus.resolve(true,InjectionStatus.FRESH,false,false))}
    @Test fun staleOverridesPauseAndArrival() {assertEquals(SimulationButtonStatus.STALE,SimulationButtonStatus.resolve(true,InjectionStatus.STALE,true,true))}
    @Test fun pausedFreshSessionShowsPaused() {assertEquals(SimulationButtonStatus.PAUSED,SimulationButtonStatus.resolve(true,InjectionStatus.FRESH,true,false))}
    @Test fun arrivalTakesPrecedenceOverPause() {assertEquals(SimulationButtonStatus.ARRIVED,SimulationButtonStatus.resolve(true,InjectionStatus.FRESH,true,true))}
}
