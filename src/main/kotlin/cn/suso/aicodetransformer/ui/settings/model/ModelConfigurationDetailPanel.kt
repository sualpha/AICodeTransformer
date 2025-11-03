package cn.suso.aicodetransformer.ui.settings.model

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.ModelType

import cn.suso.aicodetransformer.ui.components.TooltipHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 模型配置详情面板
 */
class ModelConfigurationDetailPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    // 基本信息字段
    private val nameField = JBTextField()
    private val descriptionField = JBTextField()
    private val typeComboBox = ComboBox(ModelType.values())
    private val enabledCheckBox = JBCheckBox("启用此配置")
    
    // API配置字段
    private val apiBaseUrlField = JBTextField()
    private val modelNameField = JBTextField()
    private val apiKeyField = JBPasswordField()
    
    // API key相关的UI组件，用于动态显示/隐藏
    private lateinit var apiKeyLabel: JLabel
    private lateinit var apiKeyComponent: JComponent
    
    // 参数配置字段
    private val temperatureSpinner = JSpinner(SpinnerNumberModel(0.7, 0.0, 2.0, 0.1))
    private val maxTokensSpinner = JSpinner(SpinnerNumberModel(2048, 1, 100000, 100))
    
    // 连接配置字段
    private val connectTimeoutSpinner = JSpinner(SpinnerNumberModel(30, 1, 300, 5))
    private val readTimeoutSpinner = JSpinner(SpinnerNumberModel(120, 1, 600, 10))
    private val retryAttemptsSpinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1))
    

    
    private val changeListeners = mutableListOf<ChangeListener>()
    private var currentConfiguration: ModelConfiguration? = null
    private var isUpdating = false
    
    init {
        setupUI()
        setupListeners()
        setConfiguration(null)
    }
    
    private fun setupUI() {
        val formBuilder = FormBuilder.createFormBuilder()
        
        // 基本信息部分
        formBuilder.addLabeledComponent(JLabel("配置名称:"), nameField)
        formBuilder.addLabeledComponent(JLabel("描述:"), descriptionField)
        formBuilder.addLabeledComponent(JLabel("模型类型:"), typeComboBox)
        formBuilder.addComponent(enabledCheckBox)
        
        formBuilder.addComponent(com.intellij.ui.TitledSeparator("API配置"))
        formBuilder.addLabeledComponent(JLabel("API基础URL:"), apiBaseUrlField)
        formBuilder.addLabeledComponent(JLabel("模型名称:"), modelNameField)
        
        // API密钥字段不在界面上显示
        apiKeyLabel = JLabel("API密钥:")
        apiKeyComponent = apiKeyField
        
        formBuilder.addComponent(com.intellij.ui.TitledSeparator("参数配置"))
        formBuilder.addLabeledComponent(JLabel("温度 (Temperature):"), temperatureSpinner)
        formBuilder.addLabeledComponent(JLabel("最大Token数:"), maxTokensSpinner)
        
        // 详情页不显示连接配置部分
        
        // 设置字段提示信息
        setupFieldTooltips()
        
        val panel = formBuilder.panel
        panel.border = JBUI.Borders.empty(10)
        
        add(panel, BorderLayout.NORTH)
    }
    
    private fun setupFieldTooltips() {
        // 基本信息字段提示
        TooltipHelper.setTooltip(nameField, "配置的显示名称，用于在列表中识别此模型配置")
        TooltipHelper.setTooltip(descriptionField, "配置的详细描述，说明此模型的用途和特点")
        TooltipHelper.setTooltip(typeComboBox, "选择AI服务提供商类型，不同类型有不同的默认配置")
        TooltipHelper.setTooltip(enabledCheckBox, "是否启用此配置，禁用后将不会在模型列表中显示")
        
        // API配置字段提示
        TooltipHelper.setTooltip(apiBaseUrlField, TooltipHelper.CommonTooltips.ENDPOINT)
        TooltipHelper.setTooltip(modelNameField, TooltipHelper.CommonTooltips.MODEL_ID)
        TooltipHelper.setTooltip(apiKeyField, TooltipHelper.CommonTooltips.API_KEY)
        
        // 参数配置字段提示
        TooltipHelper.setTooltip(temperatureSpinner, TooltipHelper.CommonTooltips.TEMPERATURE)
        TooltipHelper.setTooltip(maxTokensSpinner, TooltipHelper.CommonTooltips.MAX_TOKENS)
        
        // 连接配置字段提示
        TooltipHelper.setTooltip(connectTimeoutSpinner, "建立连接的超时时间（秒），建议设置为10-60秒")
        TooltipHelper.setTooltip(readTimeoutSpinner, "读取响应的超时时间（秒），建议设置为60-300秒")
        TooltipHelper.setTooltip(retryAttemptsSpinner, "连接失败时的重试次数，建议设置为1-5次")
        

    }
    
    private fun setupListeners() {
        val documentListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = notifyChange()
            override fun removeUpdate(e: DocumentEvent?) = notifyChange()
            override fun changedUpdate(e: DocumentEvent?) = notifyChange()
        }
        
        val actionListener = ActionListener { notifyChange() }
        val changeListener = javax.swing.event.ChangeListener { notifyChange() }
        
        // 添加监听器
        nameField.document.addDocumentListener(documentListener)
        descriptionField.document.addDocumentListener(documentListener)
        apiBaseUrlField.document.addDocumentListener(documentListener)
        modelNameField.document.addDocumentListener(documentListener)
        apiKeyField.document.addDocumentListener(documentListener)
        
        typeComboBox.addActionListener { 
            val selectedType = typeComboBox.selectedItem as ModelType
            updateApiKeyFieldVisibility(selectedType)
            notifyChange()
        }
        enabledCheckBox.addActionListener(actionListener)
        temperatureSpinner.addChangeListener(changeListener)
        maxTokensSpinner.addChangeListener(changeListener)
        connectTimeoutSpinner.addChangeListener(changeListener)
        readTimeoutSpinner.addChangeListener(changeListener)
        retryAttemptsSpinner.addChangeListener(changeListener)
    }
    
    private fun notifyChange() {
        if (!isUpdating) {
            changeListeners.forEach { it.stateChanged(null) }
        }
    }
    
    private fun updateApiKeyFieldVisibility(modelType: ModelType) {
        val isVisible = modelType != ModelType.LOCAL
        apiKeyLabel.isVisible = isVisible
        apiKeyComponent.isVisible = isVisible
        
        // 如果是本地模型，清空API key字段
        if (modelType == ModelType.LOCAL) {
            apiKeyField.text = ""
        }
    }
    
    /**
     * 设置要显示的配置
     */
    fun setConfiguration(config: ModelConfiguration?) {
        isUpdating = true
        currentConfiguration = config
        
        if (config != null) {
            nameField.text = config.name
            descriptionField.text = config.description
            typeComboBox.selectedItem = config.modelType
            enabledCheckBox.isSelected = config.enabled
            
            apiBaseUrlField.text = config.apiBaseUrl
            modelNameField.text = config.modelName
            
            // 直接从配置获取API密钥
            apiKeyField.text = config.apiKey
            
            temperatureSpinner.value = config.temperature
            maxTokensSpinner.value = config.maxTokens
            
            connectTimeoutSpinner.value = config.connectTimeoutSeconds
            readTimeoutSpinner.value = config.readTimeoutSeconds
            retryAttemptsSpinner.value = config.retryCount
            
            // 详情面板只读模式 - 只有启用状态可以修改
            setFieldsReadOnly()
            
            // 根据模型类型控制API key字段显示
            updateApiKeyFieldVisibility(config.modelType)
        } else {
            clearFields()
            setFieldsDisabled()
            
            // 默认显示API key字段
            updateApiKeyFieldVisibility(ModelType.OPENAI_COMPATIBLE)
        }
        
        isUpdating = false
    }
    
    private fun clearFields() {
        nameField.text = ""
        descriptionField.text = ""
        typeComboBox.selectedIndex = 0
        enabledCheckBox.isSelected = true
        
        apiBaseUrlField.text = ""
        modelNameField.text = ""
        apiKeyField.text = ""
        
        temperatureSpinner.value = 0.7
        maxTokensSpinner.value = 2048
        
        connectTimeoutSpinner.value = 30
        readTimeoutSpinner.value = 120
        retryAttemptsSpinner.value = 3
    }
    
    private fun setFieldsReadOnly() {
        // 设置所有字段为只读，只有启用状态可以修改
        nameField.isEditable = false
        descriptionField.isEditable = false
        typeComboBox.isEnabled = false
        
        apiBaseUrlField.isEditable = false
        modelNameField.isEditable = false
        apiKeyField.isEditable = false
        
        temperatureSpinner.isEnabled = false
        maxTokensSpinner.isEnabled = false
        
        // 连接配置字段在详情页不显示，无需设置
        
        // 只有启用状态可以修改
        enabledCheckBox.isEnabled = true
    }
    
    private fun setFieldsDisabled() {
        nameField.isEditable = false
        descriptionField.isEditable = false
        typeComboBox.isEnabled = false
        enabledCheckBox.isEnabled = false
        
        apiBaseUrlField.isEditable = false
        modelNameField.isEditable = false
        apiKeyField.isEditable = false
        
        temperatureSpinner.isEnabled = false
        maxTokensSpinner.isEnabled = false
        
        // 连接配置字段在详情页不显示，无需设置
    }
    
    /**
     * 获取当前配置
     */
    fun getConfiguration(): ModelConfiguration? {
        if (currentConfiguration == null) return null
        

        
        return currentConfiguration!!.copy(
            name = nameField.text.trim(),
            description = descriptionField.text.trim(),
            modelType = typeComboBox.selectedItem as ModelType,
            enabled = enabledCheckBox.isSelected,
            apiBaseUrl = apiBaseUrlField.text.trim(),
            modelName = modelNameField.text.trim(),
            apiKey = String(apiKeyField.password),
            temperature = temperatureSpinner.value as Double,
            maxTokens = maxTokensSpinner.value as Int,
            connectTimeoutSeconds = connectTimeoutSpinner.value as Int,
            readTimeoutSeconds = readTimeoutSpinner.value as Int,
            retryCount = retryAttemptsSpinner.value as Int
        )
    }
    
    /**
     * 保存API密钥（现在API密钥直接存储在配置中，此方法保持兼容性）
     */
    fun saveApiKey(): Boolean {
        // API密钥现在直接存储在ModelConfiguration中，
        // 通过getConfiguration()方法获取包含API密钥的完整配置
        return true
    }
    
    /**
     * 添加变更监听器
     */
    fun addChangeListener(listener: ChangeListener) {
        changeListeners.add(listener)
    }
    
    /**
     * 移除变更监听器
     */
    fun removeChangeListener(listener: ChangeListener) {
        changeListeners.remove(listener)
    }
}