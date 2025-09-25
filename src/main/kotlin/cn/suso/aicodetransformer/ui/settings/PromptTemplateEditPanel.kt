package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import cn.suso.aicodetransformer.ui.components.TooltipHelper

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 模板编辑面板 - 专门用于编辑模板的组件
 * 与PromptTemplateDetailPanel分离，避免逻辑混乱
 */
class PromptTemplateEditPanel : JPanel() {
    
    private val templateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
    
    // 基本信息字段
    private val nameField = JBTextField()
    private val descriptionField = JBTextField()
    private val categoryField = JBTextField()
    private val enabledCheckBox = JBCheckBox("启用模板")

    
    // 内容字段
    private val contentArea = JBTextArea()
    private val contentScrollPane = JBScrollPane(contentArea)
    
    // 编辑按钮
    private val validateTemplateButton = JButton("验证模板")
    private val insertVariableButton = JButton("插入变量")
    private val contentToolbar = JPanel()
    
    private var currentTemplate: PromptTemplate? = null
    private var isModified = false
    
    init {
        layout = BorderLayout()
        border = JBUI.Borders.empty(10)
        setupUI()
        setupListeners()
        setupFieldTooltips()
        
        // 默认启用编辑模式
        setEditMode(true)
    }
    
    private fun setupUI() {
        val tabbedPane = JBTabbedPane()
        
        // 基本信息标签页
        tabbedPane.addTab("基本信息", createBasicInfoPanel())
        
        // 模板内容标签页
        tabbedPane.addTab("模板内容", createContentPanel())
        
        add(tabbedPane, BorderLayout.CENTER)
    }
    
    private fun setupFieldTooltips() {
        TooltipHelper.setTooltip(nameField, "模板的显示名称，用于在列表中识别此模板")
        TooltipHelper.setTooltip(descriptionField, "模板的详细描述，说明模板的用途和功能")
        TooltipHelper.setTooltip(categoryField, "模板分类，用于组织和筛选模板")
        TooltipHelper.setTooltip(enabledCheckBox, "是否启用此模板，禁用后将不会在模板列表中显示")

        TooltipHelper.setTooltip(contentArea, TooltipHelper.TemplateTooltips.TEMPLATE_VARIABLES)
    }
    
    private fun createBasicInfoPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("模板名称:", nameField)
            .addLabeledComponent("描述:", descriptionField)
            .addLabeledComponent("分类:", categoryField)
            .addComponent(enabledCheckBox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
    
    private fun createContentPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        contentArea.rows = 15
        contentArea.lineWrap = true
        contentArea.wrapStyleWord = true
        contentScrollPane.preferredSize = Dimension(400, 300)
        
        panel.add(contentScrollPane, BorderLayout.CENTER)
        
        // 创建工具栏
        contentToolbar.layout = BoxLayout(contentToolbar, BoxLayout.X_AXIS)
        contentToolbar.add(insertVariableButton)
        contentToolbar.add(Box.createHorizontalStrut(8))
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
        insertVariableButton.addActionListener { showInsertVariableDialog() }
    }
    
    /**
     * 设置编辑模式
     */
    private fun setEditMode(editable: Boolean) {
        nameField.isEditable = editable
        descriptionField.isEditable = editable
        categoryField.isEditable = editable
        contentArea.isEditable = editable
        
        contentToolbar.isVisible = editable
    }
    
    /**
     * 设置模板数据
     */
    fun setTemplate(template: PromptTemplate?) {
        currentTemplate = template
        
        if (template != null) {
            nameField.text = template.name
            descriptionField.text = template.description ?: ""
            categoryField.text = template.category
            contentArea.text = template.content
            enabledCheckBox.isSelected = template.enabled
            

        } else {
            clearFields()
        }
        
        isModified = false
    }
    
    private fun clearFields() {
        nameField.text = ""
        descriptionField.text = ""
        categoryField.text = ""
        contentArea.text = ""
        enabledCheckBox.isSelected = true
        

    }
    

    

    
    private fun validateTemplateAndShowResult() {
        // 先检查基本字段
        val name = nameField.text.trim()
        val content = contentArea.text
        
        if (name.isEmpty()) {
            Messages.showWarningDialog(
                this,
                "模板名称不能为空",
                "验证模板"
            )
            return
        }
        
        if (content.isEmpty()) {
            Messages.showWarningDialog(
                this,
                "模板内容为空，无法验证",
                "验证模板"
            )
            return
        }
        
        val template = createTemplateFromFields(true)
        if (template == null) {
            Messages.showWarningDialog(
                this,
                "创建模板失败",
                "验证模板"
            )
            return
        }
        
        try {
            // 简化的模板验证逻辑
            val currentContext = template.content
            
            val message = buildString {
                append("模板验证结果：\n\n")
                
                if (currentContext.isNotBlank()) {
                    append("✓ 模板验证通过！")
                } else {
                    append("⚠ 模板内容不能为空")
                }
            }
            
            Messages.showInfoMessage(this, message, "验证结果")
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "验证模板时发生错误：${e.message}",
                "验证失败"
            )
        }
    }
    
    /**
     * 显示插入变量对话框
     */
    private fun showInsertVariableDialog() {
        val variables = listOf(
            "{{selectedCode}}" to "当前选中的代码",
            "{{fileName}}" to "当前文件名",
            "{{language}}" to "当前文件的编程语言",
            "{{filePath}}" to "当前文件路径",
            "{{className}}" to "当前类名",
            "{{methodName}}" to "当前方法名",
            "{{packageName}}" to "当前包名",
            "{{projectName}}" to "项目名称",
            "{{requestParams}}" to "方法请求参数信息（所有参数）",
            "{{firstRequestParam}}" to "第一个请求参数信息",
            "{{responseParams}}" to "方法返回参数信息",

        )
        
        val variableNames = variables.map { "${it.first} - ${it.second}" }.toTypedArray()

        val list = JBList(variableNames)
        list.selectedIndex = 0
        
        JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setTitle("插入变量")
            .setItemChoosenCallback {
                val selectedIndex = list.selectedIndex
                if (selectedIndex >= 0) {
                    val selectedVariable = variables[selectedIndex].first
                    insertVariableAtCursor(selectedVariable)
                }
            }
            .createPopup()
            .showInCenterOf(this)
    }
    
    /**
     * 在光标位置插入变量
     */
    private fun insertVariableAtCursor(variable: String) {
        val caretPosition = contentArea.caretPosition
        val currentText = contentArea.text
        val newText = currentText.substring(0, caretPosition) + variable + currentText.substring(caretPosition)
        contentArea.text = newText
        contentArea.caretPosition = caretPosition + variable.length
        contentArea.requestFocus()
        notifyModification()
    }
    
    private fun notifyModification() {
        isModified = true
    }
    
    /**
     * 检查是否有修改
     */
    fun isModified(): Boolean = isModified
    
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
}