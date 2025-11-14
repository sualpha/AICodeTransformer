package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.constants.TemplateConstants
import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.i18n.LanguageManager
import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import cn.suso.aicodetransformer.ui.components.TooltipHelper

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.SwingUtilities

/**
 * 模板编辑面板 - 专门用于编辑模板的组件
 * 与PromptTemplateDetailPanel分离，避免逻辑混乱
 */
class PromptTemplateEditPanel : JPanel() {
    
    private val templateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
    
    // 可用变量列表，默认为所有内置变量
    private var availableVariables: List<Pair<String, String>> = TemplateConstants.getBuiltInVariablesMap().toList()
    
    // 基本信息字段
    private val nameField = JBTextField()
    private val descriptionField = JBTextField()
    private val categoryField = JBTextField()
    private val enabledCheckBox = JBCheckBox()

    
    // 内容字段
    private val contentArea = JBTextArea()
    private val contentScrollPane = JBScrollPane(contentArea)
    
    // 编辑按钮
    private val validateTemplateButton = JButton()
    private val insertVariableButton = JButton()
    private val resetContentButton = JButton()
    private val contentToolbar = JPanel()
    private val tabbedPane = JBTabbedPane()
    private val languageListener: () -> Unit = {
        SwingUtilities.invokeLater { refreshTexts() }
    }
    
    private var currentTemplate: PromptTemplate? = null
    private var isModified = false
    
    init {
        layout = BorderLayout()
        border = JBUI.Borders.empty(10)
        setupUI()
        setupListeners()
        setupFieldTooltips()

        refreshTexts()

        LanguageManager.addChangeListener(languageListener)

        // 默认启用编辑模式
        setEditMode(true)
    }
    
    private fun setupUI() {
        // 基本信息标签页
        tabbedPane.addTab(I18n.t("prompt.basic.tab"), createBasicInfoPanel())
        
        // 模板内容标签页
        tabbedPane.addTab(I18n.t("prompt.content.tab"), createContentPanel())
        
        add(tabbedPane, BorderLayout.CENTER)
    }
    
    private fun setupFieldTooltips() {
        TooltipHelper.setTooltip(nameField, I18n.t("prompt.tooltip.name"))
        TooltipHelper.setTooltip(descriptionField, I18n.t("prompt.tooltip.description"))
        TooltipHelper.setTooltip(categoryField, I18n.t("prompt.tooltip.category"))
        TooltipHelper.setTooltip(enabledCheckBox, I18n.t("prompt.tooltip.enabled"))

        TooltipHelper.setTooltip(contentArea, TooltipHelper.TemplateTooltips.TEMPLATE_VARIABLES)
    }
    
    private fun createBasicInfoPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(I18n.t("prompt.name.label"), nameField)
            .addLabeledComponent(I18n.t("prompt.description.label"), descriptionField)
            .addLabeledComponent(I18n.t("prompt.category.field"), categoryField)
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
        contentToolbar.add(Box.createHorizontalStrut(8))
        contentToolbar.add(resetContentButton)
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
        resetContentButton.addActionListener { resetTemplateContent() }
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
                I18n.t("prompt.validation.name.required"),
                I18n.t("prompt.validation.title")
            )
            return
        }
        
        if (content.isEmpty()) {
            Messages.showWarningDialog(
                this,
                I18n.t("prompt.validation.content.empty"),
                I18n.t("prompt.validation.title")
            )
            return
        }
        
        val template = createTemplateFromFields(true)
        if (template == null) {
            Messages.showWarningDialog(
                this,
                I18n.t("prompt.validation.create.failed"),
                I18n.t("prompt.validation.title")
            )
            return
        }
        
        try {
            // 简化的模板验证逻辑
            val currentContext = template.content
            
            val message = buildString {
                append(I18n.t("prompt.validation.result.title"))
                append("\n\n")

                if (currentContext.isNotBlank()) {
                    append(I18n.t("prompt.validation.result.success"))
                } else {
                    append(I18n.t("prompt.validation.result.empty"))
                }
            }
            
            Messages.showInfoMessage(this, message, I18n.t("prompt.validation.result.dialog"))
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                I18n.t("prompt.validation.error.message", e.message ?: ""),
                I18n.t("prompt.validation.error.title")
            )
        }
    }
    
    /**
     * 显示插入变量对话框
     */
    private fun showInsertVariableDialog() {
        val variables = availableVariables
        
        val variableNames = variables.map { "${it.first} - ${it.second}" }.toTypedArray()

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(variableNames.toList())
            .setTitle(I18n.t("prompt.insertVariable.title"))
            .setItemChosenCallback { selectedItem ->
                val selectedVariable = variables.find { "${it.first} - ${it.second}" == selectedItem }?.first
                selectedVariable?.let { insertVariableAtCursor(it) }
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
    
    /**
     * 重置模板内容
     */
    private fun resetTemplateContent() {
        val result = Messages.showYesNoDialog(
            null,
            I18n.t("prompt.reset.confirm.message"),
            I18n.t("prompt.reset.confirm.title"),
            I18n.t("prompt.reset.confirm.ok"),
            I18n.t("prompt.reset.confirm.cancel"),
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            // 根据模板类型提供默认内容
            val defaultContent = getDefaultContentForTemplate()
            contentArea.text = defaultContent
            contentArea.caretPosition = 0
            contentArea.requestFocus()
            notifyModification()
        }
    }
    
    /**
     * 根据模板类型获取默认内容
     */
    private fun getDefaultContentForTemplate(): String {
        val category = categoryField.text.trim()
        return when {
            category.contains("commit", ignoreCase = true) || 
            category.contains("git", ignoreCase = true) -> {
                I18n.t("prompt.defaultContent.commit")
            }
            category.contains("代码", ignoreCase = true) || 
            category.contains("code", ignoreCase = true) -> {
                I18n.t("prompt.defaultContent.code")
            }
            category.contains("文档", ignoreCase = true) || 
            category.contains("doc", ignoreCase = true) -> {
                I18n.t("prompt.defaultContent.doc")
            }
            else -> {
                I18n.t("prompt.defaultContent.generic")
            }
        }
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
            
            // 验证基本字段
            if (strictValidation) {
                if (name.isEmpty()) {
                    throw IllegalArgumentException(I18n.t("prompt.validation.name.required"))
                }
                
                if (content.isEmpty()) {
                    throw IllegalArgumentException(I18n.t("prompt.validation.content.required"))
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
                enabled = enabled,
                isBuiltIn = false,
                shortcutKey = shortcut,
                createdAt = currentTemplate?.createdAt ?: java.time.LocalDateTime.now().toString(),
                updatedAt = java.time.LocalDateTime.now().toString()
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(this, I18n.t("prompt.create.error.message", e.message ?: ""), I18n.t("prompt.create.error.title"))
            return null
        }
    }
    
    /**
     * 设置可用变量列表
     */
    fun setAvailableVariables(variables: List<Pair<String, String>>) {
        this.availableVariables = variables
    }

    fun refreshTexts() {
        enabledCheckBox.text = I18n.t("prompt.enableTemplate")
        validateTemplateButton.text = I18n.t("prompt.validate")
        insertVariableButton.text = I18n.t("prompt.insertVariable")
        resetContentButton.text = I18n.t("prompt.resetContent")

        tabbedPane.setTitleAt(0, I18n.t("prompt.basic.tab"))
        tabbedPane.setTitleAt(1, I18n.t("prompt.content.tab"))

        setupFieldTooltips()
    }

    fun dispose() {
        LanguageManager.removeChangeListener(languageListener)
    }
}