package cn.suso.aicodetransformer.model

/**
 * 请求配置数据类
 */
data class RequestConfig(
    /** 请求ID */
    val requestId: String = java.util.UUID.randomUUID().toString(),

    /** 超时时间（毫秒） */
    val timeoutMs: Long = 30000,

    /** 重试次数 */
    val retryCount: Int = 0,

    /** 重试间隔（毫秒） */
    val retryDelayMs: Long = 1000,

    /** 是否启用流式响应 */
    val streaming: Boolean = false
)