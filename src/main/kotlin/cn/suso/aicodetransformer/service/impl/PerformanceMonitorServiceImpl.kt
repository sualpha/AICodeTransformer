package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.PerformanceStats
import cn.suso.aicodetransformer.model.RealTimeMetrics
import cn.suso.aicodetransformer.model.MonitorConfig
import cn.suso.aicodetransformer.model.PerformanceRecord
import cn.suso.aicodetransformer.constants.PerformanceConstants.LoadStatus
import cn.suso.aicodetransformer.service.*
import com.intellij.openapi.Disposable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 性能监控服务实现
 */
class PerformanceMonitorServiceImpl : PerformanceMonitorService, Disposable {
    
    private val logger = LoggerFactory.getLogger(PerformanceMonitorServiceImpl::class.java)
    
    // 配置
    private var config = MonitorConfig()
    
    // 活跃跟踪器
    private val activeTrackers = ConcurrentHashMap<String, PerformanceTrackerImpl>()
    
    // 性能记录
    private val performanceRecords = mutableListOf<PerformanceRecord>()
    private val recordsLock = ReentrantReadWriteLock()
    
    // 统计计数器
    private val totalRequests = AtomicLong(0)
    private val successfulRequests = AtomicLong(0)
    private val failedRequests = AtomicLong(0)
    
    override fun startTracking(requestId: String, config: ModelConfiguration, prompt: String): PerformanceTracker {
        if (!this.config.enabled) {
            return NoOpPerformanceTracker(requestId)
        }
        
        val tracker = PerformanceTrackerImpl(
            requestId = requestId,
            modelConfigId = config.id,
            modelName = config.name,
            promptLength = prompt.length,
            startTime = System.currentTimeMillis()
        )
        
        activeTrackers[requestId] = tracker
        totalRequests.incrementAndGet()
        
        logger.debug("开始性能跟踪: requestId=$requestId, model=${config.name}")
        return tracker
    }
    
    override fun endTracking(tracker: PerformanceTracker, result: ExecutionResult) {
        if (!config.enabled || tracker !is PerformanceTrackerImpl) {
            return
        }
        
        val endTime = System.currentTimeMillis()
        val responseTime = endTime - tracker.startTime
        
        // 更新计数器
        if (result.success) {
            successfulRequests.incrementAndGet()
        } else {
            failedRequests.incrementAndGet()
        }
        
        // 创建性能记录
        val record = PerformanceRecord(
            requestId = tracker.requestId,
            modelConfigId = tracker.modelConfigId,
            modelName = tracker.modelName,
            startTime = tracker.startTime,
            endTime = endTime,
            responseTime = responseTime,
            success = result.success,
            errorType = result.errorType?.name,
            promptLength = tracker.promptLength,
            responseLength = result.content?.length ?: 0,
            tokensUsed = result.tokensUsed ?: 0,
            cacheHit = tracker.cacheHit,
            rateLimited = tracker.rateLimited,
            networkLatency = tracker.networkLatency,
            customMetrics = tracker.customMetrics.toMap()
        )
        
        // 存储记录
        recordsLock.write {
            performanceRecords.add(record)
            
            // 清理旧数据
            val cutoffTime = System.currentTimeMillis() - config.dataRetentionMs
            performanceRecords.removeAll { it.startTime < cutoffTime }
        }
        
        // 移除活跃跟踪器
        activeTrackers.remove(tracker.requestId)
        
        // 检查性能警告
        checkPerformanceWarnings(record)
        
        logger.debug("结束性能跟踪: requestId=${tracker.requestId}, responseTime=${responseTime}ms")
    }
    
    override fun getPerformanceStats(modelConfigId: String?): PerformanceStats {
        recordsLock.read {
            val filteredRecords = if (modelConfigId != null) {
                performanceRecords.filter { it.modelConfigId == modelConfigId }
            } else {
                performanceRecords
            }
            
            if (filteredRecords.isEmpty()) {
                return PerformanceStats(
                    totalRequests = 0,
                    successfulRequests = 0,
                    failedRequests = 0,
                    averageResponseTime = 0.0,
                    minResponseTime = 0,
                    maxResponseTime = 0,
                    p95ResponseTime = 0,
                    p99ResponseTime = 0,
                    cacheHitRate = 0.0,
                    rateLimitHitRate = 0.0,
                    averageNetworkLatency = 0.0,
                    qps = 0.0,
                    timeRangeMs = 0
                )
            }
            
            val totalCount = filteredRecords.size.toLong()
            val successCount = filteredRecords.count { it.success }.toLong()
            val failedCount = totalCount - successCount
            
            val responseTimes = filteredRecords.map { it.responseTime }.sorted()
            val avgResponseTime = responseTimes.average()
            val minResponseTime = responseTimes.minOrNull() ?: 0L
            val maxResponseTime = responseTimes.maxOrNull() ?: 0L
            val p95ResponseTime = if (responseTimes.isNotEmpty()) {
                responseTimes[(responseTimes.size * 0.95).toInt().coerceAtMost(responseTimes.size - 1)]
            } else 0L
            val p99ResponseTime = if (responseTimes.isNotEmpty()) {
                responseTimes[(responseTimes.size * 0.99).toInt().coerceAtMost(responseTimes.size - 1)]
            } else 0L
            
            val cacheHitCount = filteredRecords.count { it.cacheHit }
            val cacheHitRate = if (totalCount > 0) cacheHitCount.toDouble() / totalCount else 0.0
            
            val rateLimitCount = filteredRecords.count { it.rateLimited }
            val rateLimitHitRate = if (totalCount > 0) rateLimitCount.toDouble() / totalCount else 0.0
            
            val networkLatencies = filteredRecords.mapNotNull { it.networkLatency }
            val avgNetworkLatency = if (networkLatencies.isNotEmpty()) networkLatencies.average() else 0.0
            
            val timeRange = if (filteredRecords.isNotEmpty()) {
                maxResponseTime - minResponseTime
            } else 0L
            
            val qps = if (timeRange > 0) {
                totalCount.toDouble() / (timeRange / 1000.0)
            } else 0.0
            
            return PerformanceStats(
                totalRequests = totalCount,
                successfulRequests = successCount,
                failedRequests = failedCount,
                averageResponseTime = avgResponseTime,
                minResponseTime = minResponseTime,
                maxResponseTime = maxResponseTime,
                p95ResponseTime = p95ResponseTime,
                p99ResponseTime = p99ResponseTime,
                cacheHitRate = cacheHitRate,
                rateLimitHitRate = rateLimitHitRate,
                averageNetworkLatency = avgNetworkLatency,
                qps = qps,
                timeRangeMs = timeRange
            )
        }
    }
    
    override fun getRealTimeMetrics(): RealTimeMetrics {
        val currentTime = System.currentTimeMillis()
        
        recordsLock.read {
            // 最近1分钟的记录
            val lastMinuteRecords = performanceRecords.filter {
                currentTime - it.endTime <= 60000
            }
            
            // 最近5分钟的记录
            val last5MinRecords = performanceRecords.filter {
                currentTime - it.endTime <= 300000
            }
            
            // 最近1小时的记录
            val lastHourRecords = performanceRecords.filter {
                currentTime - it.endTime <= 3600000
            }
            
            val qpsLastMinute = if (lastMinuteRecords.isNotEmpty()) {
                lastMinuteRecords.size.toDouble() / 60.0
            } else 0.0
            
            val avgResponseTimeLast5Min = if (last5MinRecords.isNotEmpty()) {
                last5MinRecords.map { it.responseTime }.average()
            } else 0.0
            
            val successRateLastHour = if (lastHourRecords.isNotEmpty()) {
                lastHourRecords.count { it.success }.toDouble() / lastHourRecords.size
            } else 0.0
            
            // 计算负载状态
            val loadStatus = calculateLoadStatus(qpsLastMinute, avgResponseTimeLast5Min, successRateLastHour)
            
            return RealTimeMetrics(
                activeRequests = activeTrackers.size,
                qpsLastMinute = qpsLastMinute,
                avgResponseTimeLast5Min = avgResponseTimeLast5Min,
                successRateLastHour = successRateLastHour,
                currentCacheSize = 0, // 需要从缓存服务获取
                loadStatus = loadStatus
            )
        }
    }
    
    override fun clearOldData(olderThanMs: Long) {
        val cutoffTime = System.currentTimeMillis() - olderThanMs
        
        recordsLock.write {
            val removedCount = performanceRecords.size
            performanceRecords.removeAll { it.startTime < cutoffTime }
            val remainingCount = performanceRecords.size
            
            logger.info("清理性能数据: 删除${removedCount - remainingCount}条记录，保留${remainingCount}条记录")
        }
    }
    
    override fun setMonitorConfig(config: MonitorConfig) {
        this.config = config
        logger.info("更新性能监控配置: enabled=${config.enabled}, retention=${config.dataRetentionMs}ms")
    }
    
    override fun dispose() {
        try {
            // 清理所有活动跟踪器
            activeTrackers.clear()
            
            // 清理性能记录
            recordsLock.write {
                performanceRecords.clear()
            }
            
            logger.info("PerformanceMonitorService资源清理完成")
        } catch (e: Exception) {
            logger.error("PerformanceMonitorService资源清理失败", e)
        }
    }
    
    private fun checkPerformanceWarnings(record: PerformanceRecord) {
        val thresholds = config.warningThresholds
        
        // 检查响应时间
        when {
            record.responseTime >= thresholds.responseTimeCritical -> {
                logger.warn("严重性能警告: 响应时间过长 ${record.responseTime}ms (阈值: ${thresholds.responseTimeCritical}ms), requestId=${record.requestId}")
            }
            record.responseTime >= thresholds.responseTimeWarning -> {
                logger.warn("性能警告: 响应时间较长 ${record.responseTime}ms (阈值: ${thresholds.responseTimeWarning}ms), requestId=${record.requestId}")
            }
        }
    }
    
    private fun calculateLoadStatus(qps: Double, avgResponseTime: Double, successRate: Double): LoadStatus {
        val thresholds = config.warningThresholds
        
        return when {
            qps >= thresholds.qpsCritical || avgResponseTime >= thresholds.responseTimeCritical || successRate <= (1.0 - thresholds.errorRateCritical) -> {
                LoadStatus.OVERLOADED
            }
            qps >= thresholds.qpsWarning || avgResponseTime >= thresholds.responseTimeWarning || successRate <= (1.0 - thresholds.errorRateWarning) -> {
                LoadStatus.BUSY
            }
            qps > 0 && avgResponseTime > 0 -> {
                LoadStatus.NORMAL
            }
            else -> {
                LoadStatus.IDLE
            }
        }
    }
}

/**
 * 性能跟踪器实现
 */
private class PerformanceTrackerImpl(
    override val requestId: String,
    val modelConfigId: String,
    val modelName: String,
    val promptLength: Int,
    override val startTime: Long
) : PerformanceTracker {
    
    var cacheHit: Boolean = false
        private set
    
    var rateLimited: Boolean = false
        private set
    
    var networkLatency: Long? = null
        private set
    
    val customMetrics = mutableMapOf<String, Any>()
    
    override fun recordCacheHit() {
        cacheHit = true
    }
    
    override fun recordRateLimit() {
        rateLimited = true
    }
    
    override fun recordNetworkLatency(latencyMs: Long) {
        networkLatency = latencyMs
    }
    
    override fun addMetric(key: String, value: Any) {
        customMetrics[key] = value
    }
}

/**
 * 无操作性能跟踪器（当监控被禁用时使用）
 */
private class NoOpPerformanceTracker(override val requestId: String) : PerformanceTracker {
    override val startTime: Long = System.currentTimeMillis()
    
    override fun recordCacheHit() {}
    override fun recordRateLimit() {}
    override fun recordNetworkLatency(latencyMs: Long) {}
    override fun addMetric(key: String, value: Any) {}
}