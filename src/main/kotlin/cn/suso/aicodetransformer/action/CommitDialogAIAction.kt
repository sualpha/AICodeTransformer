package cn.suso.aicodetransformer.action

import cn.suso.aicodetransformer.model.FileChangeInfo
import cn.suso.aicodetransformer.model.CommitSettings
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.service.AIModelService
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.LoggingService
import cn.suso.aicodetransformer.service.VCSService
import cn.suso.aicodetransformer.util.TokenCounter
import cn.suso.aicodetransformer.util.TokenThresholdManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import kotlinx.coroutines.*
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import java.io.File
import java.nio.file.Paths
import com.intellij.vcs.commit.CommitWorkflowUi
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager


/**
 * 在Git提交对话框中添加AI生成commit信息的Action
 */
class CommitDialogAIAction : AnAction("🤖 AI生成", "使用AI自动生成commit信息", null) {

    companion object {
        private val COMMIT_WORKFLOW_UI_KEY = DataKey.create<CommitWorkflowUi>("CommitWorkflowUi")
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 立即移到后台线程执行所有操作
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // 使用CommitSettings中配置的模板
                val configurationService = service<ConfigurationService>()
                val commitSettings = configurationService.getCommitSettings()
                
                // 获取变更的文件数量以选择合适的模板
                val changedFiles = VcsDataKeys.CHANGES.getData(e.dataContext) ?: emptyArray()
                val templateContent = if (changedFiles.size <= 1) {
                    // 单个文件或无文件使用单个文件模板
                    commitSettings.singleFileTemplate
                } else {
                    // 多个文件使用汇总模板
                    commitSettings.summaryTemplate
                }
                

                if (templateContent.isBlank()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(project, "没有可用的commit模板", "提示")
                    }
                    return@executeOnPooledThread
                }
                
                generateAndSetCommitMessage(project, e, templateContent)
            } catch (ex: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "生成commit信息失败: ${ex.message}",
                        "错误"
                    )
                }
            }
        }
    }

    private fun generateAndSetCommitMessage(project: Project, e: AnActionEvent, templateContent: String) {
        val loggingService = service<LoggingService>()

        // 确保在EDT线程中获取UI数据
        if (!ApplicationManager.getApplication().isDispatchThread) {
            ApplicationManager.getApplication().invokeLater {
                generateAndSetCommitMessage(project, e, templateContent)
            }
            return
        }

        // 在EDT中安全地获取UI数据
        val commitWorkflowHandler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER)
        var selectedChanges: Collection<Change>? = null
        
        // 方法1: 尝试通过 VcsDataKeys.CHANGES 获取选中的文件
        try {
            val changedFiles = VcsDataKeys.CHANGES.getData(e.dataContext)
            if (changedFiles != null && changedFiles.isNotEmpty()) {
                selectedChanges = changedFiles.toList()
            }
        } catch (ex: Exception) {
            loggingService.logError(ex, "CommitDialogAIAction - 方法1异常: ${ex.message}")
        }
        
        // 方法2: 尝试通过 COMMIT_WORKFLOW_HANDLER 获取包含的文件
        if (selectedChanges == null || selectedChanges.isEmpty()) {
            if (commitWorkflowHandler is AbstractCommitWorkflowHandler<*, *>) {
                try {
                    val ui = commitWorkflowHandler.ui
                    val includedChanges = ui.getIncludedChanges()
                    selectedChanges = includedChanges
                } catch (ex: Exception) {
                    loggingService.logError(ex, "CommitDialogAIAction - 方法2异常: ${ex.message}")
                }
            }
        }

        // 将获取到的数据传递给后台任务
        val finalSelectedChanges = selectedChanges
        
        // 使用ProgressManager在后台线程中执行，显示进度条
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在生成AI提交信息...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "正在分析文件变更..."
                    indicator.fraction = 0.1
                    
                    if (finalSelectedChanges == null || finalSelectedChanges.isEmpty()) {
                        // 如果没有选中文件，询问用户是否继续
                        val changeListManager = ChangeListManager.getInstance(project)
                        val allChanges = changeListManager.defaultChangeList.changes
                        
                        if (allChanges.isEmpty()) {
                            ApplicationManager.getApplication().invokeLater {
                                Messages.showInfoMessage(project, "没有检测到文件变更", "提示")
                            }
                            return
                        } else {
                            // 询问用户是否要为所有变更文件生成提交信息
                            ApplicationManager.getApplication().invokeLater {
                                val result = Messages.showYesNoDialog(
                                    project,
                                    "没有检测到选中的文件。\n\n是否要为所有 ${allChanges.size} 个变更文件生成提交信息？\n\n点击\"否\"可以取消操作，然后在Git Changes面板中选择特定文件后重试。",
                                    "AI提交信息生成",
                                    "为所有文件生成",
                                    "取消",
                                    Messages.getQuestionIcon()
                                )
                                
                                if (result == Messages.YES) {
                                    // 使用ProgressManager重新启动任务
                                    generateCommitForChangesWithProgress(e, project, allChanges.toList(), templateContent)
                                }
                            }
                            return
                        }
                    }
                    
                    generateCommitForChangesInternal(e, project, finalSelectedChanges.toList(), templateContent, indicator)
                    
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "AI生成提交信息失败: ${ex.message}",
                            "错误"
                        )
                    }
                }
            }
        })
    }
    


    /**
     * 生成单批次提交信息
     */
    private suspend fun generateCommitForSingleBatch(
        prompt: String,
        config: cn.suso.aicodetransformer.model.ModelConfiguration,
        apiKey: String,
        aiModelService: AIModelService
    ): String {

        val result = aiModelService.callModel(config, prompt, apiKey)
        
        return if (result.success) {
            val content = result.content ?: "自动生成的提交信息"
            content
        } else {
            throw Exception(result.errorMessage ?: "AI调用失败")
        }
    }


    
    /**
     * 生成单个批次的提交信息
     */
    private suspend fun generateCommitForBatch(prompt: String, project: Project): String {
        return try {
            val configurationService = service<ConfigurationService>()
            val aiModelService = project.service<AIModelService>()
            
            val config = configurationService.getDefaultModelConfiguration()
                ?: return "配置错误：未找到默认模型配置"
            
            val apiKey = config.apiKey
            if (apiKey.isBlank()) {
                return "配置错误：API密钥未设置"
            }
            
            val result = aiModelService.callModel(config, prompt, apiKey)
            
            if (result.success) {
                result.content ?: "生成失败：返回内容为空"
            } else {
                "生成失败：${result.errorMessage}"
            }
        } catch (e: Exception) {
            "生成异常：${e.message}"
        }
    }
    
    /**
     * 汇总多个批次的结果
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun summarizeBatchResults(batchResults: List<String>, templateContent: String, project: Project): String {
        val configurationService = service<ConfigurationService>()
        
        if (batchResults.size == 1) {
            // 只有一个批次，直接返回结果（去掉批次前缀）
            val result = batchResults.first().substringAfter(": ")
            return result
        }
        
        // 从配置中获取汇总模板
        val commitSettings = configurationService.getCommitSettings()
        val summaryTemplate = commitSettings.summaryTemplate
        
        // 使用模板变量替换
        val summaryPrompt = summaryTemplate.replace("{{batchCommitMessages}}", batchResults.joinToString("\n\n"))
        

        val result = generateCommitForBatch(summaryPrompt, project)
        
        return result
    }

    private fun setCommitMessageToDialog(e: AnActionEvent, message: String): Boolean {
        val cleanedMessage = cleanCommitMessage(message)
        val loggingService = service<LoggingService>()

        try {
            // 方法1: 通过VcsDataKeys获取CommitMessage（推荐方法）
            val commitMessageControl = VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(e.dataContext)

            if (commitMessageControl is CommitMessage) {
                commitMessageControl.setCommitMessage(cleanedMessage)
                return true // 成功设置
            }
            
            // 方法2: 尝试通过CommitWorkflowUi设置（备用方法）
            val commitWorkflowUi = e.getData(COMMIT_WORKFLOW_UI_KEY)

            if (commitWorkflowUi != null) {
                val commitMessageUi = commitWorkflowUi.commitMessageUi

                if (commitMessageUi is CommitMessage) {
                    commitMessageUi.setCommitMessage(cleanedMessage)
                    return true // 成功设置
                }
            }
        } catch (ex: Exception) {
            // 记录错误
            loggingService.logError(ex, "CommitDialogAIAction - 设置提交信息异常: ${ex.message}")
        }
        return false
    }

    private fun cleanCommitMessage(message: String): String {
        var cleaned = message.trim()
        
        // 如果内容被包含在代码块中，提取代码块内容
        val codeBlockPattern = Regex("```[\\s\\S]*?```")
        val codeBlockMatch = codeBlockPattern.find(cleaned)
        if (codeBlockMatch != null) {
            // 提取代码块内容（去掉```标记）
            val codeBlockContent = codeBlockMatch.value
                .replace("```", "")
                .trim()
            
            // 如果代码块内容看起来像提交信息，使用它
            if (codeBlockContent.isNotEmpty() && !codeBlockContent.contains("```")) {
                cleaned = codeBlockContent
            } else {
                // 否则移除代码块
                cleaned = cleaned.replace(codeBlockPattern, "").trim()
            }
        }
        
        return cleaned
            .replace(Regex("^[*\\-+]\\s*", RegexOption.MULTILINE), "") // 移除列表标记
            .lines()
            .filter { it.trim().isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun buildPromptForChanges(fileChanges: List<FileChangeInfo>, templateContent: String): String {
        // 构建文件变更信息
        val changesInfo = fileChanges.joinToString("\n\n") { change ->
            """
文件: ${change.filePath}
变更类型: ${change.changeType}
差异详情:
${change.diff}
            """.trimIndent()
        }
        
        // 构建文件列表
        val filesList = fileChanges.joinToString(", ") { it.filePath }


        // 替换所有内置变量 - 支持两种格式：{{variable}} 和 {VARIABLE}
        var result = templateContent
            // 新格式变量（双大括号）
            .replace("{{changedFiles}}", filesList)
            .replace("{{fileDiffs}}", changesInfo)
        
        // 如果原始模板中没有任何变量，则在末尾添加变更信息以保持兼容性
        if (!templateContent.contains("{{changedFiles}}") && 
            !templateContent.contains("{{fileDiffs}}")) {
            result = """
$templateContent

以下是代码变更信息：
$changesInfo
            """.trimIndent()
        }
        
        return result
    }


    override fun update(e: AnActionEvent) {
        val project = e.project
        
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        // 使用VCS管理器检查Git仓库状态，避免EDT线程中的文件系统操作
        val isGitRepo = try {
            val vcsManager = ProjectLevelVcsManager.getInstance(project)
            vcsManager.allVcsRoots.any { it.vcs?.name == "Git" }
        } catch (e: Exception) {
            false
        }
        
        if (!isGitRepo) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        // 检查是否有选中的文件 - 确保在EDT线程中执行
        var selectedChanges: Collection<Change>? = null
        var hasSelectedFiles = false
        
        // 只在EDT线程中尝试获取选中文件
        if (ApplicationManager.getApplication().isDispatchThread) {
            // 方法1: 尝试通过 COMMIT_WORKFLOW_HANDLER 获取选中文件
            val commitWorkflowHandler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER)
            if (commitWorkflowHandler is AbstractCommitWorkflowHandler<*, *>) {
                try {
                    val ui = commitWorkflowHandler.ui
                    selectedChanges = ui.getIncludedChanges()
                } catch (ex: Exception) {
                    // 忽略异常，尝试其他方法
                }
            }
            
          /*  // 方法2: 备用方案 - 使用传统的VcsDataKeys
            if (selectedChanges == null || selectedChanges.isEmpty()) {
                val vcsSelectedChanges = e.getData(VcsDataKeys.SELECTED_CHANGES)
                selectedChanges = vcsSelectedChanges?.toList()
            }*/
            
            hasSelectedFiles = selectedChanges?.isNotEmpty() == true
        }
        
        // 检查是否有任何变更文件
        val changeListManager = ChangeListManager.getInstance(project)
        val hasAnyChanges = changeListManager.defaultChangeList.changes.isNotEmpty()
        
        e.presentation.isEnabledAndVisible = hasAnyChanges
        
        // 根据是否有选中文件更新按钮文本和描述
        if (hasSelectedFiles && selectedChanges != null) {
            val changesSize = selectedChanges.size
            e.presentation.text = "🤖 AI生成 (${changesSize}个文件)"
            e.presentation.description = "为选中的${changesSize}个文件生成commit信息"
        } else if (hasAnyChanges) {
            val allChangesCount = changeListManager.defaultChangeList.changes.size
            e.presentation.text = "🤖 AI生成 (所有${allChangesCount}个文件)"
            e.presentation.description = "为所有${allChangesCount}个变更文件生成commit信息"
        }
    }
    


    /**
     * 执行自动提交操作
     */
    private fun performAutoCommit(e: AnActionEvent, project: Project, commitSettings: CommitSettings, loggingService: LoggingService, commitMessage: String) {
        // 确保在EDT线程中获取UI数据
        if (!ApplicationManager.getApplication().isDispatchThread) {
            ApplicationManager.getApplication().invokeLater {
                performAutoCommit(e, project, commitSettings, loggingService, commitMessage)
            }
            return
        }
        
        try {

            // 在EDT线程中获取选中的文件，使用与generateAndSetCommitMessage相同的逻辑
            var selectedChanges: Collection<Change>? = null
            
            // 方法1: 尝试通过 VcsDataKeys.CHANGES 获取选中的文件
            try {
                val changedFiles = VcsDataKeys.CHANGES.getData(e.dataContext)
                if (changedFiles != null && changedFiles.isNotEmpty()) {
                    selectedChanges = changedFiles.toList()
                }
            } catch (ex: Exception) {
                loggingService.logError(ex, "CommitDialogAIAction - 自动提交方法1异常: ${ex.message}")
            }
            
            // 方法2: 尝试通过 COMMIT_WORKFLOW_HANDLER 获取包含的文件
            if (selectedChanges?.isEmpty() != false) {
                val commitWorkflowHandler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER)
                if (commitWorkflowHandler is AbstractCommitWorkflowHandler<*, *>) {
                    try {
                        val ui = commitWorkflowHandler.ui
                        val includedChanges = ui.getIncludedChanges()
                        if (includedChanges.isNotEmpty()) {
                            selectedChanges = includedChanges
                        }
                    } catch (ex: Exception) {
                        loggingService.logError(ex, "CommitDialogAIAction - 自动提交方法2异常: ${ex.message}")
                    }
                }
            }
            
            // 检查是否成功获取到选中的文件
            if (selectedChanges?.isEmpty() != false) {
                // 如果没有选中文件，获取暂存区的所有文件
                try {
                    val changeListManager = ChangeListManager.getInstance(project)
                    val allChanges = changeListManager.defaultChangeList.changes
                    
                    if (allChanges.isNotEmpty()) {
                        selectedChanges = allChanges
                    } else {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showInfoMessage(
                                project,
                                "暂存区没有文件可提交，请先添加文件到暂存区。",
                                "无文件可提交"
                            )
                        }
                        return
                    }
                } catch (ex: Exception) {
                    loggingService.logError(ex, "CommitDialogAIAction - 获取暂存区文件异常: ${ex.message}")
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "获取暂存区文件时发生错误: ${ex.message}",
                            "获取文件错误"
                        )
                    }
                    return
                }
            }

            // 使用选中的文件执行提交
            selectedChanges?.let { changes ->
                performFallbackCommit(project, loggingService, commitSettings, commitMessage, changes.toList())
            }
            
        } catch (ex: Exception) {
            loggingService.logError(ex, "自动提交异常: ${ex.message}")
            Messages.showErrorDialog(
                project,
                "自动提交过程中发生异常: ${ex.message}",
                "自动提交异常"
            )
        }
    }

    /**
     * 执行备用提交操作
     */
    private fun performFallbackCommit(project: Project, loggingService: LoggingService, commitSettings: CommitSettings, commitMessage: String, selectedChanges: List<Change>) {
        try {

            // 使用传入的选中文件
            val changes = selectedChanges

            if (changes.isNotEmpty()) {
                if (commitMessage.isNotBlank()) {
                    // 直接执行VCS操作
                    try {
                        // 获取VCS服务
                        val vcsService = project.getService(VCSService::class.java)
                        
                        // 执行提交 - 确保使用带changes参数的方法，只提交选中的文件
                        val success = vcsService.commitChanges(changes, commitMessage)
                        
                        // 在EDT线程中更新UI
                        ApplicationManager.getApplication().invokeLater {
                            if (success) {
                                // 使用实际处理的文件数量而不是changes.size
                                val fileCount = vcsService.getActualFileCount(changes)

                                // 立即刷新VCS状态，确保UI及时更新
                                try {
                                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                                } catch (ex: Exception) {
                                    loggingService.logError(ex, "CommitDialogAIAction - VCS刷新失败: ${ex.message}")
                                }
                                
                                Notifications.Bus.notify(
                                    Notification(
                                        "VCS",
                                        "自动提交成功",
                                        "已成功提交 $fileCount 个文件到版本控制系统",
                                        NotificationType.INFORMATION
                                    ),
                                    project
                                )
                                
                                // 如果启用了自动推送，执行推送
                                if (commitSettings.autoPushEnabled) {
                                    performAutoPush(project, loggingService)
                                }
                            } else {
                                loggingService.logError(Exception("VCS提交失败"), "提交失败 - vcsService.commitChanges返回false")
                                Notifications.Bus.notify(
                                    Notification(
                                        "VCS",
                                        "自动提交失败",
                                        "提交过程中发生错误，请检查日志获取详细信息",
                                        NotificationType.ERROR
                                    ),
                                    project
                                )
                            }
                        }
                        
                    } catch (ex: Exception) {
                        // 在EDT线程中显示错误
                        ApplicationManager.getApplication().invokeLater {
                            loggingService.logError(ex, "后台提交执行失败: ${ex.message}")
                            Notifications.Bus.notify(
                                Notification(
                                    "VCS",
                                    "自动提交异常",
                                    "后台提交执行失败: ${ex.message}",
                                    NotificationType.ERROR
                                ),
                                project
                            )
                        }
                    }
                } else {
                    Notifications.Bus.notify(
                        Notification(
                            "VCS",
                            "自动提交失败",
                            "提交信息为空，无法执行提交",
                            NotificationType.WARNING
                        ),
                        project
                    )
                }
            } else {
                Notifications.Bus.notify(
                    Notification(
                        "VCS",
                        "无需提交",
                        "没有检测到需要提交的变更",
                        NotificationType.INFORMATION
                    ),
                    project
                )
            }
            
        } catch (ex: Exception) {
            loggingService.logError(ex, "备用提交方法执行失败: ${ex.message}")
            Notifications.Bus.notify(
                Notification(
                    "VCS",
                    "自动提交异常",
                    "备用提交方法执行失败: ${ex.message}",
                    NotificationType.ERROR
                ),
                project
            )
        }
    }

    private fun performAutoPush(project: Project, loggingService: LoggingService) {
        try {
            // 直接执行推送操作
            try {
                // 使用VCS服务执行推送
                val vcsService = project.getService(VCSService::class.java)
                val success = vcsService.pushChanges(project)
                
                // 在EDT线程中更新UI
                ApplicationManager.getApplication().invokeLater {
                    if (success) {
                        Notifications.Bus.notify(
                            Notification(
                                "VCS",
                                "自动推送成功",
                                "代码已自动推送到远程仓库",
                                NotificationType.INFORMATION
                            ),
                            project
                        )
                    } else {
                        Notifications.Bus.notify(
                            Notification(
                                "VCS",
                                "自动推送失败",
                                "推送过程中发生错误，请手动推送",
                                NotificationType.WARNING
                            ),
                            project
                        )
                    }
                }
                
            } catch (ex: Exception) {
                // 在EDT线程中显示错误
                ApplicationManager.getApplication().invokeLater {
                    loggingService.logError(ex, "自动推送失败: ${ex.message}")
                    Notifications.Bus.notify(
                        Notification(
                            "VCS",
                            "自动推送失败",
                            "自动推送功能暂时不可用，请手动推送。错误: ${ex.message}",
                            NotificationType.WARNING
                        ),
                        project
                    )
                }
            }
        } catch (ex: Exception) {
            loggingService.logError(ex, "自动推送过程中发生错误: ${ex.message}")
        }
    }



    /**
     * 带进度条的提交信息生成方法（用于用户确认所有文件后的重新启动）
     */
    private fun generateCommitForChangesWithProgress(e: AnActionEvent, project: Project, changes: List<Change>, templateContent: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在生成AI提交信息...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    generateCommitForChangesInternal(e, project, changes, templateContent, indicator)
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "AI生成提交信息失败: ${ex.message}",
                            "错误"
                        )
                    }
                }
            }
        })
    }

    /**
     * 内部的提交信息生成方法，支持进度指示器
     */
    private fun generateCommitForChangesInternal(e: AnActionEvent, project: Project, changes: List<Change>, templateContent: String, indicator: ProgressIndicator) {
        val vcsService = project.service<VCSService>()
        val aiModelService = service<AIModelService>()
        val configurationService = service<ConfigurationService>()
        val loggingService = service<LoggingService>()

        indicator.text = "正在分析文件变更..."
        indicator.fraction = 0.2

           // 在分析与生成diff前，确保所有编辑内容已保存并提交到PSI
        try {
            ApplicationManager.getApplication().invokeAndWait {
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments()
                com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
            }
        } catch (saveEx: Exception) {
            loggingService.logError(saveEx, "CommitDialogAIAction - 文档保存失败: ${saveEx.message}")
        }

        // 分析文件变更
        val fileChanges = mutableListOf<FileChangeInfo>()
        for ((index, change) in changes.withIndex()) {
            indicator.text = "正在分析文件 ${index + 1}/${changes.size}..."
            indicator.fraction = 0.2 + (0.3 * index / changes.size)
            
            // 获取文件路径，对删除文件特殊处理
            val fullPath = when {
                change.virtualFile != null -> change.virtualFile!!.path
                change.beforeRevision != null -> change.beforeRevision!!.file.path
                change.afterRevision != null -> change.afterRevision!!.file.path
                else -> {
                    loggingService.logWarning("无法获取文件路径，跳过此变更", "CommitDialogAIAction")
                    continue
                }
            }
            
            // 获取文件名
            val fileName = when {
                change.virtualFile != null -> change.virtualFile!!.name
                change.beforeRevision != null -> change.beforeRevision!!.file.name
                change.afterRevision != null -> change.afterRevision!!.file.name
                else -> File(fullPath).name
            }
            
            val changeType = when (change.type) {
                Change.Type.NEW -> "新增文件"
                Change.Type.DELETED -> "删除文件"
                Change.Type.MODIFICATION -> "修改文件"
                Change.Type.MOVED -> "移动文件"
                else -> "未知变更"
            }

            val diff = try {
                // 对于所有文件类型，都使用相对路径
                val relativePath = getRelativePathFromProject(project, fullPath)
                if (relativePath != null) {
                    // 确保文件在暂存区中，如果不在则添加到暂存区
                    if (ensureFileInStagingArea(project, relativePath)) {
                        vcsService.getFileDiff(project, relativePath, staged = true)
                    } else {
                        "$changeType: $fileName\n无法将文件添加到暂存区"
                    }
                } else {
                    "$changeType: $fileName\n无法获取文件差异内容"
                }
            } catch (e: Exception) {
                "获取差异失败: ${e.message}"
            }

            fileChanges.add(FileChangeInfo(fileName, changeType, diff))
        }

        indicator.text = "正在验证AI模型配置..."
        indicator.fraction = 0.5

        // 获取AI模型配置
        val config = configurationService.getDefaultModelConfiguration()
        if (config == null) {
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(project, "请先配置AI模型", "错误")
            }
            return
        }

        val apiKey = config.apiKey
        if (apiKey.isBlank()) {
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(project, "请先设置API密钥", "错误")
            }
            return
        }

        // 获取配置设置
        val commitSettings = configurationService.getCommitSettings()

        indicator.text = "正在生成提交信息..."
        indicator.fraction = 0.6


        try {
            // 使用新的TokenThresholdManager进行唯一的阈值判断
            val templateTokens = TokenCounter.estimateTokens(templateContent)
            val decision = TokenThresholdManager.decideProcessingStrategy(
                fileChanges = fileChanges,
                templateTokens = templateTokens,
                modelConfig = config,
                tokenEstimator = { TokenCounter.estimateTokensForDiff(it.diff) }
            )

            val commitMessage = if (decision.needsBatching) {
                // 分批处理
                indicator.text = "正在分批处理文件..."
                indicator.fraction = 0.7
                runBlocking {
                    processBatchCommitWithDecision(decision, templateContent, project, config, apiKey, aiModelService, indicator)
                }
            } else {
                // 单批处理
                indicator.text = "正在调用AI生成提交信息..."
                indicator.fraction = 0.8
                val prompt = buildPromptForChanges(fileChanges, templateContent)
                runBlocking {
                    generateCommitForSingleBatch(prompt, config, apiKey, aiModelService)
                }
            }

            indicator.text = "正在设置提交信息..."
            indicator.fraction = 0.9

            // 回到EDT线程设置提交信息
            ApplicationManager.getApplication().invokeLater {
                val setSuccess = setCommitMessageToDialog(e, commitMessage)

                if (setSuccess) {
                    // 检查是否启用自动提交
                    if (commitSettings.autoCommitEnabled) {
                        performAutoCommit(e, project, commitSettings, loggingService, commitMessage)
                    }
                } else {
                    Messages.showErrorDialog(
                        project,
                        "无法自动设置提交信息到对话框，请手动输入提交信息",
                        "设置失败"
                    )
                }
            }

            indicator.text = "完成"
            indicator.fraction = 1.0

        } catch (ex: Exception) {
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(
                    project,
                    "AI生成提交信息失败: ${ex.message}",
                    "错误"
                )
            }
        }
    }

    /**
     * 基于决策结果的分批处理方法
     */
    private suspend fun processBatchCommitWithDecision(
        decision: TokenThresholdManager.ProcessingDecision,
        templateContent: String,
        project: Project,
        config: ModelConfiguration,
        apiKey: String,
        aiModelService: AIModelService,
        indicator: ProgressIndicator? = null
    ): String {
        val batches = decision.batches ?: throw IllegalArgumentException("批次信息不能为空")
        val batchResults = mutableListOf<String>()
        
        for ((batchIndex, batch) in batches.withIndex()) {
            indicator?.text = "正在处理批次 ${batchIndex + 1}/${batches.size}..."
            indicator?.fraction = 0.7 + (0.15 * batchIndex / batches.size)
            
            val prompt = buildPromptForChanges(batch, templateContent)
            val batchResult = generateCommitForSingleBatch(prompt, config, apiKey, aiModelService)
            batchResults.add("批次 ${batchIndex + 1}: $batchResult")
            
        }
        
        // 汇总所有批次结果
        indicator?.text = "正在汇总批次结果..."
        indicator?.fraction = 0.9

        return summarizeBatchResults(batchResults, templateContent, project)
    }

    /**
     * 确保文件在暂存区中，如果不在则添加到暂存区
     */
    private fun ensureFileInStagingArea(project: Project, relativePath: String): Boolean {
        return try {
            val gitRepositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = gitRepositoryManager.repositories
            
            if (repositories.isEmpty()) {
                return false
            }
            
            val repository = repositories.first()
            val root = repository.root
            
            val handler = GitLineHandler(project, root, GitCommand.ADD)
            handler.addParameters(relativePath)
            
            val result = Git.getInstance().runCommand(handler)
            result.success()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取相对于项目根目录的文件路径
     */
    private fun getRelativePathFromProject(project: Project, absolutePath: String): String? {
        return try {
            val projectBasePath = project.basePath ?: return null
            val projectPath = Paths.get(projectBasePath)
            val filePath = Paths.get(absolutePath)
            
            if (filePath.startsWith(projectPath)) {
                projectPath.relativize(filePath).toString().replace("\\", "/")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}