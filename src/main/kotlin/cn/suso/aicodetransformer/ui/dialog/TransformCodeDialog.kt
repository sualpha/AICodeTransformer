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
 * 代码转换对话框
 */
class TransformCodeDialog(private val project: Project, private val selectedCode: String) : DialogWrapper(project) {
    
    private val configurationService = ApplicationManager.getApplication().service<ConfigurationService>()
    private val promptTemplateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
    
    private lateinit var templateComboBox: JComboBox<PromptTemplate>
    private lateinit var modelComboBox: JComboBox<ModelConfiguration>
    private lateinit var codeTextArea: JBTextArea
    private lateinit var previewTextArea: JBTextArea
    
    init {
        title = "AI 代码转换"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(800, 600)
        
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
        templatePanel.add(JBLabel("选择模板:"), BorderLayout.WEST)
        
        templateComboBox = JComboBox()
        loadTemplates()
        templateComboBox.addActionListener {
            updatePreview()
        }
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
        
        // 创建分割面板
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        
        // 左侧：原始代码
        val leftPanel = JPanel(BorderLayout())
        leftPanel.add(JBLabel("原始代码:"), BorderLayout.NORTH)
        
        codeTextArea = JBTextArea(selectedCode)
        codeTextArea.isEditable = false
        codeTextArea.lineWrap = true
        codeTextArea.wrapStyleWord = true
        leftPanel.add(JBScrollPane(codeTextArea), BorderLayout.CENTER)
        
        // 右侧：预览
        val rightPanel = JPanel(BorderLayout())
        rightPanel.add(JBLabel("提示预览:"), BorderLayout.NORTH)
        
        previewTextArea = JBTextArea()
        previewTextArea.isEditable = false
        previewTextArea.lineWrap = true
        previewTextArea.wrapStyleWord = true
        rightPanel.add(JBScrollPane(previewTextArea), BorderLayout.CENTER)
        
        splitPane.leftComponent = leftPanel
        splitPane.rightComponent = rightPanel
        splitPane.resizeWeight = 0.5
        
        panel.add(splitPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun loadTemplates() {
        templateComboBox.removeAllItems()
        val allTemplates = promptTemplateService.getTemplates()
        val templates = allTemplates.filter { 
            it.enabled && (it.category == "代码转换" || it.category == "转换" || it.category == "默认")
        }
        
        for (template in templates) {
            templateComboBox.addItem(template)
        }
        
        if (templates.isNotEmpty()) {
            templateComboBox.selectedIndex = 0
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
    
    private fun updatePreview() {
        val selectedTemplate = templateComboBox.selectedItem as? PromptTemplate
        if (selectedTemplate != null) {
            val preview = selectedTemplate.content.replace("{{code}}", selectedCode)
            previewTextArea.text = preview
        }
    }
    
    fun getSelectedTemplate(): PromptTemplate? {
        return templateComboBox.selectedItem as? PromptTemplate
    }
    
    fun getSelectedModel(): ModelConfiguration? {
        return modelComboBox.selectedItem as? ModelConfiguration
    }
    
    override fun getOKAction(): Action {
        val action = super.getOKAction()
        action.putValue(Action.NAME, "转换")
        return action
    }
}