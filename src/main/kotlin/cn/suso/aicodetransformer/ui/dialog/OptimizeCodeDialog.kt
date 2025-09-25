package cn.suso.aicodetransformer.ui.dialog

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * 代码优化对话框
 */
class OptimizeCodeDialog(private val project: Project, private val selectedCode: String) : DialogWrapper(project) {
    
    private val configurationService = ApplicationManager.getApplication().service<ConfigurationService>()
    private val promptTemplateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
    
    private lateinit var templateComboBox: JComboBox<PromptTemplate>
    private lateinit var modelComboBox: JComboBox<ModelConfiguration>
    private lateinit var codeTextArea: JBTextArea
    private lateinit var previewTextArea: JBTextArea
    
    init {
        title = "AI 代码优化"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 400)
        
        // 创建主面板
        val mainPanel = JPanel(BorderLayout())
        
        // 先创建代码显示面板（初始化previewTextArea）
        val centerPanel = createCodePanel()
        mainPanel.add(centerPanel, BorderLayout.CENTER)
        
        // 然后创建顶部选择面板（会调用updatePreview）
        val topPanel = createSelectionPanel()
        mainPanel.add(topPanel, BorderLayout.NORTH)
        
        panel.add(mainPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createSelectionPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        
        // 模板选择
        val templatePanel = JPanel(BorderLayout())
        templatePanel.add(JBLabel("选择优化模板:"), BorderLayout.WEST)
        
        templateComboBox = JComboBox()
        loadOptimizeTemplates()
        templatePanel.add(templateComboBox, BorderLayout.CENTER)
        
        // 模型选择
        val modelPanel = JPanel(BorderLayout())
        modelPanel.add(JBLabel("选择模型:"), BorderLayout.WEST)
        
        modelComboBox = JComboBox()
        loadModels()
        modelPanel.add(modelComboBox, BorderLayout.CENTER)
        
        panel.add(templatePanel)
        panel.add(Box.createVerticalStrut(10))
        panel.add(modelPanel)
        panel.add(Box.createVerticalStrut(10))
        
        return panel
    }
    
    private fun createCodePanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 代码显示
        panel.add(JBLabel("要优化的代码:"), BorderLayout.NORTH)
        
        codeTextArea = JBTextArea(selectedCode)
        codeTextArea.isEditable = false
        codeTextArea.lineWrap = true
        codeTextArea.wrapStyleWord = true
        panel.add(JBScrollPane(codeTextArea), BorderLayout.CENTER)
        
        // 添加预览区域
        previewTextArea = JBTextArea()
        previewTextArea.isEditable = false
        previewTextArea.lineWrap = true
        previewTextArea.wrapStyleWord = true
        previewTextArea.text = "选择模板后将显示预览..."
        
        val previewScrollPane = JBScrollPane(previewTextArea)
        previewScrollPane.preferredSize = Dimension(600, 100)
        
        val previewPanel = JPanel(BorderLayout())
        previewPanel.add(JBLabel("模板预览:"), BorderLayout.NORTH)
        previewPanel.add(previewScrollPane, BorderLayout.CENTER)
        
        val combinedPanel = JPanel(BorderLayout())
        combinedPanel.add(panel, BorderLayout.NORTH)
        combinedPanel.add(previewPanel, BorderLayout.CENTER)
        
        return combinedPanel
    }
    
    private fun loadOptimizeTemplates() {
        templateComboBox.removeAllItems()
        val allTemplates = promptTemplateService.getTemplates()
        val templates = allTemplates.filter { 
            it.enabled && (it.category == "代码优化" || it.category == "优化" || it.category == "默认")
        }
        
        for (template in templates) {
            templateComboBox.addItem(template)
        }
        
        if (templates.isNotEmpty()) {
            templateComboBox.selectedIndex = 0
        }
        
        // 添加模板选择监听器
        templateComboBox.addActionListener {
            updatePreview()
        }
    }
    
    private fun loadModels() {
        modelComboBox.removeAllItems()
        val models = configurationService.getModelConfigurations()
        models.forEach { model ->
            modelComboBox.addItem(model)
        }
        
        if (models.isNotEmpty()) {
            modelComboBox.selectedIndex = 0
        }
    }
    
    fun getSelectedTemplate(): PromptTemplate? {
        return templateComboBox.selectedItem as? PromptTemplate
    }
    
    fun getSelectedModel(): ModelConfiguration? {
        return modelComboBox.selectedItem as? ModelConfiguration
    }
    
    private fun updatePreview() {
        val selectedTemplate = getSelectedTemplate()
        if (selectedTemplate != null) {
            val preview = selectedTemplate.content.replace("{code}", selectedCode)
            previewTextArea.text = preview
        } else {
            previewTextArea.text = "选择模板后将显示预览..."
        }
    }
    
    override fun getOKAction(): Action {
        val action = super.getOKAction()
        action.putValue(Action.NAME, "优化")
        return action
    }
}