package cn.suso.aicodetransformer.model

import cn.suso.aicodetransformer.constants.LogFormat
import cn.suso.aicodetransformer.constants.LogLevel
import cn.suso.aicodetransformer.constants.LogSortBy
import cn.suso.aicodetransformer.constants.LogType
import kotlinx.serialization.Serializable

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
    
    /** 堆栈跟踪 */
    val stackTrace: String? = null,
    
    /** 元数据 */
    val metadata: LogMetadata = LogMetadata()
)

/**
 * 日志搜索条件
 */
@Serializable
data class LogSearchCriteria(
    /** 开始时间 */
    val startTime: Long? = null,
    
    /** 结束时间 */
    val endTime: Long? = null,
    
    /** 日志级别列表 */
    val levels: List<LogLevel> = emptyList(),
    
    /** 日志类型列表 */
    val types: List<LogType> = emptyList(),
    
    /** 用户ID */
    val userId: String? = null,
    
    /** 请求ID */
    val requestId: String? = null,
    
    /** 关键词 */
    val keyword: String? = null,
    
    /** 排序字段 */
    val sortBy: LogSortBy = LogSortBy.TIMESTAMP,
    
    /** 排序方向 */
    val sortDirection: SortDirection = SortDirection.DESC,
    
    /** 页面大小 */
    val pageSize: Int = 100,
    
    /** 页码 */
    val pageNumber: Int = 0
)

/**
 * 错误统计
 */
@Serializable
data class ErrorStats(
    /** 总错误数 */
    val totalErrors: Int,
    
    /** 错误率 */
    val errorRate: Double,
    
    /** 按类型分组的错误数 */
    val errorsByType: Map<String, Int>,
    
    /** 按小时分组的错误数 */
    val errorsByHour: Map<Int, Int>,
    
    /** 热门错误 */
    val topErrors: List<ErrorSummary>,
    
    /** 时间范围（毫秒） */
    val timeRangeMs: Long
)

/**
 * 错误摘要
 */
@Serializable
data class ErrorSummary(
    /** 错误消息 */
    val message: String,
    
    /** 出现次数 */
    val count: Int,
    
    /** 最后出现时间 */
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
    
    /** 时间范围（毫秒） */
    val timeRangeMs: Long
)

/**
 * 日志配置
 */
@Serializable
data class LoggingConfig(
    /** 是否启用日志 */
    val enabled: Boolean = true,
    
    /** 最小日志级别 */
    val minLevel: LogLevel = LogLevel.INFO,
    
    /** 是否记录到文件 */
    val logToFile: Boolean = true,
    
    /** 是否记录到控制台 */
    val logToConsole: Boolean = true,
    
    /** 日志文件路径 */
    val logFilePath: String = "logs/application.log",
    
    /** 最大文件大小 */
    val maxFileSize: Long = 10 * 1024 * 1024, // 10MB
    
    /** 最大文件数量 */
    val maxFileCount: Int = 10,
    
    /** 保留时间（毫秒） */
    val retentionTimeMs: Long = 604800000, // 7天
    
    /** 是否启用性能日志 */
    val enablePerformanceLogging: Boolean = true,
    
    /** 是否启用安全日志 */
    val enableSecurityLogging: Boolean = true,
    
    /** 是否记录敏感数据 */
    val logSensitiveData: Boolean = false,
    
    /** 日志格式 */
    val logFormat: LogFormat = LogFormat.JSON
)