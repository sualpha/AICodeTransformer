package cn.suso.aicodetransformer.action

import cn.suso.aicodetransformer.i18n.I18n
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
import com.intellij.ide.plugins.PluginManagerCore
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
import com.intellij.openapi.extensions.PluginId


/**
 * 在Git提交对话框中添加AI生成commit信息的Action
 */
class CommitDialogAIAction : AnAction(I18n.t("commit.aiAction.text"), I18n.t("commit.aiAction.description"), null) {

    companion object {
        private val COMMIT_WORKFLOW_UI_KEY = DataKey.create<CommitWorkflowUi>("CommitWorkflowUi")
    }

    private fun isPluginEnabled(pluginId: PluginId): Boolean {
        if (PluginManagerCore.getPlugin(pluginId) == null) {
            return false
        }
        return !PluginManagerCore.isDisabled(pluginId)
    }

    private fun tr(key: String, vararg args: Any): String = I18n.t(key, *args)

    private fun showInfo(project: Project, messageKey: String, titleKey: String = "notice", vararg args: Any) {
        Messages.showInfoMessage(project, tr(messageKey, *args), tr(titleKey))
    }

    private fun showError(project: Project, messageKey: String, vararg args: Any) {
        Messages.showErrorDialog(project, tr(messageKey, *args), tr("commit.aiAction.errorTitle"))
    }

    private fun showError(project: Project, messageKey: String, titleKey: String, vararg args: Any) {
        Messages.showErrorDialog(project, tr(messageKey, *args), tr(titleKey))
    }

    private fun notify(project: Project, titleKey: String, contentKey: String, type: NotificationType, vararg args: Any) {
        Notifications.Bus.notify(
            Notification(
                "VCS",
                tr(titleKey, *args),
                tr(contentKey, *args),
                type
            ),
            project
        )
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
                        showInfo(project, "commit.aiAction.noTemplate")
                    }
                    return@executeOnPooledThread
                }

                generateAndSetCommitMessage(project, e, templateContent)
            } catch (ex: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    showError(project, "commit.aiAction.generateError", ex.message ?: "")
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
        val hasCommitUi = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null ||
            e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER) != null ||
            e.getData(COMMIT_WORKFLOW_UI_KEY) != null
        if (!hasCommitUi) {
            e.presentation.isEnabledAndVisible = false
            return
        }

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
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, tr("commit.aiAction.progress.title"), true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = tr("commit.aiAction.progress.analyzing")
                    indicator.fraction = 0.1
                    
                    if (finalSelectedChanges == null || finalSelectedChanges.isEmpty()) {
                        // 如果没有选中文件，询问用户是否继续
                        val changeListManager = ChangeListManager.getInstance(project)
                        val allChanges = changeListManager.defaultChangeList.changes
                        
                        if (allChanges.isEmpty()) {
                            ApplicationManager.getApplication().invokeLater {
                                showInfo(project, "commit.aiAction.noChanges")
                            }
                            return
                        } else {
                            // 询问用户是否要为所有变更文件生成提交信息
                            ApplicationManager.getApplication().invokeLater {
                                val result = Messages.showYesNoDialog(
                                    project,
                                    tr("commit.aiAction.generateAll.prompt", allChanges.size),
                                    tr("commit.aiAction.generateAll.title"),
                                    tr("commit.aiAction.generateAll.yes"),
                                    tr("commit.aiAction.generateAll.no"),
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
                        showError(project, "commit.aiAction.generateFailed", ex.message ?: "")
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
            val content = result.content ?: tr("commit.aiAction.result.autoGenerated")
            content
        } else {
            throw Exception(result.errorMessage ?: tr("commit.aiAction.error.aiCallFailed"))
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
                ?: return tr("commit.aiAction.error.noDefaultConfig")
            
            // 使用getApiKey方法获取API密钥,确保内置模型的密钥被正确解密
            val apiKey = configurationService.getApiKey(config.id) ?: ""
            if (apiKey.isBlank()) {
                return tr("commit.aiAction.error.apiKeyMissing")
            }
            
            val result = aiModelService.callModel(config, prompt, apiKey)
            
            if (result.success) {
                result.content ?: tr("commit.aiAction.error.emptyResponse")
            } else {
                tr("commit.aiAction.error.batchFailed", result.errorMessage ?: "")
            }
        } catch (e: Exception) {
            tr("commit.aiAction.error.batchException", e.message ?: "")
        }
    }
    
    /**
     * 汇总多个批次的结果
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun summarizeBatchResults(batchResults: List<String>, templateContent: String, project: Project): String {
        val configurationService = service<ConfigurationService>()
        
        if (batchResults.size == 1) {
            return batchResults.first().substringAfter(": ")
        }

        val commitSettings = configurationService.getCommitSettings()
        val summaryTemplate = commitSettings.summaryTemplate
        val joinedResults = batchResults.joinToString("\n\n")

        var summaryPrompt = summaryTemplate.replace("{{batchCommitMessages}}", joinedResults)
        summaryPrompt = summaryPrompt.replace("{BATCH_COMMIT_MESSAGES}", joinedResults)

        return generateCommitForBatch(summaryPrompt, project)
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
            listOf(
                tr("commit.aiAction.prompt.fileLine", change.filePath),
                tr("commit.aiAction.prompt.changeTypeLine", change.changeType),
                tr("commit.aiAction.prompt.diffHeader"),
                change.diff
            ).joinToString("\n")
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
            result = buildString {
                append(templateContent)
                append("\n\n")
                append(tr("commit.aiAction.prompt.appendChangesHeader"))
                append("\n")
                append(changesInfo)
            }
        }
        
        return result
    }


    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val gitPluginInstalled = isPluginEnabled(PluginId.getId("Git4Idea"))
        if (!gitPluginInstalled) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val hasGitRepository = try {
            GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
        } catch (_: Exception) {
            false
        }
        if (!hasGitRepository) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        var selectedChanges: Collection<Change>? = null
        try {
            val changedFiles = VcsDataKeys.CHANGES.getData(e.dataContext)
            if (!changedFiles.isNullOrEmpty()) {
                selectedChanges = changedFiles.toList()
            }
        } catch (_: Exception) {
            // ignore, fallback below
        }

        if (selectedChanges.isNullOrEmpty()) {
            val commitWorkflowHandler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER)
            if (commitWorkflowHandler is AbstractCommitWorkflowHandler<*, *>) {
                try {
                    selectedChanges = commitWorkflowHandler.ui.getIncludedChanges()
                } catch (_: Exception) {
                    // ignore
                }
            }
        }

        val hasSelectedFiles = selectedChanges?.isNotEmpty() == true
        val changeListManager = ChangeListManager.getInstance(project)
        val hasAnyChanges = changeListManager.defaultChangeList.changes.isNotEmpty()

        e.presentation.isEnabledAndVisible = hasAnyChanges

        if (!hasAnyChanges) {
            e.presentation.text = tr("commit.aiAction.text")
            e.presentation.description = tr("commit.aiAction.description")
            return
        }

        if (hasSelectedFiles) {
            val changesSize = selectedChanges!!.size
            e.presentation.text = tr("commit.aiAction.presentation.selected", changesSize)
            e.presentation.description = tr("commit.aiAction.presentation.selected.desc", changesSize)
        } else {
            val allChangesCount = changeListManager.defaultChangeList.changes.size
            e.presentation.text = tr("commit.aiAction.presentation.all", allChangesCount)
            e.presentation.description = tr("commit.aiAction.presentation.all.desc", allChangesCount)
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
                            showInfo(project, "commit.aiAction.autoCommit.noStaged")
                        }
                        return
                    }
                } catch (ex: Exception) {
                    loggingService.logError(ex, "CommitDialogAIAction - 获取暂存区文件异常: ${ex.message}")
                    ApplicationManager.getApplication().invokeLater {
                        showError(project, "commit.aiAction.autoCommit.fetchStagedError", ex.message ?: "")
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
            showError(project, "commit.aiAction.autoCommit.exception", ex.message ?: "")
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
                                
                                notify(
                                    project,
                                    "commit.aiAction.notify.autoCommit.success.title",
                                    "commit.aiAction.notify.autoCommit.success.content",
                                    NotificationType.INFORMATION,
                                    fileCount
                                )
                                
                                // 如果启用了自动推送，执行推送
                                if (commitSettings.autoPushEnabled) {
                                    performAutoPush(project, loggingService)
                                }
                            } else {
                                loggingService.logError(Exception("VCS提交失败"), "提交失败 - vcsService.commitChanges返回false")
                                notify(
                                    project,
                                    "commit.aiAction.notify.autoCommit.failure.title",
                                    "commit.aiAction.notify.autoCommit.failure.content",
                                    NotificationType.ERROR
                                )
                            }
                        }
                        
                    } catch (ex: Exception) {
                        // 在EDT线程中显示错误
                        ApplicationManager.getApplication().invokeLater {
                            loggingService.logError(ex, "后台提交执行失败: ${ex.message}")
                            notify(
                                project,
                                "commit.aiAction.notify.autoCommit.exception.title",
                                "commit.aiAction.notify.autoCommit.exception.content",
                                NotificationType.ERROR,
                                ex.message ?: ""
                            )
                        }
                    }
                } else {
                    notify(
                        project,
                        "commit.aiAction.notify.autoCommit.failure.title",
                        "commit.aiAction.notify.autoCommit.emptyMessage",
                        NotificationType.WARNING
                    )
                }
            } else {
                notify(
                    project,
                    "commit.aiAction.notify.autoCommit.noChanges.title",
                    "commit.aiAction.notify.autoCommit.noChanges.content",
                    NotificationType.INFORMATION
                )
            }
            
        } catch (ex: Exception) {
            loggingService.logError(ex, "备用提交方法执行失败: ${ex.message}")
            notify(
                project,
                "commit.aiAction.notify.autoCommit.exception.title",
                "commit.aiAction.notify.autoCommit.fallbackError",
                NotificationType.ERROR,
                ex.message ?: ""
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
                        notify(
                            project,
                            "commit.aiAction.notify.autoPush.success.title",
                            "commit.aiAction.notify.autoPush.success.content",
                            NotificationType.INFORMATION
                        )
                    } else {
                        notify(
                            project,
                            "commit.aiAction.notify.autoPush.failure.title",
                            "commit.aiAction.notify.autoPush.failure.content",
                            NotificationType.WARNING
                        )
                    }
                }
                
            } catch (ex: Exception) {
                // 在EDT线程中显示错误
                ApplicationManager.getApplication().invokeLater {
                    loggingService.logError(ex, "自动推送失败: ${ex.message}")
                    notify(
                        project,
                        "commit.aiAction.notify.autoPush.failure.title",
                        "commit.aiAction.notify.autoPush.unavailable.content",
                        NotificationType.WARNING,
                        ex.message ?: ""
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
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, tr("commit.aiAction.progress.title"), true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    generateCommitForChangesInternal(e, project, changes, templateContent, indicator)
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        showError(project, "commit.aiAction.generateFailed", ex.message ?: "")
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

        indicator.text = tr("commit.aiAction.progress.analyzingChanges")
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
            indicator.text = tr("commit.aiAction.progress.analyzingFile", index + 1, changes.size)
            indicator.fraction = 0.2 + (0.3 * index / changes.size)
            
            // 获取文件路径，对删除文件特殊处理
            val fullPath = when {
                change.virtualFile != null -> change.virtualFile!!.path
                change.beforeRevision != null -> change.beforeRevision!!.file.path
                change.afterRevision != null -> change.afterRevision!!.file.path
                else -> {
                    loggingService.logWarning("Unable to resolve file path, skipping change", "CommitDialogAIAction")
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
                Change.Type.NEW -> tr("commit.aiAction.changeType.new")
                Change.Type.DELETED -> tr("commit.aiAction.changeType.deleted")
                Change.Type.MODIFICATION -> tr("commit.aiAction.changeType.modified")
                Change.Type.MOVED -> tr("commit.aiAction.changeType.moved")
                else -> tr("commit.aiAction.changeType.unknown")
            }

            val diffLine = tr("commit.aiAction.diff.fileLine", changeType, fileName)
            val diff = try {
                // 对于所有文件类型，都使用相对路径
                val relativePath = getRelativePathFromProject(project, fullPath)
                if (relativePath != null) {
                    // 确保文件在暂存区中，如果不在则添加到暂存区
                    if (ensureFileInStagingArea(project, relativePath)) {
                        vcsService.getFileDiff(project, relativePath, staged = true)
                    } else {
                        "$diffLine\n${tr("commit.aiAction.error.ensureStage")}"
                    }
                } else {
                    "$diffLine\n${tr("commit.aiAction.error.diffUnavailable")}" 
                }
            } catch (e: Exception) {
                tr("commit.aiAction.error.diffFailed", e.message ?: "")
            }

            fileChanges.add(FileChangeInfo(fileName, changeType, diff))
        }

        indicator.text = tr("commit.aiAction.progress.validatingConfig")
        indicator.fraction = 0.5

        // 获取AI模型配置
        val config = configurationService.getDefaultModelConfiguration()
        if (config == null) {
            ApplicationManager.getApplication().invokeLater {
                showError(project, "commit.aiAction.error.configureModelFirst")
            }
            return
        }

        // 使用getApiKey方法获取API密钥,确保内置模型的密钥被正确解密
        val apiKey = configurationService.getApiKey(config.id) ?: ""
        if (apiKey.isBlank()) {
            ApplicationManager.getApplication().invokeLater {
                showError(project, "commit.aiAction.error.apiKeyRequired")
            }
            return
        }

        // 获取配置设置
        val commitSettings = configurationService.getCommitSettings()

        indicator.text = tr("commit.aiAction.progress.generatingMessage")
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
                indicator.text = tr("commit.aiAction.progress.processingBatches")
                indicator.fraction = 0.7
                runBlocking {
                    processBatchCommitWithDecision(decision, templateContent, project, config, apiKey, aiModelService, indicator)
                }
            } else {
                // 单批处理
                indicator.text = tr("commit.aiAction.progress.callingModel")
                indicator.fraction = 0.8
                val prompt = buildPromptForChanges(fileChanges, templateContent)
                runBlocking {
                    generateCommitForSingleBatch(prompt, config, apiKey, aiModelService)
                }
            }

            indicator.text = tr("commit.aiAction.progress.settingMessage")
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
                    showError(project, "commit.aiAction.error.setMessageFailed", "commit.aiAction.error.setMessageFailed.title")
                }
            }

            indicator.text = tr("commit.aiAction.progress.done")
            indicator.fraction = 1.0

        } catch (ex: Exception) {
            ApplicationManager.getApplication().invokeLater {
                showError(project, "commit.aiAction.generateFailed", ex.message ?: "")
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
        val batches = decision.batches ?: throw IllegalArgumentException("Batch information must not be null")
        val batchResults = mutableListOf<String>()
        
        for ((batchIndex, batch) in batches.withIndex()) {
            indicator?.text = tr("commit.aiAction.progress.processingBatch", batchIndex + 1, batches.size)
            indicator?.fraction = 0.7 + (0.15 * batchIndex / batches.size)
            
            val prompt = buildPromptForChanges(batch, templateContent)
            val batchResult = generateCommitForSingleBatch(prompt, config, apiKey, aiModelService)
            batchResults.add(tr("commit.aiAction.batch.resultLine", batchIndex + 1, batchResult))
            
        }
        
        // 汇总所有批次结果
        indicator?.text = tr("commit.aiAction.progress.summarizingBatches")
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