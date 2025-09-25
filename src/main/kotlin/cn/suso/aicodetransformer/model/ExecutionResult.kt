package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

/**
 * AI执行结果数据类
 * 用于封装AI模型调用的结果信息
 */
@Serializable
data class ExecutionResult(
    /** 是否执行成功 */
    val success: Boolean,
    
    /** AI返回的内容 */
    val content: String? = null,
    
    /** 错误信息 */
    val errorMessage: String? = null,
    
    /** 错误类型 */
    val errorType: ErrorType? = null,
    
    /** 执行耗时（毫秒） */
    val executionTimeMs: Long = 0,
    
    /** 使用的Token数量 */
    val tokensUsed: Int? = null,
    
    /** 执行时间戳 */
    val timestamp: Long = System.currentTimeMillis(),
    
    /** 使用的模型配置ID */
    val modelConfigId: String? = null,
    
    /** 使用的模板ID */
    val templateId: String? = null
) {
    companion object {
        /** 创建成功结果 */
        fun success(
            content: String,
            executionTimeMs: Long = 0,
            tokensUsed: Int? = null,
            modelConfigId: String? = null,
            templateId: String? = null
        ): ExecutionResult {
            return ExecutionResult(
                success = true,
                content = content,
                executionTimeMs = executionTimeMs,
                tokensUsed = tokensUsed,
                modelConfigId = modelConfigId,
                templateId = templateId
            )
        }
        
        /** 创建失败结果 */
        fun failure(
            errorMessage: String,
            errorType: ErrorType,
            executionTimeMs: Long = 0,
            modelConfigId: String? = null,
            templateId: String? = null
        ): ExecutionResult {
            return ExecutionResult(
                success = false,
                errorMessage = errorMessage,
                errorType = errorType,
                executionTimeMs = executionTimeMs,
                modelConfigId = modelConfigId,
                templateId = templateId
            )
        }
        
        /** 创建网络错误结果 */
        fun networkError(errorMessage: String): ExecutionResult {
            return failure(errorMessage, ErrorType.NETWORK_ERROR)
        }
        
        /** 创建API错误结果 */
        fun apiError(errorMessage: String): ExecutionResult {
            return failure(errorMessage, ErrorType.API_ERROR)
        }
        
        /** 创建配置错误结果 */
        fun configError(errorMessage: String): ExecutionResult {
            return failure(errorMessage, ErrorType.CONFIGURATION_ERROR)
        }
        
        /** 创建超时错误结果 */
        fun timeoutError(): ExecutionResult {
            return failure("请求超时", ErrorType.TIMEOUT_ERROR)
        }
        
        /** 创建解析错误结果 */
        fun parseError(errorMessage: String): ExecutionResult {
            return failure(errorMessage, ErrorType.PARSE_ERROR)
        }
    }
    
    /**
     * 获取用户友好的错误信息
     */
    fun getUserFriendlyErrorMessage(): String {
        return when (errorType) {
            ErrorType.NETWORK_ERROR -> "网络连接失败，请检查网络设置"
            ErrorType.API_ERROR -> "API调用失败：${errorMessage ?: "未知错误"}"
            ErrorType.CONFIGURATION_ERROR -> "配置错误：${errorMessage ?: "请检查模型配置"}"
            ErrorType.TIMEOUT_ERROR -> "请求超时，请稍后重试"
            ErrorType.PARSE_ERROR -> "响应解析失败：${errorMessage ?: "格式错误"}"
            ErrorType.AUTHENTICATION_ERROR -> "认证失败，请检查API密钥"
            ErrorType.RATE_LIMIT_ERROR -> "请求频率过高，请稍后重试"
            ErrorType.UNKNOWN_ERROR -> "未知错误：${errorMessage ?: "请联系开发者"}"
            null -> errorMessage ?: "执行失败"
        }
    }
    
    /**
     * 是否为可重试的错误
     */
    fun isRetryable(): Boolean {
        return when (errorType) {
            ErrorType.NETWORK_ERROR,
            ErrorType.TIMEOUT_ERROR,
            ErrorType.RATE_LIMIT_ERROR -> true
            else -> false
        }
    }
}

/**
 * 错误类型枚举
 */
@Serializable
enum class ErrorType {
    /** 网络错误 */
    NETWORK_ERROR,
    
    /** API错误 */
    API_ERROR,
    
    /** 配置错误 */
    CONFIGURATION_ERROR,
    
    /** 超时错误 */
    TIMEOUT_ERROR,
    
    /** 解析错误 */
    PARSE_ERROR,
    
    /** 认证错误 */
    AUTHENTICATION_ERROR,
    
    /** 频率限制错误 */
    RATE_LIMIT_ERROR,
    
    /** 未知错误 */
    UNKNOWN_ERROR
}