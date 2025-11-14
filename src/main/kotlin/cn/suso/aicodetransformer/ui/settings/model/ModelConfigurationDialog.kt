package cn.suso.aicodetransformer.ui.settings.model

import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.i18n.LanguageManager
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.ModelType
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.util.*
import javax.swing.*
import javax.swing.SwingUtilities

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
    private val enabledCheckBox = JBCheckBox()

    private val idLabel = JLabel()
    private val nameLabel = JLabel()
    private val descriptionLabel = JLabel()
    private val typeLabel = JLabel()

    // API配置字段
    private val apiBaseUrlField = JBTextField()
    private val modelNameField = JBTextField()
    private val apiKeyField = JBPasswordField()

    // API key相关的UI组件，用于动态显示/隐藏
    private val apiKeyLabel = JLabel()
    private lateinit var apiKeyComponent: JComponent

    // 参数配置字段
    private val temperatureSpinner = JSpinner(SpinnerNumberModel(0.7, 0.0, 2.0, 0.1))
    private val maxTokensSpinner = JSpinner(SpinnerNumberModel(2048, 1, 100000, 100))
    private val temperatureLabel = JLabel()
    private val maxTokensLabel = JLabel()

    // 连接配置字段
    private val timeoutSpinner = JSpinner(SpinnerNumberModel(30, 1, 300, 5))
    private val retryAttemptsSpinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1))
    private val timeoutLabel = JLabel()
    private val retryAttemptsLabel = JLabel()

    private val apiSeparator = com.intellij.ui.TitledSeparator()
    private val paramsSeparator = com.intellij.ui.TitledSeparator()
    private val connectionSeparator = com.intellij.ui.TitledSeparator()
    private val apiBaseUrlLabel = JLabel()
    private val modelNameLabel = JLabel()

    private val languageListener: () -> Unit = {
        SwingUtilities.invokeLater {
            if (!isDisposed) {
                refreshTexts()
            }
        }
    }

    init {
        title = if (existingConfig != null) I18n.t("model.dialog.edit.title") else I18n.t("model.dialog.add.title")
        init()
        loadConfiguration()
        setupListeners()
        refreshTexts()
        LanguageManager.addChangeListener(languageListener)
    }

    override fun createCenterPanel(): JComponent {
        val formBuilder = FormBuilder.createFormBuilder()

        // 基本信息部分
        formBuilder.addLabeledComponent(idLabel, idField)
        formBuilder.addLabeledComponent(nameLabel, nameField)
        formBuilder.addLabeledComponent(descriptionLabel, descriptionField)
        formBuilder.addLabeledComponent(typeLabel, typeComboBox)
        formBuilder.addComponent(enabledCheckBox)

        formBuilder.addComponent(apiSeparator)
        formBuilder.addLabeledComponent(apiBaseUrlLabel, apiBaseUrlField)
        formBuilder.addLabeledComponent(modelNameLabel, modelNameField)

        // 保存API key相关组件的引用
        apiKeyComponent = apiKeyField
        formBuilder.addLabeledComponent(apiKeyLabel, apiKeyComponent)

        formBuilder.addComponent(paramsSeparator)
        formBuilder.addLabeledComponent(temperatureLabel, temperatureSpinner)
        formBuilder.addLabeledComponent(maxTokensLabel, maxTokensSpinner)

        formBuilder.addComponent(connectionSeparator)
        formBuilder.addLabeledComponent(timeoutLabel, timeoutSpinner)
        formBuilder.addLabeledComponent(retryAttemptsLabel, retryAttemptsSpinner)

        val panel = formBuilder.getPanel()
        panel.border = JBUI.Borders.empty(10)
        panel.preferredSize = Dimension(500, 600)

        refreshTexts()

        return JBScrollPane(panel)
    }

    private fun refreshTexts() {
        title = if (existingConfig != null) {
            I18n.t("model.dialog.edit.title")
        } else {
            I18n.t("model.dialog.add.title")
        }

        idLabel.text = I18n.t("model.config.id")
        nameLabel.text = I18n.t("model.config.name")
        descriptionLabel.text = I18n.t("model.config.description")
        typeLabel.text = I18n.t("model.config.type")
        enabledCheckBox.text = I18n.t("model.config.enabled")

        apiSeparator.text = I18n.t("model.api.section")
        apiBaseUrlLabel.text = I18n.t("model.api.baseUrl")
        modelNameLabel.text = I18n.t("model.api.modelName")
        apiKeyLabel.text = I18n.t("model.api.key")

        paramsSeparator.text = I18n.t("model.params.section")
        temperatureLabel.text = I18n.t("model.params.temperature")
        maxTokensLabel.text = I18n.t("model.params.maxTokens")

        connectionSeparator.text = I18n.t("model.params.connection.section")
        timeoutLabel.text = I18n.t("model.params.connectTimeout")
        retryAttemptsLabel.text = I18n.t("model.params.retryAttempts")

        setupFieldTooltips()
    }

    private fun setupFieldTooltips() {
        idField.toolTipText = I18n.t("model.tooltip.id")
        nameField.toolTipText = I18n.t("model.tooltip.name")
        descriptionField.toolTipText = I18n.t("model.tooltip.description")
        apiBaseUrlField.toolTipText = I18n.t("model.tooltip.apiBaseUrl")
        modelNameField.toolTipText = I18n.t("model.tooltip.modelName")
        apiKeyField.toolTipText = I18n.t("model.tooltip.apiKey")
        temperatureSpinner.toolTipText = I18n.t("model.tooltip.temperature")
        maxTokensSpinner.toolTipText = I18n.t("model.tooltip.maxTokens")
        timeoutSpinner.toolTipText = I18n.t("model.tooltip.connectTimeout")
        retryAttemptsSpinner.toolTipText = I18n.t("model.tooltip.retryAttempts")
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
            return ValidationInfo(I18n.t("model.validation.id.required"), idField)
        }

        if (nameField.text.trim().isEmpty()) {
            return ValidationInfo(I18n.t("model.validation.name.required"), nameField)
        }

        if (apiBaseUrlField.text.trim().isEmpty()) {
            return ValidationInfo(I18n.t("model.validation.apiBaseUrl.required"), apiBaseUrlField)
        }

        if (modelNameField.text.trim().isEmpty()) {
            return ValidationInfo(I18n.t("model.validation.modelName.required"), modelNameField)
        }

        // 只有非本地模型才需要验证API密钥
        if (selectedType != ModelType.LOCAL && String(apiKeyField.password).trim().isEmpty()) {
            return ValidationInfo(I18n.t("model.validation.apiKey.required"), apiKeyField)
        }

        // 验证URL格式
        try {
            java.net.URI(apiBaseUrlField.text.trim()).toURL()
        } catch (e: Exception) {
            return ValidationInfo(I18n.t("model.validation.apiBaseUrl.invalid"), apiBaseUrlField)
        }

        // 验证API路径
        val apiUrl = apiBaseUrlField.text.trim()
        when (selectedType) {
            ModelType.OPENAI_COMPATIBLE -> {
                if (apiUrl.endsWith("/chat/completions")) {
                    return ValidationInfo(I18n.t("model.validation.apiUrl.notNeeded.chat"), apiBaseUrlField)
                }
            }
            ModelType.CLAUDE -> {
                if (apiUrl.endsWith("/messages")) {
                    return ValidationInfo(I18n.t("model.validation.apiUrl.notNeeded.messages"), apiBaseUrlField)
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