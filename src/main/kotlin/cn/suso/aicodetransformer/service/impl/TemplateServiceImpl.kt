package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.model.*
import cn.suso.aicodetransformer.service.TemplateService
import cn.suso.aicodetransformer.model.TemplateRenderResult
import cn.suso.aicodetransformer.model.TemplateImportResult
import cn.suso.aicodetransformer.model.TemplateStats
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import com.intellij.openapi.Disposable

/**
 * 模板服务实现
 */
class TemplateServiceImpl : TemplateService, Disposable {
    
    private val logger = LoggerFactory.getLogger(TemplateServiceImpl::class.java)
    private val json = Json { prettyPrint = true }
    
    // 模板存储
    private val templates = ConcurrentHashMap<String, Template>()
    private val templatesLock = ReentrantReadWriteLock()
    
    // 使用计数器
    private val usageCounters = ConcurrentHashMap<String, AtomicLong>()
    
    // 评分数据
    private val ratings = ConcurrentHashMap<String, MutableList<Int>>()
    
    init {
        // 初始化内置模板
        initializeBuiltInTemplates()
        logger.info("模板服务初始化完成，加载了 ${templates.size} 个模板")
    }
    
    override fun getAllTemplates(): List<Template> {
        templatesLock.read {
            return templates.values.toList()
        }
    }
    
    override fun getTemplateById(id: String): Template? {
        return templates[id]
    }
    
    override fun searchTemplates(criteria: TemplateSearchCriteria): List<Template> {
        templatesLock.read {
            var result = templates.values.asSequence()
            
            // 过滤启用状态
            if (criteria.enabledOnly) {
                result = result.filter { it.enabled }
            }
            
            // 过滤分类
            criteria.category?.let { category ->
                result = result.filter { it.category == category }
            }
            
            // 过滤编程语言
            criteria.language?.let { language ->
                result = result.filter { 
                    it.supportedLanguages.isEmpty() || it.supportedLanguages.contains(language)
                }
            }
            
            // 过滤标签
            if (criteria.tags.isNotEmpty()) {
                result = result.filter { template ->
                    criteria.tags.any { tag -> template.tags.contains(tag) }
                }
            }
            
            // 搜索关键词
            criteria.query?.let { query ->
                if (query.isNotBlank()) {
                    result = result.filter { it.matchesSearch(query) }
                }
            }
            
            // 排序
            result = when (criteria.sortBy) {
                TemplateSortBy.NAME -> {
                    if (criteria.sortDirection == SortDirection.ASC) {
                        result.sortedBy { it.name }
                    } else {
                        result.sortedByDescending { it.name }
                    }
                }
                TemplateSortBy.CREATED_AT -> {
                    if (criteria.sortDirection == SortDirection.ASC) {
                        result.sortedBy { it.createdAt }
                    } else {
                        result.sortedByDescending { it.createdAt }
                    }
                }
                TemplateSortBy.UPDATED_AT -> {
                    if (criteria.sortDirection == SortDirection.ASC) {
                        result.sortedBy { it.updatedAt }
                    } else {
                        result.sortedByDescending { it.updatedAt }
                    }
                }
                TemplateSortBy.USAGE_COUNT -> {
                    if (criteria.sortDirection == SortDirection.ASC) {
                        result.sortedBy { it.usageCount }
                    } else {
                        result.sortedByDescending { it.usageCount }
                    }
                }
                TemplateSortBy.RATING -> {
                    if (criteria.sortDirection == SortDirection.ASC) {
                        result.sortedBy { it.rating }
                    } else {
                        result.sortedByDescending { it.rating }
                    }
                }
            }.asSequence()
            
            return result.toList()
        }
    }
    
    override fun getTemplatesByCategory(category: TemplateCategory): List<Template> {
        return searchTemplates(TemplateSearchCriteria(category = category))
    }
    
    override fun getTemplatesByTags(tags: List<String>): List<Template> {
        return searchTemplates(TemplateSearchCriteria(tags = tags))
    }
    
    override fun getRecommendedTemplates(language: String?, limit: Int): List<Template> {
        // 简单的推荐算法：结合使用次数和评分
        return searchTemplates(
            TemplateSearchCriteria(
                language = language,
                sortBy = TemplateSortBy.RATING,
                sortDirection = SortDirection.DESC
            )
        ).filter { it.usageCount > 0 || it.rating > 3.0 }
         .take(limit)
    }
    
    override fun createTemplate(template: Template): Template {
        templatesLock.write {
            val newTemplate = template.copy(
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            templates[newTemplate.id] = newTemplate
            usageCounters[newTemplate.id] = AtomicLong(0)
            ratings[newTemplate.id] = mutableListOf()
            
            logger.info("创建新模板: ${newTemplate.name} (${newTemplate.id})")
            return newTemplate
        }
    }
    
    override fun updateTemplate(template: Template): Template {
        templatesLock.write {
            val existingTemplate = templates[template.id]
            if (existingTemplate != null) {
                val updatedTemplate = template.copy(
                    createdAt = existingTemplate.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    usageCount = existingTemplate.usageCount,
                    rating = existingTemplate.rating,
                    ratingCount = existingTemplate.ratingCount
                )
                templates[template.id] = updatedTemplate
                
                logger.info("更新模板: ${updatedTemplate.name} (${updatedTemplate.id})")
                return updatedTemplate
            } else {
                throw IllegalArgumentException("模板不存在: ${template.id}")
            }
        }
    }
    
    override fun deleteTemplate(id: String): Boolean {
        templatesLock.write {
            val template = templates[id]
            if (template != null && !template.isBuiltIn) {
                templates.remove(id)
                usageCounters.remove(id)
                ratings.remove(id)
                
                logger.info("删除模板: ${template.name} ($id)")
                return true
            }
            return false
        }
    }
    
    override fun setTemplateEnabled(id: String, enabled: Boolean): Boolean {
        templatesLock.write {
            val template = templates[id]
            if (template != null) {
                templates[id] = template.copy(enabled = enabled, updatedAt = System.currentTimeMillis())
                logger.info("${if (enabled) "启用" else "禁用"}模板: ${template.name} ($id)")
                return true
            }
            return false
        }
    }
    
    override fun incrementUsageCount(id: String) {
        val counter = usageCounters[id]
        if (counter != null) {
            val newCount = counter.incrementAndGet()
            
            // 更新模板中的使用次数
            templatesLock.write {
                val template = templates[id]
                if (template != null) {
                    templates[id] = template.copy(usageCount = newCount)
                }
            }
        }
    }
    
    override fun rateTemplate(id: String, rating: Int): Boolean {
        if (rating !in 1..5) {
            return false
        }
        
        val templateRatings = ratings.computeIfAbsent(id) { mutableListOf() }
        templateRatings.add(rating)
        
        // 计算新的平均评分
        val averageRating = templateRatings.average()
        val ratingCount = templateRatings.size
        
        // 更新模板中的评分信息
        templatesLock.write {
            val template = templates[id]
            if (template != null) {
                templates[id] = template.copy(
                    rating = averageRating,
                    ratingCount = ratingCount,
                    updatedAt = System.currentTimeMillis()
                )
                return true
            }
        }
        
        return false
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
        
        // 验证变量
        val validationResult = template.validateVariables(variableValues)
        if (!validationResult.isValid) {
            return TemplateRenderResult(
                success = false,
                error = "变量替换失败",
                template = template
            )
        }
        
        try {
            val renderedContent = template.render(variableValues)
            
            // 增加使用次数
            incrementUsageCount(templateId)
            
            return TemplateRenderResult(
                success = true,
                content = renderedContent,
                template = template
            )
        } catch (e: Exception) {
            logger.error("渲染模板失败: $templateId", e)
            return TemplateRenderResult(
                success = false,
                error = "变量替换失败",
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
        
        return template.validateVariables(variableValues)
    }
    
    override fun validateTemplate(template: Template): TemplateValidationResult {
        val errors = mutableListOf<String>()
        
        // 验证ID
        if (template.id.isBlank()) {
            errors.add("模板ID不能为空")
        }
        
        // 验证名称
        if (template.name.isBlank()) {
            errors.add("模板名称不能为空")
        }
        
        // 验证内容
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
        var failureCount = 0
        var skippedCount = 0
        val errors = mutableListOf<String>()
        val importedIds = mutableListOf<String>()
        
        templates.forEach { template ->
            try {
                if (this.templates.containsKey(template.id)) {
                    skippedCount++
                } else {
                    createTemplate(template)
                    successCount++
                    importedIds.add(template.id)
                }
            } catch (e: Exception) {
                failureCount++
                errors.add("导入模板 ${template.name} 失败: ${e.message}")
            }
        }
        
        logger.info("模板导入完成: 成功=$successCount, 失败=$failureCount, 跳过=$skippedCount")
        
        return TemplateImportResult(
            successCount = successCount,
            failureCount = failureCount,
            skippedCount = skippedCount,
            errors = errors,
            importedTemplateIds = importedIds
        )
    }
    
    override fun exportTemplates(templateIds: List<String>): List<Template> {
        templatesLock.read {
            return if (templateIds.isEmpty()) {
                templates.values.toList()
            } else {
                templateIds.mapNotNull { templates[it] }
            }
        }
    }
    
    override fun getAllCategories(): List<TemplateCategory> {
        return TemplateCategory.values().toList()
    }
    
    override fun getAllTags(): List<String> {
        templatesLock.read {
            return templates.values.flatMap { it.tags }.distinct().sorted()
        }
    }
    
    override fun getTemplateStats(): TemplateStats {
        templatesLock.read {
            val allTemplates = templates.values
            val enabledTemplates = allTemplates.filter { it.enabled }
            val builtInTemplates = allTemplates.filter { it.isBuiltIn }
            val customTemplates = allTemplates.filter { !it.isBuiltIn }
            
            val templatesByCategory = TemplateCategory.values().associateWith { category ->
                allTemplates.count { it.category == category }
            }
            
            val totalUsageCount = allTemplates.sumOf { it.usageCount }
            val averageRating = if (allTemplates.isNotEmpty()) {
                allTemplates.filter { it.ratingCount > 0 }.map { it.rating }.average()
            } else 0.0
            
            val mostPopularTemplate = allTemplates.maxByOrNull { it.usageCount }
            val newestTemplate = allTemplates.maxByOrNull { it.createdAt }
            
            return TemplateStats(
                totalTemplates = allTemplates.size,
                enabledTemplates = enabledTemplates.size,
                builtInTemplates = builtInTemplates.size,
                customTemplates = customTemplates.size,
                templatesByCategory = templatesByCategory,
                totalUsageCount = totalUsageCount,
                averageRating = averageRating,
                mostPopularTemplateId = mostPopularTemplate?.id,
                newestTemplateId = newestTemplate?.id
            )
        }
    }
    
    override fun resetBuiltInTemplates(): Boolean {
        try {
            templatesLock.write {
                // 删除所有内置模板
                val builtInIds = templates.values.filter { it.isBuiltIn }.map { it.id }
                builtInIds.forEach { id ->
                    templates.remove(id)
                    usageCounters.remove(id)
                    ratings.remove(id)
                }
                
                // 重新初始化内置模板
                initializeBuiltInTemplates()
            }
            
            logger.info("重置内置模板完成")
            return true
        } catch (e: Exception) {
            logger.error("重置内置模板失败", e)
            return false
        }
    }
    
    override fun backupTemplates(): String {
        val backupDir = File("backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        
        val timestamp = System.currentTimeMillis()
        val backupFile = File(backupDir, "templates_backup_$timestamp.json")
        
        try {
            val templatesData = exportTemplates()
            val jsonData = json.encodeToString(templatesData)
            backupFile.writeText(jsonData)
            
            logger.info("模板备份完成: ${backupFile.absolutePath}")
            return backupFile.absolutePath
        } catch (e: Exception) {
            logger.error("模板备份失败", e)
            throw e
        }
    }
    
    override fun restoreTemplates(backupPath: String): Boolean {
        try {
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                logger.error("备份文件不存在: $backupPath")
                return false
            }
            
            val jsonData = backupFile.readText()
            val templatesData = json.decodeFromString<List<Template>>(jsonData)
            
            val result = importTemplates(templatesData)
            logger.info("模板恢复完成: ${result.successCount} 个模板导入成功")
            
            return result.isSuccess
        } catch (e: Exception) {
            logger.error("模板恢复失败", e)
            return false
        }
    }
    
    /**
     * 初始化内置模板（已禁用）
     */
    private fun initializeBuiltInTemplates() {
        // 初始化内置模板
        val builtInTemplates = createBuiltInTemplates()
        builtInTemplates.forEach { template ->
            if (!templates.containsKey(template.id)) {
                templates[template.id] = template
                logger.debug("添加内置模板: ${template.name}")
            }
        }
        logger.info("内置模板初始化完成，共加载 ${builtInTemplates.size} 个模板")
    }
    
    /**
     * 创建内置模板
     */
    private fun createBuiltInTemplates(): List<Template> {
        return listOf(
            // 驼峰命名转换模板
            Template(
                id = "camel-case-convert",
                name = "驼峰命名转换",
                description = "将选中的文本转换为驼峰命名格式",
                category = TemplateCategory.CODE_CONVERSION,
                tags = listOf("命名转换", "驼峰", "格式化"),
                promptTemplate = "请将以下文本转换为驼峰命名格式，只返回转换结果：\n\n```\n{{selectedCode}}\n```",
                isBuiltIn = true
            ),
            
            // 对象转换模板
            Template(
                id = "object-convert",
                name = "对象转换",
                description = "生成Java对象之间的转换方法，基于字段分析的逐字段显式转换，支持VO/DTO/DOMAIN/ENTITY转换",
                category = TemplateCategory.CODE_CONVERSION,
                tags = listOf("对象转换", "VO", "DTO", "DOMAIN", "ENTITY", "hutool", "显式转换", "字段分析"),
                promptTemplate = "请根据以下选中的代码生成对象转换代码：\n\n{{selectedCode}}\n\n方法请求参数信息：\n{{firstRequestParam}}\n\n方法返回参数信息：\n{{responseParams}}\n\n转换要求：\n1. 将 BeanUtil.copyProperties() 方法替换为逐个字段的显式转换\n2. 只需要从请求参数转换到响应参数的单向转换\n3. 对于每个字段，使用明确的 getter/setter 方法进行转换\n4. 字段转换规则：\n   - 如果源字段和目标字段都是String类型，直接复制赋值\n   - 如果类型不同，使用hutool工具进行转换\n   - 时间类型转换默认使用年月日格式（yyyy-MM-dd）\n5. 保持代码的可读性和维护性\n6. 根据上述请求参数和返回参数信息进行精确的字段映射\n7. 如果目标对象中的字段在源对象中不存在，则直接忽略该字段，不要设置为空值或默认值\n8. 只转换存在对应关系的字段\n9. 对于需要类型转换的字段，优先使用hutool的工具类\n\n注意：请直接返回纯Java代码，不要包含任何代码块标记（如```java、```等），不要包含import语句说明，不需要额外的文字说明。",
                isBuiltIn = true
            )
        )
    }
    
    override fun dispose() {
        try {
            // 清理模板缓存
            templates.clear()
            
            // 清理使用计数器
            usageCounters.clear()
            
            // 清理评分数据
            ratings.clear()
            
            logger.info("TemplateService资源清理完成")
        } catch (e: Exception) {
            logger.error("TemplateService资源清理失败", e)
        }
    }
}