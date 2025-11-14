package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.ui.settings.model.ModelConfigurationPanel
import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.i18n.LanguageManager
import cn.suso.aicodetransformer.service.LanguageSettingsService

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
    private val configurationService: ConfigurationService,
    private val languageController: LanguageSettingsService
) : JPanel(BorderLayout()) {
    
    private var tabbedPane = JBTabbedPane()
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
        promptTemplatePanel = PromptTemplatePanel(project, configurationService)
        commitSettingsPanel = CommitSettingsPanel(project, configurationService)
        systemManagementPanel = SystemManagementPanel(project, configurationService, languageController)

        configureTabbedPane(tabbedPane)
        add(tabbedPane, BorderLayout.CENTER)
        
        // 加载初始数据
        reset()
        // 监听语言变化以刷新页签文本
        LanguageManager.addChangeListener {
            SwingUtilities.invokeLater {
                languageController.syncLanguageDependentDefaults()
                val selectedIndex = tabbedPane.selectedIndex
                remove(tabbedPane)
                tabbedPane = JBTabbedPane()
                configureTabbedPane(tabbedPane, selectedIndex)
                add(tabbedPane, BorderLayout.CENTER)
                revalidate()
                repaint()
                setupHeaderTexts()
            }
        }
    }

    private fun configureTabbedPane(pane: JBTabbedPane, selectedIndex: Int = 0) {
        // 添加标签页 - 使用国际化标题
        val titles = arrayOf(
            I18n.t("tab.models"),
            I18n.t("tab.templates"),
            I18n.t("tab.commit"),
            I18n.t("tab.system")
        )
        val panels = arrayOf(
            modelConfigPanel,
            promptTemplatePanel,
            commitSettingsPanel,
            systemManagementPanel
        )
        for (i in titles.indices) {
            pane.addTab(titles[i], panels[i])
        }

        // 设置tab页独立性 - 允许每个tab页有独立的高度
        pane.setTabLayoutPolicy(JBTabbedPane.SCROLL_TAB_LAYOUT)
        pane.putClientProperty("JTabbedPane.tabAreaAlignment", "leading")
        pane.putClientProperty("JTabbedPane.hasFullBorder", true)

        if (selectedIndex in 0 until pane.tabCount) {
            pane.selectedIndex = selectedIndex
        }
    }

    private fun setupHeader() {
        val headerPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.border = EmptyBorder(JBUI.insetsBottom(16))
        
        val titleLabel = JBLabel(I18n.t("settings.title"))
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        titleLabel.foreground = UIUtil.getLabelForeground()
        
        val descLabel = JBLabel(I18n.t("settings.desc"))
        descLabel.font = descLabel.font.deriveFont(12f)
        descLabel.foreground = UIUtil.getLabelDisabledForeground()
        
        val titlePanel = JBPanel<JBPanel<*>>(BorderLayout())
        titlePanel.add(titleLabel, BorderLayout.NORTH)
        titlePanel.add(descLabel, BorderLayout.CENTER)
        
        headerPanel.add(titlePanel)
        add(headerPanel, BorderLayout.NORTH)
    }

    private fun setupHeaderTexts() {
        // 重新设置顶部标题与描述（简单方式：重建头部）
        val northComp = (layout as? BorderLayout)?.getLayoutComponent(BorderLayout.NORTH)
        if (northComp != null) {
            remove(northComp)
        }
        setupHeader()
        revalidate()
        repaint()
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
            if (hasModelChanges) savedItems.add(I18n.t("settings.save.item.models"))
            if (hasPromptChanges) savedItems.add(I18n.t("settings.save.item.prompts"))
            if (hasCommitChanges) savedItems.add(I18n.t("settings.save.item.commit"))
            if (hasSystemChanges) savedItems.add(I18n.t("settings.save.item.system"))

            val separator = I18n.t("settings.save.separator")
            val itemsText = savedItems.joinToString(separator)

            Messages.showInfoMessage(
                project,
                I18n.t("settings.save.success.message", itemsText),
                I18n.t("settings.save.success.title")
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                I18n.t("settings.save.error.message", e.message ?: ""),
                I18n.t("settings.save.error.title")
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
                    I18n.t("settings.load.error.message", e.message ?: ""),
                    I18n.t("settings.load.error.title")
                )
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun dispose() {
        // 调用SystemManagementPanel的dispose方法清理语言监听器
        systemManagementPanel.dispose()
    }
}