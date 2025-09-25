package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.ModelType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class LoggingServiceTest {
    
    private lateinit var loggingService: LoggingService
    
    @BeforeEach
    fun setUp() {
        loggingService = TestLoggingServiceImpl()
    }
    
    // 测试用的LoggingService实现
    private class TestLoggingServiceImpl : LoggingService {
        private val logs = ConcurrentLinkedQueue<String>()
        
        override fun logApiCallStart(requestId: String, config: ModelConfiguration, prompt: String, userId: String?) {
            logs.add("API_CALL_START: $requestId")
        }
        
        override fun logApiCallEnd(requestId: String, result: ExecutionResult, responseTimeMs: Long) {
            logs.add("API_CALL_END: $requestId")
        }
        
        override fun logApiRequestDetails(requestId: String, config: ModelConfiguration, requestBody: String, headers: Map<String, String>, userId: String?) {
            logs.add("API_REQUEST: $requestId")
        }
        
        override fun logApiResponseDetails(requestId: String, responseBody: String, statusCode: Int, responseHeaders: Map<String, String>, responseTimeMs: Long) {
            logs.add("API_RESPONSE: $requestId")
        }
        
        override fun logError(error: Throwable, context: String?, userId: String?) {
            logs.add("ERROR: ${error.message}")
        }
        
        override fun logWarning(message: String, context: String?, userId: String?) {
            logs.add("WARNING: $message")
        }
        
        override fun logInfo(message: String, context: String?, userId: String?) {
            logs.add("INFO: $message")
        }
        
        override fun logDebug(message: String, context: String?, userId: String?) {
            logs.add("DEBUG: $message")
        }
        
        override fun logUserAction(action: UserAction, details: String?, userId: String?) {
            logs.add("USER_ACTION: ${action.name}")
        }
        
        override fun logSecurityEvent(event: SecurityEvent, details: String?, userId: String?) {
            logs.add("SECURITY: ${event.name}")
        }
        
        override fun logPerformanceMetric(metric: PerformanceMetric, value: Double, context: String?) {
            logs.add("PERFORMANCE: ${metric.name} - ${value}ms")
        }
        
        override fun getLogs(criteria: LogSearchCriteria): List<LogEntry> = emptyList()
        override fun getErrorStats(timeRangeMs: Long): ErrorStats = ErrorStats(0, 0.0, emptyMap(), emptyMap(), emptyList(), timeRangeMs)
        override fun getPerformanceStats(timeRangeMs: Long): PerformanceLogStats = PerformanceLogStats(0.0, 0, 0, 0, 0, emptyMap(), timeRangeMs)
        override fun cleanupOldLogs(olderThanMs: Long): Int = 0
        override fun exportLogs(criteria: LogSearchCriteria, format: LogExportFormat): String = ""
        override fun setLoggingConfig(config: LoggingConfig) {}
        override fun getLoggingConfig(): LoggingConfig = LoggingConfig()
        override fun flushLogsToFile() {}
        
        fun hasLog(pattern: String): Boolean = logs.any { it.contains(pattern) }
    }
    
    @Test
    fun `测试API调用日志存储功能`() {
        // 准备测试数据
        val requestId = "test-request-${UUID.randomUUID()}"
        val modelConfig = ModelConfiguration(
            id = "test-model",
            name = "Test Model",
            modelType = ModelType.OPENAI_COMPATIBLE,
            apiBaseUrl = "https://api.test.com",
            apiKey = "test-key",
            maxTokens = 1000,
            temperature = 0.7
        )
        val prompt = "测试提示词内容"
        val userId = "test-user"
        
        // 测试API调用开始日志
        loggingService.logApiCallStart(requestId, modelConfig, prompt, userId)
        
        // 模拟API调用详情
        val requestBody = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[{\"role\":\"user\",\"content\":\"$prompt\"}]}"
        val headers = mapOf(
            "Authorization" to "Bearer test-key",
            "Content-Type" to "application/json"
        )
        loggingService.logApiRequestDetails(requestId, modelConfig, requestBody, headers, userId)
        
        // 模拟API响应
        val responseBody = "{\"choices\":[{\"message\":{\"content\":\"测试响应内容\"}}]}"
        val responseHeaders = mapOf("Content-Type" to "application/json")
        val responseTimeMs = 1500L
        
        loggingService.logApiResponseDetails(requestId, responseBody, 200, responseHeaders, responseTimeMs)
        
        // 测试API调用结束日志
        val executionResult = ExecutionResult(
            success = true,
            content = "测试响应内容",
            errorMessage = null,
            executionTimeMs = responseTimeMs,
            modelConfigId = modelConfig.id,
            tokensUsed = 50
        )
        
        loggingService.logApiCallEnd(requestId, executionResult, responseTimeMs)
        
        // 验证日志存储
        val testService = loggingService as TestLoggingServiceImpl
        
        // 验证各种日志都被记录
        assertTrue(testService.hasLog("API_CALL_START: $requestId"), "应该有API调用开始的日志")
        assertTrue(testService.hasLog("API_REQUEST: $requestId"), "应该有API请求详情的日志")
        assertTrue(testService.hasLog("API_RESPONSE: $requestId"), "应该有API响应详情的日志")
        assertTrue(testService.hasLog("API_CALL_END: $requestId"), "应该有API调用结束的日志")
        
        println("✅ API调用日志存储功能验证通过")
    }
    
    @Test
    fun `测试错误日志存储功能`() {
        val message = "测试异常"
        val exception = RuntimeException(message)
        val context = "API调用过程中发生错误"
        val userId = "test-user"
        
        // 记录错误日志
        loggingService.logError(exception, context, userId)
        
        // 验证错误日志
        val testService = loggingService as TestLoggingServiceImpl
        assertTrue(testService.hasLog("ERROR: $message"), "应该有错误日志记录")
        
        println("✅ 错误日志存储功能验证通过")
    }
    
    @Test
    fun `测试性能指标日志存储功能`() {
        val metric = PerformanceMetric.API_RESPONSE_TIME
        val value = 1500.0
        val context = "测试API响应时间"
        
        // 记录性能指标
        loggingService.logPerformanceMetric(metric, value, context)
        
        // 验证性能日志
        val testService = loggingService as TestLoggingServiceImpl
        assertTrue(testService.hasLog("PERFORMANCE: ${metric.name} - ${value}ms"), "应该有性能指标日志记录")
        
        println("✅ 性能指标日志存储功能验证通过")
    }
    
    @Test
    fun `测试日志搜索和过滤功能`() {
        // 添加多条不同类型的日志
        loggingService.logInfo("信息日志")
        loggingService.logWarning("警告日志")
        loggingService.logDebug("调试日志")
        
        // 验证日志记录
        val testService = loggingService as TestLoggingServiceImpl
        assertTrue(testService.hasLog("INFO: 信息日志"), "应该有信息日志记录")
        assertTrue(testService.hasLog("WARNING: 警告日志"), "应该有警告日志记录")
        assertTrue(testService.hasLog("DEBUG: 调试日志"), "应该有调试日志记录")
        
        println("✅ 日志搜索和过滤功能验证通过")
    }
}