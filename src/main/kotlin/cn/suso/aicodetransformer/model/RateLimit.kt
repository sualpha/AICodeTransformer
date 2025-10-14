package cn.suso.aicodetransformer.model

import cn.suso.aicodetransformer.constants.WindowType

/**
 * 限流配置
 */
data class RateLimitConfig(
    /** 是否启用限流 */
    val enabled: Boolean = true,
    
    /** 每分钟最大请求数 */
    val requestsPerMinute: Int = 60,
    
    /** 每小时最大请求数 */
    val requestsPerHour: Int = 1000,
    
    /** 每天最大请求数 */
    val requestsPerDay: Int = 10000,
    
    /** 突发请求允许数量 */
    val burstSize: Int = 10,
    
    /** 限流窗口类型 */
    val windowType: WindowType = WindowType.SLIDING
)

/**
 * 限流统计信息
 */
data class RateLimitStats(
    /** 总请求数 */
    val totalRequests: Long,
    
    /** 被限流的请求数 */
    val limitedRequests: Long,
    
    /** 当前活跃的限流键数量 */
    val activeLimitKeys: Int,
    
    /** 限流命中率 */
    val limitHitRate: Double
) {
    val allowedRequests: Long
        get() = totalRequests - limitedRequests
        
    val allowedRate: Double
        get() = if (totalRequests == 0L) 1.0 else allowedRequests.toDouble() / totalRequests
}