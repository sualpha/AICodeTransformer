package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.RateLimitConfig
import cn.suso.aicodetransformer.model.RateLimitStats

/**
 * API限流服务接口
 * 用于控制API调用频率，防止过度使用
 */
interface RateLimitService {
    
    /**
     * 检查是否允许执行请求
     * @param config 模型配置
     * @param apiKey API密钥（用于区分不同用户）
     * @return 是否允许执行
     */
    fun isAllowed(config: ModelConfiguration, apiKey: String): Boolean
    
    /**
     * 记录请求执行
     * @param config 模型配置
     * @param apiKey API密钥
     */
    fun recordRequest(config: ModelConfiguration, apiKey: String)
    
    /**
     * 获取下次允许请求的时间
     * @param config 模型配置
     * @param apiKey API密钥
     * @return 下次允许请求的时间戳（毫秒），如果当前就可以请求则返回0
     */
    fun getNextAllowedTime(config: ModelConfiguration, apiKey: String): Long
    
    /**
     * 获取剩余请求配额
     * @param config 模型配置
     * @param apiKey API密钥
     * @return 剩余请求数
     */
    fun getRemainingQuota(config: ModelConfiguration, apiKey: String): Int
    
    /**
     * 重置限流状态
     * @param config 模型配置
     * @param apiKey API密钥
     */
    fun resetLimit(config: ModelConfiguration, apiKey: String)
    
    /**
     * 设置限流配置
     * @param config 限流配置
     */
    fun setRateLimitConfig(config: RateLimitConfig)
    
    /**
     * 获取限流统计信息
     * @return 限流统计信息
     */
    fun getRateLimitStats(): RateLimitStats
}