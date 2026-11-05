package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class MapConsentPolicyTest {
    private val bundled="0123456789abcdef0123456789abcdef"

    @Test fun bundledConfigurationNeverGrantsConsentOnFirstInstall() {
        val key=MapKeyConfig.resolve(null,bundled)
        assertFalse(MapConsentPolicy.mayInitialize(key,false))
    }
    @Test fun existingConsentWithConfigurationAllowsInitialization() {
        assertTrue(MapConsentPolicy.mayInitialize(bundled,true))
    }
    @Test fun agreeingWithoutConfigurationDoesNotInitialize() {
        assertFalse(MapConsentPolicy.mayInitialize("",true))
        assertFalse(MapConsentPolicy.mayInitialize("  \t",true))
    }
    @Test fun noConfigurationAndNoConsentRemainsOffline() {
        assertFalse(MapConsentPolicy.mayInitialize("",false))
    }
    @Test fun withdrawalDoesNotRequireRemovingTheConfiguration() {
        val key=MapKeyConfig.resolve(null,bundled)
        assertTrue(MapConsentPolicy.mayInitialize(key,true))
        assertFalse(MapConsentPolicy.mayInitialize(key,false))
        assertEquals(bundled,key)
        assertTrue(MapConsentPolicy.mayInitialize(key,true))
    }
    @Test fun previousExplicitKeyRemovalDoesNotSilentlyReactivateBundledConfiguration() {
        val key=MapKeyConfig.resolve("",bundled)
        assertEquals("",key)
        assertFalse(MapConsentPolicy.mayInitialize(key,true))
    }
}
