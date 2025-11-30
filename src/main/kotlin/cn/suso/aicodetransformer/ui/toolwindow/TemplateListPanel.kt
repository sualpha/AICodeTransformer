package cn.suso.aicodetransformer.ui.toolwindow

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.i18n.I18n
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import java.awt.Font
import javax.swing.*

/**
 * Panel displaying list of templates with Run and Edit buttons
 */
class TemplateListPanel(
    private val onTemplateSelected: (PromptTemplate) -> Unit,
    private val onRunClicked: (PromptTemplate) -> Unit,
    private val onEditClicked: (PromptTemplate) -> Unit,
    private val hasSelectedCode: () -> Boolean,
    private val onModelSelected: (ModelConfiguration) -> Unit
) : JBPanel<TemplateListPanel>(BorderLayout()) {
    
    private val searchField = SearchTextField()
    private val modelComboBox = ComboBox<ModelConfiguration>()
    private val templateListPanel = JBPanel<JBPanel<*>>(VerticalFlowLayout(0, 0))
    
    private var allTemplates: List<PromptTemplate> = emptyList()
    private var selectedTemplate: PromptTemplate? = null
    private var allModels: List<ModelConfiguration> = emptyList()
    private val runButtons = mutableListOf<JButton>()
    private val editButtons = mutableListOf<JButton>()
    
    // UI elements that need language updates
    private val titleLabel = JBLabel().apply {
        font = font.deriveFont(Font.BOLD, 14f)
    }
    private val tipLabel = JBLabel().apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size - 1f)
    }
    
    private val languageChangeListener: () -> Unit = {
        // Update UI labels
        titleLabel.text = I18n.t("toolwindow.debugger.templates.title")
        tipLabel.text = I18n.t("toolwindow.debugger.tip.selectCode")
        // Reload templates to recreate buttons with new language
        filterTemplates()
    }
    
    init {
        border = JBUI.Borders.empty(8)
        background = JBColor.PanelBackground
        
        // Header with model selector and search
        val headerPanel = JBPanel<JBPanel<*>>(VerticalFlowLayout(0, 8)).apply {
            border = JBUI.Borders.emptyBottom(8)
            
            // Top row: Title and Model Selector
            val topRow = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                background = JBColor.PanelBackground
                
                // Title
                titleLabel.text = I18n.t("toolwindow.debugger.templates.title")
                add(titleLabel, BorderLayout.WEST)
                
                // Model Selector
                modelComboBox.apply {
                    preferredSize = Dimension(200, 30)
                    renderer = SimpleListCellRenderer.create { label, value, _ ->
                        label.text = value?.name ?: I18n.t("toolwindow.debugger.model.label")
                        label.icon = AllIcons.General.User
                    }
                    addActionListener {
                        if (isUpdatingModelList) return@addActionListener
                        (selectedItem as? ModelConfiguration)?.let { model ->
                            onModelSelected(model)
                        }
                    }
                }
                add(modelComboBox, BorderLayout.EAST)
            }
            
            // Search field
            searchField.textEditor.border = JBUI.Borders.empty(2, 5)
            
            add(topRow)
            add(searchField)
        }
        
        // Template list
        templateListPanel.background = JBColor.PanelBackground
        val scrollPane = JScrollPane(templateListPanel).apply {
            border = null
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        
        // Footer tip
        tipLabel.text = I18n.t("toolwindow.debugger.tip.selectCode")
        
        add(headerPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(tipLabel, BorderLayout.SOUTH)
        
        // Search listener
        searchField.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = filterTemplates()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = filterTemplates()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = filterTemplates()
        })
        
        // Add language change listener
        cn.suso.aicodetransformer.i18n.LanguageManager.addChangeListener(languageChangeListener)
    }
    
    fun dispose() {
        cn.suso.aicodetransformer.i18n.LanguageManager.removeChangeListener(languageChangeListener)
    }
    
    fun setTemplates(templates: List<PromptTemplate>) {
        allTemplates = templates
        filterTemplates()
    }
    
    private var isUpdatingModelList = false

    fun setModels(models: List<ModelConfiguration>) {
        try {
            isUpdatingModelList = true
            allModels = models
            modelComboBox.removeAllItems()
            models.forEach { modelComboBox.addItem(it) }
            if (models.isNotEmpty()) {
                modelComboBox.selectedIndex = 0
            }
        } finally {
            isUpdatingModelList = false
        }
    }
    
    fun setSelectedModel(model: ModelConfiguration) {
        modelComboBox.selectedItem = model
    }
    
    private fun filterTemplates() {
        val query = searchField.text.lowercase()
        val filtered = if (query.isBlank()) {
            allTemplates
        } else {
            allTemplates.filter {
                it.name.lowercase().contains(query) ||
                it.description?.lowercase()?.contains(query) == true ||
                it.category.lowercase().contains(query)
            }
        }
        
        renderTemplateList(filtered)
    }
    
    private fun renderTemplateList(templates: List<PromptTemplate>) {
        templateListPanel.removeAll()
        runButtons.clear()
        editButtons.clear()
        
        templates.forEach { template ->
            val itemPanel = createTemplateItem(template)
            templateListPanel.add(itemPanel)
        }
        
        templateListPanel.revalidate()
        templateListPanel.repaint()
    }
    
    private fun createTemplateItem(template: PromptTemplate): JPanel {
        val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 8)
            background = JBColor.PanelBackground
            maximumSize = Dimension(Int.MAX_VALUE, 40)
        }
        
        // Template name
        val nameLabel = JBLabel(template.name).apply {
            font = font.deriveFont(Font.BOLD)
            border = JBUI.Borders.emptyLeft(4)
        }
        
        // Action buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            background = JBColor.PanelBackground
        }
        
        val editButton = JButton(I18n.t("toolwindow.debugger.button.edit")).apply {
            preferredSize = Dimension(60, 25)
            isEnabled = hasSelectedCode()
            addActionListener {
                if (hasSelectedCode()) {
                    onEditClicked(template)
                }
            }
            editButtons.add(this)
        }
        
        val runButton = JButton("▶ " + I18n.t("toolwindow.debugger.button.run")).apply {
            preferredSize = Dimension(70, 25)
            isEnabled = hasSelectedCode()
            addActionListener {
                if (hasSelectedCode()) {
                    onRunClicked(template)
                }
            }
            runButtons.add(this)
        }
        
        buttonPanel.add(editButton)
        buttonPanel.add(runButton)
        
        panel.add(nameLabel, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.EAST)
        
        return panel
    }
    
    fun updateRunButtonStates() {
        val hasSelection = hasSelectedCode()
        runButtons.forEach { it.isEnabled = hasSelection }
        editButtons.forEach { it.isEnabled = hasSelection }
    }
    
    private fun updateButtonStates() {
        // Refresh all buttons to update enabled state
        // We don't want to re-render everything, just update states if needed
        // But for now, let's just keep it simple or leave it empty if it was just re-rendering
    }
}
