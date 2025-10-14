package cn.suso.aicodetransformer.model

/**
 * 缓存配置
 */
data class CacheConfig(
    /** 最大缓存条目数 */
    val maxSize: Long = 5000,  // 增加到5000条目

    /** 默认TTL（秒） */
    val defaultTtlSeconds: Long = 900,  // 增加到15分钟

    /** 是否启用缓存 */
    val enabled: Boolean = true,

    /** 缓存清理间隔（秒） */
    val cleanupIntervalSeconds: Long = 300,  // 增加到5分钟

    /** 代码转换缓存TTL（秒） */
    val codeTransformTtlSeconds: Long = 1800,  // 30分钟

    /** 代码解释缓存TTL（秒） */
    val codeExplainTtlSeconds: Long = 3600,  // 60分钟

    /** 简单查询缓存TTL（秒） */
    val simpleQueryTtlSeconds: Long = 7200  // 2小时
)