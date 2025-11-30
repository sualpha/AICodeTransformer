package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.constants.TemplateConstants
import cn.suso.aicodetransformer.i18n.LanguageManager
import cn.suso.aicodetransformer.model.*
import cn.suso.aicodetransformer.service.ErrorHandlingService
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.TemplateChangeListener
import cn.suso.aicodetransformer.service.TemplateVariableResolver
import cn.suso.aicodetransformer.service.java.JavaPsiHelperLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Prompt模板管理服务实现类
 */
@State(
    name = "AICodeTransformerTemplates",
    storages = [Storage("aicodetransformer-templates.xml")]
)
class PromptTemplateServiceImpl : PromptTemplateService, PersistentStateComponent<PromptTemplateState>, Disposable {
    
    companion object {
        fun getInstance(): PromptTemplateService = service<PromptTemplateService>()
    }
    
    private val logger = LoggerFactory.getLogger(PromptTemplateServiceImpl::class.java)
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val errorHandlingService: ErrorHandlingService = service()
    private val listeners = CopyOnWriteArrayList<TemplateChangeListener>()
    private val usageStats = ConcurrentHashMap<String, Int>()
    private val lock = ReentrantReadWriteLock()
    private var state = PromptTemplateState()
    private val languageListener: () -> Unit = {
        refreshBuiltInTemplatesForCurrentLanguage()
    }
    private val objectConversionTemplateId = TemplateConstants.TemplateConfig.OBJECT_CONVERT.id
    

    
    override fun getState(): PromptTemplateState {
        return lock.read {
            // 直接返回当前状态，让IntelliJ的XML序列化机制处理
            // templatesXml和usageStatisticsXml字段用于XML序列化
            state.copy(
                templatesXml = try {
                    json.encodeToString(state.templates)
                } catch (e: Exception) {
                    "[]"
                },
                usageStatisticsXml = try {
                    json.encodeToString(state.usageStatistics)
                } catch (e: Exception) {
                    "{}"
                }
            )
        }
    }
    
    override fun loadState(state: PromptTemplateState) {
        lock.write {
            this.state = state
            
            // 从XML字符串反序列化数据
            try {
                if (state.templatesXml.isNotEmpty()) {
                    val templates = json.decodeFromString<MutableList<PromptTemplate>>(state.templatesXml)
                    this.state.templates.clear()
                    this.state.templates.addAll(templates)
                }
            } catch (e: Exception) {
                // 如果反序列化失败，保持空列表
                this.state.templates.clear()
            }
            
            try {
                if (state.usageStatisticsXml.isNotEmpty()) {
                    val stats = json.decodeFromString<MutableMap<String, Int>>(state.usageStatisticsXml)
                    this.state.usageStatistics.clear()
                    this.state.usageStatistics.putAll(stats)
                    this.usageStats.clear()
                    this.usageStats.putAll(stats)
                }
            } catch (e: Exception) {
                // 如果反序列化失败，保持空映射
                this.state.usageStatistics.clear()
                this.usageStats.clear()
            }
        }
        initializeDefaultTemplates()
        refreshBuiltInTemplatesForCurrentLanguage()
        LanguageManager.addChangeListener(languageListener)
    }
    
    override fun getTemplates(): List<PromptTemplate> {
        return lock.read { state.templates.toList() }
    }
    
    override fun getTemplate(id: String): PromptTemplate? {
        return lock.read { state.templates.find { it.id == id } }
    }
    
    override fun saveTemplate(template: PromptTemplate) {
        lock.write {
            // 规范化快捷键：将空白字符串转换为null
            val normalizedTemplate = template.copy(
                shortcutKey = if (template.shortcutKey.isNullOrBlank()) null else template.shortcutKey
            )
            
            val existingIndex = state.templates.indexOfFirst { it.id == normalizedTemplate.id }
            val oldTemplate = if (existingIndex >= 0) state.templates[existingIndex] else null
            
            if (existingIndex >= 0) {
                val oldShortcut = oldTemplate?.shortcutKey
                state.templates[existingIndex] = normalizedTemplate
                listeners.forEach { it.onTemplateUpdated(oldTemplate!!, normalizedTemplate) }
                
                // 如果快捷键发生变化，通知监听器
                if (oldShortcut != normalizedTemplate.shortcutKey) {
                    listeners.forEach { it.onTemplateShortcutChanged(normalizedTemplate, oldShortcut, normalizedTemplate.shortcutKey) }
                }
            } else {
                state.templates.add(normalizedTemplate)
                listeners.forEach { it.onTemplateAdded(normalizedTemplate) }
            }
            
            // 强制保存状态到磁盘
            forceSaveState()
        }
    }
    
    override fun deleteTemplate(id: String): Boolean {
        return lock.write {
            val template = state.templates.find { it.id == id }
            if (template != null) {
                state.templates.removeIf { it.id == id }
                usageStats.remove(id)
                state.usageStatistics.remove(id)
                
                // 强制保存状态到磁盘
                forceSaveState()
                
                listeners.forEach { it.onTemplateDeleted(template) }
                true
            } else {
                false
            }
        }
    }
    
    override fun getEnabledTemplates(): List<PromptTemplate> {
        return lock.read { state.templates.filter { it.enabled } }
    }
    
    override fun getTemplateByShortcut(shortcut: String): PromptTemplate? {
        return lock.read { state.templates.find { it.enabled && it.shortcutKey == shortcut } }
    }
    
    override fun isShortcutUsed(shortcut: String, excludeId: String?): Boolean {
        return lock.read {
            // 检查模板中是否已使用该快捷键
            val usedByTemplate = state.templates.any { 
                it.shortcutKey == shortcut && it.id != excludeId 
            }
            
            // 检查IDE内置快捷键是否已使用
            val usedByIDE = try {
                val actionService = service<cn.suso.aicodetransformer.service.ActionService>()
                actionService.isShortcutInUse(shortcut)
            } catch (e: Exception) {
                false
            }
            
            usedByTemplate || usedByIDE
        }
    }
    
    override fun processTemplate(
        template: PromptTemplate,
        variables: Map<String, String>,
        editor: Editor?
    ): String {
        val (effectiveProject, effectiveEditor) = resolveProjectAndEditor(editor)

        if (effectiveProject != null && effectiveEditor != null) {
            val resolver = TemplateVariableResolver(effectiveProject)
            val resolvedContent = resolver.resolveVariables(template.content, effectiveEditor)
            val updatedTemplate = template.copy(content = resolvedContent)
            return updatedTemplate.render(variables)
        }

        return template.render(variables)
    }

    override fun processTemplate(
        templateId: String,
        variables: Map<String, String>,
        editor: Editor?
    ): String {
        val template = getTemplate(templateId)
            ?: throw IllegalArgumentException("模板不存在: $templateId")
        return processTemplate(template, variables, editor)
    }

    private fun resolveProjectAndEditor(editor: Editor?): Pair<Project?, Editor?> {
        if (editor != null) {
            return editor.project to editor
        }

        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        if (project != null) {
            val selectedEditor = FileEditorManager.getInstance(project).selectedTextEditor
            if (selectedEditor != null) {
                return project to selectedEditor
            }
        }

        return null to null
    }
    

    
    override fun validateTemplate(template: PromptTemplate): String? {
        try {
            // 验证模板名称
            if (template.name.isBlank()) {
                return "模板名称不能为空"
            }
            
            if (template.name.length > 100) {
                return "模板名称长度不能超过100个字符"
            }
            
            // 验证模板内容
            if (template.content.isBlank()) {
                return "模板内容不能为空"
            }
            
            if (template.content.length > 10000) {
                return "模板内容长度不能超过10000个字符"
            }
            
            // 添加额外的内存安全检查
            if (template.content.length > 50000) {
                return "模板内容过长，可能导致内存问题，请减少内容长度"
            }
            
            // 验证模板变量语法
            val variableValidation = validateTemplateVariables(template.content)
            if (variableValidation != null) {
                return variableValidation
            }
            
            // 验证分类名称
            if (template.category.isBlank()) {
                return "模板分类不能为空"
            }
            
            if (template.category.length > 50) {
                return "模板分类长度不能超过50个字符"
            }
            
            // 验证描述
            if (template.description != null && template.description.length > 500) {
                return "模板描述长度不能超过500个字符"
            }
            
            // 验证快捷键格式
            // 如果快捷键为空白字符串，将其视为null（无快捷键）
            val normalizedShortcutKey = if (template.shortcutKey.isNullOrBlank()) null else template.shortcutKey
            
            if (normalizedShortcutKey != null && !isValidShortcutKey(normalizedShortcutKey)) {
                return "快捷键格式无效，请使用如 Ctrl+Alt+T 的格式"
            }
            
            if (normalizedShortcutKey != null) {
                // 检查是否与其他模板冲突
                val conflictTemplate = state.templates.find { 
                    it.shortcutKey == normalizedShortcutKey && it.id != template.id 
                }
                if (conflictTemplate != null) {
                    return "快捷键已被模板 '${conflictTemplate.name}' 使用"
                }
                
                // 检查是否与IDE内置快捷键冲突
                val usedByIDE = try {
                    val actionService = service<cn.suso.aicodetransformer.service.ActionService>()
                    actionService.isShortcutInUse(normalizedShortcutKey)
                } catch (e: Exception) {
                    false
                }
                if (usedByIDE) {
                    return "快捷键与IDE内置功能冲突，请选择其他快捷键"
                }
            }
            
            if (!template.isValid()) {
                return "模板格式不正确"
            }
            
            return null
        } catch (e: Exception) {
            val context = ErrorContext(
                operation = "validateTemplate",
                component = "PromptTemplateService",
                additionalInfo = mapOf(
                    "templateName" to template.name,
                    "templateId" to template.id
                )
            )
            val handlingResult = errorHandlingService.handleException(e, context)
            return handlingResult.userMessage
        }
    }
    
    override fun duplicateTemplate(id: String, newName: String): PromptTemplate? {
        val sourceTemplate = getTemplate(id) ?: return null
        
        val newTemplate = sourceTemplate.copy(
            id = generateUniqueId(),
            name = newName,
            shortcutKey = null, // 复制的模板不继承快捷键
            createdAt = java.time.LocalDateTime.now().toString(),
            updatedAt = java.time.LocalDateTime.now().toString()
        )
        
        saveTemplate(newTemplate)
        return newTemplate
    }
    
    override fun exportTemplates(ids: List<String>): String {
        return lock.read {
            val templatesToExport = if (ids.isEmpty()) {
                state.templates
            } else {
                state.templates.filter { it.id in ids }
            }
            
            val metadata = ExportMetadata(
                totalCount = templatesToExport.size,
                categories = templatesToExport.mapNotNull { it.category }.distinct(),
                exportedBy = "AICodeTransformer"
            )
            
            val exportData = TemplateExportData(
                version = "1.0",
                exportTime = java.time.LocalDateTime.now().toString(),
                templates = templatesToExport,
                metadata = metadata
            )
            
            json.encodeToString(exportData)
        }
    }
    
    override fun importTemplates(templateJson: String, overwrite: Boolean): Int {
        return try {
            // 支持三种导入格式：
            // 1) 新格式：{"templates": [ ... ]}
            // 2) 旧包装格式：Map 包含 "templates"
            // 3) 最小格式：单对象 { id, name, description, content } 或数组 [ { ... }, ... ]
            val root: JsonElement = json.parseToJsonElement(templateJson)
            val importedTemplates: List<PromptTemplate> = when (root) {
                is JsonObject -> {
                    // 新格式：带 templates 字段
                    val templatesElem = root["templates"]
                    if (templatesElem != null) {
                        when (templatesElem) {
                            is JsonArray -> templatesElem.map { json.decodeFromJsonElement<PromptTemplate>(it) }
                            is JsonObject -> listOf(json.decodeFromJsonElement<PromptTemplate>(templatesElem))
                            else -> emptyList()
                        }
                    } else {
                        // 最小格式：单对象
                        listOf(json.decodeFromJsonElement<PromptTemplate>(root))
                    }
                }
                is JsonArray -> {
                    // 最小格式：数组
                    root.map { json.decodeFromJsonElement<PromptTemplate>(it) }
                }
                else -> emptyList()
            }

            if (importedTemplates.isEmpty()) {
                throw IllegalArgumentException("导入文件中没有找到有效的模板数据")
            }

            var importedCount = 0

            importedTemplates.forEach { template ->
                val existingTemplate = getTemplate(template.id)

                if (existingTemplate == null || overwrite) {
                    val validationError = validateTemplate(template)
                    if (validationError == null) {
                        // 如果不覆盖且存在同名模板，生成新ID
                        val finalTemplate = if (!overwrite && existingTemplate != null) {
                            template.copy(
                                id = generateUniqueId(),
                                createdAt = java.time.LocalDateTime.now().toString(),
                                updatedAt = java.time.LocalDateTime.now().toString()
                            )
                        } else {
                            template.copy(
                                updatedAt = java.time.LocalDateTime.now().toString()
                            )
                        }

                        saveTemplate(finalTemplate)
                        importedCount++
                    }
                }
            }

            importedCount
        } catch (e: Exception) {
            val handlingResult = errorHandlingService.handleConfigurationError(e, "TemplateImport")
            throw RuntimeException(handlingResult.userMessage, e)
        }
    }
    
    override fun getDefaultTemplates(): List<PromptTemplate> {
        return PromptTemplate.getBuiltInTemplates()
    }
    
    override fun resetToDefaults(keepExisting: Boolean) {
        if (!keepExisting) {
            val oldTemplates = state.templates.toList()
            state.templates.clear()
            usageStats.clear()
            state.usageStatistics.clear()
            
            // 通知监听器
            oldTemplates.forEach { listeners.forEach { listener -> listener.onTemplateDeleted(it) } }
        }
        
        // 添加默认模板
        initializeDefaultTemplates()
    }
    
    override fun searchTemplates(keyword: String): List<PromptTemplate> {
        val lowerKeyword = keyword.lowercase()
        return lock.read {
            state.templates.filter {
                it.name.lowercase().contains(lowerKeyword) ||
                it.content.lowercase().contains(lowerKeyword) ||
                it.description?.lowercase()?.contains(lowerKeyword) == true ||
                it.category.lowercase().contains(lowerKeyword) == true
            }
        }
    }
    
    override fun getTemplateUsageCount(id: String): Int {
        return usageStats[id] ?: 0
    }
    
    override fun recordTemplateUsage(id: String) {
        val currentCount = usageStats.getOrDefault(id, 0)
        usageStats[id] = currentCount + 1
        state.usageStatistics[id] = currentCount + 1
    }
    
    override fun addTemplateChangeListener(listener: TemplateChangeListener) {
        listeners.add(listener)
    }
    
    override fun removeTemplateChangeListener(listener: TemplateChangeListener) {
        listeners.remove(listener)
    }
    
    /**
     * 初始化默认模板
     */
    private fun initializeDefaultTemplates() {
        lock.write {
            val builtInConfigs = TemplateConstants.TemplateConfig.getBuiltInTemplates()
            builtInConfigs.forEach { config ->
                if (!shouldLoadBuiltIn(config)) {
                    return@forEach
                }
                val existingIndex = state.templates.indexOfFirst { it.id == config.id }
                if (existingIndex >= 0) {
                    // 已存在模板，交由 refreshBuiltInTemplatesForCurrentLanguage 处理更新逻辑
                    // 这里不做任何操作，避免覆盖用户修改
                    return@forEach
                } else {
                    val template = PromptTemplate(
                        id = config.id,
                        name = config.displayName,
                        description = config.description,
                        content = config.content,
                        category = config.category.displayName,
                        isBuiltIn = config.isBuiltIn
                    )
                    state.templates.add(template)
                    listeners.forEach { it.onTemplateAdded(template) }
                }
            }
            removeUnsupportedBuiltInTemplatesLocked()
        }
        logger.info("默认模板初始化完成，共加载 ${getDefaultTemplates().size} 个模板")
    }

    private fun refreshBuiltInTemplatesForCurrentLanguage() {
        var changed = false
        lock.write {
            // 获取当前插件版本
            val pluginId = PluginId.getId("cn.suso.AICodeTransformer")
            val plugin = PluginManagerCore.getPlugin(pluginId)
            val currentVersion = plugin?.version ?: "1.0.0"
            
            // 检查是否需要强制更新（插件版本变更）
            val shouldForceUpdate = currentVersion != state.lastPluginVersion
            
            if (shouldForceUpdate) {
                logger.info("检测到插件版本变更: ${state.lastPluginVersion} -> $currentVersion，将强制更新内置模板")
                state.lastPluginVersion = currentVersion
                changed = true
            }
            
            val builtInConfigs = TemplateConstants.TemplateConfig.getBuiltInTemplates().associateBy { it.id }
            state.templates.replaceAll { template ->
                val config = builtInConfigs[template.id]
                if (config == null || !template.isBuiltIn) {
                    template
                } else {
                    if (!shouldLoadBuiltIn(config)) {
                        return@replaceAll template
                    }
                    
                    
                    // 对于内置模板，始终更新名称、描述和分类以支持多语言切换
                    // 检查内容是否是用户修改的：通过比较是否匹配任何语言版本的内置内容
                    val isUserModified = !isBuiltInTemplateContent(template.id, template.content)
                    
                    if (!shouldForceUpdate && isUserModified) {
                        // 版本未变更且内容已被用户修改，保留用户修改的内容，但更新名称和描述
                        changed = true
                        val newTemplate = template.copy(
                            name = config.displayName,
                            description = config.description,
                            category = config.category.displayName
                        )
                        listeners.forEach { it.onTemplateUpdated(template, newTemplate) }
                        newTemplate
                    } else {
                        // 需要更新所有内容（强制更新 或 内容未被用户修改）
                        changed = true
                        val newTemplate = template.copy(
                            name = config.displayName,
                            description = config.description,
                            content = config.content,
                            category = config.category.displayName
                        )
                        listeners.forEach { it.onTemplateUpdated(template, newTemplate) }
                        newTemplate
                    }
                }
            }
            if (removeUnsupportedBuiltInTemplatesLocked()) {
                changed = true
            }
        }
        if (changed) {
            forceSaveState()
        }
    }

    /**
     * 检查内容是否是内置模板的原始内容（任何语言版本）
     * 用于区分用户修改和语言切换
     */
    private fun isBuiltInTemplateContent(templateId: String, content: String): Boolean {
        val config = TemplateConstants.TemplateConfig.values().find { it.id == templateId } ?: return false
        
        // 获取所有支持的语言版本的内容
        val supportedLanguages = listOf("zh_CN", "en_US")
        
        return supportedLanguages.any { lang ->
            try {
                val locale = when (lang) {
                    "zh_CN" -> java.util.Locale.SIMPLIFIED_CHINESE
                    "en_US" -> java.util.Locale.US
                    else -> java.util.Locale.getDefault()
                }
                val bundle = java.util.ResourceBundle.getBundle("i18n.messages", locale)
                val langContent = bundle.getString(config.contentKey)
                
                content == langContent
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun shouldLoadBuiltIn(config: TemplateConstants.TemplateConfig): Boolean {
        return config.id != objectConversionTemplateId || supportsObjectConversion()
    }

    private fun supportsObjectConversion(): Boolean = JavaPsiHelperLoader.helper() != null

    private fun removeUnsupportedBuiltInTemplatesLocked(): Boolean {
        if (supportsObjectConversion()) {
            return false
        }
        var removed = false
        val iterator = state.templates.listIterator()
        while (iterator.hasNext()) {
            val template = iterator.next()
            if (template.id == objectConversionTemplateId && template.isBuiltIn) {
                iterator.remove()
                usageStats.remove(template.id)
                state.usageStatistics.remove(template.id)
                listeners.forEach { it.onTemplateDeleted(template) }
                removed = true
            }
        }
        return removed
    }

    override fun dispose() {
        LanguageManager.removeChangeListener(languageListener)
    }
    
    /**
     * 生成唯一ID
     */
    private fun generateUniqueId(): String {
        return "template-${System.currentTimeMillis()}-${(1000..9999).random()}"
    }
    
    /**
     * 从文件获取编程语言
     */
    private fun getLanguageFromFile(file: VirtualFile): String {
        val extension = file.extension?.lowercase() ?: return "text"
        
        return when (extension) {
            "kt" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js", "ts" -> "javascript"
            "cpp", "cc", "cxx" -> "cpp"
            "c" -> "c"
            "cs" -> "csharp"
            "go" -> "go"
            "rs" -> "rust"
            "php" -> "php"
            "rb" -> "ruby"
            "swift" -> "swift"
            "scala" -> "scala"
            "sh" -> "bash"
            "sql" -> "sql"
            "html" -> "html"
            "css" -> "css"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "md" -> "markdown"
            else -> extension
        }
    }
    
    /**
     * 获取所有模板分类
     */
    fun getTemplateCategories(): List<String> {
        return lock.read {
            state.templates.mapNotNull { it.category }.distinct().sorted()
        }
    }
    
    /**
     * 根据分类获取模板
     */
    fun getTemplatesByCategory(category: String): List<PromptTemplate> {
        return lock.read {
            state.templates.filter { it.category == category }
        }
    }
    
    /**
     * 批量删除模板
     */
    fun deleteTemplates(ids: List<String>): Int {
        return lock.write {
            var deletedCount = 0
            ids.forEach { id ->
                val template = state.templates.find { it.id == id }
                if (template != null) {
                    state.templates.removeIf { it.id == id }
                    usageStats.remove(id)
                    state.usageStatistics.remove(id)
                    listeners.forEach { it.onTemplateDeleted(template) }
                    deletedCount++
                }
            }
            deletedCount
        }
    }
    
    /**
     * 批量启用/禁用模板
     */
    fun setTemplatesEnabled(ids: List<String>, enabled: Boolean): Int {
        return lock.write {
            var updatedCount = 0
            ids.forEach { id ->
                val index = state.templates.indexOfFirst { it.id == id }
                if (index >= 0) {
                    val oldTemplate = state.templates[index]
                    val newTemplate = oldTemplate.copy(enabled = enabled)
                    state.templates[index] = newTemplate
                    listeners.forEach { it.onTemplateUpdated(oldTemplate, newTemplate) }
                    updatedCount++
                }
            }
            updatedCount
        }
    }
    
    /**
     * 获取模板统计信息
     */
    fun getTemplateStatistics(): Map<String, Any> {
        return lock.read {
            mapOf<String, Any>(
                "totalTemplates" to state.templates.size,
                "enabledTemplates" to state.templates.count { it.enabled },
                "builtInTemplates" to state.templates.count { it.isBuiltIn },
                "customTemplates" to state.templates.count { !it.isBuiltIn },
                "templatesWithShortcuts" to state.templates.count { !it.shortcutKey.isNullOrBlank() },
                "categories" to getTemplateCategories().size,
                "totalUsage" to usageStats.values.sum(),
                "mostUsedTemplate" to (usageStats.maxByOrNull { it.value }?.key ?: "none")
            )
        }
    }
    
    /**
     * 验证模板内容的变量引用
     */
    fun validateTemplateVariables(template: PromptTemplate): List<String> {
        val errors = mutableListOf<String>()
        
        try {
            val validationResult = template.validate()
            if (!validationResult.isValid) {
                errors.addAll(validationResult.errors)
            }
        } catch (e: Exception) {
            errors.add("模板验证失败: ${e.message}")
        }
        
        return errors
    }
    
    /**
     * 验证模板变量语法
     */
    override fun validateTemplateVariables(content: String): String? {
        try {
            // 添加内容长度检查，防止内存溢出
            if (content.length > 50000) {
                return "模板内容过长，无法进行变量验证（最大支持50000字符）"
            }
            
            val variablePattern = Regex("\\{\\{\\s*(\\w+)\\s*\\}\\}")
            val matches = variablePattern.findAll(content)
            val variableNames = mutableSetOf<String>()
            
            // 限制变量数量，防止内存溢出
            var variableCount = 0
            val maxVariables = 100
            
            // 检查变量名是否有效
            for (match in matches) {
                variableCount++
                if (variableCount > maxVariables) {
                    return "模板中变量数量过多，最多支持${maxVariables}个变量"
                }
                
                val variableName = match.groupValues[1]
                
                // 验证变量名格式
                if (variableName.isEmpty() || !variableName.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
                    return "无效的变量名格式: $variableName，变量名必须以字母或下划线开头，只能包含字母、数字和下划线"
                }
                
                // 验证变量名长度
                if (variableName.length > 50) {
                    return "变量名长度不能超过50个字符: $variableName"
                }
                
                variableNames.add(variableName)
            }
            
            // 检查双大括号变量的匹配性
            var doubleBraceOpen = 0
            var doubleBraceClose = 0
            var hasNestedBraces = false
            var inVariable = false
            
            var i = 0
            while (i < content.length) {
                if (i < content.length - 1 && content[i] == '{' && content[i + 1] == '{') {
                    doubleBraceOpen++
                    if (inVariable) {
                        hasNestedBraces = true
                        break
                    }
                    inVariable = true
                    i += 2 // 跳过下一个字符
                } else if (i < content.length - 1 && content[i] == '}' && content[i + 1] == '}') {
                    doubleBraceClose++
                    if (inVariable) {
                        inVariable = false
                    }
                    i += 2 // 跳过下一个字符
                } else {
                    i++
                }
                
                // 每处理1000个字符检查一次，避免长时间阻塞
                if (i % 1000 == 0) {
                    Thread.yield()
                }
            }
            
            if (doubleBraceOpen != doubleBraceClose) {
                return "模板中存在未匹配的双大括号变量"
            }
            
            if (hasNestedBraces) {
                return "模板中不能包含嵌套的变量定义"
            }
            
            return null
        } catch (e: OutOfMemoryError) {
            return "模板内容过大，导致内存不足，请减少模板内容长度"
        } catch (e: Exception) {
            return "验证模板变量时发生错误: ${e.message}"
        }
    }
    
    /**
     * 验证快捷键格式
     */
    private fun isValidShortcutKey(shortcut: String): Boolean {
        try {
            // 基本格式验证：应该包含修饰键和主键
            val parts = shortcut.split("+")
            if (parts.size < 2) return false
            
            val validModifiers = setOf("Ctrl", "Alt", "Shift", "Meta", "Cmd")
            val validKeys = setOf(
                "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
                "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
                "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
                "Enter", "Space", "Tab", "Escape", "Delete", "Backspace",
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"
            )
            
            // 检查修饰键
            val modifiers = parts.dropLast(1)
            if (modifiers.isEmpty() || !modifiers.all { it in validModifiers }) {
                return false
            }
            
            // 检查主键
            val mainKey = parts.last()
            return mainKey in validKeys
        } catch (e: Exception) {
            return false
        }
    }
    
    override fun exportTemplatesToFile(filePath: String, ids: List<String>) {
         try {
             val exportJson = exportTemplates(ids)
             java.io.File(filePath).writeText(exportJson)
         } catch (e: Exception) {
             throw RuntimeException("导出模板到文件失败: ${e.message}", e)
         }
     }
     
     override fun importTemplatesFromFile(filePath: String, overwrite: Boolean): List<PromptTemplate> {
         return try {
             val content = java.io.File(filePath).readText()
             importTemplates(content, overwrite)

             // 统一解析逻辑，支持三种格式
             val root: JsonElement = json.parseToJsonElement(content)
             val templates: List<PromptTemplate> = when (root) {
                 is JsonObject -> {
                     val templatesElem = root["templates"]
                     if (templatesElem != null) {
                         when (templatesElem) {
                             is JsonArray -> templatesElem.map { json.decodeFromJsonElement<PromptTemplate>(it) }
                             is JsonObject -> listOf(json.decodeFromJsonElement<PromptTemplate>(templatesElem))
                             else -> emptyList()
                         }
                     } else {
                         listOf(json.decodeFromJsonElement<PromptTemplate>(root))
                     }
                 }
                 is JsonArray -> root.map { json.decodeFromJsonElement<PromptTemplate>(it) }
                 else -> emptyList()
             }
             templates
         } catch (e: Exception) {
             throw RuntimeException("从文件导入模板失败: ${e.message}", e)
         }
     }
     
     /**
        * 强制保存状态到磁盘
        */
       private fun forceSaveState() {
           try {
               // 简单地更新状态，IntelliJ会自动检测变化并保存
               // 通过修改状态对象来触发自动保存机制
               state = state.copy(
                   templatesXml = json.encodeToString(state.templates),
                   usageStatisticsXml = json.encodeToString(state.usageStatistics)
               )
           } catch (e: Exception) {
               // 记录错误但不抛出异常，避免影响正常功能
               val context = ErrorContext(
                   operation = "forceSaveState",
                   component = "PromptTemplateService",
                   additionalInfo = mapOf("error" to e.message.orEmpty())
               )
               errorHandlingService.handleException(e, context)
           }
       }
}