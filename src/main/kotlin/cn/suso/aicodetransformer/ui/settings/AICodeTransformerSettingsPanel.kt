package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.ui.settings.model.ModelConfigurationPanel

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * AI代码转换器设置面板
 */
class AICodeTransformerSettingsPanel(
    private val project: Project,
    private val configurationService: ConfigurationService
) : JPanel(BorderLayout()) {
    
    private val tabbedPane = JBTabbedPane()
    private val modelConfigPanel: ModelConfigurationPanel
    private val promptTemplatePanel: PromptTemplatePanel
    private val commitSettingsPanel: CommitSettingsPanel
    private val systemManagementPanel: SystemManagementPanel
    
    private var originalConfigurations: List<ModelConfiguration> = emptyList()
    
    init {
        border = EmptyBorder(JBUI.insets(16))
        setupHeader()
        
        // 初始化面板
        modelConfigPanel = ModelConfigurationPanel(project, configurationService)
        
        // 初始化Prompt模板面板
        promptTemplatePanel = PromptTemplatePanel(project, configurationService)
        
        // 初始化提交设置面板
        commitSettingsPanel = CommitSettingsPanel(project, configurationService)
        
        // 初始化配置管理面板
        systemManagementPanel = SystemManagementPanel(project)
        
        // 添加标签页 - 使用紧凑的标题
        tabbedPane.addTab("模型", modelConfigPanel)
        tabbedPane.addTab("模板", promptTemplatePanel)
        tabbedPane.addTab("commit", commitSettingsPanel)
        tabbedPane.addTab("系统", systemManagementPanel)
        
        // 设置tab页独立性 - 允许每个tab页有独立的高度
        tabbedPane.setTabLayoutPolicy(JBTabbedPane.SCROLL_TAB_LAYOUT)
        // 设置每个tab页可以有独立的高度，不强制保持一致
        tabbedPane.putClientProperty("JTabbedPane.tabAreaAlignment", "leading")
        tabbedPane.putClientProperty("JTabbedPane.hasFullBorder", true)
        
        add(tabbedPane, BorderLayout.CENTER)
        
        // 加载初始数据
        reset()
    }
    
    private fun setupHeader() {
        val headerPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.border = EmptyBorder(JBUI.insets(0, 0, 16, 0))
        
        val titleLabel = JBLabel("AI Code Transformer 设置")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        titleLabel.foreground = UIUtil.getLabelForeground()
        
        val descLabel = JBLabel("配置AI模型和Prompt模板以优化代码转换体验")
        descLabel.font = descLabel.font.deriveFont(12f)
        descLabel.foreground = UIUtil.getLabelDisabledForeground()
        
        val titlePanel = JBPanel<JBPanel<*>>(BorderLayout())
        titlePanel.add(titleLabel, BorderLayout.NORTH)
        titlePanel.add(descLabel, BorderLayout.CENTER)
        
        headerPanel.add(titlePanel)
        add(headerPanel, BorderLayout.NORTH)
    }
    
    /**
     * 检查是否有修改
     */
    fun isModified(): Boolean {
        return modelConfigPanel.isModified() || promptTemplatePanel.isModified() || commitSettingsPanel.isModified() || systemManagementPanel.isModified()
    }
    
    /**
     * 应用更改
     */
    fun apply() {
        try {
            // 检查是否有实际修改
            val hasModelChanges = modelConfigPanel.isModified()
            val hasPromptChanges = promptTemplatePanel.isModified()
            val hasCommitChanges = commitSettingsPanel.isModified()
            val hasSystemChanges = systemManagementPanel.isModified()
            
            // 只有在有修改时才执行保存操作
            if (!hasModelChanges && !hasPromptChanges && !hasCommitChanges && !hasSystemChanges) {
                return
            }
            
            // 应用模型配置更改
            if (hasModelChanges) {
                modelConfigPanel.apply()
            }
            
            // 应用Prompt模板更改
            if (hasPromptChanges) {
                promptTemplatePanel.apply()
            }
            
            // 应用提交设置更改
            if (hasCommitChanges) {
                commitSettingsPanel.apply()
            }
            
            // 应用系统设置更改
            if (hasSystemChanges) {
                systemManagementPanel.apply()
            }
            
            // 更新原始配置
            originalConfigurations = configurationService.getModelConfigurations()
            
            // 根据修改的内容显示相应的保存消息
            val savedItems = mutableListOf<String>()
            if (hasModelChanges) savedItems.add("模型配置")
            if (hasPromptChanges) savedItems.add("模板配置")
            if (hasCommitChanges) savedItems.add("提交设置")
            if (hasSystemChanges) savedItems.add("系统设置")
            
            Messages.showInfoMessage(
                project,
                "${savedItems.joinToString("、")}已成功保存！",
                "保存成功"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "保存配置时发生错误：${e.message}",
                "保存失败"
            )
            throw e
        }
    }
    
    /**
     * 重置到原始状态
     */
    fun reset() {
        // 在后台线程中加载配置，避免阻塞UI
        SwingUtilities.invokeLater {
            try {
                // 重新加载配置
                originalConfigurations = configurationService.getModelConfigurations()
                
                // 重置各个面板
                modelConfigPanel.reset()
                promptTemplatePanel.reset()
                commitSettingsPanel.reset()
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "加载配置时发生错误：${e.message}",
                    "加载失败"
                )
            }
        }
    }
}