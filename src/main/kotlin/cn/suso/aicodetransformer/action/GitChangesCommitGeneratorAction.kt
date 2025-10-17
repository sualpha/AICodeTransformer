package cn.suso.aicodetransformer.action

import cn.suso.aicodetransformer.model.FileChangeInfo
import cn.suso.aicodetransformer.service.AIModelService
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.TemplateService
import cn.suso.aicodetransformer.service.VCSService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.runBlocking
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Git Changes面板中为选中文件生成commit信息的Action
 */
class GitChangesCommitGeneratorAction : AnAction("为选中文件生成Commit信息") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val changes = e.getData(VcsDataKeys.SELECTED_CHANGES) ?: return
        
        if (changes.isEmpty()) {
            Messages.showInfoMessage(project, "请先选择要生成commit信息的文件", "提示")
            return
        }
        
        generateCommitMessageForChanges(project, changes.toList())
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val changes = e.getData(VcsDataKeys.SELECTED_CHANGES)
        
        // 只有在项目存在且有选中的变更文件时才启用此Action
        e.presentation.isEnabledAndVisible = project != null && 
                                           changes != null && 
                                           changes.isNotEmpty()
    }

    private fun generateCommitMessageForChanges(project: Project, changes: List<Change>) {
        val vcsService = project.service<VCSService>()
        val aiModelService = project.service<AIModelService>()
        val configurationService = project.service<ConfigurationService>()
        val templateService = project.service<TemplateService>()

        // 检查是否为Git仓库
        if (!vcsService.isGitRepository(project)) {
            Messages.showInfoMessage(project, "当前项目不是Git仓库", "错误")
            return
        }

        try {
            // 构建选中文件的变更信息
            val fileChanges = mutableListOf<FileChangeInfo>()
            
            for (change in changes) {
                val beforePath = change.beforeRevision?.file?.path
                val afterPath = change.afterRevision?.file?.path
                val fullPath = afterPath ?: beforePath ?: continue
                val fileName = java.io.File(fullPath).name
                
                val changeType = when {
                    change.beforeRevision == null -> "新增"
                    change.afterRevision == null -> "删除"
                    else -> "修改"
                }
                
                // 获取文件差异
                val diff = try {
                    vcsService.getFileDiff(project, fullPath)
                } catch (ex: Exception) {
                    "无法获取文件差异: ${ex.message}"
                }
                
                fileChanges.add(FileChangeInfo(fileName, changeType, diff))
            }

            // 获取默认模型配置
            val config = configurationService.getDefaultModelConfiguration()
            if (config == null) {
                Messages.showErrorDialog(project, "请先配置AI模型", "错误")
                return
            }

            // 获取API密钥
            val apiKey = config.apiKey
            if (apiKey.isBlank()) {
                Messages.showErrorDialog(project, "请先设置API密钥", "错误")
                return
            }

            // 获取提交信息模板
            val template = templateService.getTemplateById("commit_message")?.promptTemplate ?: "请生成一个简洁明了的Git提交信息"
            
            // 构建提示词
            val prompt = buildPromptForSelectedFiles(fileChanges, template)

            // 调用AI生成提交信息
            val commitMessage = runBlocking {
                val result = aiModelService.callModel(config, prompt, apiKey)
                
                if (result.success) {
                    result.content ?: "自动生成的提交信息"
                } else {
                    throw Exception(result.errorMessage ?: "AI调用失败")
                }
            }

            // 复制到剪贴板
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val stringSelection = StringSelection(commitMessage)
            clipboard.setContents(stringSelection, null)
            
            Messages.showInfoMessage(
                project,
                "已为选中的 ${fileChanges.size} 个文件生成commit信息并复制到剪贴板",
                "成功"
            )

        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "生成commit信息失败: ${ex.message}",
                "错误"
            )
        }
    }

    private fun buildPromptForSelectedFiles(fileChanges: List<FileChangeInfo>, template: String): String {
        val changesText = fileChanges.joinToString("\n" + "=".repeat(50) + "\n") { change ->
            """
            📁 文件: ${change.filePath}
            🔄 变更类型: ${change.changeType}
            
            📝 差异详情:
            ${change.diff}
            """.trimIndent()
        }

        return """
        $template
        
        请根据以下选中文件的详细变更信息生成一个规范的Git提交信息：
        
        $changesText
        
        📋 提交信息生成要求：
        1. **格式规范**: 使用 "类型(范围): 描述" 的格式
        2. **类型选择**: 
           - feat: 新功能
           - fix: 修复bug
           - docs: 文档更新
           - style: 代码格式调整
           - refactor: 重构代码
           - test: 测试相关
           - chore: 构建/工具链相关
        3. **描述要求**: 
           - 使用中文描述
           - 简洁明了，突出核心变更
           - 不超过50个字符
           - 动词开头，描述做了什么
        4. **输出格式**: 只返回一行提交信息，不要其他内容
        
        示例格式：
        - feat(用户管理): 添加用户注册功能
        - fix(登录): 修复密码验证错误
        - docs(README): 更新安装说明
        """.trimIndent()
    }
}