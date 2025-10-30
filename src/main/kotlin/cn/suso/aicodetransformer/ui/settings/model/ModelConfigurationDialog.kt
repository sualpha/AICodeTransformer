package cn.suso.aicodetransformer.ui.settings.model

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.ModelType

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.*
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.util.*
import javax.swing.*

/**
 * 模型配置编辑对话框
 */
class ModelConfigurationDialog(
    private val project: Project,
    private val existingConfig: ModelConfiguration?
) : DialogWrapper(project) {
    
    // 基本信息字段
    private val idField = JBTextField()
    private val nameField = JBTextField()
    private val descriptionField = JBTextField()
    private val typeComboBox = ComboBox(ModelType.values())
    private val enabledCheckBox = JBCheckBox("启用此配置", true)
    
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
    private val timeoutSpinner = JSpinner(SpinnerNumberModel(30, 1, 300, 5))
    private val retryAttemptsSpinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1))
    
    init {
        title = if (existingConfig != null) "编辑模型配置" else "添加模型配置"
        init()
        loadConfiguration()
        setupListeners()
    }
    
    override fun createCenterPanel(): JComponent {
        val formBuilder = FormBuilder.createFormBuilder()
        
        // 基本信息部分
        formBuilder.addLabeledComponent(JLabel("配置ID:"), idField)
        formBuilder.addLabeledComponent(JLabel("配置名称:"), nameField)
        formBuilder.addLabeledComponent(JLabel("描述:"), descriptionField)
        formBuilder.addLabeledComponent(JLabel("模型类型:"), typeComboBox)
        formBuilder.addComponent(enabledCheckBox)
        
        formBuilder.addComponent(com.intellij.ui.TitledSeparator("API配置"))
        formBuilder.addLabeledComponent(JLabel("API基础URL:"), apiBaseUrlField)
        formBuilder.addLabeledComponent(JLabel("模型名称:"), modelNameField)
        
        // 保存API key相关组件的引用
        apiKeyLabel = JLabel("API密钥:")
        apiKeyComponent = apiKeyField
        formBuilder.addLabeledComponent(apiKeyLabel, apiKeyComponent)
        
        formBuilder.addComponent(com.intellij.ui.TitledSeparator("参数配置"))
        formBuilder.addLabeledComponent(JLabel("温度 (Temperature):"), temperatureSpinner)
        formBuilder.addLabeledComponent(JLabel("最大Token数:"), maxTokensSpinner)
        
        formBuilder.addComponent(com.intellij.ui.TitledSeparator("连接配置"))
        formBuilder.addLabeledComponent(JLabel("超时时间(秒):"), timeoutSpinner)
        formBuilder.addLabeledComponent(JLabel("重试次数:"), retryAttemptsSpinner)
        
        val panel = formBuilder.getPanel()
        panel.border = JBUI.Borders.empty(10)
        panel.preferredSize = Dimension(500, 600)
        
        // 设置字段提示
        setupFieldTooltips()
        
        return JBScrollPane(panel)
    }
    
    private fun setupFieldTooltips() {
        idField.toolTipText = "配置的唯一标识符，不能重复"
        nameField.toolTipText = "配置的显示名称"
        descriptionField.toolTipText = "配置的详细描述"
        apiBaseUrlField.toolTipText = "API的基础URL，例如：https://api.openai.com/v1"
        modelNameField.toolTipText = "要使用的模型名称，例如：gpt-3.5-turbo"
        apiKeyField.toolTipText = "访问AI服务的API密钥，将安全存储（本地模型无需填写）"
        temperatureSpinner.toolTipText = "控制输出的随机性，0-2之间，值越高越随机"
        maxTokensSpinner.toolTipText = "生成文本的最大token数量"
    }
    
    private fun setupListeners() {
        // 模型类型变更时自动填充默认值并控制API key字段显示
        typeComboBox.addActionListener {
            val selectedType = typeComboBox.selectedItem as ModelType
            if (existingConfig == null) { // 只在新建时自动填充
                fillDefaultValues(selectedType)
            }
            // 根据模型类型控制API key字段的显示
            updateApiKeyFieldVisibility(selectedType)
        }
        
        // 如果是编辑模式，禁用ID字段
        if (existingConfig != null) {
            idField.isEnabled = false
        }
        
        // 初始化时也要设置API key字段的显示状态
        val initialType = typeComboBox.selectedItem as ModelType
        updateApiKeyFieldVisibility(initialType)
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
    
    private fun fillDefaultValues(type: ModelType) {
        when (type) {
            ModelType.OPENAI_COMPATIBLE -> {
                apiBaseUrlField.text = "https://api.openai.com/v1"
                modelNameField.text = "gpt-3.5-turbo"
                temperatureSpinner.value = 0.2
                maxTokensSpinner.value = 8000
            }
            ModelType.CLAUDE -> {
                apiBaseUrlField.text = "https://api.anthropic.com"
                modelNameField.text = "claude-3-sonnet-20240229"
                temperatureSpinner.value = 0.2
                maxTokensSpinner.value = 8000
            }
            ModelType.LOCAL -> {
                apiBaseUrlField.text = "http://localhost:11434/v1"
                modelNameField.text = "llama2"
                temperatureSpinner.value = 0.2
                maxTokensSpinner.value = 8000
            }
        }
    }
    
    private fun loadConfiguration() {
        if (existingConfig != null) {
            idField.text = existingConfig.id
            nameField.text = existingConfig.name
            descriptionField.text = existingConfig.description
            typeComboBox.selectedItem = existingConfig.modelType
            enabledCheckBox.isSelected = existingConfig.enabled
            
            apiBaseUrlField.text = existingConfig.apiBaseUrl
            modelNameField.text = existingConfig.modelName
            // 直接从配置获取API密钥
            apiKeyField.text = existingConfig.apiKey
            
            temperatureSpinner.value = existingConfig.temperature
            maxTokensSpinner.value = existingConfig.maxTokens
            
            timeoutSpinner.value = existingConfig.connectTimeoutSeconds
            retryAttemptsSpinner.value = existingConfig.retryCount

        } else {
            // 新建配置时生成默认ID
            idField.text = "config_${UUID.randomUUID().toString().substring(0, 8)}"
            // 根据默认类型填充默认值
            fillDefaultValues(typeComboBox.selectedItem as ModelType)
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        val selectedType = typeComboBox.selectedItem as ModelType
        
        // 验证必填字段
        if (idField.text.trim().isEmpty()) {
            return ValidationInfo("配置ID不能为空", idField)
        }
        
        if (nameField.text.trim().isEmpty()) {
            return ValidationInfo("配置名称不能为空", nameField)
        }
        
        if (apiBaseUrlField.text.trim().isEmpty()) {
            return ValidationInfo("API基础URL不能为空", apiBaseUrlField)
        }
        
        if (modelNameField.text.trim().isEmpty()) {
            return ValidationInfo("模型名称不能为空", modelNameField)
        }
        
        // 只有非本地模型才需要验证API密钥
        if (selectedType != ModelType.LOCAL && String(apiKeyField.password).trim().isEmpty()) {
            return ValidationInfo("API密钥不能为空", apiKeyField)
        }
        
        // 验证URL格式
        try {
            java.net.URI(apiBaseUrlField.text.trim()).toURL()
        } catch (e: Exception) {
            return ValidationInfo("API基础URL格式不正确", apiBaseUrlField)
        }

        // 验证API路径
        val apiUrl = apiBaseUrlField.text.trim()
        when (selectedType) {
            ModelType.OPENAI_COMPATIBLE -> {
                if (apiUrl.endsWith("/chat/completions")) {
                    return ValidationInfo("OpenAPI模型的URL不需要包含/chat/completions路径，请移除该部分", apiBaseUrlField)
                }
            }
            ModelType.CLAUDE -> {
                if (apiUrl.endsWith("/messages")) {
                    return ValidationInfo("Claude模型的URL不需要包含/messages路径，请移除该部分", apiBaseUrlField)
                }
            }
            else -> {
                // 其他模型类型不需要特殊验证
            }
        }
        
        return null
    }
    
    override fun doOKAction() {
        // 先进行验证
        val validationInfo = doValidate()
        if (validationInfo != null) {
            return
        }
        
        // API密钥现在直接包含在ModelConfiguration中，
        // 通过getConfiguration()方法获取包含API密钥的完整配置
        super.doOKAction()
    }
    
    /**
     * 获取配置对象
     */
    fun getConfiguration(): ModelConfiguration? {
        if (!isOK) return null
        
        return ModelConfiguration(
            id = idField.text.trim(),
            name = nameField.text.trim(),
            description = descriptionField.text.trim(),
            apiBaseUrl = apiBaseUrlField.text.trim(),
            modelName = modelNameField.text.trim(),
            apiKey = String(apiKeyField.password),
            temperature = temperatureSpinner.value as Double,
            maxTokens = maxTokensSpinner.value as Int,
            enabled = enabledCheckBox.isSelected,
            modelType = typeComboBox.selectedItem as ModelType,
            connectTimeoutSeconds = timeoutSpinner.value as Int,
            readTimeoutSeconds = 120,
            retryCount = retryAttemptsSpinner.value as Int,
            streamResponse = false,
            customHeaders = emptyMap(),
            createdAt = existingConfig?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastUsedAt = existingConfig?.lastUsedAt,
            usageCount = existingConfig?.usageCount ?: 0
        )
    }
}