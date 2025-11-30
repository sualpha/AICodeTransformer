package cn.suso.aicodetransformer.ui.toolwindow

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.PromptOptimizationRequest
import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.ExecutionService
import cn.suso.aicodetransformer.service.PromptOptimizationService
import cn.suso.aicodetransformer.service.PromptTemplateService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel

/**
 * Main panel for Template Debugger tool window
 * Manages two views: template list view and edit view
 */
class TemplateDebuggerPanel(private val project: Project) : JPanel(BorderLayout()), Disposable, cn.suso.aicodetransformer.service.TemplateChangeListener {
    
    private val templateService: PromptTemplateService = service()
    private val executionService: ExecutionService = service()
    private val optimizationService: PromptOptimizationService = service()
    private val configurationService: ConfigurationService = service()
    
    // UI Components
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val templateListPanel: TemplateListPanel
    private val templateEditPanel: TemplateEditPanel
    
    // State management
    private var currentTemplate: PromptTemplate? = null
    private var originalContent: String = ""
    private var isEditMode: Boolean = false
    
    init {
        border = JBUI.Borders.empty()
        background = JBColor.PanelBackground
        
        // Create sub-panels
        templateListPanel = TemplateListPanel(
            onTemplateSelected = ::onTemplateSelected,
            onRunClicked = ::onRunTemplate,
            onEditClicked = ::onEditTemplate,
            hasSelectedCode = ::hasSelectedCode,
            onModelSelected = ::onModelSelected
        )
        
        templateEditPanel = TemplateEditPanel(
            project = project,
            onOptimize = ::onOptimizeTemplate,
            onApply = ::onApplyTemplate,
            onSave = ::onSaveTemplate,
            onClose = ::exitEditMode
        )
        
        // Setup card layout
        val scrollPane = JBScrollPane(templateListPanel)
        cardPanel.add(scrollPane, VIEW_LIST)
        cardPanel.add(templateEditPanel, VIEW_EDIT)
        
        add(cardPanel, BorderLayout.CENTER)
        
        // Load templates and models
        refreshTemplateList()
        refreshModelList()
        
        setupEditorListeners()
        
        // Register template change listener
        templateService.addTemplateChangeListener(this)
    }
    
    private fun setupEditorListeners() {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        val listener = object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                if (e.editor.project == project) {
                    templateListPanel.updateRunButtonStates()
                    templateEditPanel.setApplyButtonEnabled(hasSelectedCode())
                }
            }
        }
        multicaster.addSelectionListener(listener, this)
    }
    
    override fun dispose() {
        // Unregister listener
        templateService.removeTemplateChangeListener(this)
    }
    
    // TemplateChangeListener implementation
    override fun onTemplateAdded(template: PromptTemplate) {
        ApplicationManager.getApplication().invokeLater {
            refreshTemplateList()
        }
    }
    
    override fun onTemplateUpdated(oldTemplate: PromptTemplate, newTemplate: PromptTemplate) {
        ApplicationManager.getApplication().invokeLater {
            refreshTemplateList()
            // If currently editing this template, update the view if needed
            if (currentTemplate?.id == newTemplate.id) {
                currentTemplate = newTemplate
                if (isEditMode) {
                    // Check if user has modified the content
                    val currentEditorContent = templateEditPanel.getContent()
                    val userHasModified = currentEditorContent != oldTemplate.content
                    
                    if (!userHasModified) {
                        // User hasn't modified the content, safe to update to new language
                        templateEditPanel.setContent(newTemplate.content)
                        originalContent = newTemplate.content
                    }
                    // Always update the title to reflect the new language
                    templateEditPanel.updateTemplateInfo(newTemplate)
                }
            }
        }
    }
    
    override fun onTemplateDeleted(template: PromptTemplate) {
        ApplicationManager.getApplication().invokeLater {
            refreshTemplateList()
            if (currentTemplate?.id == template.id) {
                exitEditMode()
            }
        }
    }
    
    override fun onTemplateShortcutChanged(template: PromptTemplate, oldShortcut: String?, newShortcut: String?) {
        ApplicationManager.getApplication().invokeLater {
            refreshTemplateList()
        }
    }
    
    private fun refreshTemplateList() {
        val templates = templateService.getTemplates()
        templateListPanel.setTemplates(templates)
    }
    
    private fun refreshModelList() {
        val models = configurationService.getModelConfigurations()
        templateListPanel.setModels(models)
    }
    
    private fun onModelSelected(model: ModelConfiguration) {
        // Simply set as default model - this will pin it to top in the UI
        configurationService.setDefaultModelConfiguration(model.id)
        
        // Refresh model list to show updated order
        refreshModelList()
        templateListPanel.setSelectedModel(model)
    }
    
    private fun onTemplateSelected(template: PromptTemplate) {
        currentTemplate = template
    }
    

    private fun onRunTemplate(template: PromptTemplate) {
        val editor = getActiveEditor() ?: return
        val selectedText = editor.selectionModel.selectedText ?: return
        
        if (selectedText.isBlank()) return
        
        // Execute template with selected code asynchronously
        executionService.executeTemplateAsync(
            template = template,
            selectedText = selectedText,
            project = project,
            editor = editor,
            callback = { result ->
                // Optional: Handle completion/error if needed
                if (!result.success) {
                    // Error handling is already done in service, but we could add specific UI feedback here
                }
            }
        )
    }
    
    private fun onEditTemplate(template: PromptTemplate) {
        currentTemplate = template
        originalContent = template.content
        isEditMode = true
        
        // Switch to edit view
        templateEditPanel.loadTemplate(template)
        templateEditPanel.setApplyButtonEnabled(hasSelectedCode())
        cardLayout.show(cardPanel, VIEW_EDIT)
    }
    
    private fun onOptimizeTemplate(currentContent: String) {
        val template = currentTemplate ?: return
        
        templateEditPanel.setOptimizing(true)
        
        // Run in background
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Use the same request building logic as PromptTemplateEditPanel
                val request = PromptOptimizationRequest(
                    userPrompt = currentContent,  // Use current content as user intent
                    category = template.category.takeIf { it.isNotBlank() },
                    currentName = template.name,
                    currentDescription = template.description,
                    currentContent = currentContent,
                    languageCode = cn.suso.aicodetransformer.i18n.LanguageManager.getLanguageCode(),
                    availableVariables = cn.suso.aicodetransformer.constants.TemplateConstants.getBuiltInVariablesMap().map { (placeholder, description) ->
                        cn.suso.aicodetransformer.model.PromptVariableInfo(placeholder, description)
                    }
                )
                
                val result = optimizationService.optimizePrompt(request)
                
                // Update UI on EDT with write action
                ApplicationManager.getApplication().invokeLater {
                    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                        templateEditPanel.setContent(result.content)
                    }
                    templateEditPanel.setOptimizing(false)
                    Messages.showInfoMessage(project, I18n.t("toolwindow.debugger.optimize.success"), I18n.t("toolwindow.debugger.edit.button.optimize"))
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    templateEditPanel.setOptimizing(false)
                    Messages.showErrorDialog(project, I18n.t("toolwindow.debugger.optimize.error", e.message ?: "Unknown error"), "Error")
                }
            }
        }
    }
    
    private fun onApplyTemplate(content: String) {
        val editor = getActiveEditor() ?: return
        val selectedText = editor.selectionModel.selectedText
        
        if (selectedText.isNullOrBlank()) {
            Messages.showWarningDialog(project, I18n.t("toolwindow.debugger.apply.noSelection"), I18n.t("toolwindow.debugger.apply.noSelection.title"))
            return
        }
        
        val template = currentTemplate ?: return
        
        // Create temporary template with edited content
        val tempTemplate = template.copy(content = content)
        
        // Execute with temporary template asynchronously
        executionService.executeTemplateAsync(
            template = tempTemplate,
            selectedText = selectedText,
            project = project,
            editor = editor,
            callback = { result ->
                // Optional: Handle completion
            }
        )
    }
    
    

    
    private fun onSaveTemplate(content: String) {
        val template = currentTemplate ?: return
        
        // Update template content
        val updatedTemplate = template.copy(content = content)
        templateService.saveTemplate(updatedTemplate)
        
        // Exit edit mode
        exitEditMode()
        
        // Refresh list
        refreshTemplateList()
    }
    
    private fun exitEditMode() {
        isEditMode = false
        currentTemplate = null
        originalContent = ""
        cardLayout.show(cardPanel, VIEW_LIST)
    }
    
    private fun hasSelectedCode(): Boolean {
        val editor = getActiveEditor() ?: return false
        val selectedText = editor.selectionModel.selectedText
        return !selectedText.isNullOrBlank()
    }
    
    private fun getActiveEditor(): Editor? {
        return FileEditorManager.getInstance(project).selectedTextEditor
    }
    
    companion object {
        private const val VIEW_LIST = "list"
        private const val VIEW_EDIT = "edit"
    }
}
