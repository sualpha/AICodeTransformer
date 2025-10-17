package cn.suso.aicodetransformer.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change

/**
 * VCS服务接口
 * 提供Git相关的操作功能
 */
interface VCSService {
    
    /**
     * 获取当前项目的Git差异内容
     * @param project 项目实例
     * @param staged 是否获取暂存区差异
     * @return Git差异内容
     */
    fun getGitDiff(project: Project, staged: Boolean = false): String
    
    /**
     * 获取变更文件列表
     * @param project 项目实例
     * @param staged 是否只获取暂存文件
     * @return 文件路径列表
     */
    fun getChangedFiles(project: Project, staged: Boolean = false): List<String>
    
    /**
     * 获取文件状态
     * @param project 项目实例
     * @param filePath 文件路径
     * @return 文件状态字符串
     */
    fun getFileStatus(project: Project, filePath: String): String
    
    /**
     * 获取当前Git分支名称
     * @param project 项目实例
     * @return 分支名称
     */
    fun getCurrentBranch(project: Project): String
    
    /**
     * 获取Git作者信息
     * @param project 项目实例
     * @return 作者姓名和邮箱的Pair
     */
    fun getGitAuthor(project: Project): Pair<String, String>
    
    /**
     * 获取Git仓库名称
     * @param project 项目实例
     * @return 仓库名称
     */
    fun getRepositoryName(project: Project): String
    
    /**
     * 获取Git远程仓库URL
     * @param project 项目实例
     * @return 远程仓库URL
     */
    fun getRemoteUrl(project: Project): String
    
    /**
     * 获取最近的提交信息
     * @param project 项目实例
     * @param count 获取的提交数量
     * @return 提交信息列表
     */
    fun getRecentCommits(project: Project, count: Int = 5): List<String>
    
    /**
     * 检查项目是否为Git仓库
     * @param project 项目实例
     * @return 是否为Git仓库
     */
    fun isGitRepository(project: Project): Boolean
    
    /**
     * 检查是否有未提交的变更
     * @param project 项目实例
     * @return 是否有变更
     */
    fun hasUncommittedChanges(project: Project): Boolean
    
    /**
     * 执行Git提交
     * @param project 项目实例
     * @param message 提交信息
     * @return 是否成功
     */
    fun commitChanges(project: Project, message: String): Boolean
    
    /**
     * 获取指定文件的差异内容
     * @param project 项目实例
     * @param filePath 文件路径
     * @param staged 是否获取暂存区差异
     * @return 文件差异内容
     */
    fun getFileDiff(project: Project, filePath: String, staged: Boolean = false): String
    
    /**
     * 获取选中文件的差异摘要
     * @param project 项目实例
     * @param changes 变更列表
     * @return 差异摘要
     */
    fun getChangesSummary(project: Project, changes: Collection<Change>): String
}