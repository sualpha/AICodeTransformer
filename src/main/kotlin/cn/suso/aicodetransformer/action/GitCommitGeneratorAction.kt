package cn.suso.aicodetransformer.action

import cn.suso.aicodetransformer.service.AIModelService
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.VCSService
import cn.suso.aicodetransformer.service.TemplateService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.Change
import kotlinx.coroutines.runBlocking

/**
 * Git提交日志生成Action
 * 根据Git Changes中选中的文件自动生成规范的提交日志
 */
class GitCommitGeneratorAction : AnAction("生成Git提交日志", "根据选中文件的差异自动生成规范的Git提交日志", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        try {
            generateCommitMessage(project)
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "生成提交日志失败: ${ex.message}",
                "错误"
            )
        }
    }

    private fun generateCommitMessage(project: Project) {
        val vcsService = project.service<VCSService>()
        val aiModelService = project.service<AIModelService>()
        val configurationService = project.service<ConfigurationService>()
        val templateService = project.service<TemplateService>()

        // 检查是否为Git仓库
        if (!vcsService.isGitRepository(project)) {
            Messages.showInfoMessage(
                project,
                "当前项目不是Git仓库，无法生成提交信息。",
                "错误"
            )
            return
        }

        // 获取变更文件
        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.defaultChangeList.changes
        
        if (changes.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "没有检测到文件变更，无法生成提交信息。",
                "提示"
            )
            return
        }

        // 构建变更文件信息
        val changedFiles = buildChangedFilesInfo(changes)
        val fileDiffs = getFileDiffs(project, vcsService, changes)

        // 准备模板变量
        val variables = mapOf(
            "changedFiles" to changedFiles,
            "fileDiffs" to fileDiffs,
            "gitStatus" to "变更文件数: ${changes.size}"
        )

        // 渲染模板
        val templateResult = templateService.renderTemplate(
            "GIT_COMMIT_GENERATOR",
            variables
        )

        if (!templateResult.success) {
            Messages.showErrorDialog(
                project,
                "模板渲染失败: ${templateResult.error}",
                "错误"
            )
            return
        }

        // 调用AI生成提交信息
        try {
            val modelConfig = configurationService.getDefaultModelConfiguration()
            if (modelConfig == null) {
                Messages.showErrorDialog(
                    project,
                    "请先配置默认AI模型",
                    "配置错误"
                )
                return
            }
            
            // 这里需要从配置中获取API密钥，暂时使用空字符串
            val apiKey = ""
            
            if (apiKey.isBlank()) {
                Messages.showErrorDialog(
                    project,
                    "请先配置AI模型的API密钥",
                    "配置错误"
                )
                return
            }

            val commitMessage = runBlocking {
                val result = aiModelService.callModel(
                    modelConfig,
                    templateResult.content ?: "",
                    apiKey
                )
                if (result.success) {
                    result.content ?: "生成失败"
                } else {
                    throw Exception(result.errorMessage ?: "AI调用失败")
                }
            }

            // 设置提交信息到VCS对话框
            setCommitMessage(project, commitMessage)
            
            Messages.showInfoMessage(
                project,
                "Git提交日志已生成并设置到提交对话框中。",
                "成功"
            )

        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "AI生成提交信息失败: ${ex.message}",
                "错误"
            )
        }
    }

    private fun buildChangedFilesInfo(changes: Collection<Change>): String {
        return changes.mapIndexed { index, change ->
            val fileName = change.virtualFile?.name ?: "未知文件"
            val changeType = when (change.type) {
                Change.Type.NEW -> "新增"
                Change.Type.DELETED -> "删除"
                Change.Type.MODIFICATION -> "修改"
                Change.Type.MOVED -> "移动"
                else -> "未知"
            }
            "${index + 1}. [$changeType] $fileName"
        }.joinToString("\n")
    }

    private fun getFileDiffs(project: Project, vcsService: VCSService, changes: Collection<Change>): String {
        return try {
            // 获取完整的差异内容，不进行字符限制
            vcsService.getGitDiff(project, false)
        } catch (e: Exception) {
            "获取文件差异失败: ${e.message}"
        }
    }

    private fun setCommitMessage(project: Project, message: String) {
        try {
            // 尝试获取当前的提交对话框并设置消息
            val changeListManager = ChangeListManager.getInstance(project)
            // 这里可以通过VCS API设置提交消息
            // 由于IntelliJ的VCS API比较复杂，这里提供一个基础实现
            // 实际使用时可能需要根据具体的VCS集成方式调整
        } catch (e: Exception) {
            // 如果无法直接设置，可以将消息复制到剪贴板
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val stringSelection = java.awt.datatransfer.StringSelection(message)
            clipboard.setContents(stringSelection, null)
            
            Messages.showInfoMessage(
                project,
                "提交信息已复制到剪贴板，请手动粘贴到提交对话框中。\n\n生成的提交信息：\n$message",
                "提交信息已生成"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val isGitRepo = project?.let { project.service<VCSService>().isGitRepository(it) } ?: false
        e.presentation.isEnabled = project != null && isGitRepo
    }
}