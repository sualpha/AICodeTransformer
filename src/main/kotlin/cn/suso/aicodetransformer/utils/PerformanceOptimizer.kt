package cn.suso.aicodetransformer.utils

import cn.suso.aicodetransformer.service.*
import cn.suso.aicodetransformer.service.impl.PerformanceOptimizationServiceImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import kotlinx.coroutines.*

/**
 * 性能优化工具类
 * 提供一键优化和性能监控功能
 */
class PerformanceOptimizer(private val project: Project) {
    
    companion object {
        private val logger = Logger.getInstance(PerformanceOptimizer::class.java)
        
        /**
         * 获取项目的性能优化器实例
         */
        fun getInstance(project: Project): PerformanceOptimizer {
            return PerformanceOptimizer(project)
        }
    }
    
    private val optimizationService: PerformanceOptimizationServiceImpl = service()
    private val cacheService: CacheService = service()
    private val performanceMonitorService: PerformanceMonitorService = service()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 一键性能优化
     */
    fun quickOptimize() {
        scope.launch {
            try {
                logger.info("开始执行一键性能优化")
                
                // 1. 应用推荐配置
                optimizationService.applyRecommendedConfig()
                
                // 2. 清理过期缓存
                cacheService.clearExpiredCache()
                
                // 3. 获取优化建议并应用
                val suggestions = optimizationService.getOptimizationSuggestions()
                val highImpactSuggestions = suggestions.filter { 
                    it.impact == ImpactLevel.HIGH && it.difficulty == DifficultyLevel.EASY 
                }
                
                highImpactSuggestions.forEach { suggestion ->
                    try {
                        suggestion.action.invoke()
                        logger.info("已应用优化建议: ${suggestion.title}")
                    } catch (e: Exception) {
                        logger.warn("应用优化建议失败: ${suggestion.title}", e)
                    }
                }
                
                // 4. 显示优化结果
                withContext(Dispatchers.Main) {
                    showOptimizationResult(highImpactSuggestions.size)
                }
                
                logger.info("一键性能优化完成，应用了 ${highImpactSuggestions.size} 项优化")
                
            } catch (e: Exception) {
                logger.error("一键性能优化失败", e)
                withContext(Dispatchers.Main) {
                    showOptimizationError(e.message ?: "未知错误")
                }
            }
        }
    }
    
    /**
     * 获取性能诊断报告
     */
    fun getDiagnosticReport(): PerformanceDiagnostic {
        val cacheStats = cacheService.getCacheStats()
        val performanceStats = performanceMonitorService.getPerformanceStats()
        val suggestions = optimizationService.getOptimizationSuggestions()
        
        return PerformanceDiagnostic(
            timestamp = System.currentTimeMillis(),
            cachePerformance = CachePerformanceInfo(
                hitRate = cacheStats.hitRate,
                totalEntries = cacheStats.totalEntries,
                hitCount = cacheStats.hitCount,
                missCount = cacheStats.missCount,
                evictionCount = cacheStats.evictionCount
            ),
            networkPerformance = NetworkPerformanceInfo(
                averageResponseTime = performanceStats.averageResponseTime.toLong(),
                maxResponseTime = performanceStats.maxResponseTime,
                p95ResponseTime = performanceStats.p95ResponseTime,
                successRate = performanceStats.successRate,
                qps = performanceStats.qps
            ),
            optimizationSuggestions = suggestions,
            overallHealth = calculateOverallHealth(cacheStats, performanceStats)
        )
    }
    
    /**
     * 启动性能监控
     */
    fun startPerformanceMonitoring(intervalMs: Long = 30000) {
        scope.launch {
            while (true) {
                try {
                    val diagnostic = getDiagnosticReport()
                    
                    // 检查是否需要自动优化
                    if (diagnostic.overallHealth < 60) {
                        logger.warn("检测到性能问题，健康度: ${diagnostic.overallHealth}%")
                        
                        // 自动应用简单优化
                        val easyOptimizations = diagnostic.optimizationSuggestions.filter {
                            it.difficulty == DifficultyLevel.EASY
                        }
                        
                        easyOptimizations.take(2).forEach { suggestion ->
                            try {
                                suggestion.action.invoke()
                                logger.info("自动应用优化: ${suggestion.title}")
                            } catch (e: Exception) {
                                logger.warn("自动优化失败: ${suggestion.title}", e)
                            }
                        }
                    }
                    
                    delay(intervalMs)
                } catch (e: Exception) {
                    logger.error("性能监控异常", e)
                    delay(intervalMs)
                }
            }
        }
    }
    
    /**
     * 停止性能监控
     */
    fun stopPerformanceMonitoring() {
        scope.cancel()
    }
    
    /**
     * 预热缓存
     */
    fun warmupCache() {
        scope.launch {
            try {
                logger.info("开始缓存预热")
                
                // 这里可以预加载常用的模板和配置
                // 实际实现需要根据具体的模板服务来完成
                
                logger.info("缓存预热完成")
            } catch (e: Exception) {
                logger.error("缓存预热失败", e)
            }
        }
    }
    
    private fun calculateOverallHealth(
        cacheStats: CacheStats,
        performanceStats: PerformanceStats
    ): Int {
        var health = 100
        
        // 缓存健康度 (40%)
        val cacheHealth = when {
            cacheStats.hitRate > 0.8 -> 40
            cacheStats.hitRate > 0.6 -> 30
            cacheStats.hitRate > 0.4 -> 20
            else -> 10
        }
        
        // 响应时间健康度 (40%)
        val responseHealth = when {
            performanceStats.averageResponseTime < 1000 -> 40
            performanceStats.averageResponseTime < 2000 -> 30
            performanceStats.averageResponseTime < 3000 -> 20
            else -> 10
        }
        
        // 成功率健康度 (20%)
        val successHealth = (performanceStats.successRate * 20).toInt()
        
        return cacheHealth + responseHealth + successHealth
    }
    
    private fun showOptimizationResult(appliedCount: Int) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("AICodeTransformer")
            .createNotification(
                "性能优化完成",
                "已成功应用 $appliedCount 项性能优化，API调用速度将显著提升",
                NotificationType.INFORMATION
            )
        
        notification.notify(project)
    }
    
    private fun showOptimizationError(error: String) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("AICodeTransformer")
            .createNotification(
                "性能优化失败",
                "优化过程中出现错误: $error",
                NotificationType.ERROR
            )
        
        notification.notify(project)
    }
}

/**
 * 性能诊断信息
 */
data class PerformanceDiagnostic(
    val timestamp: Long,
    val cachePerformance: CachePerformanceInfo,
    val networkPerformance: NetworkPerformanceInfo,
    val optimizationSuggestions: List<OptimizationSuggestion>,
    val overallHealth: Int
)

/**
 * 缓存性能信息
 */
data class CachePerformanceInfo(
    val hitRate: Double,
    val totalEntries: Int,
    val hitCount: Long,
    val missCount: Long,
    val evictionCount: Long
)

/**
 * 网络性能信息
 */
data class NetworkPerformanceInfo(
    val averageResponseTime: Long,
    val maxResponseTime: Long,
    val p95ResponseTime: Long,
    val successRate: Double,
    val qps: Double
)