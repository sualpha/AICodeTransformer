package cn.suso.aicodetransformer.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressManager
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Git命令执行器
 * 统一处理行结束符问题和容错参数
 */
object GitCommandExecutor {
    
    /**
     * 执行Git diff命令，自动处理行结束符问题
     */
    fun executeGitDiff(
        project: Project,
        staged: Boolean = false,
        filePath: String? = null,
        progressTitle: String = "获取Git差异..."
    ): GitCommandResult {
        return try {
            // 首先检测行结束符问题
            val lineEndingIssue = LineEndingUtils.detectLineEndingIssues(project)
            
            // 如果检测到严重问题，先尝试修复
            if (lineEndingIssue.hasIssues && lineEndingIssue.issueType == LineEndingIssueType.LINE_ENDING_CONFLICT) {
                LineEndingUtils.applyAutoFix(project, lineEndingIssue)
            }
            
            val gitRepositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = gitRepositoryManager.repositories
            
            if (repositories.isEmpty()) {
                return GitCommandResult.error("当前项目不是Git仓库")
            }
            
            val repository = repositories.first()
            val root = repository.root
            
            var result: GitCommandResult = GitCommandResult.error("未知错误")
            
            ProgressManager.getInstance().runProcessWithProgressSynchronously({
                result = executeGitDiffInternal(project, root, staged, filePath, lineEndingIssue)
            }, progressTitle, true, project)
            
            result
        } catch (e: Exception) {
            GitCommandResult.error("执行Git命令异常: ${e.message}")
        }
    }
    
    /**
     * 内部执行Git diff命令的方法
     */
    private fun executeGitDiffInternal(
        project: Project,
        root: VirtualFile,
        staged: Boolean,
        filePath: String?,
        lineEndingIssue: LineEndingIssueResult
    ): GitCommandResult {
        // 定义多种容错参数组合，按优先级排序
        val parameterSets = listOf(
            // 最严格的忽略参数
            listOf("--ignore-cr-at-eol", "--ignore-space-at-eol"),
            // 忽略所有空白差异
            listOf("--ignore-all-space", "--ignore-blank-lines"),
            // 忽略空白变化
            listOf("--ignore-space-change"),
            // 只忽略行尾回车
            listOf("--ignore-cr-at-eol"),
            // 最后尝试无参数版本
            emptyList()
        )
        
        for ((index, params) in parameterSets.withIndex()) {
            try {
                val handler = GitLineHandler(project, root, GitCommand.DIFF)
                
                // 添加容错参数
                params.forEach { handler.addParameters(it) }
                
                // 根据staged参数决定是否查看暂存区的差异
                if (staged) {
                    handler.addParameters("--cached")
                }
                
                // 如果指定了文件路径，添加文件路径参数
                if (filePath != null) {
                    handler.addParameters(filePath)
                }
                
                // 执行git diff命令
                val gitResult = Git.getInstance().runCommand(handler)
                
                if (gitResult.success()) {
                    val output = gitResult.outputAsJoinedString
                    return if (output.isBlank()) {
                        val message = when {
                            filePath != null -> "文件没有变更"
                            staged -> "暂存区没有文件变更"
                            else -> "工作区没有文件变更"
                        }
                        GitCommandResult.success(message)
                    } else {
                        GitCommandResult.success(output)
                    }
                }
                
                // 如果当前参数组合失败，记录错误但继续尝试下一组
                if (index == parameterSets.size - 1) {
                    // 最后一次尝试失败，返回详细错误信息
                    return handleGitDiffFailure(lineEndingIssue, gitResult.errorOutputAsJoinedString)
                }
                
            } catch (e: Exception) {
                if (index == parameterSets.size - 1) {
                    return GitCommandResult.error("Git命令执行异常: ${e.message}")
                }
                // 继续尝试下一组参数
            }
        }
        
        return GitCommandResult.error("所有Git diff尝试都失败")
    }
    
    /**
     * 处理Git diff失败的情况
     */
    private fun handleGitDiffFailure(
        lineEndingIssue: LineEndingIssueResult,
        gitError: String?
    ): GitCommandResult {
        return if (lineEndingIssue.hasIssues) {
            val suggestions = LineEndingUtils.generateFixSuggestions(lineEndingIssue)
            val errorMessage = buildString {
                appendLine("Git差异获取失败，可能是由于行结束符配置问题。")
                appendLine()
                if (gitError != null) {
                    appendLine("Git错误：$gitError")
                    appendLine()
                }
                appendLine("问题详情：${lineEndingIssue.message}")
                appendLine()
                appendLine("建议解决方案：")
                suggestions.forEach { suggestion ->
                    appendLine("• $suggestion")
                }
                appendLine()
                appendLine("您可以手动执行上述建议，或联系项目维护者解决此问题。")
            }
            GitCommandResult.error(errorMessage)
        } else {
            GitCommandResult.error("获取Git差异失败: $gitError")
        }
    }
    

}

/**
 * Git命令执行结果
 */
data class GitCommandResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
) {
    companion object {
        fun success(output: String) = GitCommandResult(true, output)
        fun error(error: String) = GitCommandResult(false, "", error)
    }
    
    fun getDisplayMessage(): String {
        return if (success) output else (error ?: "未知错误")
    }
}