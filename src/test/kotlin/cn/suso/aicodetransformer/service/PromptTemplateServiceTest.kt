package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Prompt模板服务测试类
 * 用于验证原有的PromptTemplate功能
 */
class PromptTemplateServiceTest {
    
    private lateinit var promptTemplateService: PromptTemplateService
    
    @TempDir
    lateinit var tempDir: File
    
    @BeforeEach
    fun setUp() {
        promptTemplateService = TestPromptTemplateServiceImpl()
    }
    
    @Test
    fun `测试创建基本Prompt模板`() {
        // 创建一个基本的Prompt模板
        val template = PromptTemplate(
            id = "test-prompt",
            name = "测试Prompt",
            content = "请优化以下代码：\n{{selectedCode}}",
            description = "用于测试的代码优化Prompt",
            category = "代码优化",
            enabled = true
        )
        
        // 保存模板
        promptTemplateService.saveTemplate(template)
        
        // 获取保存的模板
        val savedTemplate = promptTemplateService.getTemplate("test-prompt")
        
        // 验证保存成功
        assertNotNull(savedTemplate)
        assertEquals("test-prompt", savedTemplate!!.id)
        assertEquals("测试Prompt", savedTemplate.name)
        assertTrue(savedTemplate.enabled)
        assertTrue(savedTemplate.content.contains("{{selectedCode}}"))
    }
    
    @Test
    fun `测试获取所有模板`() {
        // 创建多个测试模板
        val templates = listOf(
            PromptTemplate(
                id = "optimize-1",
                name = "优化模板1",
                content = "优化代码：{{selectedCode}}",
                category = "代码优化"
            ),
            PromptTemplate(
                id = "explain-1",
                name = "解释模板1",
                content = "解释代码：{{selectedCode}}",
                category = "代码解释"
            ),
            PromptTemplate(
                id = "test-1",
                name = "测试模板1",
                content = "生成测试：{{selectedCode}}",
                category = "测试生成",
                enabled = false // 禁用状态
            )
        )
        
        // 保存所有模板
        templates.forEach { promptTemplateService.saveTemplate(it) }
        
        // 获取所有模板
        val allTemplates = promptTemplateService.getTemplates()
        assertTrue(allTemplates.size >= 3)
        
        // 获取启用的模板
        val enabledTemplates = promptTemplateService.getEnabledTemplates()
        assertTrue(enabledTemplates.all { it.enabled })
        
        // 验证禁用的模板不在启用列表中
        assertFalse(enabledTemplates.any { it.id == "test-1" })
    }
    
    @Test
    fun `测试模板变量替换`() {
        // 创建包含多个变量的模板
        val template = PromptTemplate(
            id = "multi-var",
            name = "多变量模板",
            content = "将以下{{language}}代码转换为{{targetLanguage}}：\n{{selectedCode}}\n\n要求：{{requirements}}",
            category = "代码转换"
        )
        
        promptTemplateService.saveTemplate(template)
        
        // 准备变量值
        val variables = mapOf(
            "language" to "Java",
            "targetLanguage" to "Kotlin",
            "selectedCode" to "public class Test { }",
            "requirements" to "保持原有逻辑不变"
        )
        
        // 渲染模板
        val rendered = promptTemplateService.processTemplate("multi-var", variables)
        
        // 验证变量替换
        assertTrue(rendered.contains("将以下Java代码转换为Kotlin"))
        assertTrue(rendered.contains("public class Test { }"))
        assertTrue(rendered.contains("保持原有逻辑不变"))
        assertFalse(rendered.contains("{{language}}"))
        assertFalse(rendered.contains("{{targetLanguage}}"))
    }
    
    @Test
    fun `测试内置模板`() {
        // 获取内置模板
        val builtInTemplates = promptTemplateService.getDefaultTemplates()
        
        // 验证内置模板存在
        assertTrue(builtInTemplates.isNotEmpty())
        
        // 验证包含基本的内置模板
        val templateNames = builtInTemplates.map { it.name }
        assertTrue(templateNames.any { it.contains("优化") })
        assertTrue(templateNames.any { it.contains("解释") })
        assertTrue(templateNames.any { it.contains("测试") })
        
        // 验证所有内置模板都标记为内置
        assertTrue(builtInTemplates.all { it.isBuiltIn })
    }
    
    @Test
    fun `测试模板搜索`() {
        // 创建测试模板
        val templates = listOf(
            PromptTemplate(
                id = "java-opt",
                name = "Java优化",
                content = "优化Java代码",
                category = "代码优化",
                tags = listOf("Java", "优化")
            ),
            PromptTemplate(
                id = "python-doc",
                name = "Python文档",
                content = "生成Python文档",
                category = "文档生成",
                tags = listOf("Python", "文档")
            ),
            PromptTemplate(
                id = "kotlin-test",
                name = "Kotlin测试",
                content = "生成Kotlin测试",
                category = "测试生成",
                tags = listOf("Kotlin", "测试")
            )
        )
        
        templates.forEach { promptTemplateService.saveTemplate(it) }
        
        // 搜索Java相关模板
        val javaResults = promptTemplateService.searchTemplates("Java")
        assertTrue(javaResults.any { it.name.contains("Java") })
        
        // 搜索优化相关模板
        val optimizeResults = promptTemplateService.searchTemplates("优化")
        assertTrue(optimizeResults.any { it.name.contains("优化") })
        
        // 搜索不存在的关键词
        val noResults = promptTemplateService.searchTemplates("不存在的关键词")
        assertTrue(noResults.isEmpty())
    }
    
    @Test
    fun `测试模板导入导出`() {
        // 创建测试模板
        val template = PromptTemplate(
            id = "export-test",
            name = "导出测试",
            content = "这是一个测试模板：{{selectedCode}}",
            description = "用于测试导入导出",
            category = "测试",
            tags = listOf("测试", "导出")
        )
        
        promptTemplateService.saveTemplate(template)
        
        // 导出到文件
        val exportFile = File(tempDir, "test-export.json")
        promptTemplateService.exportTemplatesToFile(exportFile.absolutePath, listOf("export-test"))
        
        // 验证文件存在
        assertTrue(exportFile.exists())
        assertTrue(exportFile.length() > 0)
        
        // 删除原模板
        promptTemplateService.deleteTemplate("export-test")
        assertNull(promptTemplateService.getTemplate("export-test"))
        
        // 从文件导入
        val importedTemplates = promptTemplateService.importTemplatesFromFile(exportFile.absolutePath)
        assertEquals(1, importedTemplates.size)
        
        // 验证导入成功
        val importedTemplate = promptTemplateService.getTemplate("export-test")
        assertNotNull(importedTemplate)
        assertEquals("导出测试", importedTemplate!!.name)
        assertEquals("这是一个测试模板：{{selectedCode}}", importedTemplate.content)
    }
    
    @Test
    fun `测试模板验证`() {
        // 测试有效模板
        val validTemplate = PromptTemplate(
            id = "valid-test",
            name = "有效模板",
            content = "这是一个有效的模板：{{selectedCode}}",
            category = "测试"
        )
        
        val validResult = validTemplate.validate()
        assertTrue(validResult.isValid)
        assertTrue(validResult.errors.isEmpty())
        
        // 测试无效模板（空名称）
        val invalidTemplate = PromptTemplate(
            id = "invalid-test",
            name = "", // 空名称
            content = "内容",
            category = "测试"
        )
        
        val invalidResult = invalidTemplate.validate()
        assertFalse(invalidResult.isValid)
        assertTrue(invalidResult.errors.isNotEmpty())
        assertTrue(invalidResult.errors.any { it.contains("名称") })
    }
    
    @Test
    fun `测试模板使用统计`() {
        // 创建测试模板
        val template = PromptTemplate(
            id = "usage-test",
            name = "使用统计测试",
            content = "测试使用统计",
            category = "测试"
        )
        
        promptTemplateService.saveTemplate(template)
        
        // 初始使用次数应为0
        assertEquals(0, promptTemplateService.getTemplateUsageCount("usage-test"))
        
        // 记录使用
        promptTemplateService.recordTemplateUsage("usage-test")
        promptTemplateService.recordTemplateUsage("usage-test")
        promptTemplateService.recordTemplateUsage("usage-test")
        
        // 验证使用次数
        assertEquals(3, promptTemplateService.getTemplateUsageCount("usage-test"))
    }
}