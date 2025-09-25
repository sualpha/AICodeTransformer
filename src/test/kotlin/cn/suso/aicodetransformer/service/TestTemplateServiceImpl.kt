package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.Template
import cn.suso.aicodetransformer.model.TemplateCategory
import cn.suso.aicodetransformer.model.TemplateValidationResult
import cn.suso.aicodetransformer.model.TemplateSearchCriteria

/**
 * 测试用的TemplateService实现
 */
class TestTemplateServiceImpl : TemplateService {
    
    private val templates = mutableListOf<Template>()
    private val usageStats = mutableMapOf<String, Int>()
    
    init {
        // 添加一些内置模板用于测试
        templates.addAll(listOf(
            Template(
                id = "builtin-code-conversion",
                name = "代码转换",
                description = "将代码从一种语言转换为另一种语言",
                category = TemplateCategory.CODE_CONVERSION,
                promptTemplate = "将以下{{sourceLanguage}}代码转换为{{targetLanguage}}：\n{{code}}",
                isBuiltIn = true,
                enabled = true
            ),
            Template(
                id = "builtin-testing",
                name = "测试生成",
                description = "为代码生成单元测试",
                category = TemplateCategory.TESTING,
                promptTemplate = "为以下{{language}}代码生成单元测试：\n{{code}}",
                isBuiltIn = true,
                enabled = true
            ),
            Template(
                id = "builtin-documentation",
                name = "文档生成",
                description = "为代码生成文档",
                category = TemplateCategory.DOCUMENTATION,
                promptTemplate = "为以下{{language}}代码生成文档：\n{{code}}",
                isBuiltIn = true,
                enabled = true
            )
        ))
    }
    
    override fun getAllTemplates(): List<Template> {
        return templates.toList()
    }
    
    override fun getTemplateById(id: String): Template? {
        return templates.find { it.id == id }
    }
    
    override fun searchTemplates(criteria: TemplateSearchCriteria): List<Template> {
        return templates.filter { template ->
            criteria.query?.let { query: String ->
                template.name.contains(query, ignoreCase = true) ||
                template.description.contains(query, ignoreCase = true) ||
                template.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            } ?: true
        }
    }
    
    override fun getTemplatesByCategory(category: TemplateCategory): List<Template> {
        return templates.filter { it.category == category }
    }
    
    override fun getTemplatesByTags(tags: List<String>): List<Template> {
        return templates.filter { template ->
            tags.any { tag -> template.tags.contains(tag) }
        }
    }
    
    override fun getRecommendedTemplates(language: String?, limit: Int): List<Template> {
        return templates.take(limit)
    }
    
    override fun createTemplate(template: Template): Template {
        templates.add(template)
        return template
    }
    
    override fun updateTemplate(template: Template): Template {
        val index = templates.indexOfFirst { it.id == template.id }
        if (index >= 0) {
            templates[index] = template
        }
        return template
    }
    
    override fun deleteTemplate(id: String): Boolean {
        return templates.removeIf { it.id == id }
    }
    
    override fun setTemplateEnabled(id: String, enabled: Boolean): Boolean {
        val template = getTemplateById(id) ?: return false
        val updatedTemplate = template.copy(enabled = enabled)
        updateTemplate(updatedTemplate)
        return true
    }
    
    override fun incrementUsageCount(id: String) {
        usageStats[id] = (usageStats[id] ?: 0) + 1
    }
    
    override fun rateTemplate(id: String, rating: Int): Boolean {
        return true
    }
    
    override fun renderTemplate(templateId: String, variableValues: Map<String, String>): TemplateRenderResult {
        val template = getTemplateById(templateId)
        if (template == null) {
            return TemplateRenderResult(
                success = false,
                error = "模板不存在: $templateId"
            )
        }
        
        if (!template.enabled) {
            return TemplateRenderResult(
                success = false,
                error = "模板已禁用: ${template.name}"
            )
        }
        
        try {
            val renderedContent = template.render(variableValues)
            incrementUsageCount(templateId)
            
            return TemplateRenderResult(
                success = true,
                content = renderedContent,
                template = template
            )
        } catch (e: Exception) {
            return TemplateRenderResult(
                success = false,
                error = "渲染失败: ${e.message}",
                template = template
            )
        }
    }
    
    override fun validateTemplateVariables(templateId: String, variableValues: Map<String, String>): TemplateValidationResult {
        val template = getTemplateById(templateId)
        if (template == null) {
            return TemplateValidationResult(
                isValid = false,
                errors = listOf("模板不存在: $templateId")
            )
        }
        
        return TemplateValidationResult(
            isValid = true,
            errors = emptyList()
        )
    }
    
    override fun validateTemplate(template: Template): TemplateValidationResult {
        val errors = mutableListOf<String>()
        
        if (template.id.isBlank()) {
            errors.add("模板ID不能为空")
        }
        
        if (template.name.isBlank()) {
            errors.add("模板名称不能为空")
        }
        
        if (template.promptTemplate.isBlank()) {
            errors.add("模板内容不能为空")
        }
        
        return TemplateValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    override fun importTemplates(templates: List<Template>): TemplateImportResult {
        var successCount = 0
        val errors = mutableListOf<String>()
        val importedIds = mutableListOf<String>()
        
        templates.forEach { template ->
            try {
                createTemplate(template)
                successCount++
                importedIds.add(template.id)
            } catch (e: Exception) {
                errors.add("导入模板 ${template.name} 失败: ${e.message}")
            }
        }
        
        return TemplateImportResult(
            successCount = successCount,
            failureCount = errors.size,
            skippedCount = 0,
            errors = errors,
            importedTemplateIds = importedIds
        )
    }
    
    override fun exportTemplates(templateIds: List<String>): List<Template> {
        return if (templateIds.isEmpty()) {
            getAllTemplates()
        } else {
            templateIds.mapNotNull { getTemplateById(it) }
        }
    }
    
    override fun getAllCategories(): List<TemplateCategory> {
        return TemplateCategory.values().toList()
    }
    
    override fun getAllTags(): List<String> {
        return templates.flatMap { it.tags }.distinct()
    }
    
    override fun getTemplateStats(): TemplateStats {
        val totalTemplates = templates.size
        val enabledTemplates = templates.count { it.enabled }
        val builtInTemplates = templates.count { it.isBuiltIn }
        val customTemplates = totalTemplates - builtInTemplates
        
        return TemplateStats(
            totalTemplates = totalTemplates,
            enabledTemplates = enabledTemplates,
            builtInTemplates = builtInTemplates,
            customTemplates = customTemplates,
            templatesByCategory = templates.groupBy { it.category }.mapValues { it.value.size }.toMap(),
            totalUsageCount = usageStats.values.sum().toLong(),
            averageRating = 4.0,
            mostPopularTemplateId = templates.maxByOrNull { usageStats[it.id] ?: 0 }?.id,
            newestTemplateId = templates.maxByOrNull { it.createdAt }?.id
        )
    }
    
    override fun resetBuiltInTemplates(): Boolean {
        templates.removeIf { it.isBuiltIn }
        // 添加一些内置模板
        createBuiltInTemplates()
        return true
    }
    
    override fun backupTemplates(): String {
        return "backup_path"
    }
    
    override fun restoreTemplates(backupPath: String): Boolean {
        return true
    }
    
    private fun createBuiltInTemplates() {
        val builtInTemplates = listOf(
            Template(
                id = "java-to-kotlin",
                name = "Java转Kotlin",
                description = "将Java代码转换为Kotlin",
                category = TemplateCategory.CODE_CONVERSION,
                promptTemplate = "将以下Java代码转换为Kotlin：\n${'$'}{selectedCode}",
                isBuiltIn = true,
                enabled = true
            ),
            Template(
                id = "generate-test",
                name = "生成单元测试",
                description = "为代码生成单元测试",
                category = TemplateCategory.TESTING,
                promptTemplate = "为以下代码生成单元测试：\n${'$'}{selectedCode}",
                isBuiltIn = true,
                enabled = true
            )
        )
        
        builtInTemplates.forEach { createTemplate(it) }
    }
}