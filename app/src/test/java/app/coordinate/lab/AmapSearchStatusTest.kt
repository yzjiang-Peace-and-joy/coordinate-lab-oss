package app.coordinate.lab

import com.amap.api.services.core.AMapException
import org.junit.Assert.*
import org.junit.Test

class AmapSearchStatusTest {
    @Test fun androidSdkSuccessIs1000NotWebRest10000() {
        assertEquals(1000, AMapException.CODE_AMAP_SUCCESS)
        assertTrue(AmapSearchStatus.isSuccess(AMapException.CODE_AMAP_SUCCESS))
        assertTrue(AmapSearchStatus.isSuccess(1000))
        assertEquals(AmapSearchStatus.Category.SUCCESS, AmapSearchStatus.category(1000))
        assertFalse(AmapSearchStatus.isSuccess(10000))
        assertEquals(AmapSearchStatus.Category.UNKNOWN, AmapSearchStatus.category(10000))
        assertTrue(AmapSearchStatus.explain(10000).contains("未成功"))
    }

    @Test fun authorizationErrorsStayFailuresAndExplainBindings() {
        val codes = listOf(AMapException.CODE_AMAP_SIGNATURE_ERROR, AMapException.CODE_AMAP_INVALID_USER_KEY,
            AMapException.CODE_AMAP_INVALID_USER_IP, AMapException.CODE_AMAP_INVALID_USER_DOMAIN,
            AMapException.CODE_AMAP_INVALID_USER_SCODE, AMapException.CODE_AMAP_USERKEY_PLAT_NOMATCH,
            AMapException.CODE_AMAP_INSUFFICIENT_PRIVILEGES, AMapException.CODE_AMAP_USER_KEY_RECYCLED)
        assertFailures(codes, AmapSearchStatus.Category.AUTHORIZATION, "授权")
        codes.forEach { assertTrue(AmapSearchStatus.explain(it).contains("SHA1")) }
    }

    @Test fun quotaAndRateLimitsStayFailures() {
        assertFailures(listOf(10021, AMapException.CODE_AMAP_DAILY_QUERY_OVER_LIMIT, AMapException.CODE_AMAP_ACCESS_TOO_FREQUENT,
            AMapException.CODE_AMAP_IP_QUERY_OVER_LIMIT), AmapSearchStatus.Category.QUOTA, "配额")
    }

    @Test fun serverAndClientTimeoutsAndNetworkErrorsStayFailures() {
        assertFailures(listOf(AMapException.CODE_AMAP_ENGINE_CONNECT_TIMEOUT, AMapException.CODE_AMAP_ENGINE_RETURN_TIMEOUT,
            AMapException.CODE_AMAP_CLIENT_SOCKET_TIMEOUT_EXCEPTION, AMapException.CODE_AMAP_CLIENT_UNKNOWHOST_EXCEPTION,
            AMapException.CODE_AMAP_CLIENT_NETWORK_EXCEPTION, AMapException.CODE_AMAP_CLIENT_IO_EXCEPTION),
            AmapSearchStatus.Category.NETWORK, "网络")
    }

    @Test fun routeFailuresHaveRouteSpecificMessagesNotKeyAdvice() {
        val codes = listOf(AMapException.CODE_AMAP_ROUTE_OUT_OF_SERVICE, AMapException.CODE_AMAP_ROUTE_NO_ROADS_NEARBY,
            AMapException.CODE_AMAP_ROUTE_FAIL, AMapException.CODE_AMAP_OVER_DIRECTION_RANGE)
        assertFailures(codes, AmapSearchStatus.Category.ROUTE, "路线")
        codes.forEach { assertFalse(AmapSearchStatus.explain(it).contains("Key")) }
    }

    @Test fun requestAndServiceErrorsAreNotEmptySuccesses() {
        assertFailures(listOf(AMapException.CODE_AMAP_SERVICE_INVALID_PARAMS, AMapException.CODE_AMAP_SERVICE_MISSING_REQUIRED_PARAMS,
            AMapException.CODE_AMAP_SERVICE_ILLEGAL_REQUEST, AMapException.CODE_AMAP_CLIENT_INVALID_PARAMETER,
            AMapException.CODE_AMAP_CLIENT_OVER_KEYWORD_LEN_MAX_COUNT_EXCEPTION), AmapSearchStatus.Category.REQUEST, "参数")
        assertFailures(listOf(AMapException.CODE_AMAP_SERVICE_NOT_AVAILBALE, AMapException.CODE_AMAP_NOT_SUPPORT_HTTPS,
            AMapException.CODE_AMAP_ENGINE_RESPONSE_ERROR, AMapException.CODE_AMAP_ENGINE_RESPONSE_DATA_ERROR,
            AMapException.CODE_AMAP_SERVICE_UNKNOWN_ERROR, AMapException.CODE_AMAP_CLIENT_ERROR_PROTOCOL,
            AMapException.CODE_AMAP_CLIENT_UNKNOWN_ERROR, AMapException.CODE_AMAP_CLIENT_NULLPOINT_EXCEPTION,
            AMapException.CODE_AMAP_SERVICE_MAINTENANCE), AmapSearchStatus.Category.SERVICE, "异常")
    }

    @Test fun unknownCodesCannotBecomeSuccessfulSearches() {
        assertFailures(listOf(0, -1, 999, 10000, 10001, Int.MAX_VALUE), AmapSearchStatus.Category.UNKNOWN, "未成功")
    }

    private fun assertFailures(codes: List<Int>, category: AmapSearchStatus.Category, text: String) {
        codes.forEach { code ->
            assertFalse("Unexpected successful SDK code $code", AmapSearchStatus.isSuccess(code))
            assertEquals("Wrong category for SDK code $code", category, AmapSearchStatus.category(code))
            assertTrue("Unhelpful message for SDK code $code", AmapSearchStatus.explain(code).contains(text))
        }
    }
}
