package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.*

/**
 * 模板服务接口
 * 用于管理AI代码转换模板
 */
interface TemplateService {
    
    /**
     * 获取所有模板
     * @return 模板列表
     */
    fun getAllTemplates(): List<Template>
    
    /**
     * 根据ID获取模板
     * @param id 模板ID
     * @return 模板，如果不存在则返回null
     */
    fun getTemplateById(id: String): Template?
    
    /**
     * 搜索模板
     * @param criteria 搜索条件
     * @return 匹配的模板列表
     */
    fun searchTemplates(criteria: TemplateSearchCriteria): List<Template>
    
    /**
     * 根据分类获取模板
     * @param category 模板分类
     * @return 该分类下的模板列表
     */
    fun getTemplatesByCategory(category: TemplateCategory): List<Template>
    
    /**
     * 根据标签获取模板
     * @param tags 标签列表
     * @return 包含任一标签的模板列表
     */
    fun getTemplatesByTags(tags: List<String>): List<Template>
    
    /**
     * 获取推荐模板
     * @param language 编程语言
     * @param limit 返回数量限制
     * @return 推荐的模板列表
     */
    fun getRecommendedTemplates(language: String? = null, limit: Int = 5): List<Template>
    
    /**
     * 创建新模板
     * @param template 模板信息
     * @return 创建的模板
     */
    fun createTemplate(template: Template): Template
    
    /**
     * 更新模板
     * @param template 更新的模板信息
     * @return 更新后的模板
     */
    fun updateTemplate(template: Template): Template
    
    /**
     * 删除模板
     * @param id 模板ID
     * @return 是否删除成功
     */
    fun deleteTemplate(id: String): Boolean
    
    /**
     * 启用/禁用模板
     * @param id 模板ID
     * @param enabled 是否启用
     * @return 是否操作成功
     */
    fun setTemplateEnabled(id: String, enabled: Boolean): Boolean
    
    /**
     * 增加模板使用次数
     * @param id 模板ID
     */
    fun incrementUsageCount(id: String)
    
    /**
     * 为模板评分
     * @param id 模板ID
     * @param rating 评分（1-5）
     * @return 是否评分成功
     */
    fun rateTemplate(id: String, rating: Int): Boolean
    
    /**
     * 渲染模板
     * @param templateId 模板ID
     * @param variableValues 变量值映射
     * @return 渲染结果
     */
    fun renderTemplate(templateId: String, variableValues: Map<String, String>): TemplateRenderResult
    
    /**
     * 验证模板变量
     * @param templateId 模板ID
     * @param variableValues 变量值映射
     * @return 验证结果
     */
    fun validateTemplateVariables(templateId: String, variableValues: Map<String, String>): TemplateValidationResult
    
    /**
     * 验证模板
     * @param template 要验证的模板
     * @return 验证结果
     */
    fun validateTemplate(template: Template): TemplateValidationResult
    
    /**
     * 导入模板
     * @param templates 模板列表
     * @return 导入结果
     */
    fun importTemplates(templates: List<Template>): TemplateImportResult
    
    /**
     * 导出模板
     * @param templateIds 要导出的模板ID列表，为空则导出所有
     * @return 导出的模板列表
     */
    fun exportTemplates(templateIds: List<String> = emptyList()): List<Template>
    
    /**
     * 获取所有分类
     * @return 分类列表
     */
    fun getAllCategories(): List<TemplateCategory>
    
    /**
     * 获取所有标签
     * @return 标签列表
     */
    fun getAllTags(): List<String>
    
    /**
     * 获取模板统计信息
     * @return 统计信息
     */
    fun getTemplateStats(): TemplateStats
    
    /**
     * 重置内置模板
     * @return 是否重置成功
     */
    fun resetBuiltInTemplates(): Boolean
    
    /**
     * 备份模板数据
     * @return 备份文件路径
     */
    fun backupTemplates(): String
    
    /**
     * 从备份恢复模板数据
     * @param backupPath 备份文件路径
     * @return 是否恢复成功
     */
    fun restoreTemplates(backupPath: String): Boolean
}