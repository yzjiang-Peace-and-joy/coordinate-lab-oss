package app.coordinate.lab.core

/** Health problems take precedence over pause/arrival; stopped sessions expose only the action icon. */
enum class SimulationButtonStatus {
    IDLE, STARTING, WRITING, STALE, PAUSED, ARRIVED;

    companion object {
        fun resolve(active: Boolean,health: InjectionStatus,paused: Boolean,arrived: Boolean): SimulationButtonStatus = when {
            !active -> IDLE
            health==InjectionStatus.STALE -> STALE
            health!=InjectionStatus.FRESH -> STARTING
            arrived -> ARRIVED
            paused -> PAUSED
            else -> WRITING
        }
    }
}
