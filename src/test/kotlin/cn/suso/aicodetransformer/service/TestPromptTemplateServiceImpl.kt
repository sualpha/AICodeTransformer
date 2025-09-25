package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.PromptTemplate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * 测试用的PromptTemplateService实现
 */
class TestPromptTemplateServiceImpl : PromptTemplateService {
    
    private val templates = mutableListOf<PromptTemplate>()
    private val listeners = CopyOnWriteArrayList<TemplateChangeListener>()
    private val usageStats = ConcurrentHashMap<String, Int>()
    
    init {
        // 添加一些内置模板用于测试
        templates.addAll(listOf(
            PromptTemplate(
                id = "builtin-code-conversion",
                name = "代码转换",
                content = "将以下\${sourceLanguage}代码转换为\${targetLanguage}：\n\${code}",
                category = "代码转换",
                isBuiltIn = true,
                enabled = true
            ),
            PromptTemplate(
                id = "builtin-testing",
                name = "测试生成",
                content = "为以下\${language}代码生成单元测试：\n\${code}",
                category = "测试",
                isBuiltIn = true,
                enabled = true
            ),
            PromptTemplate(
                id = "builtin-documentation",
                name = "文档生成",
                content = "为以下\${language}代码生成文档：\n\${code}",
                category = "文档",
                isBuiltIn = true,
                enabled = true
            )
        ))
    }
    
    override fun getTemplates(): List<PromptTemplate> {
        return templates.toList()
    }
    
    override fun getTemplate(id: String): PromptTemplate? {
        return templates.find { it.id == id }
    }
    
    override fun saveTemplate(template: PromptTemplate) {
        val existingIndex = templates.indexOfFirst { it.id == template.id }
        if (existingIndex >= 0) {
            templates[existingIndex] = template
        } else {
            templates.add(template)
        }
    }
    
    override fun deleteTemplate(id: String): Boolean {
        return templates.removeIf { it.id == id }
    }
    
    override fun getEnabledTemplates(): List<PromptTemplate> {
        return templates.filter { it.enabled }
    }
    
    override fun getTemplateByShortcut(shortcut: String): PromptTemplate? {
        return templates.find { it.enabled && it.shortcutKey == shortcut }
    }
    
    override fun isShortcutUsed(shortcut: String, excludeId: String?): Boolean {
        return templates.any { it.shortcutKey == shortcut && it.id != excludeId }
    }
    
    override fun processTemplate(template: PromptTemplate, variables: Map<String, String>): String {
        return template.render(variables)
    }
    
    override fun processTemplate(templateId: String, variables: Map<String, String>): String {
        val template = getTemplate(templateId)
            ?: throw IllegalArgumentException("模板不存在: $templateId")
        return processTemplate(template, variables)
    }
    
    override fun getContextVariables(): Map<String, String> {
        return mapOf(
            "\${selectedCode}" to "",
            "\${fileName}" to "",
            "\${language}" to "",
            "\${projectName}" to ""
        )
    }
    
    override fun validateTemplate(template: PromptTemplate): String? {
        if (template.name.isBlank()) {
            return "模板名称不能为空"
        }
        if (template.content.isBlank()) {
            return "模板内容不能为空"
        }
        return null
    }
    
    override fun validateTemplateVariables(content: String): String? {
        // 简单的变量验证
        return null
    }
    
    override fun duplicateTemplate(id: String, newName: String): PromptTemplate? {
        val template = getTemplate(id) ?: return null
        val newTemplate = template.copy(
            id = "${template.id}_copy",
            name = newName
        )
        saveTemplate(newTemplate)
        return newTemplate
    }
    
    override fun exportTemplates(ids: List<String>): String {
        val templatesToExport = if (ids.isEmpty()) {
            templates
        } else {
            templates.filter { it.id in ids }
        }
        return templatesToExport.toString()
    }
    
    override fun importTemplates(templateJson: String, overwrite: Boolean): Int {
        // 简单实现
        return 0
    }
    
    override fun exportTemplatesToFile(filePath: String, ids: List<String>) {
        val templatesToExport = if (ids.isEmpty()) {
            templates
        } else {
            templates.filter { it.id in ids }
        }
        
        val json = kotlinx.serialization.json.Json { prettyPrint = true }
        val jsonString = json.encodeToString(templatesToExport)
        java.io.File(filePath).writeText(jsonString)
    }
    
    override fun importTemplatesFromFile(filePath: String, overwrite: Boolean): List<PromptTemplate> {
        val content = java.io.File(filePath).readText()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val importedTemplates = json.decodeFromString<List<PromptTemplate>>(content)
        
        importedTemplates.forEach { template ->
            val existingTemplate = getTemplate(template.id)
            if (existingTemplate == null || overwrite) {
                saveTemplate(template)
            }
        }
        
        return importedTemplates
    }
    
    override fun getDefaultTemplates(): List<PromptTemplate> {
        return listOf(
            PromptTemplate(
                id = "default-optimize",
                name = "代码优化",
                content = "请优化以下代码：\n\${selectedCode}",
                category = "代码优化",
                isBuiltIn = true,
                enabled = true
            ),
            PromptTemplate(
                id = "default-explain",
                name = "代码解释",
                content = "请解释以下代码的功能：\n\${selectedCode}",
                category = "代码解释",
                isBuiltIn = true,
                enabled = true
            ),
            PromptTemplate(
                id = "default-test",
                name = "生成测试",
                content = "为以下代码生成单元测试：\n\${selectedCode}",
                category = "测试生成",
                isBuiltIn = true,
                enabled = true
            )
        )
    }
    
    override fun resetToDefaults(keepExisting: Boolean) {
        if (!keepExisting) {
            templates.clear()
        }
        getDefaultTemplates().forEach { saveTemplate(it) }
    }
    
    override fun searchTemplates(keyword: String): List<PromptTemplate> {
        return templates.filter { 
            it.name.contains(keyword, ignoreCase = true) ||
            it.content.contains(keyword, ignoreCase = true) ||
            it.description?.contains(keyword, ignoreCase = true) == true
        }
    }
    
    override fun getTemplateUsageCount(id: String): Int {
        return usageStats[id] ?: 0
    }
    
    override fun recordTemplateUsage(id: String) {
        usageStats[id] = (usageStats[id] ?: 0) + 1
    }
    
    override fun addTemplateChangeListener(listener: TemplateChangeListener) {
        listeners.add(listener)
    }
    
    override fun removeTemplateChangeListener(listener: TemplateChangeListener) {
        listeners.remove(listener)
    }
}