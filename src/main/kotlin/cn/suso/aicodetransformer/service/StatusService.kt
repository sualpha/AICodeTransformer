package cn.suso.aicodetransformer.service

import com.intellij.openapi.project.Project

/**
 * 状态服务接口
 * 负责状态栏显示和通知机制
 */
interface StatusService {
    
    /**
     * 显示状态栏消息
     * @param message 消息内容
     * @param project 项目实例
     * @param duration 显示时长（毫秒），-1表示永久显示
     */
    fun showStatusMessage(message: String, project: Project? = null, duration: Long = 3000)
    
    /**
     * 清除状态栏消息
     * @param project 项目实例
     */
    fun clearStatusMessage(project: Project? = null)
    
    /**
     * 显示进度状态
     * @param message 进度消息
     * @param progress 进度百分比 (0-100)
     * @param project 项目实例
     */
    fun showProgress(message: String, progress: Int, project: Project? = null)
    
    /**
     * 隐藏进度状态
     * @param project 项目实例
     */
    fun hideProgress(project: Project? = null)
    
    /**
     * 显示信息通知
     * @param title 通知标题
     * @param content 通知内容
     * @param project 项目实例
     */
    fun showInfoNotification(title: String, content: String, project: Project? = null)
    
    /**
     * 显示警告通知
     * @param title 通知标题
     * @param content 通知内容
     * @param project 项目实例
     */
    fun showWarningNotification(title: String, content: String, project: Project? = null)
    
    /**
     * 显示错误通知
     * @param title 通知标题
     * @param content 通知内容
     * @param project 项目实例
     * @param actions 可选的操作按钮
     */
    fun showErrorNotification(
        title: String,
        content: String,
        project: Project? = null,
        actions: List<NotificationAction> = emptyList()
    )
    
    /**
     * 显示成功通知
     * @param title 通知标题
     * @param content 通知内容
     * @param project 项目实例
     */
    fun showSuccessNotification(title: String, content: String, project: Project? = null)
    
    /**
     * 显示带操作的通知
     * @param title 通知标题
     * @param content 通知内容
     * @param type 通知类型
     * @param project 项目实例
     * @param actions 操作按钮列表
     */
    fun showNotificationWithActions(
        title: String,
        content: String,
        type: NotificationType,
        project: Project? = null,
        actions: List<NotificationAction> = emptyList()
    )
    
    /**
     * 显示气球提示
     * @param message 提示消息
     * @param type 提示类型
     * @param project 项目实例
     */
    fun showBalloonTip(message: String, type: BalloonType, project: Project? = null)
    
    /**
     * 更新执行状态
     * @param executionId 执行ID
     * @param status 执行状态
     * @param message 状态消息
     * @param progress 进度百分比
     * @param project 项目实例
     */
    fun updateExecutionStatus(
        executionId: String,
        status: ExecutionStatus,
        message: String,
        progress: Int = 0,
        project: Project? = null
    )
    
    /**
     * 清除执行状态
     * @param executionId 执行ID
     * @param project 项目实例
     */
    fun clearExecutionStatus(executionId: String, project: Project? = null)
    
    /**
     * 获取当前状态信息
     * @param project 项目实例
     * @return 状态信息
     */
    fun getCurrentStatus(project: Project? = null): StatusInfo?
    
    /**
     * 添加状态监听器
     * @param listener 监听器
     */
    fun addStatusListener(listener: StatusListener)
    
    /**
     * 移除状态监听器
     * @param listener 监听器
     */
    fun removeStatusListener(listener: StatusListener)
}

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
 * 气球提示类型枚举
 */
enum class BalloonType {
    INFO,
    WARNING,
    ERROR,
    SUCCESS
}

/**
 * 通知操作
 */
data class NotificationAction(
    val text: String,
    val action: () -> Unit
)

/**
 * 状态信息
 */
data class StatusInfo(
    val message: String?,
    val isProgressVisible: Boolean,
    val progressMessage: String?,
    val progressValue: Int,
    val activeExecutions: Map<String, ExecutionStatusInfo>
)

/**
 * 执行状态信息
 */
data class ExecutionStatusInfo(
    val executionId: String,
    val status: ExecutionStatus,
    val message: String,
    val progress: Int,
    val startTime: Long
)

/**
 * 状态监听器接口
 */
interface StatusListener {
    
    /**
     * 状态消息变化
     * @param message 新的状态消息
     */
    fun onStatusMessageChanged(message: String?)
    
    /**
     * 进度状态变化
     * @param visible 是否可见
     * @param message 进度消息
     * @param progress 进度值
     */
    fun onProgressChanged(visible: Boolean, message: String?, progress: Int)
    
    /**
     * 执行状态变化
     * @param executionId 执行ID
     * @param status 执行状态
     * @param message 状态消息
     * @param progress 进度值
     */
    fun onExecutionStatusChanged(
        executionId: String,
        status: ExecutionStatus,
        message: String,
        progress: Int
    )
    
    /**
     * 通知显示
     * @param title 通知标题
     * @param content 通知内容
     * @param type 通知类型
     */
    fun onNotificationShown(title: String, content: String, type: NotificationType)
}