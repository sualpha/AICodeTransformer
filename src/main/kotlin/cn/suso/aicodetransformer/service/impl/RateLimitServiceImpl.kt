package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.service.RateLimitConfig
import cn.suso.aicodetransformer.service.RateLimitService
import cn.suso.aicodetransformer.service.RateLimitStats
import cn.suso.aicodetransformer.service.WindowType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.Disposable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * API限流服务实现
 * 使用令牌桶算法控制API调用频率
 */
@Service
class RateLimitServiceImpl : RateLimitService, Disposable {
    
    companion object {
        private val logger = Logger.getInstance(RateLimitServiceImpl::class.java)
    }
    
    private val rateLimiters = ConcurrentHashMap<String, RateLimiter>()
    private val totalRequests = AtomicLong(0)
    private val limitedRequests = AtomicLong(0)
    
    private var config = RateLimitConfig()
    
    override fun isAllowed(config: ModelConfiguration, apiKey: String): Boolean {
        if (!this.config.enabled) {
            return true
        }
        
        val key = generateKey(config, apiKey)
        val limiter = rateLimiters.computeIfAbsent(key) {
            RateLimiter(this.config)
        }
        
        totalRequests.incrementAndGet()
        
        val allowed = limiter.tryAcquire()
        if (!allowed) {
            limitedRequests.incrementAndGet()
            logger.debug("Request rate limited for key: $key")
        }
        
        return allowed
    }
    
    override fun recordRequest(config: ModelConfiguration, apiKey: String) {
        val key = generateKey(config, apiKey)
        val limiter = rateLimiters.computeIfAbsent(key) {
            RateLimiter(this.config)
        }
        limiter.recordRequest()
    }
    
    override fun getNextAllowedTime(config: ModelConfiguration, apiKey: String): Long {
        if (!this.config.enabled) {
            return 0L
        }
        
        val key = generateKey(config, apiKey)
        val limiter = rateLimiters[key] ?: return 0L
        return limiter.getNextAllowedTime()
    }
    
    override fun getRemainingQuota(config: ModelConfiguration, apiKey: String): Int {
        if (!this.config.enabled) {
            return Int.MAX_VALUE
        }
        
        val key = generateKey(config, apiKey)
        val limiter = rateLimiters[key] ?: return this.config.requestsPerMinute
        return limiter.getRemainingTokens()
    }
    
    override fun resetLimit(config: ModelConfiguration, apiKey: String) {
        val key = generateKey(config, apiKey)
        rateLimiters.remove(key)
        logger.debug("Reset rate limit for key: $key")
    }
    
    override fun setRateLimitConfig(config: RateLimitConfig) {
        this.config = config
        // 清除现有限流器，使用新配置
        rateLimiters.clear()
        logger.info("Rate limit configuration updated: $config")
    }
    
    override fun getRateLimitStats(): RateLimitStats {
        val total = totalRequests.get()
        val limited = limitedRequests.get()
        val hitRate = if (total > 0) limited.toDouble() / total else 0.0
        
        return RateLimitStats(
            totalRequests = total,
            limitedRequests = limited,
            activeLimitKeys = rateLimiters.size,
            limitHitRate = hitRate
        )
    }
    
    /**
     * 生成限流键
     */
    private fun generateKey(config: ModelConfiguration, apiKey: String): String {
        // 使用模型ID和API密钥的哈希值作为键
        val keyHash = (config.id + apiKey).hashCode().toString()
        return "${config.id}_$keyHash"
    }
    
    override fun dispose() {
        try {
            // 清理所有限流器
            rateLimiters.clear()
            
            logger.info("RateLimitService资源清理完成")
        } catch (e: Exception) {
            logger.error("RateLimitService资源清理失败", e)
        }
    }
}

/**
 * 令牌桶限流器
 */
private class RateLimiter(private val config: RateLimitConfig) {
    private var tokens: Double = config.requestsPerMinute.toDouble()
    private var lastRefillTime: Long = System.currentTimeMillis()
    private val requestTimes = mutableListOf<Long>()
    
    @Synchronized
    fun tryAcquire(): Boolean {
        refillTokens()
        
        return if (tokens >= 1.0) {
            tokens -= 1.0
            true
        } else {
            false
        }
    }
    
    @Synchronized
    fun recordRequest() {
        val currentTime = System.currentTimeMillis()
        requestTimes.add(currentTime)
        
        // 清理过期的请求记录
        val oneHourAgo = currentTime - 3600000 // 1小时
        requestTimes.removeAll { it < oneHourAgo }
    }
    
    @Synchronized
    fun getNextAllowedTime(): Long {
        refillTokens()
        
        return if (tokens >= 1.0) {
            0L
        } else {
            // 计算下次令牌补充时间
            val tokensNeeded = 1.0 - tokens
            val refillRate = config.requestsPerMinute.toDouble() / 60000.0 // 每毫秒补充的令牌数
            val waitTime = (tokensNeeded / refillRate).toLong()
            System.currentTimeMillis() + waitTime
        }
    }
    
    @Synchronized
    fun getRemainingTokens(): Int {
        refillTokens()
        return tokens.toInt()
    }
    
    /**
     * 补充令牌
     */
    private fun refillTokens() {
        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - lastRefillTime
        
        if (timePassed > 0) {
            // 每分钟补充 requestsPerMinute 个令牌
            val refillRate = config.requestsPerMinute.toDouble() / 60000.0 // 每毫秒补充的令牌数
            val tokensToAdd = timePassed * refillRate
            
            tokens = minOf(tokens + tokensToAdd, config.burstSize.toDouble())
            lastRefillTime = currentTime
        }
        
        // 检查小时和天级别的限制
        if (config.windowType == WindowType.SLIDING) {
            checkSlidingWindowLimits(currentTime)
        }
    }
    
    /**
     * 检查滑动窗口限制
     */
    private fun checkSlidingWindowLimits(currentTime: Long) {
        val oneHourAgo = currentTime - 3600000 // 1小时
        val oneDayAgo = currentTime - 86400000 // 1天
        
        val requestsInLastHour = requestTimes.count { it > oneHourAgo }
        val requestsInLastDay = requestTimes.count { it > oneDayAgo }
        
        // 如果超过小时或天限制，将令牌数设为0
        if (requestsInLastHour >= config.requestsPerHour || requestsInLastDay >= config.requestsPerDay) {
            tokens = 0.0
        }
    }
}