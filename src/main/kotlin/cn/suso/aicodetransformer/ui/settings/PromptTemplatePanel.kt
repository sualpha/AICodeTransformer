package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.TemplateChangeListener
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import cn.suso.aicodetransformer.ui.components.TooltipHelper
import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.i18n.LanguageManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.SwingUtilities
import javax.swing.event.DocumentListener

/**
 * Prompt模板管理面板
 */
class PromptTemplatePanel(
    private val project: Project,
    private val configurationService: ConfigurationService
) : JPanel(), TemplateChangeListener {
    
    private val promptTemplateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
    private val json = Json { ignoreUnknownKeys = true }
    
    // 主要UI组件
    private val tabbedPane = JBTabbedPane()
    private val shortcutBindingPanel = ShortcutKeyBindingPanel()
    
    // UI 组件
    private val searchField = JBTextField()
    private lateinit var templateManagementPanel: JPanel
    private lateinit var titleLabel: JBLabel
    private lateinit var descLabel: JBLabel
    private lateinit var searchLabel: JBLabel
    private lateinit var categoryLabel: JBLabel
    private lateinit var leftTitleLabel: JBLabel
    private lateinit var rightTitleLabel: JBLabel
    private val categoryComboBox = JComboBox<String>()
    private val enabledOnlyCheckBox = JBCheckBox(I18n.t("prompt.enabledOnly"))
    
    private val templateListModel = DefaultListModel<PromptTemplate>()
    private val templateList = JBList(templateListModel)
    private val templateScrollPane = JBScrollPane(templateList)
    
    private val detailPanel = PromptTemplateDetailPanel()
    
    private val statusLabel = JLabel(I18n.t("status.ready"))
    private lateinit var importButton: JButton
    private lateinit var exportButton: JButton
    private lateinit var resetButton: JButton
    
    private var templates = listOf<PromptTemplate>()
    private var filteredTemplates = listOf<PromptTemplate>()
    private var isModified = false
    private val modificationListeners = mutableListOf<() -> Unit>()
    
    init {
        layout = BorderLayout()
        border = EmptyBorder(JBUI.insets(12))
        templateList.model = templateListModel
        setupUI()
        loadTemplates()
        setupListeners()
        setupLanguageListener()
        promptTemplateService.addTemplateChangeListener(this)
    }
    
    private fun setupUI() {
        // 添加标题
        val headerPanel = JBPanel<JBPanel<*>>(BorderLayout())
        headerPanel.border = EmptyBorder(JBUI.insets(0, 0, 16, 0))
        
        titleLabel = JBLabel(I18n.t("prompt.management.title"))
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        titleLabel.foreground = UIUtil.getLabelForeground()

        descLabel = JBLabel(I18n.t("prompt.management.desc"))
        descLabel.font = descLabel.font.deriveFont(12f)
        descLabel.foreground = UIUtil.getLabelDisabledForeground()
        
        val titlePanel = JBPanel<JBPanel<*>>(BorderLayout())
        titlePanel.add(titleLabel, BorderLayout.NORTH)
        titlePanel.add(descLabel, BorderLayout.CENTER)
        
        headerPanel.add(titlePanel, BorderLayout.WEST)
        add(headerPanel, BorderLayout.NORTH)
        
        // 初始化模板管理面板
        templateManagementPanel = createTemplateManagementPanel()
        
        // 添加标签页
        tabbedPane.border = JBUI.Borders.empty()
        tabbedPane.addTab(I18n.t("prompt.tab.templates"), templateManagementPanel)
        tabbedPane.addTab(I18n.t("prompt.tab.shortcuts"), shortcutBindingPanel)
        
        add(tabbedPane, BorderLayout.CENTER)
    }
    
    private fun createTemplateManagementPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 顶部搜索面板
        val searchPanel = createSearchPanel()
        panel.add(searchPanel, BorderLayout.NORTH)
        
        // 中间分割面板
        val splitPane = createSplitPane()
        panel.add(splitPane, BorderLayout.CENTER)
        
        // 底部状态栏
        val statusPanel = createStatusPanel()
        panel.add(statusPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createSearchPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = EmptyBorder(JBUI.insets(0, 0, 12, 0))
        
        // 搜索区域
        val searchPanel = JBPanel<JBPanel<*>>()
        searchPanel.layout = BoxLayout(searchPanel, BoxLayout.X_AXIS)
        
        searchLabel = JBLabel(I18n.t("prompt.search.label"))
        searchLabel.font = searchLabel.font.deriveFont(Font.BOLD)
        searchPanel.add(searchLabel)
        
        searchField.preferredSize = Dimension(200, searchField.preferredSize.height)
        searchField.toolTipText = I18n.t("prompt.search.tooltip")
        searchField.putClientProperty("JTextField.placeholderText", I18n.t("prompt.search.placeholder"))
        searchPanel.add(searchField)
        
        searchPanel.add(Box.createHorizontalStrut(16))
        
        categoryLabel = JBLabel(I18n.t("prompt.category.label"))
        categoryLabel.font = categoryLabel.font.deriveFont(Font.BOLD)
        searchPanel.add(categoryLabel)
        searchPanel.add(categoryComboBox)
        
        searchPanel.add(Box.createHorizontalStrut(16))
        searchPanel.add(enabledOnlyCheckBox)
        
        searchPanel.add(Box.createHorizontalGlue())
        
        panel.add(searchPanel, BorderLayout.CENTER)
        return panel
    }
    
    private fun createSplitPane(): JSplitPane {
        // 左侧面板标题和列表
        leftTitleLabel = JBLabel(I18n.t("prompt.list.title"))
        leftTitleLabel.font = leftTitleLabel.font.deriveFont(Font.BOLD, 14f)
        leftTitleLabel.border = EmptyBorder(JBUI.insets(0, 0, 8, 0))
        
        val leftPanel = JBPanel<JBPanel<*>>(BorderLayout())
        leftPanel.add(leftTitleLabel, BorderLayout.NORTH)
        leftPanel.add(createTemplateListPanel(), BorderLayout.CENTER)
        leftPanel.border = EmptyBorder(JBUI.insets(0, 0, 0, 8))
        leftPanel.preferredSize = Dimension(350, -1)
        
        // 右侧面板标题和详情
        rightTitleLabel = JBLabel(I18n.t("prompt.detail.title"))
        rightTitleLabel.font = rightTitleLabel.font.deriveFont(Font.BOLD, 14f)
        rightTitleLabel.border = EmptyBorder(JBUI.insets(0, 0, 8, 0))
        
        val rightPanel = JBPanel<JBPanel<*>>(BorderLayout())
        rightPanel.add(rightTitleLabel, BorderLayout.NORTH)
        rightPanel.add(detailPanel, BorderLayout.CENTER)
        rightPanel.border = EmptyBorder(JBUI.insets(0, 8, 0, 0))
        
        detailPanel.addModificationListener { notifyModification() }
        
        // 分割面板
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel)
        splitPane.dividerLocation = 350
        splitPane.resizeWeight = 0.4
        splitPane.border = JBUI.Borders.empty()
        splitPane.dividerSize = 8
        splitPane.setOneTouchExpandable(true)
        
        return splitPane
    }

    private fun setupLanguageListener() {
        val refreshTexts: () -> Unit = {
            val selectedId = templateList.selectedValue?.id
            titleLabel.text = I18n.t("prompt.management.title")
            descLabel.text = I18n.t("prompt.management.desc")
            tabbedPane.setTitleAt(0, I18n.t("prompt.tab.templates"))
            tabbedPane.setTitleAt(1, I18n.t("prompt.tab.shortcuts"))
            searchLabel.text = I18n.t("prompt.search.label")
            categoryLabel.text = I18n.t("prompt.category.label")
            enabledOnlyCheckBox.text = I18n.t("prompt.enabledOnly")
            leftTitleLabel.text = I18n.t("prompt.list.title")
            rightTitleLabel.text = I18n.t("prompt.detail.title")
            searchField.toolTipText = I18n.t("prompt.search.tooltip")
            searchField.putClientProperty("JTextField.placeholderText", I18n.t("prompt.search.placeholder"))
            importButton.text = I18n.t("prompt.import")
            importButton.toolTipText = I18n.t("prompt.import.tooltip")
            exportButton.text = I18n.t("prompt.export")
            exportButton.toolTipText = I18n.t("prompt.export.tooltip")
            resetButton.text = I18n.t("prompt.reset")
            resetButton.toolTipText = I18n.t("prompt.reset.tooltip")
            detailPanel.refreshTexts()
            loadTemplates()
            selectedId?.let { selectTemplate(it) }
        }
        LanguageManager.addChangeListener(refreshTexts)
    }
    
    private fun createTemplateListPanel(): JPanel {
        // 模板列表
        templateList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        templateList.cellRenderer = TemplateListCellRenderer()
        templateList.border = JBUI.Borders.empty()
        
        // 工具栏
        val decorator = ToolbarDecorator.createDecorator(templateList)
            .setAddAction { addTemplate() }
            .setRemoveAction { removeTemplate() }
            .setEditAction { editTemplate() }
            .setMoveUpAction { moveTemplateUp() }
            .setMoveDownAction { moveTemplateDown() }
            .createPanel()
        
        // 为工具栏按钮添加提示信息
        SwingUtilities.invokeLater {
            setToolbarTooltips(decorator)
        }
        
        decorator.border = JBUI.Borders.empty()
        return decorator
    }
    
    private fun setToolbarTooltips(decorator: JPanel) {
        // 递归查找工具栏按钮并设置提示
        fun setTooltipsRecursively(component: java.awt.Component) {
            if (component is javax.swing.AbstractButton) {
                val tooltip = when {
                    component.toString().contains("Add") -> TooltipHelper.TemplateActionTooltips.TEMPLATE_ADD
                    component.toString().contains("Remove") -> TooltipHelper.TemplateActionTooltips.TEMPLATE_REMOVE
                    component.toString().contains("Edit") -> TooltipHelper.TemplateActionTooltips.TEMPLATE_EDIT
                    component.toString().contains("Up") -> TooltipHelper.TemplateActionTooltips.TEMPLATE_MOVE_UP
                    component.toString().contains("Down") -> TooltipHelper.TemplateActionTooltips.TEMPLATE_MOVE_DOWN
                    else -> null
                }
                tooltip?.let { component.toolTipText = it }
            }
            if (component is java.awt.Container) {
                component.components.forEach { setTooltipsRecursively(it) }
            }
        }
        setTooltipsRecursively(decorator)
    }
    
    private fun createStatusPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = EmptyBorder(JBUI.insets(12, 0, 0, 0))
        
        statusLabel.foreground = UIUtil.getLabelDisabledForeground()
        
        val buttonPanel = JBPanel<JBPanel<*>>()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        
        importButton = JButton(I18n.t("prompt.import"))
        importButton.toolTipText = I18n.t("prompt.import.tooltip")
        importButton.addActionListener { importTemplates() }
        buttonPanel.add(importButton)
        
        buttonPanel.add(Box.createHorizontalStrut(8))
        
        exportButton = JButton(I18n.t("prompt.export"))
        exportButton.toolTipText = I18n.t("prompt.export.tooltip")
        exportButton.addActionListener { exportTemplates() }
        buttonPanel.add(exportButton)
        
        buttonPanel.add(Box.createHorizontalStrut(8))
        
        resetButton = JButton(I18n.t("prompt.reset"))
        resetButton.toolTipText = I18n.t("prompt.reset.tooltip")
        resetButton.addActionListener { resetToDefaults() }
        buttonPanel.add(resetButton)
        
        // 将操作按钮放置在最左侧，便于用户操作
        panel.add(buttonPanel, BorderLayout.WEST)
        // 状态文本居中显示
        panel.add(statusLabel, BorderLayout.CENTER)
        return panel
    }
    
    private fun setupListeners() {
        // 搜索框监听器
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterTemplates()
            override fun removeUpdate(e: DocumentEvent?) = filterTemplates()
            override fun changedUpdate(e: DocumentEvent?) = filterTemplates()
        })
        
        // 分类过滤监听器
        categoryComboBox.addActionListener { filterTemplates() }
        enabledOnlyCheckBox.addActionListener { filterTemplates() }
        
        // 模板列表选择监听器
        templateList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedTemplate = templateList.selectedValue
                detailPanel.setTemplate(selectedTemplate)
                updateStatus(
                    if (selectedTemplate != null) I18n.t("prompt.status.selected", selectedTemplate.name)
                    else I18n.t("status.ready")
                )
            }
        }
    }
    
    private fun loadTemplates() {
        try {
            templates = promptTemplateService.getTemplates()
            refreshCategoryComboBox()
            filterTemplates()
            updateStatus(I18n.t("prompt.status.loaded", templates.size))
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "加载模板失败：${e.message}",
                "加载错误"
            )
            updateStatus(I18n.t("prompt.status.load.fail", e.message ?: ""))
        }
    }

    private fun refreshCategoryComboBox() {
        val previousSelection = categoryComboBox.selectedItem as? String
        val allLabel = I18n.t("prompt.category.all")
        val categories = templates.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

        val modelValues = mutableListOf(allLabel)
        modelValues.addAll(categories)

        categoryComboBox.model = DefaultComboBoxModel(modelValues.toTypedArray())

        when {
            previousSelection != null && modelValues.contains(previousSelection) ->
                categoryComboBox.selectedItem = previousSelection
            else -> categoryComboBox.selectedIndex = 0
        }
    }

    private fun filterTemplates() {
        val searchText = searchField.text.trim().lowercase()
        val selectedCategory = categoryComboBox.selectedItem as? String
        val enabledOnly = enabledOnlyCheckBox.isSelected
        val allLabel = I18n.t("prompt.category.all")

        filteredTemplates = templates.filter { template ->
            val matchesSearch = searchText.isEmpty() ||
                template.name.lowercase().contains(searchText) ||
                template.description?.lowercase()?.contains(searchText) == true ||
                template.content.lowercase().contains(searchText)

            val matchesCategory = selectedCategory == null || selectedCategory == allLabel || template.category == selectedCategory
            val matchesEnabled = !enabledOnly || template.enabled

            matchesSearch && matchesCategory && matchesEnabled
        }

        templateListModel.clear()
        filteredTemplates.forEach { templateListModel.addElement(it) }

        updateStatus(I18n.t("prompt.status.filtered", filteredTemplates.size, templates.size))
    }

    private fun selectTemplate(templateId: String) {
        for (i in 0 until templateListModel.size) {
            if (templateListModel.getElementAt(i).id == templateId) {
                templateList.selectedIndex = i
                templateList.ensureIndexIsVisible(i)
                break
            }
        }
    }
    
    private fun updateStatus(message: String) {
        statusLabel.text = message
    }
    
    private fun addTemplate() {
        try {
            val selectedCategory = categoryComboBox.selectedItem as? String
            val allLabel = I18n.t("prompt.category.all")
            val category = if (selectedCategory == allLabel) null else selectedCategory
            val dialog = PromptTemplateEditDialog.showCreateDialog(project, category)
            if (dialog != null) {
                promptTemplateService.saveTemplate(dialog)
                loadTemplates()
                selectTemplate(dialog.id)
                notifyModification()
                updateStatus(I18n.t("prompt.status.add.success"))
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "添加模板时发生错误：${e.message}",
                "添加模板失败"
            )
            updateStatus(I18n.t("prompt.status.add.fail"))
        }
    }
    
    private fun editTemplate() {
        try {
            val selectedTemplate = templateList.selectedValue ?: return
            // Fetch the latest version of the template from service to ensure it has current language content
            val latestTemplate = promptTemplateService.getTemplate(selectedTemplate.id) ?: selectedTemplate
            val dialog = PromptTemplateEditDialog.showEditDialog(project, latestTemplate)
            if (dialog != null) {
                promptTemplateService.saveTemplate(dialog)
                loadTemplates()
                selectTemplate(dialog.id)
                notifyModification()
                updateStatus(I18n.t("prompt.status.edit.success"))
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "编辑模板时发生错误：${e.message}",
                "编辑模板失败"
            )
            updateStatus(I18n.t("prompt.status.edit.fail"))
        }
    }
    
    private fun removeTemplate() {
        try {
            val selectedTemplate = templateList.selectedValue ?: return

            val result = Messages.showYesNoDialog(
                this,
                I18n.t("prompt.delete.confirm.message", selectedTemplate.name),
                I18n.t("prompt.delete.confirm.title"),
                Messages.getQuestionIcon()
            )
            
            if (result == Messages.YES) {
                promptTemplateService.deleteTemplate(selectedTemplate.id)
                loadTemplates()
                notifyModification()
                updateStatus(I18n.t("prompt.status.delete.success"))
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                I18n.t("prompt.delete.error.message", e.message ?: ""),
                I18n.t("prompt.delete.error.title")
            )
            updateStatus(I18n.t("prompt.status.delete.fail"))
        }
    }
    
    private fun moveTemplateUp() {
        val selectedIndex = templateList.selectedIndex
        if (selectedIndex > 0) {
            val template = templateListModel.getElementAt(selectedIndex)
            templateListModel.removeElementAt(selectedIndex)
            templateListModel.insertElementAt(template, selectedIndex - 1)
            templateList.selectedIndex = selectedIndex - 1
            notifyModification()
        }
    }
    
    private fun moveTemplateDown() {
        val selectedIndex = templateList.selectedIndex
        if (selectedIndex >= 0 && selectedIndex < templateListModel.size() - 1) {
            val template = templateListModel.getElementAt(selectedIndex)
            templateListModel.removeElementAt(selectedIndex)
            templateListModel.insertElementAt(template, selectedIndex + 1)
            templateList.selectedIndex = selectedIndex + 1
            notifyModification()
        }
    }
    
    private fun importTemplates() {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON文件", "json")
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                val file = fileChooser.selectedFile
                // 读取文件内容并预解析，只有在存在相同ID时才弹出覆盖提示
                val content = java.io.File(file.absolutePath).readText()
                val root: JsonElement = json.parseToJsonElement(content)
                val importCandidates: List<PromptTemplate> = when (root) {
                    is JsonObject -> {
                        val templatesElem = root["templates"]
                        if (templatesElem != null) {
                            when (templatesElem) {
                                is JsonArray -> templatesElem.map { json.decodeFromJsonElement<PromptTemplate>(it) }
                                is JsonObject -> listOf(json.decodeFromJsonElement<PromptTemplate>(templatesElem))
                                else -> emptyList()
                            }
                        } else {
                            listOf(json.decodeFromJsonElement<PromptTemplate>(root))
                        }
                    }
                    is JsonArray -> root.map { json.decodeFromJsonElement<PromptTemplate>(it) }
                    else -> emptyList()
                }

                val existingIds = promptTemplateService.getTemplates().map { it.id }.toSet()
                val hasConflict = importCandidates.any { it.id in existingIds }
                val overwrite = if (hasConflict) {
                    Messages.showYesNoDialog(
                        this,
                        I18n.t("prompt.import.overwrite.question"),
                        I18n.t("prompt.import.overwrite.title"),
                        null
                    ) == Messages.YES
                } else {
                    false
                }

                // 进行导入
                val importedCount = promptTemplateService.importTemplates(content, overwrite)

                val suffix = if (overwrite) I18n.t("prompt.import.overwrite.suffix") else ""
                Messages.showInfoMessage(
                    this,
                    I18n.t("prompt.import.success.message", importedCount, suffix),
                    I18n.t("prompt.import.success.title")
                )
                
                loadTemplates()
                notifyModification()
                updateStatus(I18n.t("prompt.import.success.status", importedCount, suffix))
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    this,
                    I18n.t("prompt.import.failure.message", e.message ?: ""),
                    I18n.t("prompt.import.failure.title")
                )
                updateStatus(I18n.t("prompt.import.failure.status"))
            }
        }
    }
    
    private fun exportTemplates() {
        if (templateList.selectedIndices.isNotEmpty()) {
            templateList.selectedValuesList.map { it.id }
        } else {
            emptyList()
        }
        
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON文件", "json")
        fileChooser.selectedFile = java.io.File("prompt_templates.json")
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                val file = fileChooser.selectedFile
                promptTemplateService.exportTemplatesToFile(file.absolutePath, emptyList())

                Messages.showInfoMessage(
                    this,
                    I18n.t("prompt.export.success.message", file.absolutePath),
                    I18n.t("prompt.export.success.title")
                )
                updateStatus(I18n.t("prompt.export.success.status"))
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    this,
                    I18n.t("prompt.export.failure.message", e.message ?: ""),
                    I18n.t("prompt.export.failure.title")
                )
                updateStatus(I18n.t("prompt.export.failure.status"))
            }
        }
    }
    
    private fun resetToDefaults() {
        val result = Messages.showYesNoDialog(
            this,
            "确定要重置为默认模板吗？这将删除所有自定义模板。",
            "确认重置",
            Messages.getWarningIcon()
        )
        
        if (result == Messages.YES) {
            promptTemplateService.resetToDefaults(false)
            loadTemplates()
            notifyModification()
        }
    }
    
    fun isModified(): Boolean = isModified || detailPanel.isModified()
    
    fun apply() {
        detailPanel.apply()
        isModified = false
    }
    
    fun reset() {
        loadTemplates()
        detailPanel.reset()
        isModified = false
    }
    
    fun addModificationListener(listener: () -> Unit) {
        modificationListeners.add(listener)
    }
    
    private fun notifyModification() {
        isModified = true
        modificationListeners.forEach { it() }
    }
    
    // TemplateChangeListener 实现
    override fun onTemplateAdded(template: PromptTemplate) {
        SwingUtilities.invokeLater {
            loadTemplates()
        }
    }
    
    override fun onTemplateUpdated(oldTemplate: PromptTemplate, newTemplate: PromptTemplate) {
        SwingUtilities.invokeLater {
            val currentlyDisplayedId = templateList.selectedValue?.id
            loadTemplates()
            // If the updated template is currently being displayed, refresh the detail panel
            if (currentlyDisplayedId == newTemplate.id) {
                detailPanel.setTemplate(newTemplate)
            }
        }
    }
    
    override fun onTemplateDeleted(template: PromptTemplate) {
        SwingUtilities.invokeLater {
            loadTemplates()
            detailPanel.setTemplate(null)
        }
    }
    
    /**
     * 模板列表单元格渲染器
     */
    private class TemplateListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            if (value is PromptTemplate) {
                text = buildString {
                    append(value.name)
                    if (!value.enabled) {
                        append(" ")
                        append(I18n.t("prompt.template.disabled.suffix"))
                    }
                    if (value.isBuiltIn) {
                        append(" ")
                        append(I18n.t("prompt.template.builtin.suffix"))
                    }
                    if (!value.shortcutKey.isNullOrBlank()) {
                        append(" (${value.shortcutKey})")
                    }
                }
                
                toolTipText = buildString {
                    append("<html>")
                    append("<b>${value.name}</b><br>")
                    if (!value.description.isNullOrBlank()) {
                        append("${value.description}<br>")
                    }
                    if (!value.category.isNullOrBlank()) {
                        append("${I18n.t("prompt.template.tooltip.category")}: ${value.category}<br>")
                    }
                    append("</html>")
                }
            }
            
            return this
        }
    }
}