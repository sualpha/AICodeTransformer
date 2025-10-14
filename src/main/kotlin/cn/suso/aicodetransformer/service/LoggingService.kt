package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.constants.LogExportFormat
import cn.suso.aicodetransformer.constants.PerformanceMetric
import cn.suso.aicodetransformer.constants.SecurityEvent
import cn.suso.aicodetransformer.constants.UserAction
import cn.suso.aicodetransformer.model.*

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