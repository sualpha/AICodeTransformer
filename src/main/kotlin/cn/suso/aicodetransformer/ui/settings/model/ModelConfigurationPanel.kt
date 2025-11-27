package cn.suso.aicodetransformer.ui.settings.model

import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.i18n.LanguageManager
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.ui.components.TooltipHelper
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.io.File
import javax.swing.*
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * 模型配置管理面板
 */
class ModelConfigurationPanel(
    private val project: Project,
    private val configurationService: ConfigurationService
) : JPanel(BorderLayout()) {
    
    private val listModel = DefaultListModel<ModelConfiguration>()
    private val configList = JBList(listModel)
    private val detailPanel = ModelConfigurationDetailPanel(project)
    
    private var originalConfigurations: List<ModelConfiguration> = emptyList()
    private var currentConfigurations: MutableList<ModelConfiguration> = mutableListOf()
    
    private lateinit var leftTitleLabel: JBLabel
    private lateinit var defaultInfoLabel: JBLabel
    private lateinit var rightTitleLabel: JBLabel
    private lateinit var importButton: JButton
    private lateinit var exportButton: JButton
    private val languageChangeListener: () -> Unit = {
        SwingUtilities.invokeLater {
            refreshTexts()
        }
    }

    init {
        setupUI()
        setupListeners()
        loadConfigurations()
        LanguageManager.addChangeListener(languageChangeListener)
    }
    
    private fun setupUI() {
        border = EmptyBorder(JBUI.insets(12))
        
        // 设置列表
        configList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        configList.cellRenderer = ModelConfigurationListCellRenderer()
        configList.border = JBUI.Borders.empty()
        
        // 创建工具栏
        val baseDecorator = ToolbarDecorator.createDecorator(configList)
            .setAddAction { addConfiguration() }
            .setRemoveAction { removeConfiguration() }
            .setEditAction { editConfiguration() }
            .setMoveUpAction { moveUp() }
            .setMoveDownAction { moveDown() }
            .createPanel()
        
        // 创建包含额外按钮的自定义工具栏
        val customToolbarPanel = JPanel(BorderLayout())
        customToolbarPanel.add(baseDecorator, BorderLayout.CENTER)
        
        // 创建额外按钮面板
        val extraButtonsPanel = JPanel()
        extraButtonsPanel.layout = BoxLayout(extraButtonsPanel, BoxLayout.X_AXIS)
        
        importButton = JButton(I18n.t("model.import"))
        importButton.addActionListener { importConfigurations() }
        importButton.toolTipText = I18n.t("model.import.tooltip")
        
        exportButton = JButton(I18n.t("model.export"))
        exportButton.addActionListener { exportConfigurations() }
        exportButton.toolTipText = I18n.t("model.export.tooltip")
        
        extraButtonsPanel.add(importButton)
        extraButtonsPanel.add(Box.createHorizontalStrut(5))
        extraButtonsPanel.add(exportButton)
        
        customToolbarPanel.add(extraButtonsPanel, BorderLayout.SOUTH)
        val decorator = customToolbarPanel
        
        // 为工具栏按钮添加提示
        decorator.components.forEach { component ->
            when {
                component.toString().contains("Add") -> 
                    TooltipHelper.setTooltip(component as JComponent, TooltipHelper.ModelConfigTooltips.ADD_MODEL)
                component.toString().contains("Remove") -> 
                    TooltipHelper.setTooltip(component as JComponent, TooltipHelper.ModelConfigTooltips.DELETE_MODEL)
                component.toString().contains("Edit") -> 
                    TooltipHelper.setTooltip(component as JComponent, TooltipHelper.ModelConfigTooltips.EDIT_MODEL)
            }
        }
        
        // 左侧面板标题
        leftTitleLabel = JBLabel(I18n.t("model.list.title"))
        leftTitleLabel.font = leftTitleLabel.font.deriveFont(Font.BOLD, 14f)

        defaultInfoLabel = JBLabel(I18n.t("model.default.info"))
        defaultInfoLabel.font = defaultInfoLabel.font.deriveFont(Font.PLAIN, 11f)
        defaultInfoLabel.foreground = UIUtil.getLabelDisabledForeground()
        defaultInfoLabel.border = EmptyBorder(JBUI.insets(4, 0, 0, 0))

        val leftHeaderPanel = JBPanel<JBPanel<*>>()
        leftHeaderPanel.layout = BoxLayout(leftHeaderPanel, BoxLayout.Y_AXIS)
        leftHeaderPanel.border = EmptyBorder(JBUI.insets(0, 0, 8, 0))
        leftHeaderPanel.add(leftTitleLabel)
        leftHeaderPanel.add(defaultInfoLabel)
        
        // 左侧面板 - 配置列表
        val leftPanel = JBPanel<JBPanel<*>>(BorderLayout())
        leftPanel.add(leftHeaderPanel, BorderLayout.NORTH)
        leftPanel.add(decorator, BorderLayout.CENTER)
        leftPanel.border = EmptyBorder(JBUI.insets(0, 0, 0, 8))
        leftPanel.preferredSize = Dimension(300, -1)
        
        // 右侧面板标题
        rightTitleLabel = JBLabel(I18n.t("model.detail.title"))
        rightTitleLabel.font = rightTitleLabel.font.deriveFont(Font.BOLD, 14f)
        rightTitleLabel.border = EmptyBorder(JBUI.insets(0, 0, 8, 0))
        
        // 右侧面板 - 配置详情
        val rightPanel = JBPanel<JBPanel<*>>(BorderLayout())
        rightPanel.add(rightTitleLabel, BorderLayout.NORTH)
        rightPanel.add(JBScrollPane(detailPanel), BorderLayout.CENTER)
        rightPanel.border = EmptyBorder(JBUI.insets(0, 8, 0, 0))
        
        // 分割面板
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel)
        splitPane.dividerLocation = 320
        splitPane.resizeWeight = 0.4
        splitPane.border = JBUI.Borders.empty()
        splitPane.dividerSize = 8
        splitPane.setOneTouchExpandable(true)
        
        add(splitPane, BorderLayout.CENTER)
    }

    private fun refreshTexts() {
        leftTitleLabel.text = I18n.t("model.list.title")
        defaultInfoLabel.text = I18n.t("model.default.info")
        rightTitleLabel.text = I18n.t("model.detail.title")
        importButton.text = I18n.t("model.import")
        importButton.toolTipText = I18n.t("model.import.tooltip")
        exportButton.text = I18n.t("model.export")
        exportButton.toolTipText = I18n.t("model.export.tooltip")
        detailPanel.refreshTexts()
    }
    
    private fun setupListeners() {
        // 列表选择监听器
        configList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedConfig = configList.selectedValue
                detailPanel.setConfiguration(selectedConfig)
            }
        }
        
        // 详情面板变更监听器
        detailPanel.addChangeListener {
            val selectedIndex = configList.selectedIndex
            if (selectedIndex >= 0) {
                val updatedConfig = detailPanel.getConfiguration()
                if (updatedConfig != null) {
                    currentConfigurations[selectedIndex] = updatedConfig
                    listModel.setElementAt(updatedConfig, selectedIndex)
                    configList.repaint()
                }
            }
        }
    }
    
    private fun addConfiguration() {
        val dialog = ModelConfigurationDialog(project, null)
        if (dialog.showAndGet()) {
            val newConfig = dialog.getConfiguration()
            if (newConfig != null) {
                // 检查ID是否重复
                if (currentConfigurations.any { it.id == newConfig.id }) {
                    Messages.showErrorDialog(
                        project,
                        "配置ID已存在，请使用不同的ID",
                        "添加失败"
                    )
                    return
                }
                
                currentConfigurations.add(newConfig)
                listModel.addElement(newConfig)
                configList.selectedIndex = listModel.size() - 1
            }
        }
    }
    
    private fun removeConfiguration() {
        val selectedIndex = configList.selectedIndex
        if (selectedIndex >= 0) {
            val config = currentConfigurations[selectedIndex]
            
            // 检查是否为内置模型
            if (config.isBuiltIn) {
                Messages.showErrorDialog(
                    project,
                    I18n.t("model.builtin.cannotDelete"),
                    I18n.t("model.delete.error.title")
                )
                return
            }
            
            val message = I18n.t("model.delete.confirm.message", config.name)
            val title = I18n.t("model.delete.confirm.title")
            val result = Messages.showYesNoDialog(
                project,
                message,
                title,
                Messages.getQuestionIcon()
            )
            
            if (result == Messages.YES) {
                currentConfigurations.removeAt(selectedIndex)
                listModel.removeElementAt(selectedIndex)
                
                // 选择下一个或上一个项目
                val newIndex = when {
                    selectedIndex < listModel.size() -> selectedIndex
                    listModel.size() > 0 -> listModel.size() - 1
                    else -> -1
                }
                
                if (newIndex >= 0) {
                    configList.selectedIndex = newIndex
                } else {
                    detailPanel.setConfiguration(null)
                }
            }
        }
    }
    
    private fun editConfiguration() {
        val selectedIndex = configList.selectedIndex
        if (selectedIndex >= 0) {
            val config = currentConfigurations[selectedIndex]
            
            // 检查是否为内置模型
            if (config.isBuiltIn) {
                Messages.showErrorDialog(
                    project,
                    I18n.t("model.builtin.cannotEdit"),
                    I18n.t("model.delete.error.title")
                )
                return
            }
            
            val dialog = ModelConfigurationDialog(project, config)
            if (dialog.showAndGet()) {
                val updatedConfig = dialog.getConfiguration()
                if (updatedConfig != null) {
                    currentConfigurations[selectedIndex] = updatedConfig
                    listModel.setElementAt(updatedConfig, selectedIndex)
                    detailPanel.setConfiguration(updatedConfig)
                }
            }
        }
    }
    
    private fun moveUp() {
        val selectedIndex = configList.selectedIndex
        if (selectedIndex > 0) {
            val config = currentConfigurations.removeAt(selectedIndex)
            currentConfigurations.add(selectedIndex - 1, config)
            
            listModel.removeElementAt(selectedIndex)
            listModel.add(selectedIndex - 1, config)
            
            configList.selectedIndex = selectedIndex - 1
        }
    }
    
    private fun moveDown() {
        val selectedIndex = configList.selectedIndex
        if (selectedIndex >= 0 && selectedIndex < currentConfigurations.size - 1) {
            val config = currentConfigurations.removeAt(selectedIndex)
            currentConfigurations.add(selectedIndex + 1, config)
            
            listModel.removeElementAt(selectedIndex)
            listModel.add(selectedIndex + 1, config)
            
            configList.selectedIndex = selectedIndex + 1
        }
    }
    

    
    private fun importConfigurations() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
        descriptor.title = "选择配置文件"
        descriptor.description = "选择要导入的配置文件 (JSON格式)"
        descriptor.withFileFilter { file -> file.extension?.lowercase() == "json" }
        
        val file = FileChooser.chooseFile(descriptor, project, null)
        if (file != null) {
            try {
                val configJson = File(file.path).readText()
                val importedCount = configurationService.importConfigurations(configJson)
                
                if (importedCount > 0) {
                    Messages.showInfoMessage(
                        project,
                        "成功导入 $importedCount 个配置",
                        "导入成功"
                    )
                    loadConfigurations() // 重新加载配置
                } else {
                    Messages.showWarningDialog(
                        project,
                        "没有导入任何配置，请检查文件格式是否正确",
                        "导入失败"
                    )
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "导入配置失败: ${e.message}",
                    "导入错误"
                )
            }
        }
    }
    
    private fun exportConfigurations() {
        if (currentConfigurations.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "没有可导出的配置",
                "导出失败"
            )
            return
        }
        
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
        descriptor.title = "选择导出目录"
        descriptor.description = "选择配置文件的导出目录"
        
        val directory = FileChooser.chooseFile(descriptor, project, null)
        if (directory != null) {
            try {
                val configJson = configurationService.exportConfigurations()
                val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                val fileName = "aicodetransformer-config-$timestamp.json"
                val exportFile = File(directory.path, fileName)
                
                exportFile.writeText(configJson)
                
                Messages.showInfoMessage(
                    project,
                    "配置已成功导出到: ${exportFile.absolutePath}",
                    "导出成功"
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "导出配置失败: ${e.message}",
                    "导出错误"
                )
            }
        }
    }
    
    private fun loadConfigurations() {
        // 在后台线程中加载配置，避免阻塞UI
        SwingUtilities.invokeLater {
            try {
                originalConfigurations = configurationService.getModelConfigurations()
                reset()
            } catch (e: Exception) {
                // 如果加载失败，使用空配置
                originalConfigurations = emptyList()
                reset()
            }
        }
    }
    
    /**
     * 检查是否有修改
     */
    fun isModified(): Boolean {
        if (originalConfigurations.size != currentConfigurations.size) {
            return true
        }
        
        return originalConfigurations.zip(currentConfigurations).any { (original, current) ->
            original != current
        }
    }
    
    /**
     * 应用更改
     */
    fun apply() {
        // 保存当前选中项的更改
        val selectedIndex = configList.selectedIndex
        if (selectedIndex >= 0) {
            val updatedConfig = detailPanel.getConfiguration()
            if (updatedConfig != null) {
                currentConfigurations[selectedIndex] = updatedConfig
            }
            // 保存API密钥
            if (!detailPanel.saveApiKey()) {
                // API密钥保存失败，不继续保存其他配置
                return
            }
        }
        
        try {
            // 删除所有原有配置
            originalConfigurations.forEach { config ->
                configurationService.deleteModelConfiguration(config.id)
            }
            
            // 保存新配置
            currentConfigurations.forEach { config ->
                configurationService.saveModelConfiguration(config)
            }
            
            // 更新原始配置
            originalConfigurations = currentConfigurations.toList()
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "保存配置失败: ${e.message}",
                "错误"
            )
        }
    }
    
    /**
     * 重置到原始状态
     */
    fun reset() {
        currentConfigurations.clear()
        currentConfigurations.addAll(originalConfigurations)
        
        listModel.clear()
        currentConfigurations.forEach { config ->
            listModel.addElement(config)
        }
        
        if (listModel.size() > 0) {
            configList.selectedIndex = 0
        } else {
            detailPanel.setConfiguration(null)
        }
    }
}