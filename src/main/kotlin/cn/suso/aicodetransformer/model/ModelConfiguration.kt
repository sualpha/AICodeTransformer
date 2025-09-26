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
data class ModelConfiguration(
    /** 配置ID，唯一标识 */
    @Attribute("id")
    val id: String = "",
    
    /** 模型名称，用于显示 */
    @Attribute("name")
    val name: String = "",
    
    /** 模型描述 */
    @Attribute("description")
    val description: String = "",
    
    /** API基础URL */
    @Attribute("apiBaseUrl")
    val apiBaseUrl: String = "",

    /** 模型名称（如gpt-4, claude-3-sonnet等） */
    @Attribute("modelName")
    val modelName: String = "",
    
    /** 温度参数，控制输出随机性 (0.0-2.0) */
    @Attribute("temperature")
    val temperature: Double = ModelConfigConstants.DefaultParameters.TEMPERATURE,
    
    /** 最大Token数 */
    @Attribute("maxTokens")
    val maxTokens: Int = ModelConfigConstants.DefaultParameters.MAX_TOKENS_OPENAI,
    
    /** 是否启用 */
    @Attribute("enabled")
    val enabled: Boolean = ModelConfigConstants.DefaultParameters.ENABLED,
    
    /** 模型类型 */
    @Attribute("modelType")
    val modelType: ModelType = ModelType.OPENAI_COMPATIBLE,
    
    /** 连接超时时间（秒） */
    @Attribute("connectTimeoutSeconds")
    val connectTimeoutSeconds: Int = ModelConfigConstants.DefaultParameters.CONNECT_TIMEOUT_SECONDS,
    
    /** 读取超时时间（秒） */
    @Attribute("readTimeoutSeconds")
    val readTimeoutSeconds: Int = ModelConfigConstants.DefaultParameters.READ_TIMEOUT_SECONDS,
    
    /** 重试次数 */
    @Attribute("retryCount")
    val retryCount: Int = ModelConfigConstants.DefaultParameters.RETRY_COUNT,
    
    /** 是否启用流式响应 */
    @Attribute("streamResponse")
    val streamResponse: Boolean = ModelConfigConstants.DefaultParameters.STREAM_RESPONSE,
    
    /** 自定义请求头 */
    @kotlinx.serialization.Transient
    val customHeaders: Map<String, String> = emptyMap(),
    
    /** 创建时间 */
    @Attribute("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    
    /** 最后更新时间 */
    @Attribute("updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),
    
    /** 最后使用时间 */
    @Attribute("lastUsedAt")
    val lastUsedAt: Long? = null,
    
    /** 使用次数统计 */
    @Attribute("usageCount")
    val usageCount: Int = 0,
    
    /** API密钥 */
    @Attribute("apiKey")
    val apiKey: String = ""
) {
    /**
     * 验证配置是否有效
     */
    fun validate(): ValidationResult {
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
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
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
        /** 创建默认的OpenAI配置 */
        fun createOpenAIDefault(): ModelConfiguration {
            val config = ModelConfigConstants.AIProviderConfig.OPENAI
            return ModelConfiguration(
                id = config.id,
                name = config.displayName,
                description = config.description,
                apiBaseUrl = config.apiBaseUrl,
                modelName = config.defaultModelName,
                modelType = ModelType.OPENAI_COMPATIBLE,
                maxTokens = config.defaultMaxTokens,
                apiKey = ""
            )
        }
        
        /** 创建默认的Claude配置 */
        fun createClaudeDefault(): ModelConfiguration {
            val config = ModelConfigConstants.AIProviderConfig.CLAUDE
            return ModelConfiguration(
                id = config.id,
                name = config.displayName,
                description = config.description,
                apiBaseUrl = config.apiBaseUrl,
                modelName = config.defaultModelName,
                modelType = ModelType.CLAUDE,
                maxTokens = config.defaultMaxTokens,
                apiKey = ""
            )
        }
        
        /** 创建默认的本地模型配置 */
        fun createLocalDefault(): ModelConfiguration {
            val config = ModelConfigConstants.AIProviderConfig.LOCAL
            return ModelConfiguration(
                id = config.id,
                name = config.displayName,
                description = config.description,
                apiBaseUrl = config.apiBaseUrl,
                modelName = config.defaultModelName,
                modelType = ModelType.LOCAL,
                maxTokens = config.defaultMaxTokens,
                apiKey = ""
            )
        }
        
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
@Serializable
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
@Serializable
sealed class ValidationResult {
    @Serializable
    object Success : ValidationResult()
    @Serializable
    data class Error(val errors: List<String>) : ValidationResult()
    
    val isValid: Boolean
        get() = this is Success
    
    val errorMessage: String?
        get() = when (this) {
            is Success -> null
            is Error -> errors.joinToString("; ")
        }
}