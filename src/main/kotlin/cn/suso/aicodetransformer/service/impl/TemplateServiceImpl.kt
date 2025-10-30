package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.model.*
import cn.suso.aicodetransformer.service.TemplateService
import cn.suso.aicodetransformer.model.TemplateRenderResult
import org.slf4j.LoggerFactory
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
    
    // 模板存储
    private val templates = ConcurrentHashMap<String, Template>()
    private val templatesLock = ReentrantReadWriteLock()
    
    // 使用计数器
    private val usageCounters = ConcurrentHashMap<String, AtomicLong>()
    
    init {
        // 初始化内置模板
        initializeBuiltInTemplates()
        logger.info("模板服务初始化完成，加载了 ${templates.size} 个模板")
    }
    
    override fun getTemplateById(id: String): Template? {
        return templates[id]
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
    
    /**
     * 初始化内置模板
     */
    private fun initializeBuiltInTemplates() {
        // 初始化内置模板
        val builtInTemplates = createBuiltInTemplates()
        builtInTemplates.forEach { template ->
            if (!templates.containsKey(template.id)) {
                templates[template.id] = template
                usageCounters[template.id] = AtomicLong(0)
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
            // 标准Commit模板
            Template(
                id = "commit-default",
                name = "Git提交信息生成",
                description = "精简的Git提交信息模板，生成简洁明了的提交信息",
                category = TemplateCategory.GIT_OPERATIONS,
                tags = listOf("commit", "git", "default"),
                promptTemplate = """
                    根据代码变更生成简洁的Git提交信息：

                    变更文件：{{changedFiles}}
                    文件差异：{{fileDiffs}}

                    格式：<type>: <description>
                    类型：feat/fix/docs/style/refactor/test/chore
                    描述：简洁说明变更内容（30字符以内）
                    
                    直接输出提交信息，使用中文。
                """.trimIndent(),
                isBuiltIn = true
            )
        )
    }
    
    // ========== Commit模板专门方法实现 ==========
    
    override fun getCommitTemplates(): List<Template> {
        templatesLock.read {
            return templates.values.filter { template ->
                template.tags.contains("commit") || template.tags.contains("git")
            }
        }
    }
    
    override fun getDefaultCommitTemplate(): Template? {
        // 首先查找标记为默认的commit模板
        val defaultTemplate = templates.values.find { 
            it.tags.contains("commit") && it.tags.contains("default") 
        }
        
        // 如果没有默认模板，返回第一个commit模板
        return defaultTemplate ?: getCommitTemplates().firstOrNull()
    }
    
    override fun setDefaultCommitTemplate(templateId: String): Boolean {
        templatesLock.write {
            val template = templates[templateId] ?: return false
            
            // 移除其他模板的默认标记
            templates.values.filter { it.tags.contains("commit") && it.tags.contains("default") }
                .forEach { existingTemplate ->
                    val updatedTags = existingTemplate.tags.toMutableList()
                    updatedTags.remove("default")
                    templates[existingTemplate.id] = existingTemplate.copy(
                        tags = updatedTags,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            
            // 为指定模板添加默认标记
            val updatedTags = template.tags.toMutableList()
            if (!updatedTags.contains("default")) {
                updatedTags.add("default")
            }
            if (!updatedTags.contains("commit")) {
                updatedTags.add("commit")
            }
            
            templates[templateId] = template.copy(
                tags = updatedTags,
                updatedAt = System.currentTimeMillis()
            )
            
            return true
        }
    }
    
    override fun createCommitTemplate(name: String, content: String, description: String): Template {
        templatesLock.write {
            val template = Template(
                id = "commit_${System.currentTimeMillis()}",
                name = name,
                description = description,
                category = TemplateCategory.GIT_OPERATIONS,
                tags = listOf("commit", "git", "custom"),
                promptTemplate = content,
                isBuiltIn = false,
                enabled = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            templates[template.id] = template
            usageCounters[template.id] = AtomicLong(0)
            
            logger.info("创建新提交模板: ${template.name} (${template.id})")
            return template
        }
    }
    
    override fun getCommitTemplateVariables(): Map<String, String> {
        return mapOf(
            "changedFiles" to "已修改的文件列表",
            "fileDiffs" to "文件差异详情",
            "projectName" to "项目名称",
            "author" to "提交作者",
            "timestamp" to "当前时间戳",
            "fileCount" to "修改文件数量",
            "addedLines" to "新增行数",
            "deletedLines" to "删除行数",
            "modifiedLines" to "修改行数"
        )
    }

    override fun dispose() {
        try {
            // 清理模板缓存
            templates.clear()
            
            // 清理使用计数器
            usageCounters.clear()
            
            logger.info("TemplateService资源清理完成")
        } catch (e: Exception) {
            logger.error("TemplateService资源清理失败", e)
        }
    }
}