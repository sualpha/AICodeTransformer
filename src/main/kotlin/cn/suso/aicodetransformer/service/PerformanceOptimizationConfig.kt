package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.constants.PerformanceConstants

/**
 * 性能优化配置
 * 包含各种性能优化相关的配置参数
 */
data class PerformanceOptimizationConfig(
    /** HTTP连接池配置 */
    val httpConfig: HttpOptimizationConfig = HttpOptimizationConfig(),
    
    /** 缓存优化配置 */
    val cacheConfig: CacheOptimizationConfig = CacheOptimizationConfig(),
    
    /** 请求优化配置 */
    val requestConfig: RequestOptimizationConfig = RequestOptimizationConfig()
)

/**
 * HTTP连接优化配置
 */
data class HttpOptimizationConfig(
    /** 最大连接数 */
    val maxConnections: Int = PerformanceConstants.HttpOptimization.MAX_CONNECTIONS_POOL,
    
    /** 每路由最大连接数 */
    val maxConnectionsPerRoute: Int = PerformanceConstants.HttpOptimization.MAX_CONNECTIONS_PER_ROUTE_POOL,
    
    /** 管道最大大小 */
    val pipelineMaxSize: Int = PerformanceConstants.HttpOptimization.PIPELINE_MAX_SIZE,
    
    /** 连接保持时间（毫秒） */
    val keepAliveTimeMs: Long = PerformanceConstants.HttpOptimization.KEEP_ALIVE_TIME_MS,
    
    /** 连接超时时间（毫秒） */
    val connectTimeoutMs: Long = PerformanceConstants.HttpOptimization.CONNECT_TIMEOUT_MS,
    
    /** 连接尝试次数 */
    val connectAttempts: Int = PerformanceConstants.HttpOptimization.CONNECT_ATTEMPTS,
    
    /** 最大重试次数 */
    val maxRetries: Int = PerformanceConstants.HttpOptimization.MAX_RETRIES,
    
    /** 重试延迟基数 */
    val retryDelayBase: Double = PerformanceConstants.HttpOptimization.RETRY_DELAY_BASE,
    
    /** 最大重试延迟（毫秒） */
    val maxRetryDelayMs: Long = PerformanceConstants.HttpOptimization.MAX_RETRY_DELAY_MS
)

/**
 * 缓存优化配置
 */
data class CacheOptimizationConfig(
    /** 是否启用智能TTL */
    val enableSmartTtl: Boolean = PerformanceConstants.CacheOptimization.ENABLE_SMART_TTL,
    
    /** 是否启用预测性缓存 */
    val enablePredictiveCache: Boolean = PerformanceConstants.CacheOptimization.ENABLE_PREDICTIVE_CACHE,
    
    /** 缓存预热常用模板数量 */
    val preloadTemplateCount: Int = PerformanceConstants.CacheOptimization.PRELOAD_TEMPLATE_COUNT,
    
    /** LRU清理比例（0.0-1.0） */
    val lruCleanupRatio: Double = PerformanceConstants.CacheOptimization.LRU_CLEANUP_RATIO
)

/**
 * 请求优化配置
 */
data class RequestOptimizationConfig(
    /** 是否启用请求去重 */
    val enableRequestDeduplication: Boolean = PerformanceConstants.RequestOptimization.ENABLE_REQUEST_DEDUPLICATION,
    
    /** 是否启用批量请求合并 */
    val enableBatchMerging: Boolean = PerformanceConstants.RequestOptimization.ENABLE_BATCH_MERGING,
    
    /** 批量合并最大等待时间（毫秒） */
    val batchMergeWaitTimeMs: Long = PerformanceConstants.RequestOptimization.BATCH_MERGE_WAIT_TIME_MS,
    
    /** 批量合并最大请求数 */
    val batchMergeMaxRequests: Int = PerformanceConstants.RequestOptimization.BATCH_MERGE_MAX_REQUESTS,
    
    /** 是否启用流式响应 */
    val enableStreamResponse: Boolean = PerformanceConstants.RequestOptimization.ENABLE_STREAM_RESPONSE
)

/**
 * 性能优化服务接口
 */
interface PerformanceOptimizationService {
    
    /**
     * 获取当前优化配置
     */
    fun getConfig(): PerformanceOptimizationConfig
    
    /**
     * 更新优化配置
     */
    fun updateConfig(config: PerformanceOptimizationConfig)
    
    /**
     * 获取性能优化建议
     */
    fun getOptimizationSuggestions(): List<OptimizationSuggestion>
    
    /**
     * 应用推荐的优化配置
     */
    fun applyRecommendedConfig()
}

/**
 * 优化建议
 */
data class OptimizationSuggestion(
    val type: OptimizationType,
    val title: String,
    val description: String,
    val impact: ImpactLevel,
    val difficulty: DifficultyLevel,
    val action: () -> Unit
)

/**
 * 优化类型
 */
enum class OptimizationType {
    HTTP_CONNECTION,
    CACHE_STRATEGY,
    REQUEST_BATCHING,
    MEMORY_MANAGEMENT,
    NETWORK_OPTIMIZATION;
    
    companion object {
        fun fromPerformanceConstant(type: PerformanceConstants.OptimizationType): OptimizationType {
            return when (type) {
                PerformanceConstants.OptimizationType.HTTP_CONNECTION -> HTTP_CONNECTION
                PerformanceConstants.OptimizationType.CACHE_STRATEGY -> CACHE_STRATEGY
                PerformanceConstants.OptimizationType.REQUEST_BATCHING -> REQUEST_BATCHING
                PerformanceConstants.OptimizationType.MEMORY_OPTIMIZATION -> MEMORY_MANAGEMENT
                PerformanceConstants.OptimizationType.ASYNC_PROCESSING -> NETWORK_OPTIMIZATION
                PerformanceConstants.OptimizationType.THREAD_POOL_TUNING -> NETWORK_OPTIMIZATION
            }
        }
    }
}

/**
 * 影响级别
 */
enum class ImpactLevel {
    LOW,     // 5-15% 性能提升
    MEDIUM,  // 15-30% 性能提升
    HIGH;    // 30%+ 性能提升
    
    companion object {
        fun fromPerformanceConstant(level: PerformanceConstants.ImpactLevel): ImpactLevel {
            return when (level) {
                PerformanceConstants.ImpactLevel.LOW -> LOW
                PerformanceConstants.ImpactLevel.MEDIUM -> MEDIUM
                PerformanceConstants.ImpactLevel.HIGH -> HIGH
                PerformanceConstants.ImpactLevel.CRITICAL -> HIGH // 映射到HIGH
            }
        }
    }
}

/**
 * 实施难度
 */
enum class DifficultyLevel {
    EASY,    // 配置调整
    MEDIUM,  // 代码修改
    HARD;    // 架构调整
    
    companion object {
        fun fromPerformanceConstant(level: PerformanceConstants.DifficultyLevel): DifficultyLevel {
            return when (level) {
                PerformanceConstants.DifficultyLevel.EASY -> EASY
                PerformanceConstants.DifficultyLevel.MEDIUM -> MEDIUM
                PerformanceConstants.DifficultyLevel.HARD -> HARD
                PerformanceConstants.DifficultyLevel.EXPERT -> HARD // 映射到HARD
            }
        }
    }
}