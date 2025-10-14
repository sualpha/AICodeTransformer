package cn.suso.aicodetransformer.model

/**
 * Action执行结果
 */
data class ActionExecutionResult(
    /** 是否执行成功 */
    val success: Boolean,

    /** 错误信息 */
    val errorMessage: String? = null,

    /** 执行耗时（毫秒） */
    val executionTimeMs: Long = 0
)