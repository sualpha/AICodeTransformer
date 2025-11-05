package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.constants.*
import cn.suso.aicodetransformer.model.*
import cn.suso.aicodetransformer.model.SortDirection
import cn.suso.aicodetransformer.service.*
import com.intellij.openapi.components.service
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong


/**
 * 日志记录服务实现
 */
class LoggingServiceImpl : LoggingService {
    
    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private val logIdGenerator = AtomicLong(0)
    private var config = LoggingConfig()
    private var lastWrittenLogId = AtomicLong(0) // 记录最后写入文件的日志ID
    private val configurationService: ConfigurationService = service()
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    
    // 性能统计缓存
    private val performanceCache = ConcurrentHashMap<String, MutableList<Double>>()
    private val errorCache = ConcurrentHashMap<String, AtomicLong>()
    
    // 异步日志写入协程
    private val logWriterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var cleanupJob: kotlinx.coroutines.Job? = null
    private var logWriterJob: kotlinx.coroutines.Job? = null
    
    init {
        // 从配置服务加载日志配置
        loadConfigurationFromService()
        // 根据配置决定是否启动后台任务（未启用则不启动任何线程）
        updateBackgroundTasks()
    }
    
    /**
     * 从配置服务加载日志配置
     */
    private fun loadConfigurationFromService() {
        try {
            val loggingConfigState = configurationService.getLoggingConfig()
            config = convertToLoggingConfig(loggingConfigState)
        } catch (e: Exception) {
            // 如果加载失败，使用默认配置
            config = LoggingConfig()
        }
    }

    /**
     * 根据当前配置启动或停止后台任务
     */
    private fun updateBackgroundTasks() {
        // 控制清理任务（绑定到 enabled）
        if (config.enabled) {
            if (cleanupJob == null || cleanupJob?.isActive != true) {
                cleanupJob = startCleanupTask()
            }
        } else {
            cleanupJob?.cancel()
            cleanupJob = null
        }

        // 控制文件写入任务（绑定到 logToFile）
        if (config.logToFile) {
            if (logWriterJob == null || logWriterJob?.isActive != true) {
                logWriterJob = startLogWriterTask()
            }
        } else {
            logWriterJob?.cancel()
            logWriterJob = null
        }
    }
    
    /**
     * 将LoggingConfigState转换为LoggingConfig
     */
    private fun convertToLoggingConfig(state: LoggingConfigState): LoggingConfig {
        return LoggingConfig(
            enabled = state.enabled,
            minLevel = LogLevel.valueOf(state.minLevel),
            logToFile = state.logToFile,
            logToConsole = state.logToConsole,
            logFilePath = state.logFilePath,
            maxFileSize = state.maxFileSize,
            maxFileCount = state.maxFileCount,
            retentionTimeMs = state.retentionTimeMs,
            enablePerformanceLogging = state.enablePerformanceLogging,
            enableSecurityLogging = state.enableSecurityLogging,
            logSensitiveData = state.logSensitiveData,
            logFormat = LogFormat.valueOf(state.logFormat)
        )
    }
    
    /**
     * 将LoggingConfig转换为LoggingConfigState
     */
    private fun convertToLoggingConfigState(config: LoggingConfig): LoggingConfigState {
        return LoggingConfigState(
            enabled = config.enabled,
            minLevel = config.minLevel.name,
            logToFile = config.logToFile,
            logToConsole = config.logToConsole,
            logFilePath = config.logFilePath,
            maxFileSize = config.maxFileSize,
            maxFileCount = config.maxFileCount,
            retentionTimeMs = config.retentionTimeMs,
            enablePerformanceLogging = config.enablePerformanceLogging,
            enableSecurityLogging = config.enableSecurityLogging,
            logSensitiveData = config.logSensitiveData,
            logFormat = config.logFormat.name
        )
    }
    
    override fun logApiCallStart(requestId: String, config: ModelConfiguration, prompt: String, userId: String?) {
        if (!this.config.enabled) return
        
        val metadata = LogMetadata(mapOf(
            "requestId" to requestId,
            "modelName" to config.name,
            "promptLength" to prompt.length.toString(),
            "userId" to (userId ?: "anonymous")
        ))
        
        addLogEntry(
            level = LogLevel.INFO,
            type = LogType.API_CALL,
            message = "API调用开始: ${config.name}",
            context = "Request ID: $requestId",
            userId = userId,
            requestId = requestId,
            metadata = metadata
        )
    }
    
    override fun logApiCallEnd(requestId: String, result: ExecutionResult, responseTimeMs: Long) {
        if (!this.config.enabled) return
        
        val level = if (result.success) LogLevel.INFO else LogLevel.ERROR
        val metadata = LogMetadata(mapOf(
            "requestId" to requestId,
            "success" to result.success.toString(),
            "responseTimeMs" to responseTimeMs.toString(),
            "errorType" to (result.errorType?.name ?: "none")
        ))
        
        addLogEntry(
            level = level,
            type = LogType.API_CALL,
            message = "API调用完成: ${if (result.success) "成功" else "失败"} (${responseTimeMs}ms)",
            context = "Request ID: $requestId",
            requestId = requestId,
            metadata = metadata
        )
        
        // 移除重复的性能指标记录，避免与单独调用logPerformanceMetric时重复
        // 性能指标应该由调用方根据需要单独记录
    }
    
    override fun logApiRequestDetails(requestId: String, config: ModelConfiguration, requestBody: String, headers: Map<String, String>, userId: String?) {
        if (!this.config.enabled || !this.config.logSensitiveData) return
        
        val sanitizedHeaders = headers.toMutableMap()
        // 隐藏敏感信息
        sanitizedHeaders["Authorization"] = "Bearer ***"
        
        val metadata = LogMetadata(mapOf(
            "requestId" to requestId,
            "modelName" to config.name,
            "modelType" to config.modelType.name,
            "apiBaseUrl" to config.apiBaseUrl,
            "maxTokens" to config.maxTokens.toString(),
            "temperature" to config.temperature.toString(),
            "requestBodyLength" to requestBody.length.toString(),
            "headers" to sanitizedHeaders.toString(),
            "userId" to (userId ?: "anonymous")
        ))
        
        val contextInfo = "Model: ${config.name}, URL: ${config.apiBaseUrl}, Body Length: ${requestBody.length}"
        
        // 合并敏感数据记录，避免重复日志
        val message = if (this.config.logSensitiveData) {
            "API请求详情: $requestBody"
        } else {
            "API请求详情记录"
        }
        
        addLogEntry(
            level = this.config.minLevel,
            type = LogType.API_CALL,
            message = message,
            context = contextInfo,
            userId = userId,
            requestId = requestId,
            metadata = metadata
        )
    }
    
    override fun logApiResponseDetails(requestId: String, responseBody: String, statusCode: Int, responseHeaders: Map<String, String>, responseTimeMs: Long) {
        if (!this.config.enabled || !this.config.logSensitiveData) return
        
        val metadata = LogMetadata(mapOf(
            "requestId" to requestId,
            "statusCode" to statusCode.toString(),
            "responseBodyLength" to responseBody.length.toString(),
            "responseTimeMs" to responseTimeMs.toString(),
            "responseHeaders" to responseHeaders.toString()
        ))
        
        val contextInfo = "Status: $statusCode, Body Length: ${responseBody.length}, Time: ${responseTimeMs}ms"
        
        // 合并敏感数据记录，避免重复日志
        val message = if (this.config.logSensitiveData) {
            "API响应详情: $responseBody"
        } else {
            "API响应详情记录"
        }
        
        addLogEntry(
            level = this.config.minLevel,
            type = LogType.API_CALL,
            message = message,
            context = contextInfo,
            requestId = requestId,
            metadata = metadata
        )
    }
    
    override fun logError(error: Throwable, context: String?, userId: String?) {
        if (!this.config.enabled || this.config.minLevel.priority > LogLevel.ERROR.priority) return
        
        val stackTrace = StringWriter().use { sw ->
            PrintWriter(sw).use { pw ->
                error.printStackTrace(pw)
                sw.toString()
            }
        }
        
        val metadata = LogMetadata(mapOf(
            "errorClass" to error.javaClass.simpleName,
            "errorMessage" to (error.message ?: "Unknown error")
        ))
        
        addLogEntry(
            level = LogLevel.ERROR,
            type = LogType.ERROR,
            message = "系统错误: ${error.message ?: error.javaClass.simpleName}",
            context = context,
            userId = userId,
            stackTrace = stackTrace,
            metadata = metadata
        )
        
        // 更新错误统计
        val errorKey = error.javaClass.simpleName
        errorCache.computeIfAbsent(errorKey) { AtomicLong(0) }.incrementAndGet()
    }
    
    override fun logWarning(message: String, context: String?, userId: String?) {
        if (!this.config.enabled || this.config.minLevel.priority > LogLevel.WARNING.priority) return
        
        addLogEntry(
            level = LogLevel.WARNING,
            type = LogType.SYSTEM,
            message = message,
            context = context,
            userId = userId
        )
    }
    
    override fun logInfo(message: String, context: String?, userId: String?) {
        if (!this.config.enabled || this.config.minLevel.priority > LogLevel.INFO.priority) return
        
        addLogEntry(
            level = LogLevel.INFO,
            type = LogType.SYSTEM,
            message = message,
            context = context,
            userId = userId
        )
    }
    
    override fun logDebug(message: String, context: String?, userId: String?) {
        if (!this.config.enabled || this.config.minLevel.priority > LogLevel.DEBUG.priority) return
        
        addLogEntry(
            level = LogLevel.DEBUG,
            type = LogType.SYSTEM,
            message = message,
            context = context,
            userId = userId
        )
    }
    
    override fun logUserAction(action: UserAction, details: String?, userId: String?) {
        if (!this.config.enabled) return
        
        val metadata = LogMetadata(mapOf(
            "action" to action.name,
            "details" to (details ?: "")
        ))
        
        addLogEntry(
            level = LogLevel.INFO,
            type = LogType.USER_ACTION,
            message = "用户操作: ${action.name}",
            context = details,
            userId = userId,
            metadata = metadata
        )
    }
    
    override fun logPerformanceMetric(metric: PerformanceMetric, value: Double, context: String?) {
        if (!this.config.enabled || !this.config.enablePerformanceLogging) return
        
        val metadata = LogMetadata(mapOf(
            "metric" to metric.name,
            "value" to value.toString(),
            "unit" to getMetricUnit(metric)
        ))
        
        addLogEntry(
            level = LogLevel.DEBUG,
            type = LogType.PERFORMANCE,
            message = "性能指标: ${metric.name} = $value ${getMetricUnit(metric)}",
            context = context,
            metadata = metadata
        )
        
        // 缓存性能数据用于统计
        performanceCache.computeIfAbsent(metric.name) { ArrayList() }.add(value)
    }
    
    override fun logSecurityEvent(event: SecurityEvent, details: String?, userId: String?) {
        if (!this.config.enabled || !this.config.enableSecurityLogging) return
        
        val metadata = LogMetadata(mapOf(
            "event" to event.name,
            "details" to (details ?: "")
        ))
        
        addLogEntry(
            level = LogLevel.WARNING,
            type = LogType.SECURITY,
            message = "安全事件: ${event.name}",
            context = details,
            userId = userId,
            metadata = metadata
        )
    }
    
    override fun getLogs(criteria: LogSearchCriteria): List<LogEntry> {
        return logEntries.filter { entry ->
            // 时间范围过滤
            (criteria.startTime == null || entry.timestamp >= criteria.startTime) &&
            (criteria.endTime == null || entry.timestamp <= criteria.endTime) &&
            // 级别过滤
            (criteria.levels.isEmpty() || criteria.levels.contains(entry.level)) &&
            // 类型过滤
            (criteria.types.isEmpty() || criteria.types.contains(entry.type)) &&
            // 用户ID过滤
            (criteria.userId == null || entry.userId == criteria.userId) &&
            // 请求ID过滤
            (criteria.requestId == null || entry.requestId == criteria.requestId) &&
            // 关键词搜索
            (criteria.keyword == null || entry.message.contains(criteria.keyword, ignoreCase = true))
        }.sortedWith { a, b ->
            when (criteria.sortBy) {
                LogSortBy.TIMESTAMP -> compareValues(a.timestamp, b.timestamp)
                LogSortBy.LEVEL -> compareValues(a.level.priority, b.level.priority)
                LogSortBy.TYPE -> compareValues(a.type.name, b.type.name)
                LogSortBy.USER_ID -> compareValues(a.userId, b.userId)
            }.let { if (criteria.sortDirection == SortDirection.DESC) -it else it }
        }.drop(criteria.pageNumber * criteria.pageSize)
         .take(criteria.pageSize)
    }
    
    override fun getErrorStats(timeRangeMs: Long): ErrorStats {
        val cutoffTime = System.currentTimeMillis() - timeRangeMs
        val errorLogs = logEntries.filter { 
            it.level == LogLevel.ERROR && it.timestamp >= cutoffTime 
        }
        
        val totalErrors = errorLogs.size
        val totalLogs = logEntries.count { it.timestamp >= cutoffTime }
        val errorRate = if (totalLogs > 0) totalErrors.toDouble() / totalLogs else 0.0
        
        // 按错误类型分组
        val errorsByType = errorLogs.groupBy { 
            it.metadata["errorClass"] ?: "Unknown"
        }.mapValues { it.value.size }.toMap()
        
        // 按小时分组
        val errorsByHour = errorLogs.groupBy { 
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.size }.toMap()
        
        // 最常见的错误
        val topErrors = errorsByType.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (errorType, count) ->
                val lastOccurrence = errorLogs
                    .filter { it.metadata["errorClass"] == errorType }
                    .maxByOrNull { it.timestamp }?.timestamp ?: 0
                ErrorSummary(errorType, count, lastOccurrence)
            }
        
        return ErrorStats(
            totalErrors = totalErrors,
            errorRate = errorRate,
            errorsByType = errorsByType,
            errorsByHour = errorsByHour,
            topErrors = topErrors,
            timeRangeMs = timeRangeMs
        )
    }
    
    override fun getPerformanceStats(timeRangeMs: Long): PerformanceLogStats {
        val cutoffTime = System.currentTimeMillis() - timeRangeMs
        val apiLogs = logEntries.filter { 
            it.type == LogType.API_CALL && it.timestamp >= cutoffTime 
        }

        val responseTimes = apiLogs.mapNotNull {
            it.metadata["responseTimeMs"]?.toDoubleOrNull()
        }.map { it }
        
        val successfulRequests = apiLogs.count {
            it.metadata["success"]?.toBoolean() == true
        }
        
        // 按小时分组
        val requestsByHour = apiLogs.groupBy { 
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.size }
        
        return PerformanceLogStats(
            averageResponseTime = if (responseTimes.isNotEmpty()) responseTimes.average() else 0.0,
            maxResponseTime = responseTimes.maxOrNull()?.toLong() ?: 0,
            minResponseTime = responseTimes.minOrNull()?.toLong() ?: 0,
            totalRequests = apiLogs.size,
            successfulRequests = successfulRequests,
            requestsByHour = requestsByHour,
            timeRangeMs = timeRangeMs
        )
    }
    
    override fun cleanupOldLogs(olderThanMs: Long): Int {
        val cutoffTime = System.currentTimeMillis() - olderThanMs
        val initialSize = logEntries.size

        // 清理内存中的日志
        logEntries.removeIf { it.timestamp < cutoffTime }
        
        // 清理文件系统中的日志
        val fileCleanedCount = cleanupLogFiles(olderThanMs)
        
        val removedCount = initialSize - logEntries.size
        
        return removedCount + fileCleanedCount
    }
    
    override fun exportLogs(criteria: LogSearchCriteria, format: LogExportFormat): String {
        val logs = getLogs(criteria)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = "logs_export_$timestamp.${format.name.lowercase()}"
        val filePath = "exports/$fileName"
        
        // 确保导出目录存在
        File("exports").mkdirs()
        
        val content = when (format) {
            LogExportFormat.JSON -> {
                // 手动构建JSON避免LinkedHashMap序列化问题
                val logsJson = logs.joinToString(",\n", "[\n", "\n]") { entry ->
                    buildJsonString(mapOf(
                        "id" to entry.id,
                        "timestamp" to entry.timestamp.toString(),
                        "level" to entry.level.name,
                        "type" to entry.type.name,
                        "message" to entry.message,
                        "context" to (entry.context ?: ""),
                        "userId" to (entry.userId ?: ""),
                        "requestId" to (entry.requestId ?: ""),
                        "stackTrace" to (entry.stackTrace ?: ""),
                        "metadata" to entry.metadata.data.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
                    ))
                }
                logsJson
            }
            LogExportFormat.CSV -> exportToCsv(logs)
            LogExportFormat.XML -> exportToXml(logs)
            LogExportFormat.TXT -> exportToText(logs)
        }
        
        File(filePath).writeText(content)
        return filePath
    }
    
    override fun setLoggingConfig(config: LoggingConfig) {
        this.config = config
        
        // 保存配置到持久化存储
        try {
            val configState = convertToLoggingConfigState(config)
            configurationService.saveLoggingConfig(configState)
        } catch (e: Exception) {
            logError(e, "保存日志配置到持久化存储时发生错误")
        }

        // 根据新配置更新后台任务的启停状态
        updateBackgroundTasks()
    }
    
    override fun getLoggingConfig(): LoggingConfig {
        return config
    }
    
    override fun flushLogsToFile() {
        if (!config.logToFile) {
            logWarning("日志文件写入功能已禁用，无法刷新日志到文件", "日志刷新")
            return
        }
        
        try {
            writeLogsToFile()
        } catch (e: Exception) {
            logError(e, "立即刷新日志到文件时发生错误")
        }
    }
    
    private fun addLogEntry(
        level: LogLevel,
        type: LogType,
        message: String,
        context: String? = null,
        userId: String? = null,
        requestId: String? = null,
        stackTrace: String? = null,
        metadata: LogMetadata = LogMetadata()
    ) {
        val entry = LogEntry(
            id = logIdGenerator.incrementAndGet().toString(),
            timestamp = System.currentTimeMillis(),
            level = level,
            type = type,
            message = message,
            context = context,
            userId = userId,
            requestId = requestId,
            stackTrace = stackTrace,
            metadata = metadata
        )
        
        logEntries.offer(entry)
        
        // 控制台输出
        if (config.logToConsole) {
            printToConsole(entry)
        }
        
        // 限制内存中的日志数量
        while (logEntries.size > 10000) {
            logEntries.poll()
        }
    }
    
    private fun buildJsonString(map: Map<String, Any>): String {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{")
        map.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) jsonBuilder.append(",")
            jsonBuilder.append("\"$key\":")
            when (value) {
                is String -> {
                    val escapedValue = value
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                    jsonBuilder.append("\"$escapedValue\"")
                }
                is LogMetadata -> {
                    jsonBuilder.append("{")
                    value.data.entries.forEachIndexed { metaIndex, (metaKey, metaValue) ->
                        if (metaIndex > 0) jsonBuilder.append(",")
                        val escapedKey = metaKey
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                        val escapedValue = metaValue
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                            .replace("\t", "\\t")
                        jsonBuilder.append("\"$escapedKey\":\"$escapedValue\"")
                    }
                    jsonBuilder.append("}")
                }
                is Map<*, *> -> {
                    jsonBuilder.append("{")
                    value.entries.forEachIndexed { metaIndex, (metaKey, metaValue) ->
                        if (metaIndex > 0) jsonBuilder.append(",")
                        val escapedKey = metaKey.toString()
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                        val escapedValue = metaValue?.toString()
                            ?.replace("\\", "\\\\")
                            ?.replace("\"", "\\\"")
                            ?.replace("\n", "\\n")
                            ?.replace("\r", "\\r")
                            ?.replace("\t", "\\t") ?: "null"
                        jsonBuilder.append("\"$escapedKey\":\"$escapedValue\"")
                    }
                    jsonBuilder.append("}")
                }
                else -> {
                    val escapedValue = value.toString()
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                    jsonBuilder.append("\"$escapedValue\"")
                }
            }
        }
        jsonBuilder.append("}")
        return jsonBuilder.toString()
    }

    private fun printToConsole(entry: LogEntry) {
        val timestamp = dateFormat.format(Date(entry.timestamp))
        val levelStr = entry.level.name.padEnd(7)
        val typeStr = entry.type.name.padEnd(12)
        
        val output = when (config.logFormat) {
            LogFormat.JSON -> {
                // 创建带格式化时间戳的JSON输出，确保所有值都是String类型
                val jsonMap = mapOf(
                    "id" to entry.id,
                    "timestamp" to timestamp,
                    "level" to entry.level.name,
                    "type" to entry.type.name,
                    "message" to entry.message,
                    "context" to (entry.context ?: ""),
                    "userId" to (entry.userId ?: ""),
                    "requestId" to (entry.requestId ?: ""),
                    "stackTrace" to (entry.stackTrace ?: ""),
                    "metadata" to entry.metadata
                )
                // 手动构建JSON字符串避免序列化问题
                buildJsonString(jsonMap)
            }
            LogFormat.STRUCTURED -> "[$timestamp] $levelStr [$typeStr] ${entry.message}${entry.context?.let { " - $it" } ?: ""}"
            LogFormat.PLAIN -> "${entry.message}${entry.context?.let { " - $it" } ?: ""}"
        }
        
        when (entry.level) {
            LogLevel.ERROR -> System.err.println(output)
            else -> println(output)
        }
    }
    
    private fun startCleanupTask(): kotlinx.coroutines.Job {
        return logWriterScope.launch {
            while (true) {
                delay(1000) // 每小时执行一次
                try {
                    if (config.enabled) {
                        cleanupOldLogs(config.retentionTimeMs)
                    }
                } catch (e: Exception) {
                    val errorMsg = "日志清理任务失败: ${e.message}"
                    System.err.println(errorMsg)
                    logError(e, "日志清理任务执行失败")
                }
            }
        }
    }
    
    private fun startLogWriterTask(): kotlinx.coroutines.Job {
        return logWriterScope.launch {
            if (!config.logToFile) return@launch
            while (true) {
                delay(5000) // 每5秒写入一次
                try {
                    writeLogsToFile()
                } catch (e: Exception) {
                    System.err.println("日志写入文件失败: ${e.message}")
                }
            }
        }
    }
    
    private fun writeLogsToFile() {
        if (!config.logToFile) return
        
        val logFile = File(config.logFilePath)
        logFile.parentFile?.mkdirs()
        
        // 检查文件大小，如果超过限制则轮转
        if (logFile.exists() && logFile.length() > config.maxFileSize) {
            rotateLogFile(logFile)
        }
        
        // 只获取未写入文件的新日志条目
        val lastWrittenId = lastWrittenLogId.get()
        val newLogs = logEntries.toList().filter { it.id.toLong() > lastWrittenId }
        
        if (newLogs.isNotEmpty()) {
            val formattedLogs = newLogs.map { formatLogForFile(it) }.joinToString("\n")
            logFile.appendText(formattedLogs + "\n")
            
            // 更新最后写入的日志ID
            val maxId = newLogs.maxOf { it.id.toLong() }
            lastWrittenLogId.set(maxId)
        }
    }
    
    private fun rotateLogFile(logFile: File) {
        for (i in config.maxFileCount - 1 downTo 1) {
            val oldFile = File("${logFile.absolutePath}.$i")
            val newFile = File("${logFile.absolutePath}.${i + 1}")
            if (oldFile.exists()) {
                oldFile.renameTo(newFile)
            }
        }
        logFile.renameTo(File("${logFile.absolutePath}.1"))
    }
    
    private fun formatLogForFile(entry: LogEntry): String {
        return when (config.logFormat) {
            LogFormat.JSON -> {
                val timestamp = dateFormat.format(Date(entry.timestamp))
                // 创建带格式化时间戳的JSON输出
                val jsonMap = mapOf(
                    "id" to entry.id,
                    "timestamp" to timestamp,
                    "level" to entry.level.name,
                    "type" to entry.type.name,
                    "message" to entry.message,
                    "context" to (entry.context ?: ""),
                    "userId" to (entry.userId ?: ""),
                    "requestId" to (entry.requestId ?: ""),
                    "stackTrace" to (entry.stackTrace ?: ""),
                    "metadata" to entry.metadata.toString()
                )
                buildJsonString(jsonMap)
            }
            LogFormat.STRUCTURED -> {
                val timestamp = dateFormat.format(Date(entry.timestamp))
                "[$timestamp] ${entry.level.name} [${entry.type.name}] ${entry.message}${entry.context?.let { " - $it" } ?: ""}"
            }
            LogFormat.PLAIN -> entry.message
        }
    }
    
    private fun exportToCsv(logs: List<LogEntry>): String {
        val header = "ID,Timestamp,Level,Type,Message,Context,UserId,RequestId\n"
        val rows = logs.joinToString("\n") { entry ->
            arrayOf(
                entry.id,
                dateFormat.format(Date(entry.timestamp)),
                entry.level.name,
                entry.type.name,
                "\"${entry.message.replace("\"", "\\\"")}\"",
                "\"${entry.context?.replace("\"", "\\\"") ?: ""}\"",
                entry.userId ?: "",
                entry.requestId ?: ""
            ).joinToString(",")
        }
        return header + rows
    }
    
    private fun exportToXml(logs: List<LogEntry>): String {
        val xml = StringBuilder()
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        xml.append("<logs>\n")
        
        logs.forEach { entry ->
            xml.append("  <log>\n")
            xml.append("    <id>${entry.id}</id>\n")
            xml.append("    <timestamp>${dateFormat.format(Date(entry.timestamp))}</timestamp>\n")
            xml.append("    <level>${entry.level.name}</level>\n")
            xml.append("    <type>${entry.type.name}</type>\n")
            xml.append("    <message><![CDATA[${entry.message}]]></message>\n")
            if (entry.context != null) {
                xml.append("    <context><![CDATA[${entry.context}]]></context>\n")
            }
            if (entry.userId != null) {
                xml.append("    <userId>${entry.userId}</userId>\n")
            }
            if (entry.requestId != null) {
                xml.append("    <requestId>${entry.requestId}</requestId>\n")
            }
            xml.append("  </log>\n")
        }
        
        xml.append("</logs>")
        return xml.toString()
    }
    
    private fun exportToText(logs: List<LogEntry>): String {
        return logs.joinToString("\n\n") { entry ->
            val timestamp = dateFormat.format(Date(entry.timestamp))
            buildString {
                append("[$timestamp] ${entry.level.name} [${entry.type.name}]\n")
                append("Message: ${entry.message}\n")
                if (entry.context != null) {
                    append("Context: ${entry.context}\n")
                }
                if (entry.userId != null) {
                    append("User ID: ${entry.userId}\n")
                }
                if (entry.requestId != null) {
                    append("Request ID: ${entry.requestId}\n")
                }
                if (entry.stackTrace != null) {
                    append("Stack Trace:\n${entry.stackTrace}\n")
                }
            }
        }
    }
    
    private fun getMetricUnit(metric: PerformanceMetric): String {
        return when (metric) {
            PerformanceMetric.API_RESPONSE_TIME -> "ms"
            PerformanceMetric.MEMORY_USAGE -> "MB"
            PerformanceMetric.CPU_USAGE -> "%"
            PerformanceMetric.CACHE_HIT_RATE -> "%"
            PerformanceMetric.THROUGHPUT -> "req/s"
            PerformanceMetric.ERROR_RATE -> "%"
            PerformanceMetric.CONCURRENT_USERS -> "users"
        }
    }
    
    /**
     * 清理文件系统中的旧日志
     * 根据时间戳清理日志文件中的旧记录
     */
    private fun cleanupLogFiles(olderThanMs: Long): Int {
        if (!config.logToFile) return 0
        
        val logFile = File(config.logFilePath)
        if (!logFile.exists()) return 0
        
        return try {
            val cutoffTime = System.currentTimeMillis() - olderThanMs
            val tempFile = File("${config.logFilePath}.tmp")
            var keptLines = 0
            var removedLines = 0

            logFile.useLines { lines ->
                tempFile.bufferedWriter().use { writer ->
                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        
                        // 尝试解析日志行获取时间戳
                        val logTimestamp = extractTimestampFromLogLine(line)
                        
                        if (logTimestamp != null && logTimestamp >= cutoffTime) {
                            // 保留这条日志（时间较新）
                            writer.write(line)
                            writer.newLine()
                            keptLines++
                        } else if (logTimestamp == null) {
                            // 无法解析时间戳，保守起见保留这条日志
                            writer.write(line)
                            writer.newLine()
                            keptLines++
                        } else {
                            // 这条日志太旧了，删除
                            removedLines++
                        }
                    }
                }
            }
            
            // 用清理后的文件替换原文件
            if (logFile.delete()) {
                if (tempFile.renameTo(logFile)) {
                    removedLines
                } else {
                    logWarning("无法重命名临时文件到原日志文件", "文件日志清理")
                    // 尝试恢复
                    tempFile.delete()
                    0
                }
            } else {
                logWarning("无法删除原日志文件", "文件日志清理")
                tempFile.delete()
                0
            }
            
        } catch (e: Exception) {
            logError(e, "清理日志文件失败")
            0
        }
    }
    
    /**
     * 从日志行中提取时间戳
     * 支持 JSON、结构化和纯文本格式
     */
    private fun extractTimestampFromLogLine(line: String): Long? {
        return try {
            when (config.logFormat) {
                LogFormat.JSON -> {
                    // 解析JSON格式获取时间戳
                    val jsonElement = json.parseToJsonElement(line)
                    val timestampStr = jsonElement.jsonObject["timestamp"]?.jsonPrimitive?.content
                    timestampStr?.let { dateFormat.parse(it).time }
                }
                LogFormat.STRUCTURED -> {
                    // 解析结构化格式: [2024-01-15 10:30:45.123] 
                    val pattern = """\[([^\]]+)\]""".toRegex()
                    val matchResult = pattern.find(line)
                    matchResult?.groupValues?.get(1)?.let { timestampStr ->
                        dateFormat.parse(timestampStr).time
                    }
                }
                LogFormat.PLAIN -> {
                    // 纯文本格式无法提取时间戳，返回null
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}



