package cn.suso.aicodetransformer.constants

/**
 * 错误严重程度
 */
enum class ErrorSeverity {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    FATAL
}

/**
 * 错误类型
 */
enum class ErrorType {
    NETWORK,
    CONFIGURATION,
    MODEL,
    VALIDATION,
    AUTHENTICATION,
    PERMISSION,
    UNKNOWN
}