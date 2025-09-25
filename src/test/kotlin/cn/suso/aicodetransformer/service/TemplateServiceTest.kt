package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.*
import cn.suso.aicodetransformer.service.impl.TemplateServiceImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * 模板服务测试类
 * 用于验证模板功能的基本操作
 */
class TemplateServiceTest {
    
    private lateinit var templateService: TemplateService
    
    @BeforeEach
    fun setUp() {
        templateService = TestTemplateServiceImpl()
    }
    
    @Test
    fun `测试创建简单模板`() {
        // 创建一个简单的代码优化模板
        val template = Template(
            id = "test-optimize",
            name = "测试代码优化",
            description = "用于测试的代码优化模板",
            category = TemplateCategory.CODE_OPTIMIZATION,
            promptTemplate = "请优化以下代码：\n${'$'}{selectedCode}",
            enabled = true
        )
        
        // 保存模板
        val savedTemplate = templateService.createTemplate(template)
        
        // 验证保存成功
        assertNotNull(savedTemplate)
        assertEquals("test-optimize", savedTemplate.id)
        assertEquals("测试代码优化", savedTemplate.name)
        assertTrue(savedTemplate.enabled)
    }
    
    @Test
    fun `测试模板变量渲染`() {
        // 创建包含变量的模板
        val template = Template(
            id = "test-render",
            name = "测试渲染",
            description = "测试模板变量渲染",
            category = TemplateCategory.CODE_CONVERSION,
            promptTemplate = "将以下${'$'}{language}代码转换为${'$'}{targetLanguage}：\n${'$'}{selectedCode}",
            enabled = true
        )
        
        // 先保存模板
        templateService.createTemplate(template)
        
        // 渲染模板
        val result = templateService.renderTemplate(template.id, mapOf())
        
        // 验证渲染结果
        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content!!.contains("代码转换"))
    }
    
    @Test
    fun `测试模板验证`() {
        // 测试无效模板（缺少必需字段）
        val invalidTemplate = Template(
            id = "", // 空ID
            name = "", // 空名称
            description = "测试无效模板",
            category = TemplateCategory.CUSTOM,
            promptTemplate = "" // 空内容
        )
        
        // 验证模板
        val validationResult = templateService.validateTemplate(invalidTemplate)
        
        // 验证失败
        assertFalse(validationResult.isValid)
        assertTrue(validationResult.errors.isNotEmpty())
        assertTrue(validationResult.errors.any { it.contains("ID") })
        assertTrue(validationResult.errors.any { it.contains("名称") })
        assertTrue(validationResult.errors.any { it.contains("内容") })
    }
    
    @Test
    fun `测试获取内置模板`() {
        // 获取所有模板并过滤内置模板
        val allTemplates = templateService.getAllTemplates()
        val builtInTemplates = allTemplates.filter { it.isBuiltIn }
        
        // 验证内置模板存在
        assertTrue(builtInTemplates.isNotEmpty())
        
        // 验证包含基本的模板类型
        val categories = builtInTemplates.map { it.category }.toSet()
        assertTrue(categories.contains(TemplateCategory.CODE_CONVERSION))
        assertTrue(categories.contains(TemplateCategory.TESTING))
        assertTrue(categories.contains(TemplateCategory.DOCUMENTATION))
        
        // 验证所有内置模板都标记为内置
        assertTrue(builtInTemplates.all { it.isBuiltIn })
    }
    
    @Test
    fun `测试模板搜索功能`() {
        // 创建几个测试模板
        val templates = listOf(
            Template(
                id = "java-optimize",
                name = "Java代码优化",
                description = "优化Java代码性能",
                category = TemplateCategory.CODE_OPTIMIZATION,
                promptTemplate = "优化Java代码",
                tags = listOf("Java", "优化")
            ),
            Template(
                id = "python-test",
                name = "Python测试生成",
                description = "生成Python单元测试",
                category = TemplateCategory.TESTING,
                promptTemplate = "生成Python测试",
                tags = listOf("Python", "测试")
            )
        )
        
        // 保存模板
        templates.forEach { templateService.createTemplate(it) }
        
        // 搜索Java相关模板
        val javaTemplates = templateService.searchTemplates(
            TemplateSearchCriteria(query = "Java")
        )
        assertTrue(javaTemplates.any { it.name.contains("Java") })
        
        // 搜索测试相关模板
        val testTemplates = templateService.searchTemplates(
            TemplateSearchCriteria(query = "测试")
        )
        assertTrue(testTemplates.any { it.name.contains("测试") })
    }
    
    @Test
    fun `测试模板导入导出功能`() {
        // 创建测试模板
        val template = Template(
            id = "export-test",
            name = "导出测试模板",
            description = "用于测试导入导出功能",
            category = TemplateCategory.CUSTOM,
            promptTemplate = "这是一个测试模板"
        )
        
        // 保存模板
        templateService.createTemplate(template)
        
        // 导出模板
        val exportedTemplates = templateService.exportTemplates(listOf("export-test"))
        assertEquals(1, exportedTemplates.size)
        assertEquals("export-test", exportedTemplates[0].id)
        
        // 删除原模板
        templateService.deleteTemplate("export-test")
        
        // 导入模板
        val importResult = templateService.importTemplates(exportedTemplates)
        assertTrue(importResult.isSuccess)
        assertEquals(1, importResult.successCount)
        
        // 验证导入成功
        val importedTemplate = templateService.getTemplateById("export-test")
        assertNotNull(importedTemplate)
        assertEquals("导出测试模板", importedTemplate!!.name)
    }
}