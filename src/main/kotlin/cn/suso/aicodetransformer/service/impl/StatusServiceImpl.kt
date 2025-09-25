package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.service.*
import cn.suso.aicodetransformer.service.NotificationAction
import com.intellij.notification.*
import com.intellij.notification.NotificationType as IntellijNotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.messages.MessageBusConnection
import java.awt.Point
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Timer

/**
 * 状态服务实现类
 */
@Service(Service.Level.APP)
class StatusServiceImpl : StatusService, Disposable {
    
    companion object {
        private val logger = Logger.getInstance(StatusServiceImpl::class.java)
        private const val NOTIFICATION_GROUP_ID = "AICodeTransformer"
        
        fun getInstance(): StatusService = service<StatusService>()
    }
    
    private val notificationGroup: NotificationGroup by lazy {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?: throw IllegalStateException("Notification group '$NOTIFICATION_GROUP_ID' not found. Check plugin.xml configuration.")
    }
    
    private val errorHandlingService: ErrorHandlingService by lazy { service<ErrorHandlingService>() }
    private val activeExecutions = ConcurrentHashMap<String, ExecutionStatusInfo>()
    private val listeners = CopyOnWriteArrayList<StatusListener>()
    private val statusTimers = ConcurrentHashMap<Project?, Timer>()
    private var messageBusConnection: MessageBusConnection? = null
    
    @Volatile
    private var currentStatusMessage: String? = null
    
    @Volatile
    private var isProgressVisible: Boolean = false
    
    @Volatile
    private var progressMessage: String? = null
    
    @Volatile
    private var progressValue: Int = 0
    
    init {
        setupProjectCloseListener()
    }
    
    override fun showStatusMessage(message: String, project: Project?, duration: Long) {
        ApplicationManager.getApplication().invokeLater {
            try {
                currentStatusMessage = message
                notifyStatusListeners()
                
                val statusBar = getStatusBar(project)
                statusBar?.info = message
                
                // 清除之前的定时器
                statusTimers[project]?.stop()
                
                // 如果设置了持续时间，创建定时器自动清除
                if (duration > 0) {
                    val timer = Timer(duration.toInt()) {
                        clearStatusMessage(project)
                    }
                    timer.isRepeats = false
                    timer.start()
                    statusTimers[project] = timer
                }
                
            } catch (e: Exception) {
                logger.error("显示状态消息失败", e)
            }
        }
    }
    
    override fun clearStatusMessage(project: Project?) {
        ApplicationManager.getApplication().invokeLater {
            try {
                currentStatusMessage = null
                notifyStatusListeners()
                
                val statusBar = getStatusBar(project)
                statusBar?.info = ""
                
                // 停止定时器
                statusTimers[project]?.stop()
                statusTimers.remove(project)
                
            } catch (e: Exception) {
                logger.error("清除状态消息失败", e)
            }
        }
    }
    
    override fun showProgress(message: String, progress: Int, project: Project?) {
        ApplicationManager.getApplication().invokeLater {
            try {
                isProgressVisible = true
                progressMessage = message
                progressValue = progress.coerceIn(0, 100)
                
                notifyProgressListeners()
                
                val statusMessage = "$message ($progressValue%)"
                showStatusMessage(statusMessage, project, -1)
                
            } catch (e: Exception) {
                logger.error("显示进度失败", e)
            }
        }
    }
    
    override fun hideProgress(project: Project?) {
        ApplicationManager.getApplication().invokeLater {
            try {
                isProgressVisible = false
                progressMessage = null
                progressValue = 0
                
                notifyProgressListeners()
                clearStatusMessage(project)
                
            } catch (e: Exception) {
                logger.error("隐藏进度失败", e)
            }
        }
    }
    
    override fun showInfoNotification(title: String, content: String, project: Project?) {
        showNotificationWithActions(title, content, NotificationType.INFORMATION, project)
    }
    
    override fun showWarningNotification(title: String, content: String, project: Project?) {
        showNotificationWithActions(title, content, NotificationType.WARNING, project)
    }
    
    override fun showErrorNotification(
        title: String,
        content: String,
        project: Project?,
        actions: List<NotificationAction>
    ) {
        showNotificationWithActions(title, content, NotificationType.ERROR, project, actions)
    }
    
    override fun showSuccessNotification(title: String, content: String, project: Project?) {
        showNotificationWithActions(title, content, NotificationType.SUCCESS, project)
    }
    
    override fun showNotificationWithActions(
        title: String,
        content: String,
        type: NotificationType,
        project: Project?,
        actions: List<NotificationAction>
    ) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val notificationType = when (type) {
                    NotificationType.INFORMATION -> IntellijNotificationType.INFORMATION
                    NotificationType.WARNING -> IntellijNotificationType.WARNING
                    NotificationType.ERROR -> IntellijNotificationType.ERROR
                    NotificationType.SUCCESS -> IntellijNotificationType.INFORMATION
                }
                
                val notification = notificationGroup.createNotification(
                    title,
                    content,
                    notificationType
                )
                
                // 添加操作按钮
                actions.forEach { notificationAction: NotificationAction ->
                    notification.addAction(object : AnAction(notificationAction.text) {
                        override fun actionPerformed(e: AnActionEvent) {
                            try {
                                notificationAction.action()
                                notification.expire()
                            } catch (ex: Exception) {
                                val errorContext = ErrorContext(
                                    operation = "执行通知操作",
                                    component = "StatusService",
                                    additionalInfo = mapOf(
                                        "actionText" to notificationAction.text
                                    )
                                )
                                errorHandlingService.handleException(ex, errorContext)
                            }
                        }
                    })
                }
                
                notification.notify(project)
                
                // 通知监听器
                notifyNotificationListeners(title, content, type)
                
            } catch (e: Exception) {
                logger.error("显示通知失败", e)
            }
        }
    }
    
    override fun showBalloonTip(message: String, type: BalloonType, project: Project?) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val messageType = when (type) {
                    BalloonType.INFO -> MessageType.INFO
                    BalloonType.WARNING -> MessageType.WARNING
                    BalloonType.ERROR -> MessageType.ERROR
                    BalloonType.SUCCESS -> MessageType.INFO
                }
                
                val statusBar = getStatusBar(project)
                if (statusBar != null) {
                    val balloon = JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder(message, messageType, null)
                        .setFadeoutTime(3000)
                        .createBalloon()
                    
                    val component = statusBar.component
                    if (component != null) {
                        balloon.show(
                            RelativePoint(component, Point(component.width / 2, 0)),
                            Balloon.Position.above
                        )
                    }
                }
                
            } catch (e: Exception) {
                logger.error("显示气球提示失败", e)
            }
        }
    }
    
    override fun updateExecutionStatus(
        executionId: String,
        status: ExecutionStatus,
        message: String,
        progress: Int,
        project: Project?
    ) {
        val statusInfo = ExecutionStatusInfo(
            executionId = executionId,
            status = status,
            message = message,
            progress = progress.coerceIn(0, 100),
            startTime = System.currentTimeMillis()
        )
        
        activeExecutions[executionId] = statusInfo
        
        // 显示进度
        when (status) {
            ExecutionStatus.RUNNING -> {
                showProgress(message, progress, project)
            }
            ExecutionStatus.COMPLETED -> {
                hideProgress(project)
                showSuccessNotification("执行完成", message, project)
            }
            ExecutionStatus.FAILED -> {
                hideProgress(project)
                showErrorNotification("执行失败", message, project)
            }
            ExecutionStatus.CANCELLED -> {
                hideProgress(project)
                showWarningNotification("执行已取消", message, project)
            }
            else -> {
                // PENDING状态不显示通知
            }
        }
        
        // 通知监听器
        notifyExecutionStatusListeners(executionId, status, message, progress)
    }
    
    override fun clearExecutionStatus(executionId: String, project: Project?) {
        activeExecutions.remove(executionId)
        
        // 如果没有活跃的执行，隐藏进度
        if (activeExecutions.isEmpty()) {
            hideProgress(project)
        }
    }
    
    override fun getCurrentStatus(project: Project?): StatusInfo {
        return StatusInfo(
            message = currentStatusMessage,
            isProgressVisible = isProgressVisible,
            progressMessage = progressMessage,
            progressValue = progressValue,
            activeExecutions = activeExecutions.toMap()
        )
    }
    
    override fun addStatusListener(listener: StatusListener) {
        listeners.add(listener)
    }
    
    override fun removeStatusListener(listener: StatusListener) {
        listeners.remove(listener)
    }
    
    /**
     * 设置项目关闭监听器
     */
    private fun setupProjectCloseListener() {
        try {
            messageBusConnection = ApplicationManager.getApplication().messageBus.connect()
            messageBusConnection?.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
                override fun projectClosed(project: Project) {
                    cleanupProjectResources(project)
                }
            })
        } catch (e: Exception) {
            logger.error("设置项目关闭监听器失败", e)
        }
    }
    
    /**
     * 清理项目相关资源
     */
    private fun cleanupProjectResources(project: Project) {
        try {
            // 停止并移除项目相关的定时器
            statusTimers[project]?.stop()
            statusTimers.remove(project)
            
            // 清理项目相关的执行状态
            activeExecutions.values.removeIf { it.executionId.startsWith(project.name) }
            
            logger.debug("已清理项目 ${project.name} 的相关资源")
        } catch (e: Exception) {
            logger.error("清理项目资源失败", e)
        }
    }
    
    override fun dispose() {
        try {
            // 断开消息总线连接
            messageBusConnection?.disconnect()
            messageBusConnection = null
            
            // 停止所有定时器
            statusTimers.values.forEach { timer ->
                timer.stop()
            }
            statusTimers.clear()
            
            // 清理所有监听器
            listeners.clear()
            
            // 清理活动执行状态
            activeExecutions.clear()
            
            logger.info("StatusService资源清理完成")
        } catch (e: Exception) {
            logger.error("StatusService资源清理失败", e)
        }
    }
    
    /**
     * 获取状态栏
     */
    private fun getStatusBar(project: Project?): StatusBar? {
        return if (project != null) {
            WindowManager.getInstance().getStatusBar(project)
        } else {
            WindowManager.getInstance().allProjectFrames.firstOrNull()?.statusBar
        }
    }
    
    /**
     * 通知状态监听器
     */
    private fun notifyStatusListeners() {
        listeners.forEach { listener ->
            try {
                listener.onStatusMessageChanged(currentStatusMessage)
            } catch (e: Exception) {
                logger.error("通知状态监听器失败", e)
            }
        }
    }
    
    /**
     * 通知进度监听器
     */
    private fun notifyProgressListeners() {
        listeners.forEach { listener ->
            try {
                listener.onProgressChanged(isProgressVisible, progressMessage, progressValue)
            } catch (e: Exception) {
                logger.error("通知进度监听器失败", e)
            }
        }
    }
    
    /**
     * 通知执行状态监听器
     */
    private fun notifyExecutionStatusListeners(
        executionId: String,
        status: ExecutionStatus,
        message: String,
        progress: Int
    ) {
        listeners.forEach { listener ->
            try {
                listener.onExecutionStatusChanged(executionId, status, message, progress)
            } catch (e: Exception) {
                logger.error("通知执行状态监听器失败", e)
            }
        }
    }
    
    /**
     * 通知通知监听器
     */
    private fun notifyNotificationListeners(
        title: String,
        content: String,
        type: NotificationType
    ) {
        listeners.forEach { listener ->
            try {
                listener.onNotificationShown(title, content, type)
            } catch (e: Exception) {
                logger.error("通知通知监听器失败", e)
            }
        }
    }
}