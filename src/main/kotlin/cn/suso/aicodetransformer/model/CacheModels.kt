package cn.suso.aicodetransformer.model

/**
 * 缓存条目
 */
data class CacheEntry(
    val result: ExecutionResult,
    val createdAt: Long,
    val ttlMs: Long
) {
    fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
        return currentTime - createdAt > ttlMs
    }
}