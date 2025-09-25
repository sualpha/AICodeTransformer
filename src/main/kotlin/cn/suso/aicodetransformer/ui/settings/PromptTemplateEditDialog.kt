package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Prompt模板编辑对话框
 */
class PromptTemplateEditDialog(
    private val project: Project?,
    private val template: PromptTemplate? = null
) : DialogWrapper(project) {
    
    private val templateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
    private val editPanel = PromptTemplateEditPanel()
    
    private var result: PromptTemplate? = null
    
    init {
        title = if (template != null) "编辑模板" else "创建模板"
        setOKButtonText("保存")
        
        editPanel.setTemplate(template)
        
        init()
        
        // 设置对话框大小
        setSize(800, 600)
    }
    
    override fun createCenterPanel(): JComponent {
        editPanel.preferredSize = Dimension(750, 550)
        return editPanel
    }
    
    override fun doValidate(): ValidationInfo? {
        // 验证模板名称
        val templateFromFields = editPanel.createTemplateFromFields()
        if (templateFromFields == null) {
            return ValidationInfo("模板名称不能为空")
        }
        
        // 验证模板内容
        if (templateFromFields.content.isBlank()) {
            return ValidationInfo("模板内容不能为空")
        }
        
        // 验证快捷键格式
        val shortcutKey = templateFromFields.shortcutKey
        if (!shortcutKey.isNullOrBlank()) {
            if (!isValidShortcutKey(shortcutKey)) {
                return ValidationInfo("快捷键格式不正确，请使用如 Ctrl+Alt+T 的格式")
            }
            
            // 检查快捷键是否已被使用
            val existingTemplate = templateService.getTemplateByShortcut(shortcutKey)
            if (existingTemplate != null && existingTemplate.id != templateFromFields.id) {
                return ValidationInfo("快捷键已被模板 '${existingTemplate.name}' 使用")
            }
        }
        
        // 验证模板名称是否重复
        val existingTemplates = templateService.getTemplates()
        val duplicateName = existingTemplates.find { 
            it.name == templateFromFields.name && it.id != templateFromFields.id 
        }
        if (duplicateName != null) {
            return ValidationInfo("模板名称已存在")
        }
        
        // 验证模板变量
        val variableErrors = (templateService as PromptTemplateServiceImpl).validateTemplateVariables(templateFromFields)
        if (variableErrors.isNotEmpty()) {
            return ValidationInfo("模板变量验证失败: ${variableErrors.first()}")
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
                    "保存模板失败: ${e.message}",
                    "保存失败"
                )
            }
        }
    }
    
    override fun doCancelAction() {
        if (editPanel.isModified()) {
            val result = Messages.showYesNoCancelDialog(
                contentPanel,
                "模板已修改，是否保存更改？",
                "确认关闭",
                "保存",
                "不保存",
                "取消",
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
    
    /**
     * 获取编辑结果
     */
    fun getTemplate(): PromptTemplate? = result
    
    companion object {
        /**
         * 显示创建模板对话框
         */
        fun showCreateDialog(project: Project?): PromptTemplate? {
            val dialog = PromptTemplateEditDialog(project)
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
}