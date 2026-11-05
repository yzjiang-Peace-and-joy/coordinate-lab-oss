package app.coordinate.lab.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MapKeyConfigTest {
    private val bundled="0123456789abcdef0123456789abcdef"
    private val custom="abcdef0123456789abcdef0123456789"

    @Test fun firstInstallUsesBundledKey() {
        assertEquals(bundled,MapKeyConfig.resolve(null,bundled))
    }
    @Test fun manualKeyOverridesBundledKey() {
        assertEquals(custom,MapKeyConfig.resolve(custom,bundled))
    }
    @Test fun removingKeyDoesNotReactivateBundledKey() {
        assertEquals("",MapKeyConfig.resolve("",bundled))
    }
    @Test fun projectWithoutLocalKeyRemainsOffline() {
        assertEquals("",MapKeyConfig.resolve(null,""))
    }
}
