package app.coordinate.lab.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryPolicyTest {
    @Test fun recordAddsTrimmedQueryToFront() {
        assertEquals(listOf("杭州西湖","上海"),SearchHistoryPolicy.record(listOf("上海"),"  杭州西湖\n"))
    }

    @Test fun blankQueryDoesNotCreateAnEntry() {
        assertEquals(listOf("上海"),SearchHistoryPolicy.record(listOf("上海")," \t\n"))
    }

    @Test fun recordMovesDuplicateToFrontAndKeepsLatestSpelling() {
        assertEquals(
            listOf("Beijing","上海","深圳"),
            SearchHistoryPolicy.record(listOf("上海","BEIJING","深圳","beijing")," Beijing ")
        )
    }

    @Test fun recordTruncatesBeforeDeduplicating() {
        val limit="地".repeat(SearchHistoryPolicy.MAX_QUERY_LENGTH)
        assertEquals(listOf(limit,"上海"),SearchHistoryPolicy.record(listOf("上海",limit),limit+"标"))
    }

    @Test fun recordKeepsOnlyTwentyNewestTerms() {
        val actual=SearchHistoryPolicy.record((1..30).map { "位置$it" },"新位置")
        assertEquals(20,actual.size)
        assertEquals("新位置",actual.first())
        assertEquals("位置19",actual.last())
    }

    @Test fun filterSanitizesWithoutReversingNewestFirstOrder() {
        assertEquals(
            listOf("Beijing","上海","杭州"),
            SearchHistoryPolicy.filter(listOf(" Beijing ","","  ","上海","beijing"," 杭州 "))
        )
    }

    @Test fun filterNormalizesLengthsAndBounds() {
        val actual=SearchHistoryPolicy.filter(listOf("x".repeat(121))+(1..25).map { "位置$it" })
        assertEquals(20,actual.size)
        assertEquals("x".repeat(120),actual.first())
        assertEquals("位置19",actual.last())
        assertTrue(actual.all { it.length<=SearchHistoryPolicy.MAX_QUERY_LENGTH })
    }

    @Test fun filterMatchesTrimmedQueryWithoutCaseSensitivity() {
        assertEquals(listOf("Beijing South","BEIJING North"),SearchHistoryPolicy.filter(listOf("上海","Beijing South","BEIJING North")," beijing "))
    }

    @Test fun blankFilterQueryReturnsNormalizedHistory() {
        assertEquals(listOf("上海","北京"),SearchHistoryPolicy.filter(listOf(" 上海 ","北京",""),"  "))
    }

    @Test fun emptyHistoryAndBlankQueryRemainEmpty() {
        assertTrue(SearchHistoryPolicy.record(emptyList()," ").isEmpty())
        assertTrue(SearchHistoryPolicy.filter(emptyList(),"上海").isEmpty())
    }
}
