package app.coordinate.lab

import com.amap.api.services.core.AMapException

/** Android Search SDK codes are not the five-digit Web REST status codes. */
object AmapSearchStatus {
    enum class Category { SUCCESS, AUTHORIZATION, QUOTA, NETWORK, ROUTE, REQUEST, SERVICE, UNKNOWN }

    fun isSuccess(code: Int): Boolean = code == AMapException.CODE_AMAP_SUCCESS

    fun category(code: Int): Category = when (code) {
        AMapException.CODE_AMAP_SUCCESS -> Category.SUCCESS
        AMapException.CODE_AMAP_SIGNATURE_ERROR,
        AMapException.CODE_AMAP_INVALID_USER_KEY,
        AMapException.CODE_AMAP_INVALID_USER_IP,
        AMapException.CODE_AMAP_INVALID_USER_DOMAIN,
        AMapException.CODE_AMAP_INVALID_USER_SCODE,
        AMapException.CODE_AMAP_USERKEY_PLAT_NOMATCH,
        AMapException.CODE_AMAP_INSUFFICIENT_PRIVILEGES,
        AMapException.CODE_AMAP_USER_KEY_RECYCLED -> Category.AUTHORIZATION
        // Some service responses pass through the Web-layer CUQPS limit code unchanged.
        10021,
        AMapException.CODE_AMAP_DAILY_QUERY_OVER_LIMIT,
        AMapException.CODE_AMAP_ACCESS_TOO_FREQUENT,
        AMapException.CODE_AMAP_IP_QUERY_OVER_LIMIT -> Category.QUOTA
        AMapException.CODE_AMAP_ENGINE_CONNECT_TIMEOUT,
        AMapException.CODE_AMAP_ENGINE_RETURN_TIMEOUT,
        AMapException.CODE_AMAP_CLIENT_SOCKET_TIMEOUT_EXCEPTION,
        AMapException.CODE_AMAP_CLIENT_UNKNOWHOST_EXCEPTION,
        AMapException.CODE_AMAP_CLIENT_NETWORK_EXCEPTION,
        AMapException.CODE_AMAP_CLIENT_IO_EXCEPTION -> Category.NETWORK
        AMapException.CODE_AMAP_ROUTE_OUT_OF_SERVICE,
        AMapException.CODE_AMAP_ROUTE_NO_ROADS_NEARBY,
        AMapException.CODE_AMAP_ROUTE_FAIL,
        AMapException.CODE_AMAP_OVER_DIRECTION_RANGE -> Category.ROUTE
        AMapException.CODE_AMAP_SERVICE_INVALID_PARAMS,
        AMapException.CODE_AMAP_SERVICE_MISSING_REQUIRED_PARAMS,
        AMapException.CODE_AMAP_SERVICE_ILLEGAL_REQUEST,
        AMapException.CODE_AMAP_CLIENT_INVALID_PARAMETER,
        AMapException.CODE_AMAP_CLIENT_OVER_KEYWORD_LEN_MAX_COUNT_EXCEPTION -> Category.REQUEST
        AMapException.CODE_AMAP_SERVICE_NOT_AVAILBALE,
        AMapException.CODE_AMAP_NOT_SUPPORT_HTTPS,
        AMapException.CODE_AMAP_ENGINE_RESPONSE_ERROR,
        AMapException.CODE_AMAP_ENGINE_RESPONSE_DATA_ERROR,
        AMapException.CODE_AMAP_SERVICE_UNKNOWN_ERROR,
        AMapException.CODE_AMAP_CLIENT_ERROR_PROTOCOL,
        AMapException.CODE_AMAP_CLIENT_UNKNOWN_ERROR,
        AMapException.CODE_AMAP_CLIENT_NULLPOINT_EXCEPTION,
        AMapException.CODE_AMAP_SERVICE_MAINTENANCE -> Category.SERVICE
        else -> Category.UNKNOWN
    }

    fun explain(code: Int): String = when (category(code)) {
        Category.SUCCESS -> "高德请求成功（$code）"
        Category.AUTHORIZATION -> "高德授权失败（$code）：请检查 Android Key、包名、签名 SHA1 与服务权限"
        Category.QUOTA -> "高德服务配额或调用频率受限（$code），请稍后重试并检查开放平台控制台"
        Category.NETWORK -> "暂时无法连接高德或请求超时（$code），请检查网络后重试"
        Category.ROUTE -> when (code) {
            AMapException.CODE_AMAP_ROUTE_OUT_OF_SERVICE -> "路线规划超出服务范围（$code），请在支持区域内选择起终点"
            AMapException.CODE_AMAP_ROUTE_NO_ROADS_NEARBY -> "路线规划点附近没有可用道路（$code），请调整起终点或选择直线移动"
            AMapException.CODE_AMAP_OVER_DIRECTION_RANGE -> "路线距离超出服务限制（$code），请缩短起终点距离"
            else -> "路线计算失败（$code），请更换起终点或选择直线移动"
        }
        Category.REQUEST -> "高德请求参数无效（$code），请调整关键词、城市或起终点后重试"
        Category.SERVICE -> "高德服务返回或处理异常（$code），请稍后重试"
        Category.UNKNOWN -> "高德请求未成功（$code），请检查网络、Key 和服务权限"
    }

    fun failure(error: Throwable): Throwable =
        if (error is AMapException) IllegalStateException(explain(error.errorCode), error) else error
}
