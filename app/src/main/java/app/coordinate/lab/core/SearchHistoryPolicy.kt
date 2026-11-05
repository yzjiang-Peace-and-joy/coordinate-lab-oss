package app.coordinate.lab.core

/** Local search terms in newest-first order; callers decide when a search is committed. */
object SearchHistoryPolicy {
    const val MAX_ENTRIES=20
    const val MAX_QUERY_LENGTH=120

    fun record(current: List<String>, query: String): List<String> {
        val committed=query.trim().take(MAX_QUERY_LENGTH)
        return filter(if (committed.isEmpty()) current else listOf(committed)+current)
    }

    /** Normalizes stored terms without reversing their order, then optionally filters suggestions. */
    fun filter(current: List<String>, query: String=""): List<String> {
        val normalized=mutableListOf<String>()
        for (item in current) {
            val term=item.trim().take(MAX_QUERY_LENGTH)
            if (term.isEmpty() || normalized.any { it.equals(term,ignoreCase=true) }) continue
            normalized.add(term)
            if (normalized.size==MAX_ENTRIES) break
        }
        val needle=query.trim()
        return if (needle.isEmpty()) normalized else normalized.filter { it.contains(needle,ignoreCase=true) }
    }
}
