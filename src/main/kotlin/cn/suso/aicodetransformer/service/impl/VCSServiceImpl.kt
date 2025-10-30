package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.service.VCSService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.progress.ProgressManager
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import cn.suso.aicodetransformer.util.LineEndingUtils
import cn.suso.aicodetransformer.util.LineEndingIssueType
import cn.suso.aicodetransformer.util.GitCommandExecutor
import com.intellij.openapi.ui.Messages

/**
 * VCS服务实现类
 * 集成IntelliJ的Git API
 */
class VCSServiceImpl : VCSService {

    override fun getFileDiff(project: Project, filePath: String, staged: Boolean): String {
        val result = GitCommandExecutor.executeGitDiff(
            project = project,
            staged = staged,
            filePath = filePath,
            progressTitle = "获取文件差异..."
        )
        return result.getDisplayMessage()
    }

    /**
     * 从Change对象中提取文件路径
     */
    private fun getFilePathFromChange(change: Change, root: VirtualFile): String? {
        val virtualFile = change.virtualFile
        if (virtualFile != null) {
            // 使用虚拟文件的相对路径
            return VfsUtilCore.getRelativePath(virtualFile, root)
        } else {
            // 备用方案：使用revision的路径
            val filePath = when {
                change.afterRevision != null -> {
                    val path = change.afterRevision!!.file.path
                    // 转换为相对于仓库根目录的路径
                    if (path.startsWith(root.path)) {
                        path.substring(root.path.length + 1).replace('\\', '/')
                    } else {
                        path
                    }
                }
                change.beforeRevision != null -> {
                    val path = change.beforeRevision!!.file.path
                    // 转换为相对于仓库根目录的路径
                    if (path.startsWith(root.path)) {
                        path.substring(root.path.length + 1).replace('\\', '/')
                    } else {
                        path
                    }
                }
                else -> null
            }
            return filePath
        }
    }

    override fun getActualFileCount(changes: List<Change>): Int {
        return try {
            if (changes.isEmpty()) {
                return 0
            }

            // 从Change对象中获取项目实例 - 避免慢操作
            val project = changes.firstOrNull()?.let { change ->
                // 尝试从虚拟文件获取项目
                val virtualFile = change.virtualFile ?: change.beforeRevision?.file?.virtualFile ?: change.afterRevision?.file?.virtualFile
                
                if (virtualFile != null) {
                    // 使用虚拟文件查找项目
                    ApplicationManager.getApplication().runReadAction<Project?> {
                        ProjectManager.getInstance().openProjects.find { project ->
                            try {
                                val projectBasePath = project.basePath
                                if (projectBasePath != null) {
                                    val filePath = virtualFile.path
                                    filePath.startsWith(projectBasePath)
                                } else {
                                    false
                                }
                            } catch (e: Exception) {
                                false
                            }
                        }
                    }
                } else {
                    // 虚拟文件为null时，尝试从revision路径获取项目
                    val filePath = when {
                        change.beforeRevision != null -> change.beforeRevision!!.file.path
                        change.afterRevision != null -> change.afterRevision!!.file.path
                        else -> null
                    }
                    
                    filePath?.let { path ->
                        ApplicationManager.getApplication().runReadAction<Project?> {
                            ProjectManager.getInstance().openProjects.find { project ->
                                try {
                                    val projectBasePath = project.basePath
                                    if (projectBasePath != null) {
                                        path.startsWith(projectBasePath)
                                    } else {
                                        false
                                    }
                                } catch (e: Exception) {
                                    false
                                }
                            }
                        }
                    }
                }
            }

            if (project == null) {
                println("VCSServiceImpl: getActualFileCount - 无法获取项目实例")
                return changes.size // 回退到原始数量
            }

            // 获取Git仓库
            val repositories = GitRepositoryManager.getInstance(project).repositories

            if (repositories.isEmpty()) {
                println("VCSServiceImpl: getActualFileCount - 没有找到Git仓库")
                return changes.size // 回退到原始数量
            }

            val repository = repositories.first()
            val root = repository.root
            
            var actualFileCount = 0
            
            for (change in changes) {
                when (change.type) {
                    Change.Type.DELETED -> {
                        // 删除文件：无论是否存在都会被处理
                        val filePath = getFilePathFromChange(change, root)
                        if (filePath != null) {
                            actualFileCount++
                        }
                    }
                    Change.Type.NEW, Change.Type.MODIFICATION, Change.Type.MOVED -> {
                        // 新增、修改、移动文件：都会被处理
                        val filePath = getFilePathFromChange(change, root)
                        if (filePath != null) {
                            actualFileCount++
                        }
                    }
                    else -> {
                        // 其他类型暂时不计入
                    }
                }
            }
            
            println("VCSServiceImpl: getActualFileCount - 变更数量: ${changes.size}, 实际处理文件数量: $actualFileCount")
            actualFileCount
            
        } catch (e: Exception) {
            println("VCSServiceImpl: getActualFileCount异常: ${e.message}")
            e.printStackTrace()
            changes.size // 回退到原始数量
        }
    }

    override fun isGitRepository(project: Project): Boolean {
        return try {
            val gitRepositoryManager = GitRepositoryManager.getInstance(project)
            gitRepositoryManager.repositories.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    override fun commitChanges(changes: List<Change>, message: String): Boolean {
        return try {
            if (changes.isEmpty()) {
                return false
            }

            // 从Change对象中获取项目实例 - 避免慢操作
            val project = changes.firstOrNull()?.let { change ->
                // 尝试从虚拟文件获取项目
                val virtualFile = change.virtualFile ?: change.beforeRevision?.file?.virtualFile ?: change.afterRevision?.file?.virtualFile
                
                if (virtualFile != null) {
                    // 使用虚拟文件查找项目
                    ApplicationManager.getApplication().runReadAction<Project?> {
                        ProjectManager.getInstance().openProjects.find { project ->
                            try {
                                val projectBasePath = project.basePath
                                if (projectBasePath != null) {
                                    val filePath = virtualFile.path
                                    filePath.startsWith(projectBasePath)
                                } else {
                                    false
                                }
                            } catch (e: Exception) {
                                false
                            }
                        }
                    }
                } else {
                    // 虚拟文件为null时，尝试从revision路径获取项目
                    val filePath = when {
                        change.beforeRevision != null -> change.beforeRevision!!.file.path
                        change.afterRevision != null -> change.afterRevision!!.file.path
                        else -> null
                    }
                    
                    filePath?.let { path ->
                        ApplicationManager.getApplication().runReadAction<Project?> {
                            ProjectManager.getInstance().openProjects.find { project ->
                                try {
                                    val projectBasePath = project.basePath
                                    if (projectBasePath != null) {
                                        path.startsWith(projectBasePath)
                                    } else {
                                        false
                                    }
                                } catch (e: Exception) {
                                    false
                                }
                            }
                        }
                    }
                }
            }

            if (project == null) {
                return false
            }

            val gitRepositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = gitRepositoryManager.repositories

            if (repositories.isEmpty()) {
                return false
            }

            val repository = repositories.first()
            val root = repository.root

            // 使用ProgressManager在后台线程执行Git命令
            var success = false
            ProgressManager.getInstance().runProcessWithProgressSynchronously({
                try {
                    // 提取选中文件的路径
                    val filePaths = mutableListOf<String>()
                    changes.forEach { change ->
                        val filePath = getFilePathFromChange(change, root)
                        if (filePath != null) {
                            filePaths.add(filePath)
                        }
                    }

                    if (filePaths.isNotEmpty()) {
                        // 直接使用 git commit 命令指定要提交的文件，不影响暂存区
                        val commitHandler = GitLineHandler(project, root, GitCommand.COMMIT)
                        commitHandler.addParameters("-m", message)
                        
                        // 添加所有选中的文件作为参数
                        filePaths.forEach { path ->
                            commitHandler.addParameters(path)
                        }
                        
                        // 执行git commit命令，只提交指定的文件
                        val commitResult = Git.getInstance().runCommand(commitHandler)
                        success = commitResult.success()
                    } else {
                        success = false
                    }
                } catch (e: Exception) {
                    success = false
                }
            }, "提交变更...", true, project)

            success
        } catch (e: Exception) {
            false
        }
    }

    override fun pushChanges(project: Project): Boolean {
        return try {
            val gitRepositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = gitRepositoryManager.repositories

            if (repositories.isEmpty()) {
                return false
            }

            val repository = repositories.first()
            val root = repository.root

            // 使用ProgressManager在后台线程执行Git命令
            var success = false
            ProgressManager.getInstance().runProcessWithProgressSynchronously({
                try {
                    // 创建GitLineHandler执行git push命令
                    val handler = GitLineHandler(project, root, GitCommand.PUSH)

                    // 执行git push命令
                    val gitResult = Git.getInstance().runCommand(handler)
                    success = gitResult.success()
                } catch (e: Exception) {
                    success = false
                }
            }, "推送变更...", true, project)

            success
        } catch (e: Exception) {
            false
        }
    }

}