package app.coordinate.lab.core

object MapKeyConfig {
    /** Null means never configured; an explicit empty override means the user disabled maps. */
    fun resolve(saved: String?, bundled: String): String = saved ?: bundled
}
