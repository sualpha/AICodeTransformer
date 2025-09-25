package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.ModelConfiguration

/**
 * 性能监控服务接口
 * 用于监控和统计API调用性能
 */
interface PerformanceMonitorService {
    
    /**
     * 记录API调用开始
     * @param requestId 请求ID
     * @param config 模型配置
     * @param prompt 提示内容
     * @return 性能跟踪器
     */
    fun startTracking(requestId: String, config: ModelConfiguration, prompt: String): PerformanceTracker
    
    /**
     * 记录API调用完成
     * @param tracker 性能跟踪器
     * @param result 执行结果
     */
    fun endTracking(tracker: PerformanceTracker, result: ExecutionResult)
    
    /**
     * 获取性能统计信息
     * @param modelConfigId 模型配置ID，为null时返回全部统计
     * @return 性能统计
     */
    fun getPerformanceStats(modelConfigId: String? = null): PerformanceStats
    
    /**
     * 获取实时性能指标
     * @return 实时性能指标
     */
    fun getRealTimeMetrics(): RealTimeMetrics
    
    /**
     * 清除性能数据
     * @param olderThanMs 清除指定时间之前的数据（毫秒）
     */
    fun clearOldData(olderThanMs: Long = 86400000) // 默认清除1天前的数据
    
    /**
     * 设置性能监控配置
     * @param config 监控配置
     */
    fun setMonitorConfig(config: MonitorConfig)
}

/**
 * 性能跟踪器
 */
interface PerformanceTracker {
    val requestId: String
    val startTime: Long
    
    /**
     * 记录缓存命中
     */
    fun recordCacheHit()
    
    /**
     * 记录限流
     */
    fun recordRateLimit()
    
    /**
     * 记录网络延迟
     * @param latencyMs 网络延迟（毫秒）
     */
    fun recordNetworkLatency(latencyMs: Long)
    
    /**
     * 添加自定义指标
     * @param key 指标名称
     * @param value 指标值
     */
    fun addMetric(key: String, value: Any)
}

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
    
    /** 每秒请求数（QPS） */
    val qps: Double,
    
    /** 统计时间范围 */
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
    
    /** 系统负载状态 */
    val loadStatus: LoadStatus
)

/**
 * 系统负载状态
 */
enum class LoadStatus {
    /** 低负载 */
    LOW,
    
    /** 正常负载 */
    NORMAL,
    
    /** 高负载 */
    HIGH,
    
    /** 过载 */
    OVERLOAD
}

/**
 * 监控配置
 */
data class MonitorConfig(
    /** 是否启用监控 */
    val enabled: Boolean = true,
    
    /** 数据保留时间（毫秒） */
    val dataRetentionMs: Long = 86400000, // 1天
    
    /** 性能数据采样率 (0.0-1.0) */
    val samplingRate: Double = 1.0,
    
    /** 是否记录详细指标 */
    val detailedMetrics: Boolean = true,
    
    /** 性能警告阈值 */
    val warningThresholds: WarningThresholds = WarningThresholds()
)

/**
 * 性能警告阈值
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
    
    /** QPS严重阈值 */
    val qpsCritical: Double = 200.0
)