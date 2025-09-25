package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import java.time.LocalDateTime

/**
 * 快捷键绑定功能测试
 * 测试快捷键验证和绑定的核心逻辑
 */
@Disabled("需要IntelliJ环境支持")
class ShortcutKeyBindingTest {

    private lateinit var templateService: PromptTemplateService
    private lateinit var testTemplate: PromptTemplate

    @BeforeEach
    fun setUp() {
        templateService = PromptTemplateServiceImpl.getInstance()
        testTemplate = PromptTemplate(
            id = "test-template-1",
            name = "测试模板",
            content = "这是一个测试模板：{{code}}",
            description = "用于测试的模板",
            category = "测试",
            tags = listOf("test"),
            shortcutKey = null,
            enabled = true,
            createdAt = LocalDateTime.now().toString(),
            updatedAt = LocalDateTime.now().toString()
        )
    }

    @Test
    fun `测试快捷键格式验证`() {
        // 测试有效的快捷键格式
        val validShortcuts = listOf(
            "Ctrl+Alt+T",
            "Ctrl+Shift+F",
            "Alt+F1",
            "Ctrl+F12"
        )
        
        validShortcuts.forEach { shortcut ->
            val template = testTemplate.copy(shortcutKey = shortcut)
            val error = templateService.validateTemplate(template)
            assertNull(error, "快捷键 $shortcut 应该是有效的")
        }
        
        // 测试无效的快捷键格式
        val invalidShortcuts = listOf(
            "InvalidKey",
            "Ctrl+",
            "+Alt+T",
            "Ctrl++T"
        )
        
        invalidShortcuts.forEach { shortcut ->
            val template = testTemplate.copy(shortcutKey = shortcut)
            val error = templateService.validateTemplate(template)
            assertNotNull(error, "快捷键 $shortcut 应该是无效的")
        }
    }

    @Test
    fun `测试快捷键清除功能`() {
        // 设置快捷键
        val templateWithShortcut = testTemplate.copy(shortcutKey = "Ctrl+Alt+T")
        
        // 清除快捷键
        val clearedTemplate = templateWithShortcut.copy(shortcutKey = null)
        
        // 验证清除后的模板
        val error = templateService.validateTemplate(clearedTemplate)
        assertNull(error, "清除快捷键后应该验证通过")
        assertNull(clearedTemplate.shortcutKey, "快捷键应该被清除")
    }

    @Test
    fun `测试空白快捷键处理`() {
        // 测试空字符串和空白字符串
        val blankShortcuts = listOf("", "   ", "\t", "\n")
        
        blankShortcuts.forEach { shortcut ->
            val template = testTemplate.copy(shortcutKey = shortcut)
            val error = templateService.validateTemplate(template)
            // 空白快捷键应该被当作无快捷键处理，验证应该通过
            assertNull(error, "空白快捷键 '$shortcut' 应该验证通过")
        }
    }

    @Test
    fun `测试模板基本验证`() {
        // 测试正常模板
        val error = templateService.validateTemplate(testTemplate)
        assertNull(error, "正常模板应该验证通过")
        
        // 测试空名称模板
        val emptyNameTemplate = testTemplate.copy(name = "")
        val nameError = templateService.validateTemplate(emptyNameTemplate)
        assertNotNull(nameError, "空名称模板应该验证失败")
        
        // 测试空内容模板
        val emptyContentTemplate = testTemplate.copy(content = "")
        val contentError = templateService.validateTemplate(emptyContentTemplate)
        assertNotNull(contentError, "空内容模板应该验证失败")
    }
}