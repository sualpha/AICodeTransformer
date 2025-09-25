package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.service.impl.ActionServiceImpl
import cn.suso.aicodetransformer.model.PromptTemplate
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import javax.swing.KeyStroke

class EmptyShortcutTest {

    private lateinit var actionService: ActionService
    private lateinit var templateService: PromptTemplateService

    @Before
    fun setUp() {
        actionService = ActionServiceImpl()
        templateService = TestPromptTemplateServiceImpl()
    }

    @Test
    fun `测试空快捷键验证`() {
        // 空字符串应该是有效的
        assertTrue("空字符串应该是有效的快捷键", actionService.validateShortcut(""))
        
        // 空白字符串应该是有效的
        assertTrue("空白字符串应该是有效的快捷键", actionService.validateShortcut("   "))
        
        // 空快捷键不应该与IDE内置功能冲突
        assertFalse("空快捷键不应该与IDE内置功能冲突", actionService.isShortcutInUse(""))
        assertFalse("空白快捷键不应该与IDE内置功能冲突", actionService.isShortcutInUse("   "))
    }

    @Test
    fun `测试空快捷键解析`() {
        // 空字符串解析应该返回null
        assertNull("空字符串解析应该返回null", actionService.parseShortcut(""))
        
        // 空白字符串解析应该返回null
        assertNull("空白字符串解析应该返回null", actionService.parseShortcut("   "))
    }

    @Test
    fun `测试模板空快捷键保存`() {
        val template = PromptTemplate(
            id = "test-empty-shortcut",
            name = "测试空快捷键模板",
            content = "测试内容",
            shortcutKey = "", // 空快捷键
            category = "测试"
        )

        // 验证模板应该通过
        val validationResult = templateService.validateTemplate(template)
        assertNull("空快捷键模板验证应该通过: $validationResult", validationResult)

        // 保存模板应该成功（无异常抛出）
        try {
            templateService.saveTemplate(template)
        } catch (e: Exception) {
            fail("空快捷键模板保存应该成功，但抛出异常: ${e.message}")
        }
    }

    @Test
    fun `测试模板空白快捷键保存`() {
        val template = PromptTemplate(
            id = "test-blank-shortcut",
            name = "测试空白快捷键模板",
            content = "测试内容",
            shortcutKey = "   ", // 空白快捷键
            category = "测试"
        )

        // 验证模板应该通过
        val validationResult = templateService.validateTemplate(template)
        assertNull("空白快捷键模板验证应该通过: $validationResult", validationResult)

        // 保存模板应该成功（无异常抛出）
        try {
            templateService.saveTemplate(template)
        } catch (e: Exception) {
            fail("空白快捷键模板保存应该成功，但抛出异常: ${e.message}")
        }
        
        // 保存后快捷键应该被规范化为null
        val savedTemplate = templateService.getTemplate("test-blank-shortcut")
        assertNotNull("保存的模板应该存在", savedTemplate)
        assertTrue("保存后快捷键应该为null或空", savedTemplate?.shortcutKey.isNullOrBlank())
    }
}