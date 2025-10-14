package cn.suso.aicodetransformer.model

import cn.suso.aicodetransformer.constants.ErrorSeverity
import kotlinx.serialization.Serializable

/**
 * 错误上下文
 */
@Serializable
data class ErrorContext(
    val operation: String,
    val component: String,
    val additionalInfo: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 错误处理结果
 */
data class ErrorHandlingResult(
    val handled: Boolean,
    val userMessage: String,
    val suggestions: List<ErrorSuggestion> = emptyList(),
    val shouldRetry: Boolean = false,
    val shouldShowNotification: Boolean = true,
    val logLevel: ErrorSeverity = ErrorSeverity.ERROR
)

/**
 * 重试配置
 */
data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 10000,
    val backoffMultiplier: Double = 2.0,
    val retryableExceptions: Set<Class<out Throwable>> = setOf(
        java.net.SocketTimeoutException::class.java,
        java.net.ConnectException::class.java,
        java.io.IOException::class.java
    )
)

/**
 * 重试结果
 */
data class RetryResult<T>(
    val success: Boolean,
    val result: T? = null,
    val exception: Throwable? = null,
    val attempts: Int = 0,
    val totalDuration: Long = 0
)

/**
 * 错误建议
 */
data class ErrorSuggestion(
    val title: String,
    val description: String,
    val action: (() -> Unit)? = null,
    val actionText: String? = null
)