package cn.suso.aicodetransformer.model

/**
 * 缓存统计信息
 */
data class CacheStats(
    val totalEntries: Int,
    val hitCount: Long,
    val missCount: Long,
    val evictionCount: Long,
    val averageLoadTime: Double
) {
    val hitRate: Double
        get() = if (hitCount + missCount == 0L) 0.0 else hitCount.toDouble() / (hitCount + missCount)
}