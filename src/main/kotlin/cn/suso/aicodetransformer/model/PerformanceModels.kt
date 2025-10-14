package cn.suso.aicodetransformer.model

import cn.suso.aicodetransformer.constants.PerformanceConstants.LoadStatus

/**
 * 性能统计信息
 */
data class PerformanceStats(
    /** 总请求数 */
    val totalRequests: Long,
    
    /** 成功请求数 */
    val successfulRequests: Long,
    
    /** 失败请求数 */
    val failedRequests: Long,
    
    /** 平均响应时间（毫秒） */
    val averageResponseTime: Double,
    
    /** 最小响应时间（毫秒） */
    val minResponseTime: Long,
    
    /** 最大响应时间（毫秒） */
    val maxResponseTime: Long,
    
    /** 95百分位响应时间（毫秒） */
    val p95ResponseTime: Long,
    
    /** 99百分位响应时间（毫秒） */
    val p99ResponseTime: Long,
    
    /** 缓存命中率 */
    val cacheHitRate: Double,
    
    /** 限流命中率 */
    val rateLimitHitRate: Double,
    
    /** 平均网络延迟（毫秒） */
    val averageNetworkLatency: Double,
    
    /** 每秒查询数 */
    val qps: Double,
    
    /** 时间范围（毫秒） */
    val timeRangeMs: Long
) {
    val successRate: Double
        get() = if (totalRequests > 0) successfulRequests.toDouble() / totalRequests else 0.0
    
    val errorRate: Double
        get() = if (totalRequests > 0) failedRequests.toDouble() / totalRequests else 0.0
}

/**
 * 实时性能指标
 */
data class RealTimeMetrics(
    /** 当前活跃请求数 */
    val activeRequests: Int,
    
    /** 最近1分钟QPS */
    val qpsLastMinute: Double,
    
    /** 最近5分钟平均响应时间 */
    val avgResponseTimeLast5Min: Double,
    
    /** 最近1小时成功率 */
    val successRateLastHour: Double,
    
    /** 当前缓存大小 */
    val currentCacheSize: Int,
    
    /** 负载状态 */
    val loadStatus: LoadStatus
)

/**
 * 监控配置
 */
data class MonitorConfig(
    /** 是否启用监控 */
    val enabled: Boolean = true,
    
    /** 数据保留时间（毫秒） */
    val dataRetentionMs: Long = 86400000, // 1天
    
    /** 采样率 */
    val samplingRate: Double = 1.0,
    
    /** 是否启用详细指标 */
    val detailedMetrics: Boolean = true,
    
    /** 警告阈值 */
    val warningThresholds: WarningThresholds = WarningThresholds()
)

/**
 * 警告阈值配置
 */
data class WarningThresholds(
    /** 响应时间警告阈值（毫秒） */
    val responseTimeWarning: Long = 5000,
    
    /** 响应时间严重阈值（毫秒） */
    val responseTimeCritical: Long = 10000,
    
    /** 错误率警告阈值 */
    val errorRateWarning: Double = 0.05, // 5%
    
    /** 错误率严重阈值 */
    val errorRateCritical: Double = 0.10, // 10%
    
    /** QPS警告阈值 */
    val qpsWarning: Double = 100.0,
    
    /**
 * QPS严重阈值 */
    val qpsCritical: Double = 200.0
)

/**
 * 性能记录数据类
 */
data class PerformanceRecord(
    val requestId: String,
    val modelConfigId: String,
    val modelName: String,
    val startTime: Long,
    val endTime: Long,
    val responseTime: Long,
    val success: Boolean,
    val errorType: String?,
    val promptLength: Int,
    val responseLength: Int,
    val tokensUsed: Int,
    val cacheHit: Boolean,
    val rateLimited: Boolean,
    val networkLatency: Long?,
    val customMetrics: Map<String, Any>
)

/**
 * 性能报告
 */
data class PerformanceReport(
    val cacheHitRate: Double,
    val averageResponseTime: Long,
    val totalCacheEntries: Int,
    val optimizationSuggestions: List<OptimizationSuggestion>,
    val overallScore: Int
) {
    val performanceLevel: String
        get() = when {
            overallScore >= 90 -> "优秀"
            overallScore >= 75 -> "良好"
            overallScore >= 60 -> "一般"
            else -> "需要优化"
        }
}