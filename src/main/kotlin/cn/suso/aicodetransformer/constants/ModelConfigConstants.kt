package cn.suso.aicodetransformer.constants

/**
 * 模型配置相关常量定义
 * 统一管理所有模型配置的默认参数和常量值
 */
object ModelConfigConstants {
    
    /**
     * AI服务提供商配置枚举
     * 提供各个服务提供商的基本配置信息
     */
    enum class AIProviderConfig(
        val typeDisplayName: String,
        val apiBaseUrl: String,
        val supportedModels: List<String>,
        val defaultMaxTokens: Int
    ) {
        OPENAI(
            typeDisplayName = "OpenAI兼容",
            apiBaseUrl = "https://api.openai.com/v1",
            supportedModels = listOf(
                "gpt-4", "gpt-4-turbo-preview", "gpt-4-0125-preview",
                "gpt-3.5-turbo", "gpt-3.5-turbo-0125"
            ),
            defaultMaxTokens = 8000
        ),
        
        CLAUDE(
            typeDisplayName = "Claude",
            apiBaseUrl = "https://api.anthropic.com",
            supportedModels = listOf(
                "claude-3-opus-20240229", "claude-3-sonnet-20240229", 
                "claude-3-haiku-20240307", "claude-2.1", "claude-2.0"
            ),
            defaultMaxTokens = 100000
        ),
        
        LOCAL(
            typeDisplayName = "本地模型",
            apiBaseUrl = "http://localhost:11434",
            supportedModels = listOf(
                "local-model", "llama2", "codellama", "mistral", "custom"
            ),
            defaultMaxTokens = 2048
        );
        
        companion object {
            /**
             * 根据模型类型查找配置
             */
            fun findByModelType(modelType: String): AIProviderConfig? {
                return when (modelType.lowercase()) {
                    "openai_compatible", "openai" -> OPENAI
                    "claude" -> CLAUDE
                    "local" -> LOCAL
                    else -> null
                }
            }
            
            /**
             * 获取所有支持的模型
             */
            fun getAllSupportedModels(): List<String> {
                return values().flatMap { it.supportedModels }
            }
        }
    }
    
    /**
     * 默认参数值
     */
    object DefaultParameters {
        const val TEMPERATURE = 0.2
        const val MAX_TOKENS_OPENAI = 8000
        const val MAX_TOKENS_CLAUDE = 100000
        const val MAX_TOKENS_LOCAL = 2048
        const val CONNECT_TIMEOUT_SECONDS = 30
        const val READ_TIMEOUT_SECONDS = 120
        const val RETRY_COUNT = 2
        const val STREAM_RESPONSE = false
        const val ENABLED = true
        const val VERSION = "1.0"
    }
    
    /**
     * 验证规则常量
     */
    object ValidationRules {
        const val MIN_NAME_LENGTH = 1
        const val MAX_NAME_LENGTH = 100
        const val MIN_DESCRIPTION_LENGTH = 0
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MIN_TEMPERATURE = 0.0
        const val MAX_TEMPERATURE = 2.0
        const val MIN_MAX_TOKENS = 1
        const val MAX_MAX_TOKENS = 200000
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 600
        const val MIN_RETRY_COUNT = 0
        const val MAX_RETRY_COUNT = 10
    }
    
    /**
     * 错误消息常量
     */
    object ErrorMessages {
        const val ID_EMPTY = "配置ID不能为空"
        const val NAME_EMPTY = "模型名称不能为空"
        const val API_BASE_URL_EMPTY = "API基础URL不能为空"
        const val API_BASE_URL_INVALID = "API基础URL格式不正确"
        const val MODEL_NAME_EMPTY = "模型名称不能为空"
        const val API_KEY_EMPTY = "API密钥不能为空"
        const val INVALID_URL_FORMAT = "URL格式不正确"
        const val TEMPERATURE_OUT_OF_RANGE = "温度参数必须在0.0-2.0之间"
        const val MAX_TOKENS_OUT_OF_RANGE = "最大Token数必须大于0"
        const val MAX_TOKENS_INVALID = "最大Token数必须大于0"
        const val TIMEOUT_OUT_OF_RANGE = "超时时间必须大于0"
        const val RETRY_COUNT_OUT_OF_RANGE = "重试次数必须大于等于0"
    }
    

}