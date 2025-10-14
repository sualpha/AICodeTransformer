package cn.suso.aicodetransformer.model

import cn.suso.aicodetransformer.constants.*

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