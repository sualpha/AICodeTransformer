package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.constants.TemplateConstants
import cn.suso.aicodetransformer.model.CommitSettings
import cn.suso.aicodetransformer.model.CommitTemplateType
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.ui.components.TooltipHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * Commit设置面板 - 包含单个模板管理
 */
class CommitSettingsPanel(
    private val project: Project,
    private val configurationService: ConfigurationService
) : JPanel(BorderLayout()) {
    
    // UI组件
    private val autoCommitCheckBox = JBCheckBox("启用自动提交")
    private val autoPushCheckBox = JBCheckBox("启用自动推送")
    private val singleFileTemplateTextArea = JBTextArea()
    private val summaryTemplateTextArea = JBTextArea()
    
    // 保留向后兼容
    @Deprecated("使用 singleFileTemplateTextArea 替代")
    private val templateTextArea = JBTextArea()
    
    // 当前设置
    private var currentSettings = CommitSettings.createDefault()
    private var originalSettings = CommitSettings.createDefault()
    
    init {
        setupUI()
        loadSettings()
        setupListeners()
    }
    
    private fun setupUI() {
        border = EmptyBorder(JBUI.insets(16))
        
        val formPanel = FormBuilder.createFormBuilder()
            .addComponent(autoCommitCheckBox)
            .addComponent(autoPushCheckBox)
            .addVerticalGap(10)
            .addComponent(createSingleFileTemplatePanel())
            .addVerticalGap(10)
            .addComponent(createSummaryTemplatePanel())
            .panel
        
        add(formPanel, BorderLayout.NORTH)
    }
    


    private fun createSingleFileTemplatePanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 添加标签到顶部
        val titleLabel = JBLabel("单个文件提示词:")
        panel.add(titleLabel, BorderLayout.NORTH)
        
        singleFileTemplateTextArea.rows = 6
        singleFileTemplateTextArea.columns = 50
        singleFileTemplateTextArea.lineWrap = true
        singleFileTemplateTextArea.wrapStyleWord = true
        
        val scrollPane = JBScrollPane(singleFileTemplateTextArea)
        scrollPane.preferredSize = Dimension(500, 150)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 创建底部面板，包含按钮和说明
        val bottomPanel = JPanel(BorderLayout())
        
        // 创建按钮面板
        val buttonPanel = JPanel(BorderLayout())
        
        // 左侧按钮面板（重置、Insert Variable）
        val leftButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val resetButton = JButton("重置为默认")
        resetButton.addActionListener { 
            singleFileTemplateTextArea.text = CommitSettings.SIMPLE_TEMPLATE
        }
        leftButtonPanel.add(resetButton)
        
        val insertVariableButton = JButton("插入内置变量")
        insertVariableButton.addActionListener { showVariablePopup(insertVariableButton, singleFileTemplateTextArea, singleFileVariables) }
        leftButtonPanel.add(insertVariableButton)
        
        buttonPanel.add(leftButtonPanel, BorderLayout.WEST)
        
        bottomPanel.add(buttonPanel, BorderLayout.NORTH)
        panel.add(bottomPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createSummaryTemplatePanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 添加标签到顶部
        val titleLabel = JBLabel("汇总提示词:")
        panel.add(titleLabel, BorderLayout.NORTH)
        
        summaryTemplateTextArea.rows = 6
        summaryTemplateTextArea.columns = 50
        summaryTemplateTextArea.lineWrap = true
        summaryTemplateTextArea.wrapStyleWord = true
        
        val scrollPane = JBScrollPane(summaryTemplateTextArea)
        scrollPane.preferredSize = Dimension(500, 150)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 创建底部面板，包含按钮和说明
        val bottomPanel = JPanel(BorderLayout())
        
        // 创建按钮面板
        val buttonPanel = JPanel(BorderLayout())
        
        // 左侧按钮面板（重置、Insert Variable）
        val leftButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val resetButton = JButton("重置为默认")
        resetButton.addActionListener { 
            summaryTemplateTextArea.text = CommitSettings.SUMMARY_TEMPLATE
        }
        leftButtonPanel.add(resetButton)
        
        val insertVariableButton = JButton("插入内置变量")
        insertVariableButton.addActionListener { showVariablePopup(insertVariableButton, summaryTemplateTextArea, summaryVariables) }
        leftButtonPanel.add(insertVariableButton)
        
        buttonPanel.add(leftButtonPanel, BorderLayout.WEST)
        
        bottomPanel.add(buttonPanel, BorderLayout.NORTH)
        panel.add(bottomPanel, BorderLayout.SOUTH)
        
        return panel
    }
    

    
    private fun setupListeners() {
        // 自动推送依赖于自动提交
        autoCommitCheckBox.addActionListener {
            if (!autoCommitCheckBox.isSelected) {
                autoPushCheckBox.isSelected = false
                autoPushCheckBox.isEnabled = false
            } else {
                autoPushCheckBox.isEnabled = true
            }
        }
        
        setupTooltips()
    }
    
    private fun setupTooltips() {
        TooltipHelper.setTooltip(autoCommitCheckBox, "启用后，生成提交消息后会自动执行提交操作")
        TooltipHelper.setTooltip(autoPushCheckBox, "启用后，自动提交完成后会自动推送到远程仓库")
    }
    
    private fun loadSettings() {
        try {
            // 从ConfigurationService加载设置
            currentSettings = configurationService.getCommitSettings()
            originalSettings = currentSettings.copy()
            
            updateUIFromSettings()
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "加载Commit设置失败: ${e.message}",
                "加载失败"
            )
            // 如果加载失败，使用默认设置
            currentSettings = CommitSettings.createDefault()
            originalSettings = currentSettings.copy()
            updateUIFromSettings()
        }
    }
    
    private fun updateUIFromSettings() {
        autoCommitCheckBox.isSelected = currentSettings.autoCommitEnabled
        autoPushCheckBox.isSelected = currentSettings.autoPushEnabled
        autoPushCheckBox.isEnabled = currentSettings.autoCommitEnabled
        singleFileTemplateTextArea.text = currentSettings.singleFileTemplate
        summaryTemplateTextArea.text = currentSettings.summaryTemplate
    }
    

    
    /**
     * 检查是否有修改
     */
    fun isModified(): Boolean {
        val currentUISettings = getSettingsFromUI()
        return currentUISettings != originalSettings
    }
    
    /**
     * 应用更改
     */
    fun apply() {
        // 只在有修改时才保存
        if (!isModified()) {
            return
        }
        
        try {
            currentSettings = getSettingsFromUI()
            // 保存到ConfigurationService
            configurationService.saveCommitSettings(currentSettings)
            originalSettings = currentSettings.copy()

            // 移除单独的保存成功消息，由主面板统一显示
        } catch (e: Exception) {

        }
    }
    
    /**
     * 重置到原始状态
     */
    fun reset() {
        currentSettings = originalSettings.copy()
        updateUIFromSettings()
    }
    
    private fun getSettingsFromUI(): CommitSettings {
        return CommitSettings(
            autoCommitEnabled = autoCommitCheckBox.isSelected,
            autoPushEnabled = autoPushCheckBox.isSelected,
            singleFileTemplate = singleFileTemplateTextArea.text,
            summaryTemplate = summaryTemplateTextArea.text,
            useBatchProcessing = currentSettings.useBatchProcessing,
            batchSize = currentSettings.batchSize,
            maxFileContentLength = currentSettings.maxFileContentLength,
            maxTotalContentLength = currentSettings.maxTotalContentLength
        )
    }
    
    /**
     * 单个文件模板专用变量
     */
    private val singleFileVariables = listOf(
        "{{changedFiles}}" to "Git变更文件列表",
        "{{fileDiffs}}" to "文件差异详情"
    )
    
    /**
     * 汇总模板专用变量
     */
    private val summaryVariables = listOf(
        "{{batchCommitMessages}}" to "多个批次的提交信息"
    )
    
    /**
     * 显示变量选择弹窗
     */
    private fun showVariablePopup(component: JButton, targetTextArea: JBTextArea = singleFileTemplateTextArea, customVariables: List<Pair<String, String>>? = null) {
        val variables = customVariables ?: TemplateConstants.GitBuiltInVariable.values().map { it.variable to it.description }
        
        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<String>("选择变量", variables.map { "${it.first} - ${it.second}" }) {
                override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice) {
                        val selectedVariable = variables.find { "${it.first} - ${it.second}" == selectedValue }?.first
                        selectedVariable?.let { insertVariableAtCursor(it, targetTextArea) }
                    }
                    return null
                }
                
                override fun getTextFor(value: String): String {
                    return value
                }
                
                override fun getIconFor(value: String): javax.swing.Icon? {
                    return null
                }
            }
        )
        
        popup.showUnderneathOf(component)
    }
    
    /**
     * 在光标位置插入变量
     */
    private fun insertVariableAtCursor(variableName: String, targetTextArea: JBTextArea = singleFileTemplateTextArea) {
        val caretPosition = targetTextArea.caretPosition
        val currentText = targetTextArea.text
        val newText = currentText.substring(0, caretPosition) + variableName + currentText.substring(caretPosition)
        targetTextArea.text = newText
        targetTextArea.caretPosition = caretPosition + variableName.length
    }
    

}