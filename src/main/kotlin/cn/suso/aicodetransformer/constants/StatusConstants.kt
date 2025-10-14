package cn.suso.aicodetransformer.constants

/**
 * 通知类型枚举
 */
enum class NotificationType {
    INFORMATION,
    WARNING,
    ERROR,
    SUCCESS
}

/**
 * 气球提示类型
 */
enum class BalloonType {
    INFO,
    WARNING,
    ERROR,
    SUCCESS
}

/**
 * 执行状态枚举
 */
enum class ExecutionStatus {
    PENDING,        // 等待执行
    RUNNING,        // 正在执行
    COMPLETED,      // 执行完成
    FAILED,         // 执行失败
    CANCELLED       // 已取消
}