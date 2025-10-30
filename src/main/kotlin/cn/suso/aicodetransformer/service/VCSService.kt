package cn.suso.aicodetransformer.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change

/**
 * VCS服务接口
 * 提供Git相关的操作功能
 */
interface VCSService {
    

    /**
     * 检查项目是否为Git仓库
     * @param project 项目实例
     * @return 是否为Git仓库
     */
    fun isGitRepository(project: Project): Boolean
    
    /**
     * 执行Git提交（指定变更列表）
     * @param changes 变更列表
     * @param message 提交信息
     * @return 是否成功
     */
    fun commitChanges(changes: List<Change>, message: String): Boolean
    
    /**
     * 计算变更列表中实际需要处理的文件数量
     * @param changes 变更列表
     * @return 实际处理的文件数量
     */
    fun getActualFileCount(changes: List<Change>): Int
    
    /**
     * 执行Git推送
     * @param project 项目实例
     * @return 是否成功
     */
    fun pushChanges(project: Project): Boolean
    
    /**
     * 获取指定文件的差异内容
     * @param project 项目实例
     * @param filePath 文件路径
     * @param staged 是否获取暂存区差异
     * @return 文件差异内容
     */
    fun getFileDiff(project: Project, filePath: String, staged: Boolean = false): String

}