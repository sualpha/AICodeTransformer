package cn.suso.aicodetransformer.ui.toolwindow

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.i18n.I18n
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import com.intellij.openapi.project.Project

/**
 * Panel for editing template content with AI optimization
 */
class TemplateEditPanel(
    private val project: Project,
    private val onOptimize: (String) -> Unit,
    private val onApply: (String) -> Unit,
    private val onSave: (String) -> Unit,
    private val onClose: () -> Unit
) : JBPanel<TemplateEditPanel>(BorderLayout()) {
    
    private var editor: EditorEx? = null
    private var currentTemplate: PromptTemplate? = null
    
    // Buttons
    private var optimizeButton: JButton? = null
    private var applyButton: JButton? = null
    private var saveButton: JButton? = null
    
    private val headerLabel = JBLabel()
    private val editorContainer = JBPanel<JBPanel<*>>(BorderLayout())
    private val tipLabel = JBLabel(I18n.t("toolwindow.debugger.edit.tip")).apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size - 1f)
    }
    
    
    private val languageChangeListener: () -> Unit = {
        // Update button texts
        optimizeButton?.text = I18n.t("toolwindow.debugger.edit.button.optimize")
        applyButton?.text = I18n.t("toolwindow.debugger.edit.button.apply")
        saveButton?.text = I18n.t("toolwindow.debugger.edit.button.save")
        
        // Update tip label
        tipLabel.text = I18n.t("toolwindow.debugger.edit.tip")
        
        // Update header if template is loaded
        currentTemplate?.let { template ->
            headerLabel.text = I18n.t("toolwindow.debugger.edit.editing", template.name)
        }
    }
    
    init {
        border = JBUI.Borders.empty(8)
        background = JBColor.PanelBackground
        
        // Header
        val headerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(8)
            add(headerLabel, BorderLayout.CENTER)
            add(createCloseButton(), BorderLayout.EAST)
        }
        
        // Button panel
        val buttonPanel = createButtonPanel()
        
        // Footer panel with buttons and tip
        val footerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            add(buttonPanel, BorderLayout.NORTH)
            add(tipLabel, BorderLayout.SOUTH)
        }
        
        add(headerPanel, BorderLayout.NORTH)
        add(editorContainer, BorderLayout.CENTER)
        add(footerPanel, BorderLayout.SOUTH)
        
        // Register language change listener
        cn.suso.aicodetransformer.i18n.LanguageManager.addChangeListener(languageChangeListener)
    }
    
    fun loadTemplate(template: PromptTemplate) {
        currentTemplate = template
        headerLabel.text = I18n.t("toolwindow.debugger.edit.editing", template.name)
        
        // Dispose old editor if exists
        editor?.let { EditorFactory.getInstance().releaseEditor(it) }
        
        // Create new editor
        val document = EditorFactory.getInstance().createDocument(template.content)
        val newEditor = EditorFactory.getInstance().createEditor(document, project, PlainTextFileType.INSTANCE, false) as EditorEx
        
        // Ensure editable
        newEditor.isViewer = false
        newEditor.document.setReadOnly(false)
        
        newEditor.settings.apply {
            isLineNumbersShown = true
            isWhitespacesShown = false
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            additionalColumnsCount = 0
            additionalLinesCount = 0
        }
        
        editor = newEditor
        
        // Add editor to center
        editorContainer.removeAll()
        editorContainer.add(newEditor.component, BorderLayout.CENTER)
        
        revalidate()
        repaint()
    }
    
    /**
     * Update template info (title) without reloading the editor content
     * Used when template metadata changes (e.g., language switch) but content stays the same
     */
    fun updateTemplateInfo(template: PromptTemplate) {
        currentTemplate = template
        headerLabel.text = I18n.t("toolwindow.debugger.edit.editing", template.name)
    }
    
    fun setContent(content: String) {
        editor?.document?.setText(content)
    }
    
    fun getContent(): String {
        return editor?.document?.text ?: ""
    }
    
    private fun createCloseButton(): JButton {
        return JButton(AllIcons.Actions.Close).apply {
            preferredSize = Dimension(20, 20)
            isContentAreaFilled = false
            isBorderPainted = false
            addActionListener { onClose() }
        }
    }
    
    private fun createButtonPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            background = JBColor.PanelBackground
        }
        
        optimizeButton = JButton(I18n.t("toolwindow.debugger.edit.button.optimize")).apply {
            addActionListener {
                onOptimize(getContent())
            }
        }
        
        applyButton = JButton(I18n.t("toolwindow.debugger.edit.button.apply")).apply {
            addActionListener {
                onApply(getContent())
            }
        }
        
        saveButton = JButton(I18n.t("toolwindow.debugger.edit.button.save")).apply {
            addActionListener {
                onSave(getContent())
            }
        }
        
        panel.add(optimizeButton)
        panel.add(applyButton)
        panel.add(saveButton)
        
        return panel
    }
    
    fun setApplyButtonEnabled(enabled: Boolean) {
        applyButton?.isEnabled = enabled
    }
    
    fun setOptimizing(isOptimizing: Boolean) {
        optimizeButton?.isEnabled = !isOptimizing
        optimizeButton?.text = if (isOptimizing) I18n.t("toolwindow.debugger.edit.button.optimizing") else I18n.t("toolwindow.debugger.edit.button.optimize")
        editor?.isViewer = isOptimizing
    }
    
    fun dispose() {
        cn.suso.aicodetransformer.i18n.LanguageManager.removeChangeListener(languageChangeListener)
        editor?.let { EditorFactory.getInstance().releaseEditor(it) }
    }
}
