package cn.suso.aicodetransformer.action

import cn.suso.aicodetransformer.model.FileChangeInfo
import cn.suso.aicodetransformer.model.Template
import cn.suso.aicodetransformer.service.AIModelService
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.LoggingService
import cn.suso.aicodetransformer.service.TemplateService
import cn.suso.aicodetransformer.service.VCSService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.vcs.commit.CommitWorkflowUi
import kotlinx.coroutines.runBlocking

/**
 * 在Git提交对话框中添加AI生成commit信息的Action
 */
class CommitDialogAIAction : AnAction("🤖 AI生成", "使用AI自动生成commit信息", null) {

    companion object {
        private val COMMIT_WORKFLOW_UI_KEY = DataKey.create<CommitWorkflowUi>("CommitWorkflowUi")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        try {
            // 直接使用默认的commit模板
            val templateService = service<TemplateService>()
            val defaultTemplate = templateService.getDefaultCommitTemplate()
            
            if (defaultTemplate == null) {
                Messages.showInfoMessage(project, "没有可用的commit模板", "提示")
                return
            }
            
            generateAndSetCommitMessage(project, e, defaultTemplate)
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "生成commit信息失败: ${ex.message}",
                "错误"
            )
        }
    }

    private fun generateAndSetCommitMessage(project: Project, e: AnActionEvent, template: Template) {
        val vcsService = service<VCSService>()
        val aiModelService = service<AIModelService>()
        val configurationService = service<ConfigurationService>()
        val templateService = service<TemplateService>()
        val loggingService = service<LoggingService>()

        // 获取当前变更的文件
        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.defaultChangeList.changes.toList()
        
        if (changes.isEmpty()) {
            Messages.showInfoMessage(project, "没有检测到文件变更", "提示")
            return
        }

        // 分析文件变更
        val fileChanges = mutableListOf<FileChangeInfo>()
        for (change in changes) {
            val fullPath = change.virtualFile?.path ?: continue
            val fileName = change.virtualFile?.name ?: continue
            val changeType = when (change.type) {
                Change.Type.NEW -> "新增文件"
                Change.Type.DELETED -> "删除文件"
                Change.Type.MODIFICATION -> "修改文件"
                Change.Type.MOVED -> "移动文件"
                else -> "未知变更"
            }
            
            val diff = try {
                vcsService.getFileDiff(project, fullPath, false)
            } catch (e: Exception) {
                "获取差异失败: ${e.message}"
            }
            
            fileChanges.add(FileChangeInfo(fileName, changeType, diff))
        }

        // 获取AI模型配置
        val config = configurationService.getDefaultModelConfiguration()
        if (config == null) {
            Messages.showErrorDialog(project, "请先配置AI模型", "错误")
            return
        }

        val apiKey = config.apiKey
        if (apiKey.isBlank()) {
            Messages.showErrorDialog(project, "请先设置API密钥", "错误")
            return
        }

        // 构建提示词，使用传入的模板
        val prompt = buildPromptForChanges(fileChanges, template, project)

        // 调用AI生成commit信息
        val commitMessage = runBlocking {
            // 添加调试日志
            loggingService.logInfo("开始调用AI生成commit信息", "CommitDialogAIAction - 模型: ${config.name}")
            
            val result = aiModelService.callModel(config, prompt, apiKey)
            
            // 记录调用结果
            loggingService.logInfo(
                "AI调用完成: ${if (result.success) "成功" else "失败"}", 
                "CommitDialogAIAction - 结果: ${result.errorMessage ?: "成功"}"
            )
            
            if (result.success) {
                result.content ?: "自动生成的提交信息"
            } else {
                throw Exception(result.errorMessage ?: "AI调用失败")
            }
        }

        // 直接设置到提交对话框
        val setSuccess = setCommitMessageToDialog(e, commitMessage)
        
        if (!setSuccess) {
            Messages.showInfoMessage(
                project,
                "已生成commit信息并复制到剪贴板，请手动粘贴到提交框中",
                "成功"
            )
        } 
    }

    private fun setCommitMessageToDialog(e: AnActionEvent, message: String): Boolean {
        val cleanedMessage = cleanCommitMessage(message)
        
        try {
            // 方法1: 尝试通过CommitWorkflowUi设置
            val commitWorkflowUi = e.getData(COMMIT_WORKFLOW_UI_KEY)
            if (commitWorkflowUi != null) {
                val commitMessageUi = commitWorkflowUi.commitMessageUi
                if (commitMessageUi is CommitMessage) {
                    commitMessageUi.setCommitMessage(cleanedMessage)
                    return true // 成功设置
                }
            }
            
            // 方法2: 尝试通过VcsDataKeys获取CommitMessage
            val commitMessageControl = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
            if (commitMessageControl is CommitMessage) {
                commitMessageControl.setCommitMessage(cleanedMessage)
                return true
            }
            
        } catch (ex: Exception) {
            // 忽略错误，继续使用剪贴板方案
        }
        
        // 如果直接设置失败，使用剪贴板方案
        copyToClipboard(cleanedMessage)
        return false // 使用了剪贴板方案
    }
    
    private fun copyToClipboard(message: String) {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = java.awt.datatransfer.StringSelection(message)
        clipboard.setContents(stringSelection, null)
    }

    private fun cleanCommitMessage(message: String): String {
        return message
            .trim()
            .replace(Regex("```[\\s\\S]*?```"), "") // 移除代码块
            .replace(Regex("^[*\\-+]\\s*"), "") // 移除列表标记
            .lines()
            .filter { it.trim().isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun buildPromptForChanges(fileChanges: List<FileChangeInfo>, template: Template, project: Project): String {
        val vcsService = service<VCSService>()
        
        // 准备模板变量
        val variables = mutableMapOf<String, String>()
        
        // 基本文件信息
        variables["changedFiles"] = fileChanges.joinToString("\n") { "${it.changeType}: ${it.filePath}" }
        variables["fileDiffs"] = fileChanges.joinToString("\n\n") { change ->
            """
            文件: ${change.filePath}
            变更类型: ${change.changeType}
            差异详情:
            ${change.diff}
            """.trimIndent()
        }
        
        // Git信息已移除，不再需要分支和项目信息
        
        // 统计信息
        variables["fileCount"] = fileChanges.size.toString()
        variables["addedLines"] = "0" // 简化处理，实际可以从diff中解析
        variables["deletedLines"] = "0" // 简化处理，实际可以从diff中解析
        variables["author"] = System.getProperty("user.name") ?: "unknown"
        
        // 替换模板中的变量
        var prompt = template.promptTemplate
        variables.forEach { (key, value) ->
            prompt = prompt.replace("{{$key}}", value)
        }
        
        return prompt
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val isGitRepo = project?.let { project.service<VCSService>().isGitRepository(it) } ?: false
        e.presentation.isEnabledAndVisible = project != null && isGitRepo
    }
}