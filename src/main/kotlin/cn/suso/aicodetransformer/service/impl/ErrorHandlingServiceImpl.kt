package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.service.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeoutException
import kotlin.math.min
import kotlin.math.pow

/**
 * 错误处理服务实现类
 */
class ErrorHandlingServiceImpl : ErrorHandlingService {
    
    companion object {
        private val logger = Logger.getInstance(ErrorHandlingServiceImpl::class.java)
        
        fun getInstance(): ErrorHandlingService = service<ErrorHandlingService>()
    }
    
    private val statusService: StatusService by lazy { service<StatusService>() }
    private val listeners = CopyOnWriteArrayList<ErrorListener>()
    
    override fun handleException(
        exception: Throwable,
        context: ErrorContext,
        project: Project?
    ): ErrorHandlingResult {
        val userMessage = getUserFriendlyMessage(exception, context)
        val suggestions = getSuggestions(exception, context)
        
        val result = ErrorHandlingResult(
            handled = true,
            userMessage = userMessage,
            suggestions = suggestions,
            shouldRetry = isRetryable(exception),
            shouldShowNotification = true,
            logLevel = getErrorSeverity(exception)
        )
        
        // 记录错误
        logError(exception, context, result.logLevel)
        
        // 显示通知
        if (result.shouldShowNotification) {
            showErrorNotification(result, project)
        }
        
        // 通知监听器
        notifyErrorListeners(exception, context, result)
        
        return result
    }
    
    override fun handleModelError(
        exception: Throwable,
        modelName: String?,
        project: Project?
    ): ErrorHandlingResult {
        val context = ErrorContext(
            operation = "AI模型调用",
            component = "AIModelService",
            additionalInfo = mapOf("modelName" to (modelName ?: "未知"))
        )
        
        return handleException(exception, context, project)
    }
    
    override fun handleNetworkError(
        exception: Throwable,
        url: String?,
        project: Project?
    ): ErrorHandlingResult {
        val context = ErrorContext(
            operation = "网络请求",
            component = "NetworkClient",
            additionalInfo = mapOf("url" to (url ?: "未知"))
        )
        
        return handleException(exception, context, project)
    }
    
    override fun handleConfigurationError(
        exception: Throwable,
        configType: String,
        project: Project?
    ): ErrorHandlingResult {
        val context = ErrorContext(
            operation = "配置加载",
            component = "ConfigurationService",
            additionalInfo = mapOf("configType" to configType)
        )
        
        return handleException(exception, context, project)
    }
    
    override fun handleCodeReplacementError(
        exception: Throwable,
        originalText: String?,
        newText: String?,
        project: Project?
    ): ErrorHandlingResult {
        val context = ErrorContext(
            operation = "代码替换",
            component = "CodeReplacementService",
            additionalInfo = mapOf(
                "originalTextLength" to (originalText?.length ?: 0).toString(),
                "newTextLength" to (newText?.length ?: 0).toString()
            )
        )
        
        return handleException(exception, context, project)
    }
    
    override fun <T> executeWithRetry(
        operation: () -> T,
        retryConfig: RetryConfig,
        context: ErrorContext
    ): RetryResult<T> {
        val startTime = System.currentTimeMillis()
        var lastException: Throwable? = null
        var attempt = 0
        
        while (attempt < retryConfig.maxAttempts) {
            attempt++
            
            try {
                // 通知监听器重试开始
                if (attempt > 1) {
                    notifyRetryStarted(context, attempt)
                }
                
                val result = operation()
                
                // 成功执行
                if (attempt > 1) {
                    notifyRetrySucceeded(context, attempt)
                }
                
                return RetryResult(
                    success = true,
                    result = result,
                    attempts = attempt,
                    totalDuration = System.currentTimeMillis() - startTime
                )
                
            } catch (e: Throwable) {
                lastException = e
                
                // 检查是否可重试
                if (!isRetryableException(e, retryConfig) || attempt >= retryConfig.maxAttempts) {
                    break
                }
                
                // 计算延迟时间
                val delay = calculateDelay(attempt, retryConfig)
                
                try {
                    Thread.sleep(delay)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        
        // 重试失败
        notifyRetryFailed(context, lastException!!, attempt)
        
        return RetryResult(
            success = false,
            exception = lastException,
            attempts = attempt,
            totalDuration = System.currentTimeMillis() - startTime
        )
    }
    
    override fun <T> executeWithRetryAsync(
        operation: () -> T,
        retryConfig: RetryConfig,
        context: ErrorContext,
        callback: (RetryResult<T>) -> Unit
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = executeWithRetry(operation, retryConfig, context)
            ApplicationManager.getApplication().invokeLater {
                callback(result)
            }
        }
    }
    
    override fun getUserFriendlyMessage(exception: Throwable, context: ErrorContext): String {
        return when (exception) {
            is SocketTimeoutException -> "网络连接超时，请检查网络连接或稍后重试"
            is ConnectException -> "无法连接到服务器，请检查网络连接和服务器状态"
            is UnknownHostException -> "无法解析主机名，请检查网络连接和URL配置"
            is TimeoutException -> "操作超时，请稍后重试"
            is SecurityException -> "权限不足，请检查相关权限设置"
            is IllegalArgumentException -> "参数错误：${exception.message ?: "请检查输入参数"}"
            is IllegalStateException -> "状态错误：${exception.message ?: "请检查当前操作状态"}"
            is NullPointerException -> "数据异常，请重试或联系技术支持"
            else -> {
                val message = exception.message
                when {
                    message?.contains("API key", ignoreCase = true) == true -> "API密钥配置错误，请检查API密钥设置"
                    message?.contains("quota", ignoreCase = true) == true -> "API配额不足，请检查账户余额或升级套餐"
                    message?.contains("rate limit", ignoreCase = true) == true -> "请求频率过高，请稍后重试"
                    message?.contains("unauthorized", ignoreCase = true) == true -> "认证失败，请检查API密钥和权限"
                    message?.contains("forbidden", ignoreCase = true) == true -> "访问被拒绝，请检查权限设置"
                    message?.contains("not found", ignoreCase = true) == true -> "资源不存在，请检查配置"
                    message?.contains("bad request", ignoreCase = true) == true -> "请求格式错误，请检查输入参数"
                    message?.contains("server error", ignoreCase = true) == true -> "服务器错误，请稍后重试"
                    !message.isNullOrBlank() -> "操作失败：$message"
                    else -> "操作失败，请重试或联系技术支持"
                }
            }
        }
    }
    
    override fun getSuggestions(exception: Throwable, context: ErrorContext): List<ErrorSuggestion> {
        val suggestions = mutableListOf<ErrorSuggestion>()
        
        when (exception) {
            is SocketTimeoutException, is ConnectException -> {
                suggestions.add(
                    ErrorSuggestion(
                        title = "检查网络连接",
                        description = "确保网络连接正常，可以访问外部服务"
                    )
                )
                suggestions.add(
                    ErrorSuggestion(
                        title = "检查代理设置",
                        description = "如果使用代理，请确保代理配置正确"
                    )
                )
            }
            
            is UnknownHostException -> {
                suggestions.add(
                    ErrorSuggestion(
                        title = "检查URL配置",
                        description = "确保API端点URL配置正确"
                    )
                )
                suggestions.add(
                    ErrorSuggestion(
                        title = "检查DNS设置",
                        description = "确保DNS解析正常工作"
                    )
                )
            }
            
            is SecurityException -> {
                suggestions.add(
                    ErrorSuggestion(
                        title = "检查权限设置",
                        description = "确保应用具有必要的权限"
                    )
                )
            }
        }
        
        // 根据错误消息添加特定建议
        val message = exception.message?.lowercase() ?: ""
        when {
            "api key" in message -> {
                suggestions.add(
                    ErrorSuggestion(
                        title = "配置API密钥",
                        description = "请在设置中配置正确的API密钥",
                        actionText = "打开设置"
                    )
                )
            }
            
            "quota" in message || "billing" in message -> {
                suggestions.add(
                    ErrorSuggestion(
                        title = "检查账户余额",
                        description = "请检查API账户余额或升级套餐"
                    )
                )
            }
            
            "rate limit" in message -> {
                suggestions.add(
                    ErrorSuggestion(
                        title = "降低请求频率",
                        description = "请稍等片刻后再试，或联系服务提供商提高限额"
                    )
                )
            }
        }
        
        // 通用建议
        if (isRetryable(exception)) {
            suggestions.add(
                ErrorSuggestion(
                    title = "重试操作",
                    description = "这是一个临时错误，稍后重试可能会成功"
                )
            )
        }
        
        return suggestions
    }
    
    override fun logError(exception: Throwable, context: ErrorContext, severity: ErrorSeverity) {
        val logMessage = "[${context.component}] ${context.operation} 失败: ${exception.message}"
        
        when (severity) {
            ErrorSeverity.DEBUG -> logger.debug(logMessage, exception)
            ErrorSeverity.INFO -> logger.info(logMessage)
            ErrorSeverity.WARNING -> logger.warn(logMessage, exception)
            ErrorSeverity.ERROR -> logger.error(logMessage, exception)
            ErrorSeverity.FATAL -> logger.error("FATAL: $logMessage", exception)
        }
    }
    
    override fun addErrorListener(listener: ErrorListener) {
        listeners.add(listener)
    }
    
    override fun removeErrorListener(listener: ErrorListener) {
        listeners.remove(listener)
    }
    
    /**
     * 分类错误类型
     */
    private fun classifyError(exception: Throwable): ErrorType {
        return when (exception) {
            is SocketTimeoutException, is ConnectException, is UnknownHostException -> ErrorType.NETWORK
            is SecurityException -> ErrorType.PERMISSION
            is IllegalArgumentException, is IllegalStateException -> ErrorType.VALIDATION
            else -> {
                val message = exception.message?.lowercase() ?: ""
                when {
                    "api key" in message || "unauthorized" in message -> ErrorType.AUTHENTICATION
                    "config" in message -> ErrorType.CONFIGURATION
                    "model" in message -> ErrorType.MODEL
                    else -> ErrorType.UNKNOWN
                }
            }
        }
    }
    
    /**
     * 判断异常是否可重试
     */
    private fun isRetryable(exception: Throwable): Boolean {
        return when (exception) {
            is SocketTimeoutException, is ConnectException, is TimeoutException -> true
            else -> {
                val message = exception.message?.lowercase() ?: ""
                "rate limit" in message || "server error" in message || "timeout" in message
            }
        }
    }
    
    /**
     * 判断异常是否在重试配置中
     */
    private fun isRetryableException(exception: Throwable, retryConfig: RetryConfig): Boolean {
        return retryConfig.retryableExceptions.any { it.isInstance(exception) } || isRetryable(exception)
    }
    
    /**
     * 获取错误严重程度
     */
    private fun getErrorSeverity(exception: Throwable): ErrorSeverity {
        return when (exception) {
            is SocketTimeoutException, is ConnectException -> ErrorSeverity.WARNING
            is SecurityException -> ErrorSeverity.ERROR
            is NullPointerException -> ErrorSeverity.FATAL
            else -> ErrorSeverity.ERROR
        }
    }
    
    /**
     * 计算重试延迟
     */
    private fun calculateDelay(attempt: Int, retryConfig: RetryConfig): Long {
        val delay = (retryConfig.initialDelayMs * retryConfig.backoffMultiplier.pow(attempt - 1)).toLong()
        return min(delay, retryConfig.maxDelayMs)
    }
    
    /**
     * 显示错误通知
     */
    private fun showErrorNotification(result: ErrorHandlingResult, project: Project?) {
        val actions = result.suggestions.mapNotNull { suggestion ->
            if (suggestion.action != null && suggestion.actionText != null) {
                NotificationAction(suggestion.actionText, suggestion.action)
            } else null
        }
        
        when (result.logLevel) {
            ErrorSeverity.WARNING -> {
                statusService.showWarningNotification("操作警告", result.userMessage, project)
            }
            ErrorSeverity.ERROR, ErrorSeverity.FATAL -> {
                statusService.showErrorNotification("操作失败", result.userMessage, project, actions)
            }
            else -> {
                statusService.showInfoNotification("提示", result.userMessage, project)
            }
        }
    }
    
    /**
     * 通知错误监听器
     */
    private fun notifyErrorListeners(
        exception: Throwable,
        context: ErrorContext,
        result: ErrorHandlingResult
    ) {
        listeners.forEach { listener ->
            try {
                listener.onErrorOccurred(exception, context, result)
            } catch (e: Exception) {
                logger.error("通知错误监听器失败", e)
            }
        }
    }
    
    /**
     * 通知重试开始
     */
    private fun notifyRetryStarted(context: ErrorContext, attempt: Int) {
        listeners.forEach { listener ->
            try {
                listener.onRetryStarted(context, attempt)
            } catch (e: Exception) {
                logger.error("通知重试开始失败", e)
            }
        }
    }
    
    /**
     * 通知重试成功
     */
    private fun notifyRetrySucceeded(context: ErrorContext, attempts: Int) {
        listeners.forEach { listener ->
            try {
                listener.onRetrySucceeded(context, attempts)
            } catch (e: Exception) {
                logger.error("通知重试成功失败", e)
            }
        }
    }
    
    /**
     * 通知重试失败
     */
    private fun notifyRetryFailed(context: ErrorContext, finalException: Throwable, attempts: Int) {
        listeners.forEach { listener ->
            try {
                listener.onRetryFailed(context, finalException, attempts)
            } catch (e: Exception) {
                logger.error("通知重试失败失败", e)
            }
        }
    }
}