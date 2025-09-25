package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.ModelConfiguration
import kotlinx.serialization.Serializable

/**
 * 日志记录服务接口
 * 用于统一管理系统运行日志和错误信息
 */
interface LoggingService {
    
    /**
     * 记录API调用开始
     * @param requestId 请求ID
     * @param config 模型配置
     * @param prompt 提示内容
     * @param userId 用户ID（可选）
     */
    fun logApiCallStart(requestId: String, config: ModelConfiguration, prompt: String, userId: String? = null)
    
    /**
     * 记录API调用完成
     * @param requestId 请求ID
     * @param result 执行结果
     * @param responseTimeMs 响应时间（毫秒）
     */
    fun logApiCallEnd(requestId: String, result: ExecutionResult, responseTimeMs: Long)
    
    /**
     * 记录详细的API请求参数
     * @param requestId 请求ID
     * @param config 模型配置
     * @param requestBody 请求体内容
     * @param headers 请求头信息
     * @param userId 用户ID（可选）
     */
    fun logApiRequestDetails(requestId: String, config: ModelConfiguration, requestBody: String, headers: Map<String, String>, userId: String? = null)
    
    /**
     * 记录详细的API响应参数
     * @param requestId 请求ID
     * @param responseBody 响应体内容
     * @param statusCode HTTP状态码
     * @param responseHeaders 响应头信息
     * @param responseTimeMs 响应时间（毫秒）
     */
    fun logApiResponseDetails(requestId: String, responseBody: String, statusCode: Int, responseHeaders: Map<String, String>, responseTimeMs: Long)
    
    /**
     * 记录系统错误
     * @param error 错误信息
     * @param context 错误上下文
     * @param userId 用户ID（可选）
     */
    fun logError(error: Throwable, context: String? = null, userId: String? = null)
    
    /**
     * 记录系统警告
     * @param message 警告消息
     * @param context 警告上下文
     * @param userId 用户ID（可选）
     */
    fun logWarning(message: String, context: String? = null, userId: String? = null)
    
    /**
     * 记录系统信息
     * @param message 信息消息
     * @param context 信息上下文
     * @param userId 用户ID（可选）
     */
    fun logInfo(message: String, context: String? = null, userId: String? = null)
    
    /**
     * 记录调试信息
     * @param message 调试消息
     * @param context 调试上下文
     * @param userId 用户ID（可选）
     */
    fun logDebug(message: String, context: String? = null, userId: String? = null)
    
    /**
     * 记录用户操作
     * @param action 操作类型
     * @param details 操作详情
     * @param userId 用户ID
     */
    fun logUserAction(action: UserAction, details: String? = null, userId: String? = null)
    
    /**
     * 记录性能指标
     * @param metric 性能指标
     * @param value 指标值
     * @param context 指标上下文
     */
    fun logPerformanceMetric(metric: PerformanceMetric, value: Double, context: String? = null)
    
    /**
     * 记录安全事件
     * @param event 安全事件
     * @param details 事件详情
     * @param userId 用户ID（可选）
     */
    fun logSecurityEvent(event: SecurityEvent, details: String? = null, userId: String? = null)
    
    /**
     * 获取日志记录
     * @param criteria 查询条件
     * @return 日志记录列表
     */
    fun getLogs(criteria: LogSearchCriteria): List<LogEntry>
    
    /**
     * 获取错误统计
     * @param timeRangeMs 时间范围（毫秒）
     * @return 错误统计信息
     */
    fun getErrorStats(timeRangeMs: Long = 86400000): ErrorStats // 默认24小时
    
    /**
     * 获取性能统计
     * @param timeRangeMs 时间范围（毫秒）
     * @return 性能统计信息
     */
    fun getPerformanceStats(timeRangeMs: Long = 86400000): PerformanceLogStats
    
    /**
     * 清理旧日志
     * @param olderThanMs 清理指定时间之前的日志（毫秒）
     * @return 清理的日志数量
     */
    fun cleanupOldLogs(olderThanMs: Long = 604800000): Int // 默认7天
    
    /**
     * 导出日志
     * @param criteria 导出条件
     * @param format 导出格式
     * @return 导出文件路径
     */
    fun exportLogs(criteria: LogSearchCriteria, format: LogExportFormat = LogExportFormat.JSON): String
    
    /**
     * 设置日志配置
     * @param config 日志配置
     */
    fun setLoggingConfig(config: LoggingConfig)
    
    /**
     * 获取当前日志配置
     * @return 日志配置
     */
    fun getLoggingConfig(): LoggingConfig
    
    /**
     * 立即刷新日志到文件
     * 强制将缓存的日志立即写入文件，而不等待定时任务
     */
    fun flushLogsToFile()
}

/**
 * 日志元数据
 */
@Serializable
data class LogMetadata(
    val data: Map<String, String> = emptyMap()
) {
    operator fun get(key: String): String? = data[key]
    operator fun plus(other: Map<String, String>): LogMetadata = LogMetadata(data + other)
    fun isEmpty(): Boolean = data.isEmpty()
    fun isNotEmpty(): Boolean = data.isNotEmpty()
    override fun toString(): String = data.toString()
}

/**
 * 日志条目
 */
@Serializable
data class LogEntry(
    /** 日志ID */
    val id: String,
    
    /** 时间戳 */
    val timestamp: Long,
    
    /** 日志级别 */
    val level: LogLevel,
    
    /** 日志类型 */
    val type: LogType,
    
    /** 消息内容 */
    val message: String,
    
    /** 上下文信息 */
    val context: String? = null,
    
    /** 用户ID */
    val userId: String? = null,
    
    /** 请求ID */
    val requestId: String? = null,
    
    /** 错误堆栈（如果是错误日志） */
    val stackTrace: String? = null,
    
    /** 额外数据 */
    val metadata: LogMetadata = LogMetadata()
)

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
    USER_ACTION,    // 用户操作日志
    PERFORMANCE,    // 性能日志
    SECURITY,       // 安全日志
    ERROR           // 错误日志
}

/**
 * 用户操作类型
 */
@Serializable
enum class UserAction {
    LOGIN,              // 登录
    LOGOUT,             // 登出
    CODE_TRANSFORM,     // 代码转换
    TEMPLATE_CREATE,    // 创建模板
    TEMPLATE_UPDATE,    // 更新模板
    TEMPLATE_DELETE,    // 删除模板
    CONFIG_UPDATE,      // 更新配置
    FILE_UPLOAD,        // 文件上传
    FILE_DOWNLOAD,      // 文件下载
    EXPORT_DATA,        // 导出数据
    IMPORT_DATA         // 导入数据
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
 * 安全事件类型
 */
@Serializable
enum class SecurityEvent {
    AUTHENTICATION_FAILURE,     // 认证失败
    AUTHORIZATION_FAILURE,      // 授权失败
    SUSPICIOUS_ACTIVITY,        // 可疑活动
    DATA_ACCESS_VIOLATION,      // 数据访问违规
    API_ABUSE,                  // API滥用
    RATE_LIMIT_EXCEEDED,        // 超出限流
    INVALID_INPUT,              // 无效输入
    SECURITY_SCAN_DETECTED      // 检测到安全扫描
}

/**
 * 日志搜索条件
 */
@Serializable
data class LogSearchCriteria(
    /** 开始时间 */
    val startTime: Long? = null,
    
    /** 结束时间 */
    val endTime: Long? = null,
    
    /** 日志级别过滤 */
    val levels: List<LogLevel> = emptyList(),
    
    /** 日志类型过滤 */
    val types: List<LogType> = emptyList(),
    
    /** 用户ID过滤 */
    val userId: String? = null,
    
    /** 请求ID过滤 */
    val requestId: String? = null,
    
    /** 关键词搜索 */
    val keyword: String? = null,
    
    /** 排序字段 */
    val sortBy: LogSortBy = LogSortBy.TIMESTAMP,
    
    /** 排序方向 */
    val sortDirection: SortDirection = SortDirection.DESC,
    
    /** 分页大小 */
    val pageSize: Int = 100,
    
    /** 页码 */
    val pageNumber: Int = 0
)

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
 * 错误统计信息
 */
@Serializable
data class ErrorStats(
    /** 总错误数 */
    val totalErrors: Int,
    
    /** 错误率 */
    val errorRate: Double,
    
    /** 按错误类型分组的统计 */
    val errorsByType: Map<String, Int>,
    
    /** 按小时分组的错误数 */
    val errorsByHour: Map<Int, Int>,
    
    /** 最常见的错误 */
    val topErrors: List<ErrorSummary>,
    
    /** 统计时间范围 */
    val timeRangeMs: Long
)

/**
 * 错误摘要
 */
@Serializable
data class ErrorSummary(
    /** 错误消息 */
    val message: String,
    
    /** 发生次数 */
    val count: Int,
    
    /** 最后发生时间 */
    val lastOccurrence: Long
)

/**
 * 性能日志统计
 */
@Serializable
data class PerformanceLogStats(
    /** 平均响应时间 */
    val averageResponseTime: Double,
    
    /** 最大响应时间 */
    val maxResponseTime: Long,
    
    /** 最小响应时间 */
    val minResponseTime: Long,
    
    /** 总请求数 */
    val totalRequests: Int,
    
    /** 成功请求数 */
    val successfulRequests: Int,
    
    /** 按小时分组的请求数 */
    val requestsByHour: Map<Int, Int>,
    
    /** 统计时间范围 */
    val timeRangeMs: Long
)

/**
 * 日志导出格式
 */
@Serializable
enum class LogExportFormat {
    JSON,       // JSON格式
    CSV,        // CSV格式
    XML,        // XML格式
    TXT         // 纯文本格式
}

/**
 * 日志配置
 */
@Serializable
data class LoggingConfig(
    /** 是否启用日志记录 */
    val enabled: Boolean = true,
    
    /** 最小日志级别 */
    val minLevel: LogLevel = LogLevel.INFO,
    
    /** 是否记录到文件 */
    val logToFile: Boolean = true,
    
    /** 是否记录到控制台 */
    val logToConsole: Boolean = true,
    
    /** 日志文件路径 */
    val logFilePath: String = "logs/application.log",
    
    /** 日志文件最大大小（字节） */
    val maxFileSize: Long = 10 * 1024 * 1024, // 10MB
    
    /** 保留的日志文件数量 */
    val maxFileCount: Int = 10,
    
    /** 日志保留时间（毫秒） */
    val retentionTimeMs: Long = 604800000, // 7天
    
    /** 是否启用性能日志 */
    val enablePerformanceLogging: Boolean = true,
    
    /** 是否启用安全日志 */
    val enableSecurityLogging: Boolean = true,
    
    /** 是否记录敏感信息 */
    val logSensitiveData: Boolean = false,
    
    /** 日志格式 */
    val logFormat: LogFormat = LogFormat.JSON
)

/**
 * 日志格式
 */
@Serializable
enum class LogFormat {
    JSON,       // JSON格式
    PLAIN,      // 纯文本格式
    STRUCTURED  // 结构化格式
}

/**
 * 排序方向
 */
@Serializable
enum class SortDirection {
    ASC,    // 升序
    DESC    // 降序
}