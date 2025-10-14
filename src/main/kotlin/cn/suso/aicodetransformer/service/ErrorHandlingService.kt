package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.ErrorContext
import cn.suso.aicodetransformer.model.ErrorHandlingResult
import cn.suso.aicodetransformer.model.RetryConfig
import cn.suso.aicodetransformer.model.RetryResult
import cn.suso.aicodetransformer.model.ErrorSuggestion
import cn.suso.aicodetransformer.constants.ErrorSeverity
import com.intellij.openapi.project.Project

/**
 * 错误处理服务接口
 * 负责异常处理和用户友好的错误提示
 */
interface ErrorHandlingService {
    
    /**
     * 处理异常
     * @param exception 异常对象
     * @param context 错误上下文
     * @param project 项目实例
     * @return 错误处理结果
     */
    fun handleException(
        exception: Throwable,
        context: ErrorContext,
        project: Project? = null
    ): ErrorHandlingResult
    
    /**
     * 处理AI模型调用错误
     * @param exception 异常对象
     * @param modelName 模型名称
     * @param project 项目实例
     * @return 错误处理结果
     */
    fun handleModelError(
        exception: Throwable,
        modelName: String?,
        project: Project? = null
    ): ErrorHandlingResult
    
    /**
     * 处理网络错误
     * @param exception 异常对象
     * @param url 请求URL
     * @param project 项目实例
     * @return 错误处理结果
     */
    fun handleNetworkError(
        exception: Throwable,
        url: String?,
        project: Project? = null
    ): ErrorHandlingResult
    
    /**
     * 处理配置错误
     * @param exception 异常对象
     * @param configType 配置类型
     * @param project 项目实例
     * @return 错误处理结果
     */
    fun handleConfigurationError(
        exception: Throwable,
        configType: String,
        project: Project? = null
    ): ErrorHandlingResult
    
    /**
     * 处理代码替换错误
     * @param exception 异常对象
     * @param originalText 原始文本
     * @param newText 新文本
     * @param project 项目实例
     * @return 错误处理结果
     */
    fun handleCodeReplacementError(
        exception: Throwable,
        originalText: String?,
        newText: String?,
        project: Project? = null
    ): ErrorHandlingResult
    
    /**
     * 执行带重试的操作
     * @param operation 要执行的操作
     * @param retryConfig 重试配置
     * @param context 错误上下文
     * @return 操作结果
     */
    fun <T> executeWithRetry(
        operation: () -> T,
        retryConfig: RetryConfig = RetryConfig(),
        context: ErrorContext
    ): RetryResult<T>
    
    /**
     * 执行带重试的异步操作
     * @param operation 要执行的操作
     * @param retryConfig 重试配置
     * @param context 错误上下文
     * @param callback 结果回调
     */
    fun <T> executeWithRetryAsync(
        operation: () -> T,
        retryConfig: RetryConfig = RetryConfig(),
        context: ErrorContext,
        callback: (RetryResult<T>) -> Unit
    )
    
    /**
     * 获取用户友好的错误消息
     * @param exception 异常对象
     * @param context 错误上下文
     * @return 用户友好的错误消息
     */
    fun getUserFriendlyMessage(exception: Throwable, context: ErrorContext): String
    
    /**
     * 获取错误解决建议
     * @param exception 异常对象
     * @param context 错误上下文
     * @return 解决建议列表
     */
    fun getSuggestions(exception: Throwable, context: ErrorContext): List<ErrorSuggestion>
    
    /**
     * 记录错误
     * @param exception 异常对象
     * @param context 错误上下文
     * @param severity 错误严重程度
     */
    fun logError(exception: Throwable, context: ErrorContext, severity: ErrorSeverity = ErrorSeverity.ERROR)
    
    /**
     * 添加错误监听器
     * @param listener 监听器
     */
    fun addErrorListener(listener: ErrorListener)
    
    /**
     * 移除错误监听器
     * @param listener 监听器
     */
    fun removeErrorListener(listener: ErrorListener)
}

/**
 * 错误监听器接口
 */
interface ErrorListener {
    
    /**
     * 错误发生
     * @param exception 异常对象
     * @param context 错误上下文
     * @param result 处理结果
     */
    fun onErrorOccurred(
        exception: Throwable,
        context: ErrorContext,
        result: ErrorHandlingResult
    )
    
    /**
     * 重试开始
     * @param context 错误上下文
     * @param attempt 重试次数
     */
    fun onRetryStarted(context: ErrorContext, attempt: Int)
    
    /**
     * 重试成功
     * @param context 错误上下文
     * @param attempts 总重试次数
     */
    fun onRetrySucceeded(context: ErrorContext, attempts: Int)
    
    /**
     * 重试失败
     * @param context 错误上下文
     * @param finalException 最终异常
     * @param attempts 总重试次数
     */
    fun onRetryFailed(context: ErrorContext, finalException: Throwable, attempts: Int)
}