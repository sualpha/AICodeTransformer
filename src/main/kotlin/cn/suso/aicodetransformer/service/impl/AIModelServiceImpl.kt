package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.constants.SecurityEvent
import cn.suso.aicodetransformer.constants.RequestStatusConstants
import cn.suso.aicodetransformer.model.*
import cn.suso.aicodetransformer.service.AIModelService
import cn.suso.aicodetransformer.service.CacheService
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.ErrorHandlingService
import cn.suso.aicodetransformer.service.LoggingService
import cn.suso.aicodetransformer.service.PerformanceMonitorService
import cn.suso.aicodetransformer.service.RateLimitService
import cn.suso.aicodetransformer.service.RequestListener
import cn.suso.aicodetransformer.util.TokenCounter
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import io.ktor.client.*
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
import kotlinx.serialization.encodeToString
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
            // 连接池配置 - 进一步优化，减少UI卡顿
            engine {
                maxConnectionsCount = 50   // 进一步减少连接数
                endpoint {
                    maxConnectionsPerRoute = 10  // 减少每个路由的连接数
                    pipelineMaxSize = 10
                    keepAliveTime = 30000  // 30秒保持连接
                    connectTimeout = 3000   // 3秒连接超时
                    connectAttempts = 3     // 减少连接尝试次数
                }
            }
            
            install(ContentNegotiation) {
                json(json)
            }
            
            install(Logging) {
                level = LogLevel.INFO
            }
            
            // 超时配置将在每次请求时动态设置
            install(HttpTimeout) {
                requestTimeoutMillis = 120000  // 默认2分钟请求超时
                connectTimeoutMillis = 10000   // 默认10秒连接超时
                socketTimeoutMillis = 120000   // 默认2分钟socket超时
            }
            
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 0)  // 禁用HTTP层重试，避免与ErrorHandlingService重试叠加
                exponentialDelay(base = 1.5, maxDelayMs = 10000)
                retryIf { _, response ->
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
/*        if (!rateLimitService.isAllowed(config, apiKey)) {
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
        }*/
        
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
            notifyListeners { it.onRequestCancelled(requestId) }
            val cancelResult = ExecutionResult.failure("请求已取消", ErrorType.UNKNOWN_ERROR)
            loggingService.logApiCallEnd(requestId, cancelResult, 0)
            cancelResult
            
        } catch (e: Exception) {
            activeRequests.remove(requestId)
            
            // 记录错误
            loggingService.logError(e, "API调用异常, requestId: $requestId, 模型: ${config.name}")
            
            // 使用ErrorHandlingService处理异常
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
    
    override fun getRequestStatus(requestId: String): RequestStatusConstants? {
        val job = activeRequests[requestId]
        return when {
            job == null -> null
            job.isCancelled -> RequestStatusConstants.CANCELLED
            job.isCompleted -> RequestStatusConstants.COMPLETED
            job.isActive -> RequestStatusConstants.RUNNING
            else -> RequestStatusConstants.PENDING
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
        return executeWithRetry(maxRetries = 3) {
            when (modelConfig.modelType) {
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
    }
    
    /**
     * 带重试机制的执行方法
     */
    private suspend fun executeWithRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 8000,
        multiplier: Double = 2.0,
        operation: suspend () -> ExecutionResult
    ): ExecutionResult {
        var lastException: Exception? = null
        var delay = initialDelayMs
        
        repeat(maxRetries + 1) { attempt ->
            try {
                val result = operation()
                
                // 如果成功或者是非网络错误，直接返回
                if (result.success || !isRetryableError(result)) {
                    return result
                }
                
                // 如果是最后一次尝试，返回结果
                if (attempt == maxRetries) {
                    return result
                }
                
                // 等待后重试
                logger.warn("API调用失败，${delay}ms后进行第${attempt + 1}次重试: ${result.errorMessage}")
                delay(delay)
                delay = (delay * multiplier).toLong().coerceAtMost(maxDelayMs)
                
            } catch (e: Exception) {
                lastException = e
                
                // 检查是否是可重试的异常
                if (!isRetryableException(e) || attempt == maxRetries) {
                    throw e
                }
                
                logger.warn("API调用异常，${delay}ms后进行第${attempt + 1}次重试: ${e.message}")
                delay(delay)
                delay = (delay * multiplier).toLong().coerceAtMost(maxDelayMs)
            }
        }
        
        // 如果所有重试都失败了，抛出最后的异常
        throw lastException ?: RuntimeException("所有重试都失败了")
    }
    
    /**
     * 判断是否是可重试的错误结果
     */
    private fun isRetryableError(result: ExecutionResult): Boolean {
        val errorMessage = result.errorMessage?.lowercase() ?: return false
        return errorMessage.contains("unexpected eof") ||
               errorMessage.contains("connection reset") ||
               errorMessage.contains("timeout") ||
               errorMessage.contains("网络连接被意外中断") ||
               errorMessage.contains("响应读取超时")
    }
    
    /**
     * 判断是否是可重试的异常
     */
    private fun isRetryableException(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: return false
        return message.contains("unexpected eof") ||
               message.contains("connection reset") ||
               message.contains("timeout") ||
               message.contains("connect timed out") ||
               message.contains("read timed out") ||
               exception is java.net.SocketTimeoutException ||
               exception is java.net.ConnectException ||
               exception is java.io.EOFException
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
        
        // 改进的响应体读取，添加EOF错误处理
        val responseBodyText = try {
            response.bodyAsText()
        } catch (e: Exception) {
            logger.error("读取Claude响应体失败", e)
            val errorMessage = when {
                e.message?.contains("unexpected EOF", ignoreCase = true) == true -> 
                    "网络连接被意外中断，请检查网络连接稳定性并重试"
                e.message?.contains("timeout", ignoreCase = true) == true -> 
                    "响应读取超时，请检查网络连接或增加超时时间"
                else -> "读取Claude API响应失败: ${e.message}"
            }
            return ExecutionResult.failure(errorMessage, ErrorType.NETWORK_ERROR)
        }
        
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
                val choice = responseBody.choices.firstOrNull()
                
                if (choice == null) {
                    return ExecutionResult.failure(
                        "API响应格式错误：choices数组为空", 
                        ErrorType.API_ERROR
                    )
                }
                
                // 检查finish_reason，如果是input_length_exceeded等错误状态
                val finishReason = choice.finishReason
                if (finishReason != null && finishReason != "stop") {
                    val errorMessage = when (finishReason) {
                        "length" -> "响应被截断：达到最大token限制"
                        "content_filter" -> "内容被过滤：违反了内容政策"
                        "input_length_exceeded", "input_length" -> "输入长度超限：请减少输入内容或分批处理"
                        "model_length" -> "模型长度限制：请使用支持更长输入的模型"
                        "function_call" -> "函数调用完成"
                        "tool_calls" -> "工具调用完成"
                        else -> "API响应异常：finish_reason=$finishReason，请检查输入内容或联系技术支持"
                    }
                    return ExecutionResult.failure(errorMessage, ErrorType.API_ERROR)
                }
                
                val message = choice.message
                if (message == null) {
                    return ExecutionResult.failure(
                        "API响应格式错误：message字段为null，finish_reason=$finishReason", 
                        ErrorType.API_ERROR
                    )
                }
                
                val content = message.content
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
                    "API响应解析失败: ${e.message}\nJSON input: ${responseBodyText.take(200)}...",
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
        @Suppress("UNUSED_PARAMETER") apiKey: String?
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
        @Suppress("UNUSED_PARAMETER") apiKey: String?
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