package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import cn.suso.aicodetransformer.ui.components.TooltipHelper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Prompt模板详情面板
 */
class PromptTemplateDetailPanel : JPanel() {
    
    private val templateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
    
    // 基本信息字段
    private val nameField = JBTextField()
    private val descriptionField = JBTextField()
    private val categoryField = JBTextField()
    private val enabledCheckBox = JBCheckBox("启用模板")
    private val builtInCheckBox = JBCheckBox("内置模板")
    
    // 快捷键字段
    private val shortcutField = JBTextField()
    private val shortcutHelpLabel = JLabel("<html><small>格式: Ctrl+Alt+T 或 Ctrl+Shift+A</small></html>")
    
    // 内容字段
    private val contentArea = JBTextArea()
    private val contentScrollPane = JBScrollPane(contentArea)
    
    // 标签字段已移除 - 不再需要标签功能
    
    // 编辑按钮（仅在创建模式下显示）
    private val validateTemplateButton = JButton("验证模板")
    private val contentToolbar = JPanel()
    
    private var currentTemplate: PromptTemplate? = null
    private var isModified = false
    private val modificationListeners = mutableListOf<() -> Unit>()
    
    init {
        layout = BorderLayout()
        setupUI()
        setupListeners()
        setTemplate(null)
    }
    
    private fun setupUI() {
        val tabbedPane = JTabbedPane()
        
        // 基本信息标签页
        val basicPanel = createBasicInfoPanel()
        tabbedPane.addTab("基本信息", basicPanel)
        
        // 内容标签页
        val contentPanel = createContentPanel()
        tabbedPane.addTab("模板内容", contentPanel)
        

        
        add(tabbedPane, BorderLayout.CENTER)
        
        // 移除底部按钮面板，简化界面
        
        // 设置字段提示
        setupFieldTooltips()
    }

    private fun setupFieldTooltips() {
        // 基本信息字段提示
        TooltipHelper.setTooltip(nameField, "模板的显示名称，用于在列表中识别此模板")
        TooltipHelper.setTooltip(descriptionField, "模板的详细描述，说明模板的用途和功能")
        TooltipHelper.setTooltip(categoryField, "模板分类，用于组织和筛选模板")
        TooltipHelper.setTooltip(enabledCheckBox, "是否启用此模板，禁用后将不会在模板列表中显示")
        TooltipHelper.setTooltip(builtInCheckBox, "标识是否为内置模板，内置模板某些字段不可编辑")
        TooltipHelper.setTooltip(shortcutField, TooltipHelper.ShortcutTooltips.SHORTCUT_FORMAT)
        
        // 内容字段提示
        TooltipHelper.setTooltip(contentArea, TooltipHelper.TemplateTooltips.TEMPLATE_VARIABLES)
        
        // 标签字段提示已移除
    }

    private fun createBasicInfoPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        
        // 创建紧凑的表单布局
        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("模板名称:", nameField)
            .addLabeledComponent("描述:", descriptionField)
            .addLabeledComponent("分类:", categoryField)
            .addLabeledComponent("快捷键:", JPanel(BorderLayout()).apply {
                add(shortcutField, BorderLayout.CENTER)
                add(shortcutHelpLabel, BorderLayout.SOUTH)
            })
            .addComponent(enabledCheckBox)
            .addComponent(builtInCheckBox)
            .panel
        
        panel.add(formPanel)
        panel.add(Box.createVerticalGlue())
        
        return panel
    }
    
    private fun createContentPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        contentArea.rows = 10
        contentArea.lineWrap = true
        contentArea.wrapStyleWord = true
        contentScrollPane.preferredSize = Dimension(400, 200)
        
        panel.add(contentScrollPane, BorderLayout.CENTER)
        
        // 创建工具栏（仅在创建模式下显示）
        contentToolbar.layout = BoxLayout(contentToolbar, BoxLayout.X_AXIS)
        contentToolbar.add(validateTemplateButton)
        panel.add(contentToolbar, BorderLayout.SOUTH)
        
        return panel
    }
    


    private fun setupListeners() {
        val documentListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = notifyModification()
            override fun removeUpdate(e: DocumentEvent?) = notifyModification()
            override fun changedUpdate(e: DocumentEvent?) = notifyModification()
        }
        
        nameField.document.addDocumentListener(documentListener)
        descriptionField.document.addDocumentListener(documentListener)
        categoryField.document.addDocumentListener(documentListener)

        contentArea.document.addDocumentListener(documentListener)
        
        enabledCheckBox.addActionListener { notifyModification() }
        
        // 编辑按钮事件监听器
        validateTemplateButton.addActionListener { validateTemplateAndShowResult() }
    }
    
    fun setTemplate(template: PromptTemplate?) {
        currentTemplate = template
        
        if (template != null) {
            // 详情面板只读模式 - 只有启用状态可以修改
            nameField.text = template.name
            descriptionField.text = template.description ?: ""
            categoryField.text = template.category
            shortcutField.text = template.shortcutKey ?: ""
            contentArea.text = template.content
            enabledCheckBox.isSelected = template.enabled
            builtInCheckBox.isSelected = template.isBuiltIn

            
            // 设置所有字段为只读
            nameField.isEditable = false
            descriptionField.isEditable = false
            categoryField.isEditable = false
            shortcutField.isEditable = false
            contentArea.isEditable = false
            builtInCheckBox.isEnabled = false

            
            // 隐藏所有编辑工具栏
            contentToolbar.isVisible = false
            
            // 只有启用状态可以修改
            enabledCheckBox.isEnabled = true
            

        } else {
            // 清空显示
            clearFields()
            
            // 保持只读状态
            nameField.isEditable = false
            descriptionField.isEditable = false
            categoryField.isEditable = false

            contentArea.isEditable = false
    
            enabledCheckBox.isEnabled = false
            
            // 隐藏编辑工具栏
            contentToolbar.isVisible = false
        }
        
        isModified = false
    }
    
    private fun clearFields() {
        nameField.text = ""
        descriptionField.text = ""
        categoryField.text = ""
        shortcutField.text = ""
        contentArea.text = ""
        enabledCheckBox.isSelected = true
        builtInCheckBox.isSelected = false

        

        
        // 所有字段保持只读状态
        nameField.isEditable = false
        descriptionField.isEditable = false
        categoryField.isEditable = false
        shortcutField.isEditable = false
        contentArea.isEditable = false
        builtInCheckBox.isEnabled = false
        enabledCheckBox.isEnabled = false
    }
    

    

    
    /**
     * 验证当前模板数据
     */
    private fun validateTemplate(): String? {
        try {
            // 先检查基本字段
            val name = nameField.text.trim()
            val content = contentArea.text
            
            if (name.isEmpty()) {
                return "模板名称不能为空"
            }
            
            if (content.isEmpty()) {
                return "模板内容不能为空"
            }
            
            // 添加内存安全检查
            if (content.length > 50000) {
                return "模板内容过长，可能导致内存问题，请减少内容长度（当前：${content.length}字符，最大：50000字符）"
            }
            
            // 创建临时模板进行验证
            val tempTemplate = createTemplateFromFields() ?: return "创建模板失败"
            
            // 使用服务进行完整验证
            val validationError = templateService.validateTemplate(tempTemplate)
            if (validationError != null) {
                return validationError
            }
            
            return null
        } catch (e: OutOfMemoryError) {
            return "内存不足，模板内容可能过大，请减少内容长度"
        } catch (e: Exception) {
            return "验证模板时发生错误：${e.message}"
        }
    }
    
    private fun validateTemplateAndShowResult() {
        val result = validateTemplate()
        if (result == null) {
            Messages.showInfoMessage("模板验证通过！", "验证结果")
        } else {
            Messages.showErrorDialog(result, "验证失败")
        }
    }
    

    
    // 移除了保存、重置、复制功能的相关方法
    
    /**
     * 从表单字段创建模板对象
     */
    fun createTemplateFromFields(strictValidation: Boolean = true): PromptTemplate? {
        try {
            val name = nameField.text.trim()
            val description = descriptionField.text.trim()
            val category = categoryField.text.trim().ifEmpty { "默认" }
            val content = contentArea.text
            val enabled = enabledCheckBox.isSelected
            val shortcut: String? = null
            // 标签功能已移除
            val tags = emptyList<String>()
            
            // 验证基本字段
            if (strictValidation) {
                if (name.isEmpty()) {
                    throw IllegalArgumentException("模板名称不能为空")
                }
                
                if (content.isEmpty()) {
                    throw IllegalArgumentException("模板内容不能为空")
                }
            } else {
                // 非严格验证模式，允许空内容用于预览
                if (content.isEmpty()) {
                    return null
                }
            }
            
            return PromptTemplate(
                id = currentTemplate?.id ?: java.util.UUID.randomUUID().toString(),
                name = name,
                description = description.takeIf { it.isNotEmpty() },
                content = content,
                category = category,
                tags = tags,
                enabled = enabled,
                isBuiltIn = false,
                shortcutKey = shortcut,
                createdAt = currentTemplate?.createdAt ?: java.time.LocalDateTime.now().toString(),
                updatedAt = java.time.LocalDateTime.now().toString()
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(this, "创建模板对象失败：${e.message}", "错误")
            return null
        }
    }
    
    fun isModified(): Boolean = isModified
    
    fun apply() {
        // 保存功能已移除，不执行任何操作
    }
    
    fun reset() {
        // 重置功能已移除，不执行任何操作
    }
    
    fun addModificationListener(listener: () -> Unit) {
        modificationListeners.add(listener)
    }
    
    private fun notifyModification() {
        isModified = true
        modificationListeners.forEach { it() }
    }
}