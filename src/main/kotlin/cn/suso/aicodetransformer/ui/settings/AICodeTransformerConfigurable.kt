package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.service.ConfigurationService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import javax.swing.JComponent

/**
 * AI代码转换器设置页面配置类
 */
class AICodeTransformerConfigurable : Configurable {
    
    constructor() // 无参构造函数用于plugin.xml注册
    constructor(project: Project) { // 保留原有构造函数以兼容现有代码
        this.project = project
    }
    
    private var project: Project? = null
    
    private var settingsPanel: AICodeTransformerSettingsPanel? = null
    private val configurationService = ApplicationManager.getApplication().service<ConfigurationService>()
    
    override fun getDisplayName(): String = "AI Code Transformer"
    
    override fun getHelpTopic(): String? = null
    
    override fun createComponent(): JComponent? {
        return try {
            if (settingsPanel == null) {
                val currentProject = project ?: ProjectManager.getInstance().defaultProject
                settingsPanel = AICodeTransformerSettingsPanel(currentProject, configurationService)
            }
            settingsPanel
        } catch (e: Exception) {
            // 如果创建设置面板失败，记录错误并返回一个简单的错误提示面板
            val errorPanel = javax.swing.JPanel(java.awt.BorderLayout())
            val errorLabel = javax.swing.JLabel(
                "<html><div style='text-align: center; padding: 20px;'>" +
                "<h3>配置界面加载失败</h3>" +
                "<p>错误信息: ${e.message}</p>" +
                "<p>请尝试重启IDE或检查插件配置</p>" +
                "</div></html>"
            )
            errorLabel.horizontalAlignment = javax.swing.SwingConstants.CENTER
            errorPanel.add(errorLabel, java.awt.BorderLayout.CENTER)
            errorPanel
        }
    }
    
    override fun isModified(): Boolean {
        return settingsPanel?.isModified() ?: false
    }
    
    @Throws(ConfigurationException::class)
    override fun apply() {
        // 只有在有修改时才执行保存操作
        if (isModified()) {
            settingsPanel?.apply()
        }
    }
    
    override fun reset() {
        settingsPanel?.reset()
    }
    
    override fun disposeUIResources() {
        settingsPanel = null
    }
}