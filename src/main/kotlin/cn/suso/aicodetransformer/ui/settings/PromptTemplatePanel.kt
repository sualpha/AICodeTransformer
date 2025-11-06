package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.TemplateChangeListener
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import cn.suso.aicodetransformer.ui.components.TooltipHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
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
import javax.swing.event.DocumentListener
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

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
    private val categoryComboBox = JComboBox<String>()
    private val enabledOnlyCheckBox = JBCheckBox("仅显示启用的模板")
    
    private val templateListModel = DefaultListModel<PromptTemplate>()
    private val templateList = JBList(templateListModel)
    private val templateScrollPane = JBScrollPane(templateList)
    
    private val detailPanel = PromptTemplateDetailPanel()
    
    private val statusLabel = JLabel("就绪")
    
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
        promptTemplateService.addTemplateChangeListener(this)
    }
    
    private fun setupUI() {
        // 添加标题
        val headerPanel = JBPanel<JBPanel<*>>(BorderLayout())
        headerPanel.border = EmptyBorder(JBUI.insets(0, 0, 16, 0))
        
        val titleLabel = JBLabel("Prompt 模板管理")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        titleLabel.foreground = UIUtil.getLabelForeground()
        
        val descLabel = JBLabel("管理AI代码转换的Prompt模板和快捷键绑定")
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
        tabbedPane.addTab("模板管理", templateManagementPanel)
        tabbedPane.addTab("快捷键绑定", shortcutBindingPanel)
        
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
        
        val searchLabel = JBLabel("搜索: ")
        searchLabel.font = searchLabel.font.deriveFont(Font.BOLD)
        searchPanel.add(searchLabel)
        
        searchField.preferredSize = Dimension(200, searchField.preferredSize.height)
        searchField.toolTipText = "搜索模板名称、内容或标签"
        searchField.putClientProperty("JTextField.placeholderText", "输入关键词搜索...")
        searchPanel.add(searchField)
        
        searchPanel.add(Box.createHorizontalStrut(16))
        
        val categoryLabel = JBLabel("分类: ")
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
        val leftTitleLabel = JBLabel("模板列表")
        leftTitleLabel.font = leftTitleLabel.font.deriveFont(Font.BOLD, 14f)
        leftTitleLabel.border = EmptyBorder(JBUI.insets(0, 0, 8, 0))
        
        val leftPanel = JBPanel<JBPanel<*>>(BorderLayout())
        leftPanel.add(leftTitleLabel, BorderLayout.NORTH)
        leftPanel.add(createTemplateListPanel(), BorderLayout.CENTER)
        leftPanel.border = EmptyBorder(JBUI.insets(0, 0, 0, 8))
        leftPanel.preferredSize = Dimension(350, -1)
        
        // 右侧面板标题和详情
        val rightTitleLabel = JBLabel("模板详情")
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
        
        val importButton = JButton("导入模板")
        importButton.toolTipText = "从文件导入模板配置"
        importButton.addActionListener { importTemplates() }
        buttonPanel.add(importButton)
        
        buttonPanel.add(Box.createHorizontalStrut(8))
        
        val exportButton = JButton("导出模板")
        exportButton.toolTipText = "导出当前模板配置到文件"
        exportButton.addActionListener { exportTemplates() }
        buttonPanel.add(exportButton)
        
        buttonPanel.add(Box.createHorizontalStrut(8))
        
        val resetButton = JButton("重置为默认")
        resetButton.toolTipText = "恢复到系统默认模板"
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
                updateStatus(if (selectedTemplate != null) "已选择模板: ${selectedTemplate.name}" else "就绪")
            }
        }
    }
    
    private fun loadTemplates() {
        try {
            val templates = promptTemplateService.getTemplates()
            this.templates = templates
            
            // 更新分类下拉框
            val categories = templates.map { it.category }.distinct().sorted()
            categoryComboBox.removeAllItems()
            categoryComboBox.addItem("全部")
            categories.forEach { categoryComboBox.addItem(it) }
            
            filterTemplates()
            updateStatus("已加载 ${templates.size} 个模板")
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "加载模板失败：${e.message}",
                "加载错误"
            )
            updateStatus("加载模板失败: ${e.message}")
        }
    }
    
    private fun filterTemplates() {
        val searchText = searchField.text.lowercase()
        val selectedCategory = categoryComboBox.selectedItem as? String
        val enabledOnly = enabledOnlyCheckBox.isSelected
        
        val filtered = templates.filter { template ->
            val matchesSearch = searchText.isEmpty() || 
                template.name.lowercase().contains(searchText) ||
                template.description?.lowercase()?.contains(searchText) == true ||
                template.content.lowercase().contains(searchText) ||
                template.tags.any { it.lowercase().contains(searchText) }
            
            val matchesCategory = selectedCategory == null || selectedCategory == "全部" || template.category == selectedCategory
            val matchesEnabled = !enabledOnly || template.enabled
            
            matchesSearch && matchesCategory && matchesEnabled
        }
        
        templateListModel.clear()
        filtered.forEach { templateListModel.addElement(it) }
        
        updateStatus("显示 ${filtered.size} / ${templates.size} 个模板")
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
            val category = if (selectedCategory == "全部") null else selectedCategory
            val dialog = PromptTemplateEditDialog.showCreateDialog(project, category)
            if (dialog != null) {
                promptTemplateService.saveTemplate(dialog)
                loadTemplates()
                selectTemplate(dialog.id)
                notifyModification()
                updateStatus("模板添加成功")
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "添加模板时发生错误：${e.message}",
                "添加模板失败"
            )
            updateStatus("添加模板失败")
        }
    }
    
    private fun editTemplate() {
        try {
            val selectedTemplate = templateList.selectedValue ?: return
            val dialog = PromptTemplateEditDialog.showEditDialog(project, selectedTemplate)
            if (dialog != null) {
                promptTemplateService.saveTemplate(dialog)
                loadTemplates()
                selectTemplate(dialog.id)
                notifyModification()
                updateStatus("模板编辑成功")
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "编辑模板时发生错误：${e.message}",
                "编辑模板失败"
            )
            updateStatus("编辑模板失败")
        }
    }
    
    private fun removeTemplate() {
        try {
            val selectedTemplate = templateList.selectedValue ?: return
            
            val result = Messages.showYesNoDialog(
                this,
                "确定要删除模板 '${selectedTemplate.name}' 吗？",
                "确认删除",
                Messages.getQuestionIcon()
            )
            
            if (result == Messages.YES) {
                promptTemplateService.deleteTemplate(selectedTemplate.id)
                loadTemplates()
                notifyModification()
                updateStatus("模板删除成功")
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "删除模板时发生错误：${e.message}",
                "删除模板失败"
            )
            updateStatus("删除模板失败")
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
                        "检测到相同ID的模板，是否覆盖同名模板？",
                        "导入选项",
                        null
                    ) == Messages.YES
                } else {
                    false
                }

                // 进行导入
                val importedCount = promptTemplateService.importTemplates(content, overwrite)

                val suffix = if (overwrite) "（已覆盖同名模板）" else ""
                Messages.showInfoMessage(
                    this,
                    "成功导入 ${importedCount} 个模板$suffix",
                    "导入成功"
                )
                
                loadTemplates()
                notifyModification()
                updateStatus("成功导入 ${importedCount} 个模板$suffix")
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    this,
                    "导入失败：${e.message}",
                    "导入错误"
                )
                updateStatus("导入模板失败")
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
                    "模板已成功导出到: ${file.absolutePath}",
                    "导出成功"
                )
                updateStatus("模板导出成功")
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    this,
                    "导出失败：${e.message}",
                    "导出错误"
                )
                updateStatus("导出模板失败")
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
            loadTemplates()
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
                        append(" (已禁用)")
                    }
                    if (value.isBuiltIn) {
                        append(" [内置]")
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
                        append("分类: ${value.category}<br>")
                    }
                    if (value.tags.isNotEmpty()) {
                        append("标签: ${value.tags.joinToString(", ")}<br>")
                    }
                    append("</html>")
                }
            }
            
            return this
        }
    }
}