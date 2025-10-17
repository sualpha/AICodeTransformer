package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.model.CommitSettings
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.ui.components.TooltipHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
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
            .addComponent(createTemplatePanel())
            .panel
        
        add(formPanel, BorderLayout.NORTH)
    }
    
    private fun createTemplatePanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 添加标签到顶部
        val titleLabel = JBLabel("Commit 提示词:")
        panel.add(titleLabel, BorderLayout.NORTH)
        
        templateTextArea.rows = 8
        templateTextArea.columns = 50
        templateTextArea.lineWrap = true
        templateTextArea.wrapStyleWord = true
        
        val scrollPane = JBScrollPane(templateTextArea)
        scrollPane.preferredSize = Dimension(500, 200)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 创建底部面板，包含按钮和说明
        val bottomPanel = JPanel(BorderLayout())
        
        // 创建按钮面板
        val buttonPanel = JPanel(BorderLayout())
        
        // 左侧按钮面板（Insert Variable）
        val leftButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val insertVariableButton = JButton("插入内置变量")
        insertVariableButton.addActionListener { showVariablePopup(insertVariableButton) }
        leftButtonPanel.add(insertVariableButton)
        
        // 右侧按钮面板（重置、保存）
        val rightButtonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val resetButton = JButton("重置")
        resetButton.addActionListener { reset() }
        val saveButton = JButton("保存")
        saveButton.addActionListener { apply() }
        rightButtonPanel.add(resetButton)
        rightButtonPanel.add(saveButton)
        
        buttonPanel.add(leftButtonPanel, BorderLayout.WEST)
        buttonPanel.add(rightButtonPanel, BorderLayout.EAST)
        
        // 创建说明面板
        val helpPanel = JPanel(BorderLayout())
        val helpLabel = JBLabel("<html><small>此模板将作为AI提示词使用。点击\"插入变量\"按钮选择内置变量，每个变量都有详细的含义说明。</small></html>")
        helpPanel.add(helpLabel, BorderLayout.CENTER)
        
        bottomPanel.add(buttonPanel, BorderLayout.NORTH)
        bottomPanel.add(helpPanel, BorderLayout.CENTER)
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
        templateTextArea.text = currentSettings.commitTemplate
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
        try {
            currentSettings = getSettingsFromUI()
            // 保存到ConfigurationService
            configurationService.saveCommitSettings(currentSettings)
            originalSettings = currentSettings.copy()
            
            Messages.showInfoMessage(
                this,
                "Commit设置已保存！",
                "保存成功"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                "保存Commit设置失败: ${e.message}",
                "保存失败"
            )
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
            commitTemplate = templateTextArea.text
        )
    }
    
    /**
     * 显示变量选择弹窗
     */
    private fun showVariablePopup(component: JButton) {
        val variables = CommitSettings.BUILT_IN_VARIABLES
        
        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<String>("选择变量", variables.map { "${it.name} - ${it.description}" }) {
                override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice) {
                        val selectedVariable = variables[values.indexOf(selectedValue)]
                        insertVariableAtCursor(selectedVariable.name)
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
    private fun insertVariableAtCursor(variableName: String) {
        val caretPosition = templateTextArea.caretPosition
        val currentText = templateTextArea.text
        val newText = currentText.substring(0, caretPosition) + variableName + currentText.substring(caretPosition)
        templateTextArea.text = newText
        templateTextArea.caretPosition = caretPosition + variableName.length
        templateTextArea.requestFocus()
    }
}