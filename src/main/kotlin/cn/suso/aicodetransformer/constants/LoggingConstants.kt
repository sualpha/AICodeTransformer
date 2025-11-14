package cn.suso.aicodetransformer.constants

import kotlinx.serialization.Serializable

/**
 * 日志级别
 */
@Serializable
enum class LogLevel(val priority: Int) {
    DEBUG(0),
    INFO(1),
    WARNING(2),
    ERROR(3)
}

/**
 * 日志类型
 */
@Serializable
enum class LogType {
    SYSTEM,         // 系统日志
    API_CALL,       // API调用日志
    ERROR           // 错误日志
}

/**
 * 性能指标类型
 */
@Serializable
enum class PerformanceMetric {
    API_RESPONSE_TIME,      // API响应时间
    MEMORY_USAGE,           // 内存使用量
    CPU_USAGE,              // CPU使用率
    CACHE_HIT_RATE,         // 缓存命中率
    THROUGHPUT,             // 吞吐量
    ERROR_RATE,             // 错误率
    CONCURRENT_USERS        // 并发用户数
}

/**
 * 日志排序字段
 */
@Serializable
enum class LogSortBy {
    TIMESTAMP,      // 时间戳
    LEVEL,          // 日志级别
    TYPE,           // 日志类型
    USER_ID         // 用户ID
}

/**
 * 日志格式
 */
@Serializable
enum class LogFormat {
    JSON,       // JSON格式
    PLAIN,      // 纯文本格式
    STRUCTURED  // 结构化格式
}