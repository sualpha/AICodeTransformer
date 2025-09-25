package cn.suso.aicodetransformer.notification

import cn.suso.aicodetransformer.service.ShortcutRecoveryService
import cn.suso.aicodetransformer.service.ShortcutValidationResult
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * 快捷键通知服务
 * 用于显示快捷键相关的通知和提示
 */
class ShortcutNotificationService {
    
    companion object {
        private const val NOTIFICATION_GROUP_ID = "AICodeTransformer.Shortcuts"
        
        private val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
    }
    
    private val shortcutRecoveryService: ShortcutRecoveryService = service()
    
    /**
     * 显示快捷键恢复成功通知
     */
    fun showRecoverySuccessNotification(project: Project?, recoveredCount: Int) {
        if (recoveredCount <= 0) return
        
        val content = "成功恢复了 $recoveredCount 个快捷键配置"
        val notification = notificationGroup.createNotification(
            "快捷键恢复完成",
            content,
            NotificationType.INFORMATION
        )
        
        notification.addAction(object : AnAction("查看详情") {
            override fun actionPerformed(e: AnActionEvent) {
                showValidationDialog(project)
                notification.expire()
            }
        })
        
        notification.notify(project)
    }
    
    /**
     * 显示快捷键冲突警告通知
     */
    fun showConflictWarningNotification(project: Project?, conflictingShortcuts: List<String>) {
        if (conflictingShortcuts.isEmpty()) return
        
        val content = "发现 ${conflictingShortcuts.size} 个快捷键冲突，可能影响功能使用"
        val notification = notificationGroup.createNotification(
            "快捷键冲突警告",
            content,
            NotificationType.WARNING
        )
        
        notification.addAction(object : AnAction("查看冲突") {
            override fun actionPerformed(e: AnActionEvent) {
                showConflictDialog(project, conflictingShortcuts)
                notification.expire()
            }
        })
        
        notification.addAction(object : AnAction("忽略") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.expire()
            }
        })
        
        notification.notify(project)
    }
    
    /**
     * 显示快捷键缺失提示通知
     */
    fun showMissingShortcutsNotification(project: Project?, missingCount: Int) {
        if (missingCount <= 0) return
        
        val content = "发现 $missingCount 个模板缺少快捷键配置"
        val notification = notificationGroup.createNotification(
            "快捷键配置提示",
            content,
            NotificationType.INFORMATION
        )
        
        notification.addAction(object : AnAction("配置快捷键") {
            override fun actionPerformed(e: AnActionEvent) {
                openShortcutSettings()
                notification.expire()
            }
        })
        
        notification.notify(project)
    }
    
    /**
     * 显示快捷键验证结果对话框
     */
    private fun showValidationDialog(project: Project?) {
        val validationResult = shortcutRecoveryService.validateShortcutIntegrity()
        
        val message = buildString {
            appendLine("快捷键配置验证结果：")
            appendLine()
            appendLine(validationResult.message)
            
            if (validationResult.missingShortcuts.isNotEmpty()) {
                appendLine()
                appendLine("缺少快捷键的模板：")
                validationResult.missingShortcuts.forEach { templateName ->
                    appendLine("• $templateName")
                }
            }
            
            if (validationResult.conflictingShortcuts.isNotEmpty()) {
                appendLine()
                appendLine("冲突的快捷键：")
                validationResult.conflictingShortcuts.forEach { conflict ->
                    appendLine("• $conflict")
                }
            }
        }
        
        Messages.showInfoMessage(
            project,
            message,
            "快捷键验证结果"
        )
    }
    
    /**
     * 显示快捷键冲突详情对话框
     */
    private fun showConflictDialog(project: Project?, conflicts: List<String>) {
        val message = buildString {
            appendLine("检测到以下快捷键冲突：")
            appendLine()
            conflicts.forEach { conflict ->
                appendLine("• $conflict")
            }
            appendLine()
            appendLine("建议：")
            appendLine("1. 在设置中重新配置冲突的快捷键")
            appendLine("2. 或者禁用冲突的功能")
        }
        
        val result = Messages.showYesNoDialog(
            project,
            message,
            "快捷键冲突",
            "打开设置",
            "稍后处理",
            Messages.getWarningIcon()
        )
        
        if (result == Messages.YES) {
            openShortcutSettings()
        }
    }
    
    /**
     * 打开快捷键设置页面
     */
    private fun openShortcutSettings() {
        // 打开插件的快捷键设置页面
        com.intellij.openapi.options.ShowSettingsUtil.getInstance()
            .showSettingsDialog(null, "AICodeTransformer")
    }
    
    /**
     * 验证并显示快捷键状态
     */
    fun validateAndNotify(project: Project?) {
        val validationResult = shortcutRecoveryService.validateShortcutIntegrity()
        
        if (!validationResult.isValid) {
            if (validationResult.conflictingShortcuts.isNotEmpty()) {
                showConflictWarningNotification(project, validationResult.conflictingShortcuts)
            }
            
            if (validationResult.missingShortcuts.isNotEmpty()) {
                showMissingShortcutsNotification(project, validationResult.missingShortcuts.size)
            }
        }
    }
}