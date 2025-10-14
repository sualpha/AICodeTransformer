package cn.suso.aicodetransformer.constants

/**
 * 请求状态枚举
 */
enum class RequestStatusConstants {
    /** 等待中 */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 已完成 */
    COMPLETED,

    /** 已取消 */
    CANCELLED,

    /** 失败 */
    FAILED
}