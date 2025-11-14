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
        const val ENABLE_REQUEST_DEDUPLICATION = true

        // PerformanceOptimizationConfig 需要的常量
        const val ENABLE_BATCH_MERGING = false
        const val BATCH_MERGE_WAIT_TIME_MS = 100L
        const val BATCH_MERGE_MAX_REQUESTS = 5
        const val ENABLE_STREAM_RESPONSE = false
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
}