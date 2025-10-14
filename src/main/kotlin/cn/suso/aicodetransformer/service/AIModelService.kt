package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.constants.RequestStatusConstants
import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.ModelInfo

/**
 * AI模型调用服务接口
 * 负责与各种AI模型进行通信
 */
interface AIModelService {
    
    /**
     * 调用AI模型
     * @param config 模型配置
     * @param prompt 提示内容
     * @param apiKey API密钥
     * @return 执行结果
     */
    suspend fun callModel(
        config: ModelConfiguration,
        prompt: String,
        apiKey: String
    ): ExecutionResult
    
    /**
     * 测试模型连接
     * @param config 模型配置
     * @param apiKey API密钥
     * @return 测试结果
     */
    suspend fun testConnection(
        config: ModelConfiguration,
        apiKey: String
    ): ExecutionResult
    
    /**
     * 获取模型信息
     * @param config 模型配置
     * @param apiKey API密钥
     * @return 模型信息，如果获取失败返回null
     */
    suspend fun getModelInfo(
        config: ModelConfiguration,
        apiKey: String
    ): ModelInfo?
    
    /**
     * 取消正在进行的请求
     * @param requestId 请求ID
     */
    fun cancelRequest(requestId: String): Unit
    
    /**
     * 获取支持的模型类型
     * @return 支持的模型类型列表
     */
    fun getSupportedModelTypes(): List<String>
    
    /**
     * 验证API密钥格式
     * @param apiKey API密钥
     * @param modelType 模型类型
     * @return 是否有效
     */
    fun validateApiKeyFormat(apiKey: String, modelType: String): Boolean
    
    /**
     * 估算Token使用量
     * @param text 文本内容
     * @return 估算的Token数量
     */
    fun estimateTokens(text: String): Int
    
    /**
     * 获取请求状态
     * @param requestId 请求ID
     * @return 请求状态
     */
    fun getRequestStatus(requestId: String): RequestStatusConstants?
    
    /**
     * 添加请求监听器
     * @param listener 监听器
     */
    fun addRequestListener(listener: RequestListener)
    
    /**
     * 移除请求监听器
     * @param listener 监听器
     */
    fun removeRequestListener(listener: RequestListener)
}


/**
 * 请求监听器接口
 */
interface RequestListener {
    /**
     * 请求开始时调用
     * @param requestId 请求ID
     * @param config 模型配置
     * @param prompt 提示内容
     */
    fun onRequestStarted(requestId: String, config: ModelConfiguration, prompt: String) {}
    
    /**
     * 请求进度更新时调用
     * @param requestId 请求ID
     * @param progress 进度（0-100）
     */
    fun onRequestProgress(requestId: String, progress: Int) {}
    
    /**
     * 请求完成时调用
     * @param requestId 请求ID
     * @param result 执行结果
     */
    fun onRequestCompleted(requestId: String, result: ExecutionResult) {}
    
    /**
     * 请求取消时调用
     * @param requestId 请求ID
     */
    fun onRequestCancelled(requestId: String) {}
    
    /**
     * 请求失败时调用
     * @param requestId 请求ID
     * @param error 错误信息
     */
    fun onRequestFailed(requestId: String, error: String) {}
}

