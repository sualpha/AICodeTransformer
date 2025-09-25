package cn.suso.aicodetransformer.constants

/**
 * 性能优化相关常量定义
 * 统一管理所有性能优化配置的默认参数和常量值
 */
object PerformanceConstants {
    
    /**
     * HTTP优化配置默认值
     */
    object HttpOptimization {
        const val MAX_CONNECTIONS = 50
        const val MAX_CONNECTIONS_PER_ROUTE = 20
        const val CONNECTION_TIMEOUT_MS = 5000
        const val SOCKET_TIMEOUT_MS = 30000
        const val CONNECTION_REQUEST_TIMEOUT_MS = 3000
        const val KEEP_ALIVE_DURATION_MS = 60000L
        const val VALIDATE_AFTER_INACTIVITY_MS = 2000
        const val ENABLE_COMPRESSION = true
        const val ENABLE_RETRY = true
        const val MAX_RETRY_COUNT = 3
        
        // PerformanceOptimizationConfig 需要的常量
        const val MAX_CONNECTIONS_POOL = 200
        const val MAX_CONNECTIONS_PER_ROUTE_POOL = 50
        const val PIPELINE_MAX_SIZE = 30
        const val KEEP_ALIVE_TIME_MS = 30000L
        const val CONNECT_TIMEOUT_MS = 3000L
        const val CONNECT_ATTEMPTS = 3
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_BASE = 1.5
        const val MAX_RETRY_DELAY_MS = 10000L
    }
    
    /**
     * 缓存优化配置默认值
     */
    object CacheOptimization {
        const val ENABLE_RESPONSE_CACHE = true
        const val MAX_CACHE_SIZE = 100
        const val CACHE_EXPIRE_MINUTES = 30L
        const val ENABLE_TEMPLATE_CACHE = true
        const val TEMPLATE_CACHE_SIZE = 50
        const val TEMPLATE_CACHE_EXPIRE_MINUTES = 60L
        const val ENABLE_CONFIG_CACHE = true
        const val CONFIG_CACHE_SIZE = 20
        const val CONFIG_CACHE_EXPIRE_MINUTES = 120L
        
        // PerformanceOptimizationConfig 需要的常量
        const val ENABLE_SMART_TTL = true
        const val ENABLE_PREDICTIVE_CACHE = false
        const val PRELOAD_TEMPLATE_COUNT = 10
        const val LRU_CLEANUP_RATIO = 0.1
    }
    
    /**
     * 请求优化配置默认值
     */
    object RequestOptimization {
        const val ENABLE_REQUEST_BATCHING = false
        const val BATCH_SIZE = 5
        const val BATCH_TIMEOUT_MS = 1000L
        const val ENABLE_REQUEST_DEDUPLICATION = true
        const val DEDUPLICATION_WINDOW_MS = 5000L
        const val ENABLE_ASYNC_PROCESSING = true
        const val ASYNC_THREAD_POOL_SIZE = 10
        const val ASYNC_QUEUE_CAPACITY = 100
        
        // PerformanceOptimizationConfig 需要的常量
        const val ENABLE_BATCH_MERGING = false
        const val BATCH_MERGE_WAIT_TIME_MS = 100L
        const val BATCH_MERGE_MAX_REQUESTS = 5
        const val ENABLE_STREAM_RESPONSE = false
    }
    
    /**
     * 性能监控配置默认值
     */
    object MonitorConfig {
        const val ENABLE_PERFORMANCE_MONITORING = true
        const val METRICS_COLLECTION_INTERVAL_MS = 5000L
        const val MEMORY_THRESHOLD_PERCENTAGE = 80.0
        const val CPU_THRESHOLD_PERCENTAGE = 85.0
        const val RESPONSE_TIME_THRESHOLD_MS = 10000L
        const val ERROR_RATE_THRESHOLD_PERCENTAGE = 5.0
        const val ENABLE_ALERTS = true
        const val ALERT_COOLDOWN_MINUTES = 10L
    }
    
    /**
     * 优化类型枚举
     */
    enum class OptimizationType(val typeName: String, val description: String) {
        HTTP_CONNECTION("HTTP连接优化", "优化HTTP连接池配置以提高网络请求性能"),
        CACHE_STRATEGY("缓存策略优化", "优化缓存配置以减少重复请求和提高响应速度"),
        REQUEST_BATCHING("请求批处理", "启用请求批处理以减少网络开销"),
        ASYNC_PROCESSING("异步处理", "启用异步处理以提高并发性能"),
        MEMORY_OPTIMIZATION("内存优化", "优化内存使用以减少GC压力"),
        THREAD_POOL_TUNING("线程池调优", "调整线程池配置以提高并发处理能力")
    }
    
    /**
     * 影响级别枚举
     */
    enum class ImpactLevel(val levelName: String, val description: String) {
        LOW("低", "对性能有轻微改善"),
        MEDIUM("中", "对性能有明显改善"),
        HIGH("高", "对性能有显著改善"),
        CRITICAL("关键", "对性能有重大改善")
    }
    
    /**
     * 难度级别枚举
     */
    enum class DifficultyLevel(val levelName: String, val description: String) {
        EASY("简单", "容易实施，风险低"),
        MEDIUM("中等", "需要一定配置，风险中等"),
        HARD("困难", "需要深度配置，风险较高"),
        EXPERT("专家", "需要专业知识，风险高")
    }
    
    /**
     * 负载状态枚举
     */
    enum class LoadStatus(val statusName: String, val description: String) {
        IDLE("空闲", "系统负载很低"),
        NORMAL("正常", "系统负载正常"),
        BUSY("繁忙", "系统负载较高"),
        OVERLOADED("过载", "系统负载过高")
    }
    
    /**
     * 性能指标阈值
     */
    object PerformanceThresholds {
        const val RESPONSE_TIME_EXCELLENT_MS = 1000L
        const val RESPONSE_TIME_GOOD_MS = 3000L
        const val RESPONSE_TIME_ACCEPTABLE_MS = 5000L
        const val MEMORY_USAGE_LOW_PERCENTAGE = 50.0
        const val MEMORY_USAGE_NORMAL_PERCENTAGE = 70.0
        const val MEMORY_USAGE_HIGH_PERCENTAGE = 85.0
        const val CPU_USAGE_LOW_PERCENTAGE = 30.0
        const val CPU_USAGE_NORMAL_PERCENTAGE = 60.0
        const val CPU_USAGE_HIGH_PERCENTAGE = 80.0
        const val ERROR_RATE_EXCELLENT_PERCENTAGE = 0.1
        const val ERROR_RATE_GOOD_PERCENTAGE = 1.0
        const val ERROR_RATE_ACCEPTABLE_PERCENTAGE = 3.0
    }
    
    /**
     * 日志消息常量
     */
    object LogMessages {
        const val PERFORMANCE_OPTIMIZATION_STARTED = "性能优化已启动"
        const val PERFORMANCE_OPTIMIZATION_COMPLETED = "性能优化已完成"
        const val CACHE_STATISTICS_UPDATED = "缓存统计信息已更新"
        const val PERFORMANCE_STATISTICS_UPDATED = "性能统计信息已更新"
        const val PERFORMANCE_ALERT_TRIGGERED = "性能警报已触发"
        const val HTTP_CONNECTION_POOL_OPTIMIZED = "HTTP连接池已优化"
        const val CACHE_CONFIGURATION_UPDATED = "缓存配置已更新"
        const val ASYNC_PROCESSING_ENABLED = "异步处理已启用"
        const val REQUEST_BATCHING_ENABLED = "请求批处理已启用"
    }
    
    /**
     * 获取负载状态基于CPU使用率
     */
    fun getLoadStatusByCpuUsage(cpuUsage: Double): LoadStatus {
        return when {
            cpuUsage < PerformanceThresholds.CPU_USAGE_LOW_PERCENTAGE -> LoadStatus.IDLE
            cpuUsage < PerformanceThresholds.CPU_USAGE_NORMAL_PERCENTAGE -> LoadStatus.NORMAL
            cpuUsage < PerformanceThresholds.CPU_USAGE_HIGH_PERCENTAGE -> LoadStatus.BUSY
            else -> LoadStatus.OVERLOADED
        }
    }
    
    /**
     * 获取负载状态基于内存使用率
     */
    fun getLoadStatusByMemoryUsage(memoryUsage: Double): LoadStatus {
        return when {
            memoryUsage < PerformanceThresholds.MEMORY_USAGE_LOW_PERCENTAGE -> LoadStatus.IDLE
            memoryUsage < PerformanceThresholds.MEMORY_USAGE_NORMAL_PERCENTAGE -> LoadStatus.NORMAL
            memoryUsage < PerformanceThresholds.MEMORY_USAGE_HIGH_PERCENTAGE -> LoadStatus.BUSY
            else -> LoadStatus.OVERLOADED
        }
    }
    
    /**
     * 获取响应时间等级
     */
    fun getResponseTimeLevel(responseTimeMs: Long): String {
        return when {
            responseTimeMs <= PerformanceThresholds.RESPONSE_TIME_EXCELLENT_MS -> "优秀"
            responseTimeMs <= PerformanceThresholds.RESPONSE_TIME_GOOD_MS -> "良好"
            responseTimeMs <= PerformanceThresholds.RESPONSE_TIME_ACCEPTABLE_MS -> "可接受"
            else -> "需要优化"
        }
    }
}