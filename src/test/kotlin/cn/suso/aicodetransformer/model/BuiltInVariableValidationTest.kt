package cn.suso.aicodetransformer.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 测试内置变量的验证功能
 */
class BuiltInVariableValidationTest {
    
    @Test
    fun `测试使用内置变量selectedCode的模板验证`() {
        // 用户提到的模板内容
        val template = PromptTemplate(
            id = "test-camel-case",
            name = "转换为驼峰格式",
            content = "帮我把这段代码${'$'}{selectedCode}转换成驼峰的格式",
            category = "代码转换"
        )
        
        val validationResult = template.validate()
        
        // 验证应该通过，因为selectedCode是内置变量
        assertTrue(validationResult.isValid, "使用内置变量selectedCode的模板应该验证通过")
        assertTrue(validationResult.errors.isEmpty(), "不应该有验证错误")
    }
    
    @Test
    fun `测试使用多个内置变量的模板验证`() {
        val template = PromptTemplate(
            id = "test-multiple-builtin",
            name = "多变量模板",
            content = "在文件${'$'}{fileName}中，将${'$'}{selectedCode}转换为${'$'}{language}风格的代码",
            category = "代码转换"
        )
        
        val validationResult = template.validate()
        
        assertTrue(validationResult.isValid, "使用多个内置变量的模板应该验证通过")
        assertTrue(validationResult.errors.isEmpty(), "不应该有验证错误")
    }
    
    @Test
    fun `测试使用未定义变量的模板验证`() {
        val template = PromptTemplate(
            id = "test-undefined-var",
            name = "未定义变量模板",
            content = "使用未定义的变量${'$'}{undefinedVariable}",
            category = "测试"
        )
        
        val validationResult = template.validate()
        
        // 基本验证应该通过，因为模板的基本字段都是有效的
        assertTrue(validationResult.isValid, "模板基本验证应该通过")
        assertTrue(validationResult.errors.isEmpty(), "基本验证不应该有错误")
    }
    
    @Test
    fun `测试混合使用内置变量和自定义变量的模板验证`() {
        val template = PromptTemplate(
            id = "test-mixed-vars",
            name = "混合变量模板",
            content = "处理${'$'}{selectedCode}，使用自定义参数${'$'}{customVar}",
            category = "测试"
        )
        
        val validationResult = template.validate()
        
        assertTrue(validationResult.isValid, "混合使用内置和自定义变量的模板应该验证通过")
        assertTrue(validationResult.errors.isEmpty(), "不应该有验证错误")
    }
}