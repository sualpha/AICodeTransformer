package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.service.*
import cn.suso.aicodetransformer.model.CacheStats
import cn.suso.aicodetransformer.model.CacheConfig
import cn.suso.aicodetransformer.model.PerformanceOptimizationConfig
import cn.suso.aicodetransformer.model.HttpOptimizationConfig
import cn.suso.aicodetransformer.model.CacheOptimizationConfig
import cn.suso.aicodetransformer.model.RequestOptimizationConfig
import cn.suso.aicodetransformer.model.OptimizationSuggestion
import cn.suso.aicodetransformer.model.PerformanceStats
import cn.suso.aicodetransformer.model.PerformanceReport
import cn.suso.aicodetransformer.constants.OptimizationType
import cn.suso.aicodetransformer.constants.ImpactLevel
import cn.suso.aicodetransformer.constants.DifficultyLevel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger

/**
 * 性能优化服务实现
 */
@Service
class PerformanceOptimizationServiceImpl : PerformanceOptimizationService {
    
    companion object {
        private val logger = Logger.getInstance(PerformanceOptimizationServiceImpl::class.java)
    }
    
    private var currentConfig = PerformanceOptimizationConfig()
    private val cacheService: CacheService = service()
    private val performanceMonitorService: PerformanceMonitorService = service()
    
    override fun getConfig(): PerformanceOptimizationConfig {
        return currentConfig
    }
    
    override fun updateConfig(config: PerformanceOptimizationConfig) {
        currentConfig = config
        logger.info("性能优化配置已更新: $config")
        
        // 应用缓存配置
        applyCacheConfig(config.cacheConfig)
    }
    
    override fun getOptimizationSuggestions(): List<OptimizationSuggestion> {
        val suggestions = mutableListOf<OptimizationSuggestion>()
        
        // 分析当前性能状态
        val cacheStats = cacheService.getCacheStats()
        val performanceStats = performanceMonitorService.getPerformanceStats()
        
        // 缓存命中率优化建议
        if (cacheStats.hitRate < 0.6) {
            suggestions.add(
                OptimizationSuggestion(
                    type = OptimizationType.CACHE_STRATEGY,
                    title = "提升缓存命中率",
                    description = "当前缓存命中率为 ${String.format("%.1f", cacheStats.hitRate * 100)}%，建议增加缓存大小和TTL时间",
                    impact = ImpactLevel.HIGH,
                    difficulty = DifficultyLevel.EASY
                ) {
                    optimizeCacheStrategy()
                }
            )
        }
        
        // 响应时间优化建议
        if (performanceStats.averageResponseTime > 3000) {
            suggestions.add(
                OptimizationSuggestion(
                    type = OptimizationType.HTTP_CONNECTION,
                    title = "优化HTTP连接池",
                    description = "平均响应时间为 ${performanceStats.averageResponseTime}ms，建议优化连接池配置",
                    impact = ImpactLevel.MEDIUM,
                    difficulty = DifficultyLevel.EASY
                ) {
                    optimizeHttpConnection()
                }
            )
        }
        
        // 请求去重建议
        if (!currentConfig.requestConfig.enableRequestDeduplication) {
            suggestions.add(
                OptimizationSuggestion(
                    type = OptimizationType.REQUEST_BATCHING,
                    title = "启用请求去重",
                    description = "启用请求去重可以避免重复的API调用，显著提升性能",
                    impact = ImpactLevel.MEDIUM,
                    difficulty = DifficultyLevel.EASY
                ) {
                    enableRequestDeduplication()
                }
            )
        }
        
        // 智能TTL建议
        if (!currentConfig.cacheConfig.enableSmartTtl) {
            suggestions.add(
                OptimizationSuggestion(
                    type = OptimizationType.CACHE_STRATEGY,
                    title = "启用智能TTL",
                    description = "根据请求类型自动调整缓存过期时间，提高缓存效率",
                    impact = ImpactLevel.MEDIUM,
                    difficulty = DifficultyLevel.EASY
                ) {
                    enableSmartTtl()
                }
            )
        }
        
        // 内存使用优化建议
        if (cacheStats.totalEntries > 4000) {
            suggestions.add(
                OptimizationSuggestion(
                    type = OptimizationType.MEMORY_OPTIMIZATION,
                    title = "优化内存使用",
                    description = "当前缓存条目数为 ${cacheStats.totalEntries}，建议启用更积极的清理策略",
                    impact = ImpactLevel.LOW,
                    difficulty = DifficultyLevel.EASY
                ) {
                    optimizeMemoryUsage()
                }
            )
        }
        
        return suggestions
    }
    
    override fun applyRecommendedConfig() {
        val recommendedConfig = PerformanceOptimizationConfig(
            httpConfig = HttpOptimizationConfig(
                maxConnections = 200,
                maxConnectionsPerRoute = 50,
                keepAliveTimeMs = 30000,
                connectTimeoutMs = 3000,
                maxRetries = 3
            ),
            cacheConfig = CacheOptimizationConfig(
                enableSmartTtl = true,
                enablePredictiveCache = false,
                lruCleanupRatio = 0.1
            ),
            requestConfig = RequestOptimizationConfig(
                enableRequestDeduplication = true,
                enableBatchMerging = false,
                enableStreamResponse = false
            )
        )
        
        updateConfig(recommendedConfig)
        logger.info("已应用推荐的性能优化配置")
    }
    
    private fun applyCacheConfig(config: CacheOptimizationConfig) {
        val cacheConfig = CacheConfig(
            maxSize = if (config.enablePredictiveCache) 8000 else 5000,
            defaultTtlSeconds = 900,
            enabled = true,
            cleanupIntervalSeconds = 300
        )
        
        cacheService.setCacheConfig(cacheConfig)
    }
    
    private fun optimizeCacheStrategy() {
        val optimizedConfig = currentConfig.copy(
            cacheConfig = currentConfig.cacheConfig.copy(
                enableSmartTtl = true,
                lruCleanupRatio = 0.15
            )
        )
        updateConfig(optimizedConfig)
    }
    
    private fun optimizeHttpConnection() {
        val optimizedConfig = currentConfig.copy(
            httpConfig = currentConfig.httpConfig.copy(
                maxConnections = 300,
                maxConnectionsPerRoute = 75,
                keepAliveTimeMs = 45000
            )
        )
        updateConfig(optimizedConfig)
    }
    
    private fun enableRequestDeduplication() {
        val optimizedConfig = currentConfig.copy(
            requestConfig = currentConfig.requestConfig.copy(
                enableRequestDeduplication = true
            )
        )
        updateConfig(optimizedConfig)
    }
    
    private fun enableSmartTtl() {
        val optimizedConfig = currentConfig.copy(
            cacheConfig = currentConfig.cacheConfig.copy(
                enableSmartTtl = true
            )
        )
        updateConfig(optimizedConfig)
    }
    
    private fun optimizeMemoryUsage() {
        val optimizedConfig = currentConfig.copy(
            cacheConfig = currentConfig.cacheConfig.copy(
                lruCleanupRatio = 0.2
            )
        )
        updateConfig(optimizedConfig)
        
        // 立即清理过期缓存
        cacheService.clearExpiredCache()
    }
    

    
    private fun calculatePerformanceScore(
        cacheStats: CacheStats,
        performanceStats: PerformanceStats
    ): Int {
        // 缓存命中率评分 (40%权重)
        val cacheScore = (cacheStats.hitRate * 40).toInt()
        
        // 响应时间评分 (40%权重)
        val responseScore = when {
            performanceStats.averageResponseTime < 1000 -> 40
            performanceStats.averageResponseTime < 2000 -> 30
            performanceStats.averageResponseTime < 3000 -> 20
            else -> 10
        }
        
        // 配置优化评分 (20%权重)
        val configScore = when {
            currentConfig.cacheConfig.enableSmartTtl && 
            currentConfig.requestConfig.enableRequestDeduplication -> 20
            currentConfig.cacheConfig.enableSmartTtl || 
            currentConfig.requestConfig.enableRequestDeduplication -> 15
            else -> 10
        }
        
        return cacheScore + responseScore + configScore
    }
}