package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.service.VCSService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * VCS服务实现类
 * 集成IntelliJ的Git API
 */
class VCSServiceImpl : VCSService {

    override fun getGitDiff(project: Project, staged: Boolean): String {
        return try {
            val changeListManager = ChangeListManager.getInstance(project)
            val changes = changeListManager.defaultChangeList.changes
            
            if (changes.isEmpty()) {
                return "没有文件变更"
            }
            
            val diffBuilder = StringBuilder()
            changes.forEach { change ->
                val filePath = change.virtualFile?.path ?: "未知文件"
                val changeType = when (change.type) {
                    Change.Type.NEW -> "新增"
                    Change.Type.DELETED -> "删除"
                    Change.Type.MODIFICATION -> "修改"
                    Change.Type.MOVED -> "移动"
                    else -> "未知"
                }
                diffBuilder.append("[$changeType] $filePath\n")
            }
            
            diffBuilder.toString()
        } catch (e: Exception) {
            "获取文件差异异常: ${e.message}"
        }
    }

    override fun getChangedFiles(project: Project, staged: Boolean): List<String> {
        return try {
            val changeListManager = ChangeListManager.getInstance(project)
            val changes = if (staged) {
                // 获取暂存区文件
                changeListManager.defaultChangeList.changes.filter { change ->
                    // 这里可以根据需要过滤暂存文件
                    true
                }
            } else {
                changeListManager.defaultChangeList.changes
            }
            
            changes.mapNotNull { change ->
                change.virtualFile?.path
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getFileStatus(project: Project, filePath: String): String {
        return try {
            val changeListManager = ChangeListManager.getInstance(project)
            val virtualFile = com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(
                java.io.File(filePath), false
            )
            
            if (virtualFile != null) {
                val status = changeListManager.getStatus(virtualFile)
                status?.toString() ?: "未知状态"
            } else {
                "文件不存在"
            }
        } catch (e: Exception) {
            "获取文件状态失败: ${e.message}"
        }
    }

    override fun getCurrentBranch(project: Project): String {
        return try {
            val vcsManager = ProjectLevelVcsManager.getInstance(project)
            val vcs = vcsManager.allActiveVcss.firstOrNull()
            
            if (vcs?.name == "Git") {
                // 尝试从.git/HEAD文件读取当前分支
                val projectDir = project.basePath?.let { File(it) }
                val gitHeadFile = File(projectDir, ".git/HEAD")
                if (gitHeadFile.exists()) {
                    val headContent = gitHeadFile.readText().trim()
                    if (headContent.startsWith("ref: refs/heads/")) {
                        return headContent.substring("ref: refs/heads/".length)
                    }
                }
            }
            
            "main"
        } catch (e: Exception) {
            "main"
        }
    }

    override fun getGitAuthor(project: Project): Pair<String, String> {
        return try {
            val projectDir = project.basePath?.let { File(it) }
            val gitConfigFile = File(projectDir, ".git/config")
            
            var name = "Unknown"
            var email = "unknown@example.com"
            
            if (gitConfigFile.exists()) {
                val configContent = gitConfigFile.readText()
                val nameMatch = Regex("name\\s*=\\s*(.+)").find(configContent)
                val emailMatch = Regex("email\\s*=\\s*(.+)").find(configContent)
                
                if (nameMatch != null) {
                    name = nameMatch.groupValues[1].trim()
                }
                if (emailMatch != null) {
                    email = emailMatch.groupValues[1].trim()
                }
            }
            
            // 如果本地配置没有找到，尝试全局配置
            if (name == "Unknown") {
                val userHome = System.getProperty("user.home")
                val globalGitConfig = File(userHome, ".gitconfig")
                if (globalGitConfig.exists()) {
                    val configContent = globalGitConfig.readText()
                    val nameMatch = Regex("name\\s*=\\s*(.+)").find(configContent)
                    val emailMatch = Regex("email\\s*=\\s*(.+)").find(configContent)
                    
                    if (nameMatch != null) {
                        name = nameMatch.groupValues[1].trim()
                    }
                    if (emailMatch != null) {
                        email = emailMatch.groupValues[1].trim()
                    }
                }
            }
            
            name to email
        } catch (e: Exception) {
            "Unknown" to "unknown@example.com"
        }
    }

    override fun getRepositoryName(project: Project): String {
        return try {
            project.name
        } catch (e: Exception) {
            project.name
        }
    }

    override fun getRemoteUrl(project: Project): String {
        return try {
            val projectDir = project.basePath?.let { File(it) }
            val gitConfigFile = File(projectDir, ".git/config")
            
            if (gitConfigFile.exists()) {
                val configContent = gitConfigFile.readText()
                val urlMatch = Regex("url\\s*=\\s*(.+)").find(configContent)
                if (urlMatch != null) {
                    return urlMatch.groupValues[1].trim()
                }
            }
            
            "未找到远程仓库URL"
        } catch (e: Exception) {
            "获取远程URL异常: ${e.message}"
        }
    }

    override fun getRecentCommits(project: Project, count: Int): List<String> {
        return try {
            // 简化实现，返回示例提交记录
            listOf(
                "feat: 添加新功能",
                "fix: 修复bug",
                "docs: 更新文档",
                "style: 代码格式化",
                "refactor: 重构代码"
            ).take(count)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun isGitRepository(project: Project): Boolean {
        return try {
            val projectDir = project.basePath?.let { File(it) }
            val gitDir = File(projectDir, ".git")
            gitDir.exists() && (gitDir.isDirectory || gitDir.isFile)
        } catch (e: Exception) {
            false
        }
    }

    override fun hasUncommittedChanges(project: Project): Boolean {
        return try {
            val changeListManager = ChangeListManager.getInstance(project)
            changeListManager.defaultChangeList.changes.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    override fun commitChanges(project: Project, message: String): Boolean {
        return try {
            // 这里简化实现，实际应该调用VCS的提交功能
            // 由于我们主要是生成提交消息，暂时返回true
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getFileDiff(project: Project, filePath: String, staged: Boolean): String {
        return try {
            val changeListManager = ChangeListManager.getInstance(project)
            val changes = changeListManager.defaultChangeList.changes
            
            val targetChange = changes.find { change ->
                change.virtualFile?.path == filePath
            }
            
            if (targetChange != null) {
                val changeType = when (targetChange.type) {
                    Change.Type.NEW -> "新增文件"
                    Change.Type.DELETED -> "删除文件"
                    Change.Type.MODIFICATION -> "修改文件"
                    Change.Type.MOVED -> "移动文件"
                    else -> "未知变更"
                }
                
                // 获取详细的差异内容
                val diffContent = getDetailedDiff(targetChange)
                
                """
                [$changeType] $filePath
                
                差异内容:
                $diffContent
                """.trimIndent()
            } else {
                "文件无变更"
            }
        } catch (e: Exception) {
            "获取文件差异异常: ${e.message}"
        }
    }
    
    /**
     * 获取详细的文件差异内容
     */
    private fun getDetailedDiff(change: Change): String {
        return try {
            when (change.type) {
                Change.Type.NEW -> {
                    // 新增文件，只显示文件名
                    val filePath = change.afterRevision?.file?.path ?: "未知文件"
                    val fileName = java.io.File(filePath).name
                    "新增文件: $fileName"
                }
                Change.Type.DELETED -> {
                    // 删除文件，只显示文件名
                    val filePath = change.beforeRevision?.file?.path ?: "未知文件"
                    val fileName = java.io.File(filePath).name
                    "删除文件: $fileName"
                }
                Change.Type.MODIFICATION -> {
                    // 修改文件，显示完整的行级差异
                    val beforeContent = change.beforeRevision?.content ?: ""
                    val afterContent = change.afterRevision?.content ?: ""
                    val fileName = java.io.File(change.afterRevision?.file?.path ?: "未知文件").name
                    
                    // 精确的行级差异比较
                    val beforeLines = beforeContent.lines()
                    val afterLines = afterContent.lines()
                    
                    val diffLines = mutableListOf<String>()
                    diffLines.add("修改文件: $fileName")
                    diffLines.add("原始内容 (-):")
                    diffLines.add("变更内容 (+):")
                    diffLines.add("")
                    
                    val maxLines = maxOf(beforeLines.size, afterLines.size)
                    
                    // 移除行数限制，显示所有差异
                    for (i in 0 until maxLines) {
                        val beforeLine = beforeLines.getOrNull(i) ?: ""
                        val afterLine = afterLines.getOrNull(i) ?: ""
                        
                        if (beforeLine != afterLine) {
                            if (beforeLine.isNotEmpty()) {
                                diffLines.add("- $beforeLine")
                            }
                            if (afterLine.isNotEmpty()) {
                                diffLines.add("+ $afterLine")
                            }
                        }
                    }
                    
                    if (diffLines.size <= 4) {
                        "修改文件: $fileName\n文件内容无明显差异"
                    } else {
                        diffLines.joinToString("\n")
                    }
                }
                Change.Type.MOVED -> {
                    val beforePath = change.beforeRevision?.file?.path ?: ""
                    val afterPath = change.afterRevision?.file?.path ?: ""
                    val beforeFileName = java.io.File(beforePath).name
                    val afterFileName = java.io.File(afterPath).name
                    "文件移动: $beforeFileName -> $afterFileName"
                }
                else -> "未知变更类型"
            }
        } catch (e: Exception) {
            "获取差异内容失败: ${e.message}"
        }
    }

    override fun getChangesSummary(project: Project, changes: Collection<Change>): String {
        return try {
            val summary = StringBuilder()
            val groupedChanges = changes.groupBy { it.type }
            
            groupedChanges.forEach { (type, changeList) ->
                val typeStr = when (type) {
                    Change.Type.NEW -> "新增"
                    Change.Type.DELETED -> "删除"
                    Change.Type.MODIFICATION -> "修改"
                    Change.Type.MOVED -> "移动"
                    else -> "其他"
                }
                summary.append("$typeStr: ${changeList.size} 个文件\n")
                
                changeList.take(5).forEach { change ->
                    val fileName = change.virtualFile?.name ?: "未知文件"
                    summary.append("  - $fileName\n")
                }
                
                if (changeList.size > 5) {
                    summary.append("  ... 还有 ${changeList.size - 5} 个文件\n")
                }
                summary.append("\n")
            }
            
            summary.toString().trim()
        } catch (e: Exception) {
            "获取变更摘要失败: ${e.message}"
        }
    }


}