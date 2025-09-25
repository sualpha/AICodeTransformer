package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.PromptTemplate

/**
 * 快捷键恢复服务接口
 * 用于处理插件卸载重装后的快捷键恢复
 */
interface ShortcutRecoveryService {
    
    /**
     * 备份当前快捷键配置
     * 在插件卸载前保存快捷键配置到持久化存储
     */
    fun backupShortcuts()
    
    /**
     * 恢复快捷键配置
     * 在插件重装后从持久化存储恢复快捷键配置
     * @return 是否成功恢复
     */
    fun restoreShortcuts(): Boolean
    
    /**
     * 检查是否有备份的快捷键配置
     * @return 是否存在备份
     */
    fun hasBackup(): Boolean
    
    /**
     * 清除备份的快捷键配置
     */
    fun clearBackup()
    
    /**
     * 获取备份的快捷键配置
     * @return 备份的模板列表
     */
    fun getBackupShortcuts(): List<PromptTemplate>
    
    /**
     * 自动检测并恢复快捷键
     * 在插件启动时自动检查并恢复快捷键
     * @return 恢复的快捷键数量
     */
    fun autoRecoverShortcuts(): Int
    
    /**
     * 验证快捷键配置的完整性
     * @return 验证结果
     */
    fun validateShortcutIntegrity(): ShortcutValidationResult
}

/**
 * 快捷键验证结果
 */
data class ShortcutValidationResult(
    val isValid: Boolean,
    val missingShortcuts: List<String>,
    val conflictingShortcuts: List<String>,
    val message: String
)