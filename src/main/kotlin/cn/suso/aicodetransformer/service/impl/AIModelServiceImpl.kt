package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.ErrorType
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.ModelType
import cn.suso.aicodetransformer.service.AIModelService
import cn.suso.aicodetransformer.service.CacheService
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.ErrorContext
import cn.suso.aicodetransformer.service.ErrorHandlingService
import cn.suso.aicodetransformer.service.ModelInfo
import cn.suso.aicodetransformer.service.LoggingService
import cn.suso.aicodetransformer.service.PerformanceMonitorService
import cn.suso.aicodetransformer.service.PerformanceTracker
import cn.suso.aicodetransformer.service.RateLimitService
import cn.suso.aicodetransformer.service.SecurityEvent
import cn.suso.aicodetransformer.service.UserAction
import cn.suso.aicodetransformer.service.RequestConfig
import cn.suso.aicodetransformer.service.RequestListener
import cn.suso.aicodetransformer.service.RequestStatus
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * AI模型服务实现类
 */
class AIModelServiceImpl : AIModelService {
    
    companion object {
        private val logger = Logger.getInstance(AIModelServiceImpl::class.java)
        fun getInstance(): AIModelService = service<AIModelService>()
    }
    
    private val configurationService: ConfigurationService = service()
    private val errorHandlingService: ErrorHandlingService = service()
    private val cacheService: CacheService = service()
    private val rateLimitService: RateLimitService = service()
    private val performanceMonitorService: PerformanceMonitorService = service()
    private val loggingService: LoggingService = service()
    private val httpClient: HttpClient
    private val requestIdGenerator = AtomicLong(0)
    private val activeRequests = mutableMapOf<String, Job>()
    private val pendingRequests = mutableMapOf<String, MutableList<CompletableDeferred<ExecutionResult>>>()
    private val listeners = CopyOnWriteArrayList<RequestListener>()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }
    
    init {
        httpClient = HttpClient(CIO) {
            // 连接池配置 - 优化版本
            engine {
                maxConnectionsCount = 200
                endpoint {
                    maxConnectionsPerRoute = 50
                    pipelineMaxSize = 30
                    keepAliveTime = 30000  // 增加到30秒
                    connectTimeout = 3000   // 减少到3秒
                    connectAttempts = 3
                }
            }
            
            install(ContentNegotiation) {
                json(json)
            }
            
            install(Logging) {
                level = LogLevel.INFO
            }
            
            // 超时配置将在每次请求时动态设置
            install(HttpTimeout)
            
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 0)  // 禁用HTTP层重试，避免与ErrorHandlingService重试叠加
                exponentialDelay(base = 1.5, maxDelayMs = 10000)
                retryIf { request, response ->
                    response.status.value in 500..599 || response.status.value == 429
                }
            }
        }
    }
    
    override suspend fun callModel(
        config: ModelConfiguration,
        prompt: String,
        apiKey: String
    ): ExecutionResult {
        val requestId = generateRequestId()
        
        // 记录API调用开始
        loggingService.logApiCallStart(requestId, config, prompt)
        
        // 开始性能跟踪
        val performanceTracker = performanceMonitorService.startTracking(requestId, config, prompt)
        
        // 检查限流
        if (!rateLimitService.isAllowed(config, apiKey)) {
            val nextAllowedTime = rateLimitService.getNextAllowedTime(config, apiKey)
            val waitTime = if (nextAllowedTime > 0) {
                (nextAllowedTime - System.currentTimeMillis()) / 1000
            } else 0
            
            val errorMessage = if (waitTime > 0) {
                "API调用频率超限，请等待 ${waitTime} 秒后重试"
            } else {
                "API调用频率超限，请稍后重试"
            }
            
            logger.warn("API调用被限流，requestId: $requestId, 模型: ${config.name}")
            loggingService.logSecurityEvent(
                SecurityEvent.RATE_LIMIT_EXCEEDED,
                "API调用频率超限: ${config.name}"
            )
            
            val rateLimitResult = ExecutionResult.failure(errorMessage, ErrorType.RATE_LIMIT_ERROR)
            performanceMonitorService.endTracking(performanceTracker, rateLimitResult)
            loggingService.logApiCallEnd(requestId, rateLimitResult, 0)
            
            return rateLimitResult
        }
        
        // 检查缓存
        val cacheKey = cacheService.generateCacheKey(config, prompt, config.temperature, config.maxTokens)
        val cachedResult = cacheService.getCachedResponse(cacheKey)
        if (cachedResult != null) {
            logger.debug("返回缓存结果，requestId: $requestId")
            loggingService.logInfo("返回缓存结果", "requestId: $requestId")
            notifyListeners { it.onRequestStarted(requestId, config, prompt) }
            
            performanceTracker.recordCacheHit()
            performanceMonitorService.endTracking(performanceTracker, cachedResult)
            loggingService.logApiCallEnd(requestId, cachedResult, 0)
            
            notifyListeners { it.onRequestCompleted(requestId, cachedResult) }
            return cachedResult
        }
        
        // 检查是否有相同请求正在处理（请求去重）
        val deferred = synchronized(pendingRequests) {
            if (pendingRequests.containsKey(cacheKey)) {
                logger.debug("发现重复请求，等待现有请求完成，requestId: $requestId")
                val deferredResult = CompletableDeferred<ExecutionResult>()
                pendingRequests[cacheKey]?.add(deferredResult)
                deferredResult
            } else {
                pendingRequests[cacheKey] = mutableListOf()
                null
            }
        }
        
        if (deferred != null) {
            return deferred.await()
        }
        
        return try {
            notifyListeners { it.onRequestStarted(requestId, config, prompt) }
            
            val startTime = System.currentTimeMillis()
             val job = coroutineScope {
                async {
                    executeModelCall(config, apiKey, prompt, RequestConfig())
                }
            }
            
            activeRequests[requestId] = job
            
            val result = job.await()
            activeRequests.remove(requestId)
            
            val responseTime = System.currentTimeMillis() - startTime
            performanceMonitorService.endTracking(performanceTracker, result)
            loggingService.logApiCallEnd(requestId, result, responseTime)
            
            // 记录请求并缓存成功的结果
            rateLimitService.recordRequest(config, apiKey)
            if (result.success) {
                val smartTtl = cacheService.getSmartTtl(prompt)
                cacheService.cacheResponse(cacheKey, result, smartTtl)
                logger.debug("缓存结果使用智能TTL: ${smartTtl}秒, requestId: $requestId")
                loggingService.logInfo("API调用成功并缓存响应", "requestId: $requestId, 模型: ${config.name}")
            } else {
                loggingService.logWarning("API调用失败", "requestId: $requestId, 错误: ${result.errorMessage}")
            }
            
            // 通知所有等待的重复请求
            synchronized(pendingRequests) {
                pendingRequests[cacheKey]?.forEach { deferred ->
                    deferred.complete(result)
                }
                pendingRequests.remove(cacheKey)
            }
            
            notifyListeners { 
                if (result.success) {
                    it.onRequestCompleted(requestId, result)
                } else {
                    it.onRequestFailed(requestId, result.errorMessage ?: "未知错误")
                }
            }
            
            result
            
        } catch (e: CancellationException) {
            activeRequests.remove(requestId)
            loggingService.logInfo("API调用被取消", "requestId: $requestId")
            notifyListeners { it.onRequestCancelled(requestId) }
            val cancelResult = ExecutionResult.failure("请求已取消", ErrorType.UNKNOWN_ERROR)
            loggingService.logApiCallEnd(requestId, cancelResult, 0)
            cancelResult
            
        } catch (e: Exception) {
            activeRequests.remove(requestId)
            
            // 记录错误
            loggingService.logError(e, "API调用异常, requestId: $requestId, 模型: ${config.name}")
            
            // 使用ErrorHandlingService处理异常
            val errorContext = ErrorContext(
                operation = "AI模型调用",
                component = "AIModelService",
                additionalInfo = mapOf(
                    "modelName" to config.name,
                    "modelType" to config.modelType.name,
                    "requestId" to requestId
                )
            )
            
            val handlingResult = errorHandlingService.handleModelError(e, config.name)
            
            val errorResult = ExecutionResult.failure(
                handlingResult.userMessage,
                when (e) {
                    is HttpRequestTimeoutException -> ErrorType.TIMEOUT_ERROR
            is ClientRequestException -> ErrorType.API_ERROR
            is ServerResponseException -> ErrorType.API_ERROR
            else -> ErrorType.NETWORK_ERROR
                }
            )
            
            // 通知所有等待的重复请求（错误情况）
            synchronized(pendingRequests) {
                pendingRequests[cacheKey]?.forEach { deferred ->
                    deferred.complete(errorResult)
                }
                pendingRequests.remove(cacheKey)
            }
            
            loggingService.logApiCallEnd(requestId, errorResult, 0)
            notifyListeners { it.onRequestFailed(requestId, handlingResult.userMessage) }
            errorResult
        }
    }
    
    override suspend fun testConnection(
        config: ModelConfiguration,
        apiKey: String
    ): ExecutionResult {
        return try {
            when (config.modelType) {
                ModelType.OPENAI_COMPATIBLE -> {
                 testOpenAICompatibleConnection(config, apiKey)
             }
             ModelType.CLAUDE -> {
                 testClaudeConnection(config, apiKey)
             }
             ModelType.LOCAL -> {
                 testLocalConnection(config)
             }
            }
        } catch (e: Exception) {
            // 使用ErrorHandlingService处理异常
            val errorContext = ErrorContext(
                operation = "连接测试",
                component = "AIModelService",
                additionalInfo = mapOf(
                    "modelName" to config.name,
                    "modelType" to config.modelType.name,
                    "baseUrl" to config.apiBaseUrl
                )
            )
            
            val handlingResult = errorHandlingService.handleNetworkError(e, config.apiBaseUrl)
            
            ExecutionResult.failure(
                handlingResult.userMessage,
                when (e) {
                    is HttpRequestTimeoutException -> ErrorType.TIMEOUT_ERROR
            is ClientRequestException -> ErrorType.API_ERROR
            is ServerResponseException -> ErrorType.API_ERROR
            else -> ErrorType.NETWORK_ERROR
                }
            )
        }
    }
    
    override suspend fun getModelInfo(
        config: ModelConfiguration,
        apiKey: String
    ): ModelInfo? {
        return try {
            when (config.modelType) {
                ModelType.OPENAI_COMPATIBLE -> {
                 getOpenAIModelInfo(config, apiKey)
             }
             ModelType.CLAUDE -> {
                 getClaudeModelInfo(config, apiKey)
             }
             ModelType.LOCAL -> {
                 getLocalModelInfo(config)
             }
            }
        } catch (e: Exception) {
            // 使用ErrorHandlingService处理异常
            val errorContext = ErrorContext(
                operation = "获取模型信息",
                component = "AIModelService",
                additionalInfo = mapOf(
                    "modelName" to config.name,
                    "modelType" to config.modelType.name
                )
            )
            
            errorHandlingService.handleModelError(e, config.name)
            null
        }
    }
    
    override fun cancelRequest(requestId: String): Unit {
        val job = activeRequests[requestId]
        if (job != null && job.isActive) {
            job.cancel()
            activeRequests.remove(requestId)
            notifyListeners { it.onRequestCancelled(requestId) }
        }
    }
    
    override fun getSupportedModelTypes(): List<String> {
        return listOf(
            "OpenAI Compatible",
            "Claude",
            "Local Model"
        )
    }
    
    override fun validateApiKeyFormat(apiKey: String, modelType: String): Boolean {
        return when (modelType.lowercase()) {
            "openai", "openai compatible" -> {
                // OpenAI API key format: sk-...
                apiKey.startsWith("sk-") && apiKey.length >= 20
            }
            "claude" -> {
                // Claude API key format: sk-ant-...
                apiKey.startsWith("sk-ant-") && apiKey.length >= 20
            }
            "local", "local model" -> {
                // Local models may not require API key or have different formats
                true
            }
            else -> false
        }
    }
    
    override fun estimateTokens(text: String): Int {
        // Simple estimation: roughly 4 characters per token for English text
        // This is a rough approximation, real tokenization would be more accurate
        return (text.length / 4).coerceAtLeast(1)
    }
    
    override fun getRequestStatus(requestId: String): RequestStatus? {
        val job = activeRequests[requestId]
        return when {
            job == null -> null
            job.isCancelled -> RequestStatus.CANCELLED
            job.isCompleted -> RequestStatus.COMPLETED
            job.isActive -> RequestStatus.RUNNING
            else -> RequestStatus.PENDING
        }
    }
    
    override fun addRequestListener(listener: RequestListener) {
        listeners.add(listener)
    }
    
    override fun removeRequestListener(listener: RequestListener) {
        listeners.remove(listener)
    }
    
    /**
     * 执行模型调用
     */
    private suspend fun executeModelCall(
        modelConfig: ModelConfiguration,
        apiKey: String,
        prompt: String,
        config: RequestConfig
    ): ExecutionResult {
        return when (modelConfig.modelType) {
            ModelType.OPENAI_COMPATIBLE -> {
                callOpenAICompatibleModel(modelConfig, apiKey, prompt, config)
            }
            ModelType.CLAUDE -> {
                callClaudeModel(modelConfig, apiKey, prompt, config)
            }
            ModelType.LOCAL -> {
                callLocalModel(modelConfig, prompt, config)
            }
        }
    }
    
    /**
     * 调用OpenAI兼容的模型
     */
    private suspend fun callOpenAICompatibleModel(
        modelConfig: ModelConfiguration,
        apiKey: String,
        prompt: String,
        config: RequestConfig
    ): ExecutionResult {
        val requestBody = OpenAIRequest(
            model = modelConfig.modelName,
            messages = listOf(
                OpenAIMessage(role = "user", content = prompt)
            ),
            maxTokens = modelConfig.maxTokens,
            temperature = modelConfig.temperature,
            stream = false
        )
        
        val requestBodyJson = json.encodeToString(requestBody)
        val requestHeaders = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
        
        // 记录详细的请求参数
        loggingService.logApiRequestDetails(
            requestId = config.requestId,
            config = modelConfig,
            requestBody = requestBodyJson,
            headers = requestHeaders
        )
        
        val startTime = System.currentTimeMillis()
        
        val response = httpClient.post("${modelConfig.apiBaseUrl}/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            header("Content-Type", "application/json")
            setBody(requestBody)
            
            // 动态设置超时配置
            timeout {
                requestTimeoutMillis = (modelConfig.readTimeoutSeconds * 1000).toLong()
                connectTimeoutMillis = (modelConfig.connectTimeoutSeconds * 1000).toLong()
                socketTimeoutMillis = (modelConfig.readTimeoutSeconds * 1000).toLong()
            }
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        val responseBodyText = response.bodyAsText()
        val responseHeaders = response.headers.entries().associate { it.key to it.value.joinToString(", ") }
        
        // 记录详细的响应参数
        loggingService.logApiResponseDetails(
            requestId = config.requestId,
            statusCode = response.status.value,
            responseTimeMs = responseTime,
            responseBody = responseBodyText,
            responseHeaders = responseHeaders
        )
        
        if (response.status.isSuccess()) {
            try {
                val responseBody = json.decodeFromString<OpenAIResponse>(responseBodyText)
                val message = responseBody.choices.firstOrNull()?.message
                val content = message?.content
                
                if (content.isNullOrBlank()) {
                    return ExecutionResult.failure(
                        "API响应格式错误：消息内容为空或缺失content字段", 
                        ErrorType.API_ERROR
                    )
                }
                
                return ExecutionResult.success(content.trim())
            } catch (e: Exception) {
                logger.error("解析响应失败", e)
                return ExecutionResult.failure(
                    "API响应解析失败: ${e.message}",
                    ErrorType.API_ERROR
                )
            }
        } else {
            return ExecutionResult.failure(
                "API调用失败: ${response.status.value} - $responseBodyText",
                ErrorType.API_ERROR
            )
        }
    }
    
    /**
     * 调用Claude模型
     */
    private suspend fun callClaudeModel(
        modelConfig: ModelConfiguration,
        apiKey: String,
        prompt: String,
        config: RequestConfig
    ): ExecutionResult {
        val requestBody = ClaudeRequest(
            model = modelConfig.modelName,
            maxTokens = modelConfig.maxTokens,
            messages = listOf(
                ClaudeMessage(role = "user", content = prompt)
            )
        )
        
        // 记录请求参数
        val requestBodyJson = json.encodeToString(requestBody)
        val requestHeaders = mapOf(
            "x-api-key" to "[MASKED]",
            "Content-Type" to "application/json",
            "anthropic-version" to "2023-06-01"
        )
        
        loggingService.logApiRequestDetails(
            requestId = config.requestId,
            config = modelConfig,
            requestBody = requestBodyJson,
            headers = requestHeaders
        )
        
        val startTime = System.currentTimeMillis()
        
        val response = httpClient.post("${modelConfig.apiBaseUrl}/messages") {
            header("x-api-key", apiKey)
            header("Content-Type", "application/json")
            header("anthropic-version", "2023-06-01")
            setBody(requestBody)
            
            // 动态设置超时配置
            timeout {
                requestTimeoutMillis = (modelConfig.readTimeoutSeconds * 1000).toLong()
                connectTimeoutMillis = (modelConfig.connectTimeoutSeconds * 1000).toLong()
                socketTimeoutMillis = (modelConfig.readTimeoutSeconds * 1000).toLong()
            }
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        val responseBodyText = response.bodyAsText()
        val responseHeaders = response.headers.entries().associate { it.key to it.value.joinToString(", ") }
        
        // 记录响应参数
        loggingService.logApiResponseDetails(
            requestId = config.requestId,
            statusCode = response.status.value,
            responseTimeMs = responseTime,
            responseBody = responseBodyText,
            responseHeaders = responseHeaders
        )
        
        if (response.status.isSuccess()) {
            try {
                val responseBody = json.decodeFromString<ClaudeResponse>(responseBodyText)
                val content = responseBody.content.firstOrNull()?.text
                    ?: return ExecutionResult.failure("响应内容为空", ErrorType.API_ERROR)
                
                return ExecutionResult.success(content.trim())
            } catch (e: Exception) {
                return ExecutionResult.failure(
                    "解析Claude响应失败: ${e.message}",
                    ErrorType.API_ERROR
                )
            }
        } else {
            return ExecutionResult.failure(
                "Claude API调用失败: ${response.status.value} - $responseBodyText",
                ErrorType.API_ERROR
            )
        }
    }
    
    /**
     * 调用本地模型
     */
    private suspend fun callLocalModel(
        modelConfig: ModelConfiguration,
        prompt: String,
        config: RequestConfig
    ): ExecutionResult {
        // 本地模型调用实现（如Ollama等）
        val requestBody = LocalModelRequest(
            model = modelConfig.modelName,
            prompt = prompt,
            stream = false,
            options = LocalModelOptions(
                temperature = modelConfig.temperature,
                numPredict = modelConfig.maxTokens
            )
        )
        
        val requestBodyJson = json.encodeToString(requestBody)
        val requestHeaders = mapOf(
            "Content-Type" to "application/json"
        )
        
        // 记录详细的请求参数
        loggingService.logApiRequestDetails(
            requestId = config.requestId,
            config = modelConfig,
            requestBody = requestBodyJson,
            headers = requestHeaders
        )
        
        val startTime = System.currentTimeMillis()
        
        val response = httpClient.post("${modelConfig.apiBaseUrl}") {
            header("Content-Type", "application/json")
            setBody(requestBody)
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        val responseBodyText = response.bodyAsText()
        val responseHeaders = response.headers.entries().associate { it.key to it.value.joinToString(", ") }
        
        // 记录详细的响应参数
        loggingService.logApiResponseDetails(
            requestId = config.requestId,
            statusCode = response.status.value,
            responseTimeMs = responseTime,
            responseBody = responseBodyText,
            responseHeaders = responseHeaders
        )
        
        if (response.status.isSuccess()) {
            try {
                // 处理可能包含多个JSON对象的响应
                val content = parseLocalModelResponse(responseBodyText)
                
                if (content.isEmpty()) {
                    return ExecutionResult.failure("响应内容为空", ErrorType.API_ERROR)
                }
                
                return ExecutionResult.success(content.trim())
            } catch (e: Exception) {
                logger.error("解析本地模型响应失败", e)
                return ExecutionResult.failure(
                    "本地模型响应解析失败: ${e.message}\nJSON input: ${responseBodyText.take(200)}...",
                    ErrorType.API_ERROR
                )
            }
        } else {
            return ExecutionResult.failure(
                "本地模型调用失败: ${response.status.value} - $responseBodyText",
                ErrorType.API_ERROR
            )
        }
    }
    
    /**
      * 解析本地模型响应，处理可能包含多个JSON对象的情况
      */
     private fun parseLocalModelResponse(responseBodyText: String): String {
         return parseLocalModelResponseInternal(responseBodyText)
     }
     
     /**
      * 实际的解析逻辑
      */
     private fun parseLocalModelResponseInternal(responseBodyText: String): String {
        val responses = mutableListOf<String>()
        
        // 按行分割响应，每行可能是一个JSON对象
        val lines = responseBodyText.trim().split("\n")
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            
            try {
                // 尝试解析每一行作为JSON对象
                val responseObj = json.decodeFromString<LocalModelResponse>(trimmedLine)
                if (responseObj.response.isNotEmpty()) {
                    responses.add(responseObj.response)
                }
            } catch (e: Exception) {
                // 如果单行解析失败，尝试解析整个响应体
                if (lines.size == 1) {
                    val responseObj = json.decodeFromString<LocalModelResponse>(responseBodyText)
                    return responseObj.response
                }
                // 忽略无法解析的行
                logger.debug("跳过无法解析的响应行: $trimmedLine")
            }
        }
        
        return responses.joinToString("")
    }
    
    /**
     * 测试OpenAI兼容连接
     */
    private suspend fun testOpenAICompatibleConnection(
        modelConfig: ModelConfiguration,
        apiKey: String
    ): ExecutionResult {
        val response = httpClient.get("${modelConfig.apiBaseUrl}/models") {
            header("Authorization", "Bearer $apiKey")
            
            // 动态设置超时配置
            timeout {
                requestTimeoutMillis = (modelConfig.readTimeoutSeconds * 1000).toLong()
                connectTimeoutMillis = (modelConfig.connectTimeoutSeconds * 1000).toLong()
                socketTimeoutMillis = (modelConfig.readTimeoutSeconds * 1000).toLong()
            }
        }
        
        return if (response.status.isSuccess()) {
            ExecutionResult.success("连接成功")
        } else {
            ExecutionResult.failure(
                "连接失败: ${response.status.value}",
                ErrorType.API_ERROR
            )
        }
    }
    
    /**
     * 测试Claude连接
     */
    private suspend fun testClaudeConnection(
        modelConfig: ModelConfiguration,
        apiKey: String
    ): ExecutionResult {
        val testRequest = ClaudeRequest(
            model = modelConfig.modelName,
            maxTokens = 10,
            messages = listOf(
                ClaudeMessage(role = "user", content = "Hello")
            )
        )
        
        val response = httpClient.post("${modelConfig.apiBaseUrl}/messages") {
            header("x-api-key", apiKey)
            header("Content-Type", "application/json")
            header("anthropic-version", "2023-06-01")
            setBody(testRequest)
            
            // 动态设置超时配置
            timeout {
                requestTimeoutMillis = (modelConfig.readTimeoutSeconds * 1000).toLong()
                connectTimeoutMillis = (modelConfig.connectTimeoutSeconds * 1000).toLong()
                socketTimeoutMillis = (modelConfig.readTimeoutSeconds * 1000).toLong()
            }
        }
        
        return if (response.status.isSuccess()) {
            ExecutionResult.success("连接成功")
        } else {
            ExecutionResult.failure(
                "连接失败: ${response.status.value}",
                ErrorType.API_ERROR
            )
        }
    }
    
    /**
     * 测试本地连接
     */
    private suspend fun testLocalConnection(modelConfig: ModelConfiguration): ExecutionResult {
        val response = httpClient.get("${modelConfig.apiBaseUrl}/api/tags")
        
        return if (response.status.isSuccess()) {
            ExecutionResult.success("连接成功")
        } else {
            ExecutionResult.failure(
                "连接失败: ${response.status.value}",
                ErrorType.API_ERROR
            )
        }
    }
    
    /**
     * 获取OpenAI模型信息
     */
    private suspend fun getOpenAIModelInfo(
        modelConfig: ModelConfiguration,
        apiKey: String?
    ): ModelInfo {
        return ModelInfo(
            name = modelConfig.name,
            version = null,
            maxContextLength = 4096, // 默认值，实际应从API获取
            capabilities = listOf("chat", "completion"),
            description = "OpenAI Compatible"
        )
    }
    
    /**
     * 获取Claude模型信息
     */
    private suspend fun getClaudeModelInfo(
        modelConfig: ModelConfiguration,
        apiKey: String?
    ): ModelInfo {
        return ModelInfo(
            name = modelConfig.name,
            version = null,
            maxContextLength = 100000, // Claude的上下文长度
            capabilities = listOf("chat", "analysis"),
            description = "Anthropic Claude"
        )
    }
    
    /**
     * 获取本地模型信息
     */
    private suspend fun getLocalModelInfo(modelConfig: ModelConfiguration): ModelInfo {
        return ModelInfo(
            name = modelConfig.name,
            version = null,
            maxContextLength = 2048, // 默认值
            capabilities = listOf("chat", "completion"),
            description = "Local"
        )
    }
    
    /**
     * 生成请求ID
     */
    private fun generateRequestId(): String {
        return "req-${requestIdGenerator.incrementAndGet()}-${System.currentTimeMillis()}"
    }
    
    /**
     * 通知监听器
     */
    private fun notifyListeners(action: (RequestListener) -> Unit) {
        listeners.forEach { listener ->
            try {
                action(listener)
            } catch (e: Exception) {
                logger.error("通知监听器失败", e)
            }
        }
    }
    
    /**
      * 清理资源
      */
    fun dispose() {
        activeRequests.values.forEach { it.cancel() }
        activeRequests.clear()
        httpClient.close()
    }
}

// OpenAI API数据类
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
    val stream: Boolean = false
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String? = null
)

@Serializable
data class OpenAIResponse(
    val choices: List<OpenAIChoice>
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIMessage
)

// Claude API数据类
@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<ClaudeMessage>
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
data class ClaudeResponse(
    val content: List<ClaudeContent>
)

@Serializable
data class ClaudeContent(
    val text: String
)

// 本地模型API数据类
@Serializable
data class LocalModelRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: LocalModelOptions
)

@Serializable
data class LocalModelOptions(
    val temperature: Double,
    @SerialName("num_predict") val numPredict: Int
)

@Serializable
data class LocalModelResponse(
    val response: String
)