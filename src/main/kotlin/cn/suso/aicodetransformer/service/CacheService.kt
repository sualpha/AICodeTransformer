package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.CacheConfig
import cn.suso.aicodetransformer.model.CacheStats
import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.ModelConfiguration

/**
 * 缓存服务接口
 * 用于缓存API响应以提升性能
 */
interface CacheService {
    
    /**
     * 获取缓存的响应
     * @param key 缓存键
     * @return 缓存的执行结果，如果不存在则返回null
     */
    fun getCachedResponse(key: String): ExecutionResult?
    
    /**
     * 缓存响应
     * @param key 缓存键
     * @param result 执行结果
     * @param ttlSeconds 生存时间（秒）
     */
    fun cacheResponse(key: String, result: ExecutionResult, ttlSeconds: Long = 300)
    
    /**
     * 生成缓存键
     * @param config 模型配置
     * @param prompt 提示内容
     * @param temperature 温度参数
     * @param maxTokens 最大token数
     * @return 缓存键
     */
    fun generateCacheKey(
        config: ModelConfiguration,
        prompt: String,
        temperature: Double = 0.7,
        maxTokens: Int = 2048
    ): String
    
    /**
     * 清除过期缓存
     */
    fun clearExpiredCache()
    
    /**
     * 清除所有缓存
     */
    fun clearAllCache()
    
    /**
     * 获取缓存统计信息
     * @return 缓存统计
     */
    fun getCacheStats(): CacheStats
    
    /**
     * 设置缓存配置
     * @param config 缓存配置
     */
    fun setCacheConfig(config: CacheConfig)
    
    /**
     * 智能选择TTL时间
     * @param prompt 提示内容
     * @return 推荐的TTL时间（秒）
     */
    fun getSmartTtl(prompt: String): Long
}



