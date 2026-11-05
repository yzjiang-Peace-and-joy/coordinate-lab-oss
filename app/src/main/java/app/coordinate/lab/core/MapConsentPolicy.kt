package app.coordinate.lab.core

/** Bundled client configuration is not permission to contact the map provider. */
object MapConsentPolicy {
    fun mayInitialize(key: String, agreed: Boolean): Boolean = agreed && key.isNotBlank()
}
