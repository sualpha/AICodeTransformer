package cn.suso.aicodetransformer.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import java.io.File

/**
 * 行结束符问题检测和修复工具类
 * 用于处理其他项目可能存在的LF/CRLF冲突问题
 */
object LineEndingUtils {
    
    /**
     * 检测项目是否存在行结束符问题
     */
    fun detectLineEndingIssues(project: Project): LineEndingIssueResult {
        return try {
            val gitRepositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = gitRepositoryManager.repositories
            
            if (repositories.isEmpty()) {
                return LineEndingIssueResult(
                    hasIssues = false,
                    issueType = LineEndingIssueType.NOT_GIT_REPO,
                    message = "当前项目不是Git仓库"
                )
            }
            
            val repository = repositories.first()
            val root = repository.root
            
            // 检查Git配置
            val autocrlf = getGitConfig(project, root, "core.autocrlf")
            val eol = getGitConfig(project, root, "core.eol")
            
            // 检查是否存在.gitattributes文件
            val gitattributesFile = File(root.path, ".gitattributes")
            val hasGitattributes = gitattributesFile.exists()
            
            // 尝试执行git diff来检测问题
            val diffResult = testGitDiff(project, root)
            
            when {
                diffResult.hasLineEndingWarnings -> {
                    LineEndingIssueResult(
                        hasIssues = true,
                        issueType = LineEndingIssueType.LINE_ENDING_CONFLICT,
                        message = "检测到行结束符冲突问题",
                        autocrlf = autocrlf,
                        eol = eol,
                        hasGitattributes = hasGitattributes,
                        diffError = diffResult.errorMessage
                    )
                }
                diffResult.failed -> {
                    LineEndingIssueResult(
                        hasIssues = true,
                        issueType = LineEndingIssueType.GIT_DIFF_FAILED,
                        message = "Git diff命令执行失败",
                        autocrlf = autocrlf,
                        eol = eol,
                        hasGitattributes = hasGitattributes,
                        diffError = diffResult.errorMessage
                    )
                }
                else -> {
                    LineEndingIssueResult(
                        hasIssues = false,
                        issueType = LineEndingIssueType.NO_ISSUES,
                        message = "未检测到行结束符问题",
                        autocrlf = autocrlf,
                        eol = eol,
                        hasGitattributes = hasGitattributes
                    )
                }
            }
        } catch (e: Exception) {
            LineEndingIssueResult(
                hasIssues = true,
                issueType = LineEndingIssueType.DETECTION_ERROR,
                message = "检测过程中发生异常: ${e.message}"
            )
        }
    }
    
    /**
     * 获取Git配置值
     */
    private fun getGitConfig(project: Project, root: VirtualFile, configKey: String): String? {
        return try {
            val handler = GitLineHandler(project, root, GitCommand.CONFIG)
            handler.addParameters("--get", configKey)
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) {
                result.outputAsJoinedString.trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 测试git diff命令是否正常工作
     */
    private fun testGitDiff(project: Project, root: VirtualFile): GitDiffTestResult {
        return try {
            val handler = GitLineHandler(project, root, GitCommand.DIFF)
            handler.addParameters("--name-only") // 只获取文件名，减少输出
            val result = Git.getInstance().runCommand(handler)
            
            val errorOutput = result.errorOutputAsJoinedString
            val hasLineEndingWarnings = errorOutput.contains("LF will be replaced by CRLF") ||
                    errorOutput.contains("CRLF will be replaced by LF") ||
                    errorOutput.contains("line ending")
            
            GitDiffTestResult(
                failed = !result.success(),
                hasLineEndingWarnings = hasLineEndingWarnings,
                errorMessage = if (!result.success() || hasLineEndingWarnings) errorOutput else null
            )
        } catch (e: Exception) {
            GitDiffTestResult(
                failed = true,
                hasLineEndingWarnings = false,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * 生成修复建议
     */
    fun generateFixSuggestions(issueResult: LineEndingIssueResult): List<String> {
        val suggestions = mutableListOf<String>()
        
        when (issueResult.issueType) {
            LineEndingIssueType.LINE_ENDING_CONFLICT -> {
                suggestions.add("建议在项目根目录创建.gitattributes文件统一行结束符处理")
                suggestions.add("执行: git config core.autocrlf false")
                suggestions.add("执行: git add --renormalize . 重新规范化文件")
            }
            LineEndingIssueType.GIT_DIFF_FAILED -> {
                suggestions.add("尝试配置Git忽略行结束符差异")
                suggestions.add("检查项目是否存在文件权限问题")
            }
            LineEndingIssueType.NOT_GIT_REPO -> {
                suggestions.add("当前项目不是Git仓库，无法使用Git差异功能")
            }
            else -> {
                // 无需修复建议
            }
        }
        
        return suggestions
    }
    
    /**
     * 自动应用修复（需要用户确认）
     */
    fun applyAutoFix(project: Project, issueResult: LineEndingIssueResult): Boolean {
        if (!issueResult.hasIssues) return true
        
        return try {
            val gitRepositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = gitRepositoryManager.repositories
            
            if (repositories.isEmpty()) return false
            
            val repository = repositories.first()
            val root = repository.root
            
            when (issueResult.issueType) {
                LineEndingIssueType.LINE_ENDING_CONFLICT -> {
                    // 设置Git配置
                    setGitConfig(project, root, "core.autocrlf", "false")
                    setGitConfig(project, root, "core.filemode", "false")
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 设置Git配置
     */
    private fun setGitConfig(project: Project, root: VirtualFile, key: String, value: String): Boolean {
        return try {
            val handler = GitLineHandler(project, root, GitCommand.CONFIG)
            handler.addParameters(key, value)
            val result = Git.getInstance().runCommand(handler)
            result.success()
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * 行结束符问题检测结果
 */
data class LineEndingIssueResult(
    val hasIssues: Boolean,
    val issueType: LineEndingIssueType,
    val message: String,
    val autocrlf: String? = null,
    val eol: String? = null,
    val hasGitattributes: Boolean = false,
    val diffError: String? = null
)

/**
 * 行结束符问题类型
 */
enum class LineEndingIssueType {
    NO_ISSUES,              // 无问题
    LINE_ENDING_CONFLICT,   // 行结束符冲突
    GIT_DIFF_FAILED,        // Git diff失败
    NOT_GIT_REPO,          // 不是Git仓库
    DETECTION_ERROR        // 检测过程错误
}

/**
 * Git diff测试结果
 */
private data class GitDiffTestResult(
    val failed: Boolean,
    val hasLineEndingWarnings: Boolean,
    val errorMessage: String?
)