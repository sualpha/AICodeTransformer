package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.i18n.LanguageManager
import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import cn.suso.aicodetransformer.constants.TemplateConstants
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Prompt模板编辑对话框
 */
class PromptTemplateEditDialog(
    private val project: Project?,
    private val template: PromptTemplate? = null,
    private val defaultCategory: String? = null
) : DialogWrapper(project) {
    
    private val templateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
    private val editPanel = PromptTemplateEditPanel()
    
    private var result: PromptTemplate? = null

    private val applyButton = JButton()
    private val okButton = JButton()
    private val cancelButton = JButton()

    private val languageListener: () -> Unit = {
        SwingUtilities.invokeLater {
            if (!isDisposed) {
                refreshTexts()
            }
        }
    }

    init {
        editPanel.setTemplate(template)

        // 根据模板类型设置可用变量
        setAvailableVariablesForTemplate(template)

        init()

        // 设置对话框大小
        setSize(800, 600)

        refreshTexts()
        LanguageManager.addChangeListener(languageListener)
    }
    
    override fun createCenterPanel(): JComponent {
        editPanel.preferredSize = Dimension(750, 550)
        return editPanel
    }
    
    override fun createSouthPanel(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))

        // 创建应用按钮
        applyButton.addActionListener {
            if (doValidate() == null) {
                doApplyAction()
            }
        }

        // 创建确认按钮
        okButton.addActionListener {
            doOKAction()
        }

        // 创建取消按钮
        cancelButton.addActionListener {
            doCancelAction()
        }

        panel.add(applyButton)
        panel.add(okButton)
        panel.add(cancelButton)

        return panel
    }

    private fun doApplyAction() {
        val templateFromFields = editPanel.createTemplateFromFields()
        if (templateFromFields != null) {
            try {
                templateService.saveTemplate(templateFromFields)
                result = templateFromFields
                // 应用后不关闭对话框，只是保存
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    contentPanel,
                    I18n.t("prompt.dialog.save.error.message", e.message ?: ""),
                    I18n.t("prompt.dialog.save.error.title")
                )
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        // 验证模板名称
        val templateFromFields = editPanel.createTemplateFromFields()
        if (templateFromFields == null) {
            return ValidationInfo(I18n.t("prompt.validation.name.required"))
        }

        // 验证模板内容
        if (templateFromFields.content.isBlank()) {
            return ValidationInfo(I18n.t("prompt.validation.content.required"))
        }

        // 验证快捷键格式
        val shortcutKey = templateFromFields.shortcutKey
        if (!shortcutKey.isNullOrBlank()) {
            if (!isValidShortcutKey(shortcutKey)) {
                return ValidationInfo(I18n.t("prompt.validation.shortcut.invalid"))
            }
            
            // 检查快捷键是否已被使用
            val existingTemplate = templateService.getTemplateByShortcut(shortcutKey)
            if (existingTemplate != null && existingTemplate.id != templateFromFields.id) {
                return ValidationInfo(I18n.t("prompt.validation.shortcut.duplicate", existingTemplate.name))
            }
        }
        
        // 验证模板名称是否重复
        val existingTemplates = templateService.getTemplates()
        val duplicateName = existingTemplates.find { 
            it.name == templateFromFields.name && it.id != templateFromFields.id 
        }
        if (duplicateName != null) {
            return ValidationInfo(I18n.t("prompt.validation.name.duplicate"))
        }
        
        // 验证模板变量
        val variableErrors = (templateService as PromptTemplateServiceImpl).validateTemplateVariables(templateFromFields)
        if (variableErrors.isNotEmpty()) {
            return ValidationInfo(I18n.t("prompt.validation.variables.failed", variableErrors.first()))
        }
        
        return null
    }

    
    override fun doOKAction() {
        val templateFromFields = editPanel.createTemplateFromFields()
        if (templateFromFields != null) {
            try {
                templateService.saveTemplate(templateFromFields)
                result = templateFromFields
                super.doOKAction()
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    contentPanel,
                    I18n.t("prompt.dialog.save.error.message", e.message ?: ""),
                    I18n.t("prompt.dialog.save.error.title")
                )
            }
        }
    }

    override fun doCancelAction() {
        if (editPanel.isModified()) {
            val result = Messages.showYesNoCancelDialog(
                contentPanel,
                I18n.t("prompt.dialog.confirm.close.message"),
                I18n.t("prompt.dialog.confirm.close.title"),
                I18n.t("prompt.dialog.confirm.close.save"),
                I18n.t("prompt.dialog.confirm.close.discard"),
                I18n.t("prompt.dialog.confirm.close.cancel"),
                Messages.getQuestionIcon()
            )
            
            when (result) {
                Messages.YES -> {
                    doOKAction()
                    return
                }
                Messages.NO -> {
                    super.doCancelAction()
                }
                Messages.CANCEL -> {
                    return
                }
            }
        } else {
            super.doCancelAction()
        }
    }

    /**
     * 验证快捷键格式
     */
    private fun isValidShortcutKey(shortcutKey: String): Boolean {
        // 简单的快捷键格式验证
        val pattern = Regex("^(Ctrl|Alt|Shift|Meta)(\\+(Ctrl|Alt|Shift|Meta))*\\+[A-Za-z0-9]$")
        return pattern.matches(shortcutKey)
    }

    private fun refreshTexts() {
        title = if (template != null) {
            I18n.t("prompt.dialog.edit.title")
        } else {
            I18n.t("prompt.dialog.create.title")
        }

        setOKButtonText(I18n.t("prompt.dialog.button.ok"))
        setCancelButtonText(I18n.t("prompt.dialog.button.cancel"))

        applyButton.text = I18n.t("prompt.dialog.button.apply")
        okButton.text = I18n.t("prompt.dialog.button.ok")
        cancelButton.text = I18n.t("prompt.dialog.button.cancel")

        getOKAction().putValue(Action.NAME, I18n.t("prompt.dialog.button.ok"))
        getCancelAction().putValue(Action.NAME, I18n.t("prompt.dialog.button.cancel"))

        setAvailableVariablesForTemplate(template)
        editPanel.refreshTexts()
    }

    /**
     * 获取编辑结果
     */
    fun getTemplate(): PromptTemplate? = result
     
     /**
      * 根据模板类型设置可用变量
      */
     private fun setAvailableVariablesForTemplate(template: PromptTemplate?) {
        val isGitRelated = sequenceOf(
            template?.id,
            template?.name,
            template?.category,
            defaultCategory
        ).filterNotNull().any { value ->
            value.contains("commit", ignoreCase = true) ||
                value.contains("git", ignoreCase = true)
        }

        val availableVariables = if (isGitRelated) {
            TemplateConstants.GitBuiltInVariable.values().map { it.variable to it.description }
        } else {
            TemplateConstants.TemplateBuiltInVariable.values().map { it.variable to it.description }
        }

        editPanel.setAvailableVariables(availableVariables)
    }
    
    companion object {
        /**
         * 显示创建模板对话框
         */
        fun showCreateDialog(project: Project?, category: String? = null): PromptTemplate? {
            val dialog = PromptTemplateEditDialog(project, null, category)
            return if (dialog.showAndGet()) {
                dialog.getTemplate()
            } else {
                null
            }
        }
        
        /**
         * 显示编辑模板对话框
         */
        fun showEditDialog(project: Project?, template: PromptTemplate): PromptTemplate? {
            val dialog = PromptTemplateEditDialog(project, template)
            return if (dialog.showAndGet()) {
                dialog.getTemplate()
            } else {
                null
            }
        }
    }

    override fun dispose() {
        editPanel.dispose()
        LanguageManager.removeChangeListener(languageListener)
        super.dispose()
    }
}