package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.PromptTemplate

/**
 * Prompt模板管理服务接口
 * 负责管理用户自定义的AI指令模板
 */
interface PromptTemplateService {
    
    /**
     * 获取所有模板
     * @return 模板列表
     */
    fun getTemplates(): List<PromptTemplate>
    
    /**
     * 根据ID获取模板
     * @param id 模板ID
     * @return 模板，如果不存在返回null
     */
    fun getTemplate(id: String): PromptTemplate?
    
    /**
     * 保存模板
     * @param template 模板
     */
    fun saveTemplate(template: PromptTemplate)
    
    /**
     * 删除模板
     * @param id 模板ID
     * @return 是否删除成功
     */
    fun deleteTemplate(id: String): Boolean
    
    /**
     * 获取启用的模板
     * @return 启用的模板列表
     */
    fun getEnabledTemplates(): List<PromptTemplate>
    
    /**
     * 根据快捷键获取模板
     * @param shortcut 快捷键字符串
     * @return 模板，如果不存在返回null
     */
    fun getTemplateByShortcut(shortcut: String): PromptTemplate?
    
    /**
     * 检查快捷键是否已被使用
     * @param shortcut 快捷键字符串
     * @param excludeId 排除的模板ID（用于编辑时检查）
     * @return 是否已被使用
     */
    fun isShortcutUsed(shortcut: String, excludeId: String? = null): Boolean
    
    /**
     * 处理模板内容，替换变量
     * @param template 模板
     * @param variables 变量映射
     * @return 处理后的内容
     */
    fun processTemplate(template: PromptTemplate, variables: Map<String, String>): String
    
    /**
     * 处理模板内容，替换变量
     * @param templateId 模板ID
     * @param variables 变量映射
     * @return 处理后的内容
     */
    fun processTemplate(templateId: String, variables: Map<String, String>): String
    

    
    /**
     * 验证模板是否有效
     * @param template 模板
     * @return 验证结果，成功返回null，失败返回错误信息
     */
    fun validateTemplate(template: PromptTemplate): String?
    
    /**
     * 验证模板变量语法
     * @param content 模板内容
     * @return 验证结果，成功返回null，失败返回错误信息
     */
    fun validateTemplateVariables(content: String): String?
    
    /**
     * 复制模板
     * @param id 源模板ID
     * @param newName 新模板名称
     * @return 新模板，如果源模板不存在返回null
     */
    fun duplicateTemplate(id: String, newName: String): PromptTemplate?
    
    /**
     * 导出模板
     * @param ids 要导出的模板ID列表，如果为空则导出所有模板
     * @return JSON格式的模板字符串
     */
    fun exportTemplates(ids: List<String> = emptyList()): String
    
    /**
     * 导入模板
     * @param templateJson JSON格式的模板字符串
     * @param overwrite 是否覆盖同名模板
     * @return 导入成功的模板数量
     */
    fun importTemplates(templateJson: String, overwrite: Boolean = false): Int
    
    /**
     * 导出模板到文件
     * @param filePath 文件路径
     * @param ids 要导出的模板ID列表，如果为空则导出所有模板
     */
    fun exportTemplatesToFile(filePath: String, ids: List<String> = emptyList())
    
    /**
     * 从文件导入模板
     * @param filePath 文件路径
     * @param overwrite 是否覆盖同名模板
     * @return 导入成功的模板列表
     */
    fun importTemplatesFromFile(filePath: String, overwrite: Boolean = false): List<PromptTemplate>
    
    /**
     * 获取默认模板
     * @return 默认模板列表
     */
    fun getDefaultTemplates(): List<PromptTemplate>
    
    /**
     * 重置为默认模板
     * @param keepExisting 是否保留现有模板
     */
    fun resetToDefaults(keepExisting: Boolean = true)
    
    /**
     * 搜索模板
     * @param keyword 关键词
     * @return 匹配的模板列表
     */
    fun searchTemplates(keyword: String): List<PromptTemplate>
    
    /**
     * 获取模板使用统计
     * @param id 模板ID
     * @return 使用次数
     */
    fun getTemplateUsageCount(id: String): Int
    
    /**
     * 记录模板使用
     * @param id 模板ID
     */
    fun recordTemplateUsage(id: String)
    
    /**
     * 添加模板变更监听器
     * @param listener 监听器
     */
    fun addTemplateChangeListener(listener: TemplateChangeListener)
    
    /**
     * 移除模板变更监听器
     * @param listener 监听器
     */
    fun removeTemplateChangeListener(listener: TemplateChangeListener)
}

/**
 * 模板变更监听器接口
 */
interface TemplateChangeListener {
    /**
     * 模板添加时调用
     * @param template 新增的模板
     */
    fun onTemplateAdded(template: PromptTemplate) {}
    
    /**
     * 模板更新时调用
     * @param oldTemplate 旧模板
     * @param newTemplate 新模板
     */
    fun onTemplateUpdated(oldTemplate: PromptTemplate, newTemplate: PromptTemplate) {}
    
    /**
     * 模板删除时调用
     * @param template 被删除的模板
     */
    fun onTemplateDeleted(template: PromptTemplate) {}
    
    /**
     * 模板快捷键变更时调用
     * @param template 模板
     * @param oldShortcut 旧快捷键
     * @param newShortcut 新快捷键
     */
    fun onTemplateShortcutChanged(template: PromptTemplate, oldShortcut: String?, newShortcut: String?) {}
}