package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.constants.TemplateConstants
import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.i18n.LanguageManager
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
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * Commit设置面板 - 包含单个模板管理
 */
class CommitSettingsPanel(
    private val project: Project,
    private val configurationService: ConfigurationService
) : JPanel(BorderLayout()) {
    
    // UI组件
    private val autoCommitCheckBox = JBCheckBox(I18n.t("commit.autoCommit"))
    private val autoPushCheckBox = JBCheckBox(I18n.t("commit.autoPush"))
    private val singleFileTemplateTextArea = JBTextArea()
    private val summaryTemplateTextArea = JBTextArea()
    private var lastSimpleDefault: String = CommitSettings.SIMPLE_TEMPLATE
    private var lastSummaryDefault: String = CommitSettings.SUMMARY_TEMPLATE
    
    // 保留向后兼容
    @Deprecated("使用 singleFileTemplateTextArea 替代")
    private val templateTextArea = JBTextArea()
    
    // 当前设置
    private var currentSettings = CommitSettings.createDefault()
    private var originalSettings = CommitSettings.createDefault()
    
    private lateinit var singleTitleLabel: JBLabel
    private lateinit var summaryTitleLabel: JBLabel
    private lateinit var singleResetButton: JButton
    private lateinit var singleInsertButton: JButton
    private lateinit var summaryResetButton: JButton
    private lateinit var summaryInsertButton: JButton
    private var singleFileVariables: List<Pair<String, String>> = emptyList()
    private var summaryVariables: List<Pair<String, String>> = emptyList()
    private val languageChangeListener: () -> Unit = {
        SwingUtilities.invokeLater {
            refreshTexts()
        }
    }

    init {
        setupUI()
        loadSettings()
        setupListeners()
        refreshTexts()
        LanguageManager.addChangeListener(languageChangeListener)
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
        singleTitleLabel = JBLabel(I18n.t("commit.single.title"))
        panel.add(singleTitleLabel, BorderLayout.NORTH)
        
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
        singleResetButton = JButton(I18n.t("commit.resetDefault"))
        singleResetButton.addActionListener { 
            singleFileTemplateTextArea.text = CommitSettings.SIMPLE_TEMPLATE
        }
        leftButtonPanel.add(singleResetButton)
        
        singleInsertButton = JButton(I18n.t("commit.insertVariable"))
        singleInsertButton.addActionListener { showVariablePopup(singleInsertButton, singleFileTemplateTextArea, singleFileVariables) }
        leftButtonPanel.add(singleInsertButton)
        
        buttonPanel.add(leftButtonPanel, BorderLayout.WEST)
        
        bottomPanel.add(buttonPanel, BorderLayout.NORTH)
        panel.add(bottomPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createSummaryTemplatePanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 添加标签到顶部
        summaryTitleLabel = JBLabel(I18n.t("commit.summary.title"))
        panel.add(summaryTitleLabel, BorderLayout.NORTH)
        
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
        summaryResetButton = JButton(I18n.t("commit.resetDefault"))
        summaryResetButton.addActionListener { 
            summaryTemplateTextArea.text = CommitSettings.SUMMARY_TEMPLATE
        }
        leftButtonPanel.add(summaryResetButton)
        
        summaryInsertButton = JButton(I18n.t("commit.insertVariable"))
        summaryInsertButton.addActionListener { showVariablePopup(summaryInsertButton, summaryTemplateTextArea, summaryVariables) }
        leftButtonPanel.add(summaryInsertButton)
        
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
        TooltipHelper.setTooltip(autoCommitCheckBox, I18n.t("commit.autoCommit"))
        TooltipHelper.setTooltip(autoPushCheckBox, I18n.t("commit.autoPush"))
    }

    private fun refreshTexts() {
        autoCommitCheckBox.text = I18n.t("commit.autoCommit")
        autoPushCheckBox.text = I18n.t("commit.autoPush")
        singleTitleLabel.text = I18n.t("commit.single.title")
        summaryTitleLabel.text = I18n.t("commit.summary.title")
        singleResetButton.text = I18n.t("commit.resetDefault")
        summaryResetButton.text = I18n.t("commit.resetDefault")
        singleInsertButton.text = I18n.t("commit.insertVariable")
        summaryInsertButton.text = I18n.t("commit.insertVariable")

        val newSimpleDefault = CommitSettings.SIMPLE_TEMPLATE
        if (CommitSettings.matchesSimpleTemplateDefault(singleFileTemplateTextArea.text) ||
            singleFileTemplateTextArea.text == lastSimpleDefault
        ) {
            singleFileTemplateTextArea.text = newSimpleDefault
        }
        lastSimpleDefault = newSimpleDefault

        val newSummaryDefault = CommitSettings.SUMMARY_TEMPLATE
        if (CommitSettings.matchesSummaryTemplateDefault(summaryTemplateTextArea.text) ||
            summaryTemplateTextArea.text == lastSummaryDefault
        ) {
            summaryTemplateTextArea.text = newSummaryDefault
        }
        lastSummaryDefault = newSummaryDefault

        singleFileVariables = listOf(
            TemplateConstants.GitBuiltInVariable.CHANGED_FILES.variable to I18n.t("prompt.variable.changedFiles"),
            TemplateConstants.GitBuiltInVariable.FILE_DIFFS.variable to I18n.t("prompt.variable.fileDiffs")
        )
        summaryVariables = listOf(
            TemplateConstants.GitBuiltInVariable.BATCH_COMMIT_MESSAGES.variable to I18n.t("prompt.variable.batchCommitMessages")
        )
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

    private fun showVariablePopup(
        component: JButton,
        targetTextArea: JBTextArea = singleFileTemplateTextArea,
        customVariables: List<Pair<String, String>>? = null
    ) {
        val variables = customVariables ?: listOf(
            TemplateConstants.GitBuiltInVariable.CHANGED_FILES.variable to I18n.t("prompt.variable.changedFiles"),
            TemplateConstants.GitBuiltInVariable.FILE_DIFFS.variable to I18n.t("prompt.variable.fileDiffs"),
            TemplateConstants.GitBuiltInVariable.BATCH_COMMIT_MESSAGES.variable to I18n.t("prompt.variable.batchCommitMessages")
        )

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<String>(
                I18n.t("commit.insertVariable"),
                variables.map { "${it.first} - ${it.second}" }
            ) {
                override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice) {
                        val selectedVariable = variables.find { "${it.first} - ${it.second}" == selectedValue }?.first
                        selectedVariable?.let { insertVariableAtCursor(it, targetTextArea) }
                    }
                    return null
                }

                override fun getTextFor(value: String): String = value

                override fun getIconFor(value: String): javax.swing.Icon? = null
            }
        )

        popup.showUnderneathOf(component)
    }

    private fun insertVariableAtCursor(variableName: String, targetTextArea: JBTextArea = singleFileTemplateTextArea) {
        val caretPosition = targetTextArea.caretPosition
        val currentText = targetTextArea.text
        val newText = currentText.substring(0, caretPosition) + variableName + currentText.substring(caretPosition)
        targetTextArea.text = newText
        targetTextArea.caretPosition = caretPosition + variableName.length
    }
}