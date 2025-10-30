package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import cn.suso.aicodetransformer.constants.ModelConfigConstants

/**
 * AI模型配置数据类
 * 用于存储AI模型的连接配置信息
 */
@Serializable
@Tag("ModelConfiguration")
class ModelConfiguration {
    /** 配置ID，唯一标识 */
    @Attribute("id")
    var id: String = ""
        get() = field
        set(value) { field = value }
    
    /** 模型名称，用于显示 */
    @Attribute("name")
    var name: String = ""
        get() = field
        set(value) { field = value }
    
    /** 模型描述 */
    @Attribute("description")
    var description: String = ""
        get() = field
        set(value) { field = value }
    
    /** API基础URL */
    @Attribute("apiBaseUrl")
    var apiBaseUrl: String = ""
        get() = field
        set(value) { field = value }

    /** 模型名称（如gpt-4, claude-3-sonnet等） */
    @Attribute("modelName")
    var modelName: String = ""
        get() = field
        set(value) { field = value }
    
    /** 温度参数，控制输出随机性 (0.0-2.0) */
    @Attribute("temperature")
    var temperature: Double = ModelConfigConstants.DefaultParameters.TEMPERATURE
        get() = field
        set(value) { field = value }
    
    /** 最大Token数 */
    @Attribute("maxTokens")
    var maxTokens: Int = ModelConfigConstants.DefaultParameters.MAX_TOKENS_OPENAI
        get() = field
        set(value) { field = value }
    
    /** 是否启用 */
    @Attribute("enabled")
    var enabled: Boolean = ModelConfigConstants.DefaultParameters.ENABLED
        get() = field
        set(value) { field = value }
    
    /** 模型类型 */
    @Attribute("modelType")
    var modelType: ModelType = ModelType.OPENAI_COMPATIBLE
        get() = field
        set(value) { field = value }
    
    /** 连接超时时间（秒） */
    @Attribute("connectTimeoutSeconds")
    var connectTimeoutSeconds: Int = ModelConfigConstants.DefaultParameters.CONNECT_TIMEOUT_SECONDS
        get() = field
        set(value) { field = value }
    
    /** 读取超时时间（秒） */
    @Attribute("readTimeoutSeconds")
    var readTimeoutSeconds: Int = ModelConfigConstants.DefaultParameters.READ_TIMEOUT_SECONDS
        get() = field
        set(value) { field = value }
    
    /** 重试次数 */
    @Attribute("retryCount")
    var retryCount: Int = ModelConfigConstants.DefaultParameters.RETRY_COUNT
        get() = field
        set(value) { field = value }
    
    /** 是否启用流式响应 */
    @Attribute("streamResponse")
    var streamResponse: Boolean = ModelConfigConstants.DefaultParameters.STREAM_RESPONSE
        get() = field
        set(value) { field = value }
    
    /** 自定义请求头 */
    @Transient
    var customHeaders: Map<String, String> = emptyMap()
        get() = field
        set(value) { field = value }
    
    /** 创建时间 */
    @Attribute("createdAt")
    var createdAt: Long = System.currentTimeMillis()
        get() = field
        set(value) { field = value }
    
    /** 最后更新时间 */
    @Attribute("updatedAt")
    var updatedAt: Long = System.currentTimeMillis()
        get() = field
        set(value) { field = value }
    
    /** 最后使用时间 */
    @Attribute("lastUsedAt")
    var lastUsedAt: Long? = null
        get() = field
        set(value) { field = value }
    
    /** 使用次数统计 */
    @Attribute("usageCount")
    var usageCount: Int = 0
        get() = field
        set(value) { field = value }
    
    /** API密钥 */
    @Attribute("apiKey")
    var apiKey: String = ""
        get() = field
        set(value) { field = value }
    
    // 默认构造函数
    constructor()
    
    // 复制构造函数
    constructor(
        id: String = "",
        name: String = "",
        description: String = "",
        apiBaseUrl: String = "",
        modelName: String = "",
        temperature: Double = ModelConfigConstants.DefaultParameters.TEMPERATURE,
        maxTokens: Int = ModelConfigConstants.DefaultParameters.MAX_TOKENS_OPENAI,
        enabled: Boolean = ModelConfigConstants.DefaultParameters.ENABLED,
        modelType: ModelType = ModelType.OPENAI_COMPATIBLE,
        connectTimeoutSeconds: Int = ModelConfigConstants.DefaultParameters.CONNECT_TIMEOUT_SECONDS,
        readTimeoutSeconds: Int = ModelConfigConstants.DefaultParameters.READ_TIMEOUT_SECONDS,
        retryCount: Int = ModelConfigConstants.DefaultParameters.RETRY_COUNT,
        streamResponse: Boolean = ModelConfigConstants.DefaultParameters.STREAM_RESPONSE,
        customHeaders: Map<String, String> = emptyMap(),
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis(),
        lastUsedAt: Long? = null,
        usageCount: Int = 0,
        apiKey: String = ""
    ) {
        this.id = id
        this.name = name
        this.description = description
        this.apiBaseUrl = apiBaseUrl
        this.modelName = modelName
        this.temperature = temperature
        this.maxTokens = maxTokens
        this.enabled = enabled
        this.modelType = modelType
        this.connectTimeoutSeconds = connectTimeoutSeconds
        this.readTimeoutSeconds = readTimeoutSeconds
        this.retryCount = retryCount
        this.streamResponse = streamResponse
        this.customHeaders = customHeaders
        this.createdAt = createdAt
        this.updatedAt = updatedAt
        this.lastUsedAt = lastUsedAt
        this.usageCount = usageCount
        this.apiKey = apiKey
    }
    
    // 复制方法
    fun copy(
        id: String = this.id,
        name: String = this.name,
        description: String = this.description,
        apiBaseUrl: String = this.apiBaseUrl,
        modelName: String = this.modelName,
        temperature: Double = this.temperature,
        maxTokens: Int = this.maxTokens,
        enabled: Boolean = this.enabled,
        modelType: ModelType = this.modelType,
        connectTimeoutSeconds: Int = this.connectTimeoutSeconds,
        readTimeoutSeconds: Int = this.readTimeoutSeconds,
        retryCount: Int = this.retryCount,
        streamResponse: Boolean = this.streamResponse,
        customHeaders: Map<String, String> = this.customHeaders,
        createdAt: Long = this.createdAt,
        updatedAt: Long = this.updatedAt,
        lastUsedAt: Long? = this.lastUsedAt,
        usageCount: Int = this.usageCount,
        apiKey: String = this.apiKey
    ): ModelConfiguration {
        return ModelConfiguration(
            id, name, description, apiBaseUrl, modelName, temperature, maxTokens,
            enabled, modelType, connectTimeoutSeconds, readTimeoutSeconds, retryCount,
            streamResponse, customHeaders, createdAt, updatedAt, lastUsedAt, usageCount, apiKey
        )
    }
    /**
     * 验证配置是否有效
     */
    fun validate(): ModelValidationResult {
        val errors = mutableListOf<String>()
        
        if (id.isBlank()) {
            errors.add(ModelConfigConstants.ErrorMessages.ID_EMPTY)
        }
        
        if (name.isBlank()) {
            errors.add(ModelConfigConstants.ErrorMessages.NAME_EMPTY)
        }
        
        if (apiBaseUrl.isBlank()) {
            errors.add(ModelConfigConstants.ErrorMessages.API_BASE_URL_EMPTY)
        } else if (!isValidUrl(apiBaseUrl)) {
            errors.add(ModelConfigConstants.ErrorMessages.API_BASE_URL_INVALID)
        }
        
        if (modelName.isBlank()) {
            errors.add(ModelConfigConstants.ErrorMessages.MODEL_NAME_EMPTY)
        }
        
        if (temperature < ModelConfigConstants.ValidationRules.MIN_TEMPERATURE || temperature > ModelConfigConstants.ValidationRules.MAX_TEMPERATURE) {
            errors.add(ModelConfigConstants.ErrorMessages.TEMPERATURE_OUT_OF_RANGE)
        }
        
        if (maxTokens <= ModelConfigConstants.ValidationRules.MIN_MAX_TOKENS) {
            errors.add(ModelConfigConstants.ErrorMessages.MAX_TOKENS_INVALID)
        }
        
        return if (errors.isEmpty()) {
            ModelValidationResult.Success
        } else {
            ModelValidationResult.Error(errors)
        }
    }
    
    /**
     * 创建副本并更新使用统计
     */
    fun withUsageUpdate(): ModelConfiguration {
        return copy(
            lastUsedAt = System.currentTimeMillis(),
            usageCount = usageCount + 1
        )
    }
    
    /**
     * 获取显示名称
     */
    fun getDisplayName(): String {
        return if (description.isNotBlank()) {
            "$name - $description"
        } else {
            name
        }
    }
    
    /**
     * 检查是否为默认配置
     */
    fun isDefault(): Boolean {
        return id.endsWith("-default")
    }
    
    private fun isValidUrl(url: String): Boolean {
        return try {
            val regex = "^https?://[\\w.-]+(:[0-9]+)?(/.*)?$".toRegex()
            regex.matches(url)
        } catch (e: Exception) {
            false
        }
    }
    
    companion object {
        
        /** 创建空的配置用于新建 */
        fun createEmpty(): ModelConfiguration {
            return ModelConfiguration(
                id = "",
                name = "",
                apiBaseUrl = "",
                modelName = "",
                apiKey = ""
            )
        }
    }
}

/**
 * 模型类型枚举
 */
enum class ModelType(val displayName: String, val defaultBaseUrl: String) {
    /** OpenAI兼容API */
    OPENAI_COMPATIBLE(ModelConfigConstants.AIProviderConfig.OPENAI.typeDisplayName, ModelConfigConstants.AIProviderConfig.OPENAI.apiBaseUrl),
    
    /** Claude API */
    CLAUDE(ModelConfigConstants.AIProviderConfig.CLAUDE.typeDisplayName, ModelConfigConstants.AIProviderConfig.CLAUDE.apiBaseUrl),
    
    /** 本地模型 */
    LOCAL(ModelConfigConstants.AIProviderConfig.LOCAL.typeDisplayName, ModelConfigConstants.AIProviderConfig.LOCAL.apiBaseUrl);
    
    /**
     * 获取对应的AI提供商配置
     */
    fun getProviderConfig(): ModelConfigConstants.AIProviderConfig {
        return when (this) {
            OPENAI_COMPATIBLE -> ModelConfigConstants.AIProviderConfig.OPENAI
            CLAUDE -> ModelConfigConstants.AIProviderConfig.CLAUDE
            LOCAL -> ModelConfigConstants.AIProviderConfig.LOCAL
        }
    }
    
    /**
     * 获取支持的模型名称列表
     */
    fun getSupportedModels(): List<String> {
        return getProviderConfig().supportedModels
    }
    
    /**
     * 获取默认的最大Token数
     */
    fun getDefaultMaxTokens(): Int {
        return getProviderConfig().defaultMaxTokens
    }
}

/**
 * 验证结果
 */
sealed class ModelValidationResult {
    object Success : ModelValidationResult()
    data class Error(val errors: List<String>) : ModelValidationResult()
    
    val isValid: Boolean
        get() = this is Success
    
    val errorMessage: String?
        get() = when (this) {
            is Success -> null
            is Error -> errors.joinToString("; ")
        }
}