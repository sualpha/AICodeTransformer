package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.CacheConfig
import cn.suso.aicodetransformer.service.CacheService
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.model.CacheStats
import cn.suso.aicodetransformer.model.CacheEntry
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 缓存服务实现
 * 提供内存缓存功能以优化API调用性能
 */
@Service
class CacheServiceImpl : CacheService {
    
    companion object {
        private val logger = Logger.getInstance(CacheServiceImpl::class.java)
    }
    
    private val configurationService: ConfigurationService by lazy { service() }
    
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val hitCount = AtomicLong(0)
    private val missCount = AtomicLong(0)
    private val evictionCount = AtomicLong(0)
    private val totalLoadTime = AtomicLong(0)
    private val loadCount = AtomicLong(0)
    
    private var config = CacheConfig()
    private val cleanupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "CacheCleanup").apply { isDaemon = true }
    }
    
    init {
        // 启动定期清理任务
        cleanupExecutor.scheduleAtFixedRate(
            { clearExpiredCache() },
            config.cleanupIntervalSeconds,
            config.cleanupIntervalSeconds,
            TimeUnit.SECONDS
        )
    }
    
    /**
     * 检查缓存是否启用
     * 同时检查本地配置和全局配置
     */
    private fun isCacheEnabled(): Boolean {
        return try {
            config.enabled && configurationService.getGlobalSettings().enableCache
        } catch (e: Exception) {
            logger.warn("Failed to check global cache settings, using local config only", e)
            false
        }
    }
    
    override fun getCachedResponse(key: String): ExecutionResult? {
        if (!isCacheEnabled()) {
            return null
        }
        
        val entry = cache[key]
        return if (entry != null && !entry.isExpired()) {
            hitCount.incrementAndGet()
            logger.debug("Cache hit for key: $key")
            entry.result
        } else {
            missCount.incrementAndGet()
            if (entry != null && entry.isExpired()) {
                cache.remove(key)
                evictionCount.incrementAndGet()
                logger.debug("Cache entry expired and removed: $key")
            }
            logger.debug("Cache miss for key: $key")
            null
        }
    }
    
    override fun cacheResponse(key: String, result: ExecutionResult, ttlSeconds: Long) {
        if (!isCacheEnabled() || !result.success) {
            return
        }
        
        // 检查缓存大小限制 - 优化的LRU策略
        if (cache.size >= config.maxSize) {
            // 移除最少使用的条目（基于创建时间的简化LRU）
            val entriesToRemove = cache.entries
                .sortedBy { it.value.createdAt }
                .take((config.maxSize * 0.1).toInt().coerceAtLeast(1))
            
            entriesToRemove.forEach { entry ->
                cache.remove(entry.key)
                evictionCount.incrementAndGet()
            }
            
            logger.debug("Cache evicted ${entriesToRemove.size} oldest entries")
        }
        
        val entry = CacheEntry(
            result = result,
            createdAt = System.currentTimeMillis(),
            ttlMs = ttlSeconds * 1000
        )
        
        cache[key] = entry
        logger.debug("Cached response for key: $key, TTL: ${ttlSeconds}s")
    }
    
    override fun generateCacheKey(
        config: ModelConfiguration,
        prompt: String,
        temperature: Double,
        maxTokens: Int
    ): String {
        val content = "${config.id}|${config.modelName}|$prompt|$temperature|$maxTokens"
        return hashString(content)
    }
    
    override fun clearExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = cache.entries
            .filter { it.value.isExpired(currentTime) }
            .map { it.key }
        
        expiredKeys.forEach { key ->
            cache.remove(key)
            evictionCount.incrementAndGet()
        }
        
        if (expiredKeys.isNotEmpty()) {
            logger.debug("Cleared ${expiredKeys.size} expired cache entries")
        }
    }
    
    override fun clearAllCache() {
        val size = cache.size
        cache.clear()
        evictionCount.addAndGet(size.toLong())
        logger.info("Cleared all cache entries: $size")
    }
    
    override fun getCacheStats(): CacheStats {
        val hits = hitCount.get()
        val misses = missCount.get()
        val loads = loadCount.get()
        val avgLoadTime = if (loads > 0) totalLoadTime.get().toDouble() / loads else 0.0
        
        return CacheStats(
            totalEntries = cache.size,
            hitCount = hits,
            missCount = misses,
            evictionCount = evictionCount.get(),
            averageLoadTime = avgLoadTime
        )
    }
    
    override fun setCacheConfig(config: CacheConfig) {
        this.config = config
        logger.info("Cache configuration updated: $config")
        
        // 如果禁用缓存（本地或全局），清除所有条目
        if (!isCacheEnabled()) {
            clearAllCache()
        }
    }
    
    override fun getSmartTtl(prompt: String): Long {
        val lowerPrompt = prompt.lowercase()
        
        return when {
            // 代码转换相关关键词
            lowerPrompt.contains("转换") || lowerPrompt.contains("convert") || 
            lowerPrompt.contains("transform") || lowerPrompt.contains("重构") ||
            lowerPrompt.contains("refactor") -> config.codeTransformTtlSeconds
            
            // 代码解释相关关键词
            lowerPrompt.contains("解释") || lowerPrompt.contains("explain") ||
            lowerPrompt.contains("分析") || lowerPrompt.contains("analyze") ||
            lowerPrompt.contains("什么意思") || lowerPrompt.contains("作用") -> config.codeExplainTtlSeconds
            
            // 简单查询相关关键词
            lowerPrompt.contains("是什么") || lowerPrompt.contains("如何") ||
            lowerPrompt.contains("怎么") || lowerPrompt.contains("what is") ||
            lowerPrompt.contains("how to") -> config.simpleQueryTtlSeconds
            
            // 默认使用标准TTL
            else -> config.defaultTtlSeconds
        }
    }
    
    /**
     * 生成字符串的哈希值
     */
    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 清理资源
     */
    fun dispose() {
        cleanupExecutor.shutdown()
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cleanupExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        clearAllCache()
    }
}