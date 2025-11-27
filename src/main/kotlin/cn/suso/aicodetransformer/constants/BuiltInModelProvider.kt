package cn.suso.aicodetransformer.constants

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.ModelType
import cn.suso.aicodetransformer.security.BuiltInModelEncryption

/**
 * 内置模型提供者
 * 定义插件预配置的内置模型
 * 
 * 配置通过 Gradle 从 .env 文件注入为系统属性
 * 详见 build.gradle.kts 中的 runIde 任务配置
 */
object BuiltInModelProvider {
    
    /**
     * 获取所有内置模型配置
     * @return 内置模型配置列表
     * 
     * 从系统属性读取配置(由 Gradle 注入)
     */
    fun getBuiltInModels(): List<ModelConfiguration> {
        // 验证加密服务是否正常工作
        if (!BuiltInModelEncryption.verify()) {
            throw IllegalStateException("加密服务验证失败,无法初始化内置模型")
        }
        
        return try {
            // 从系统属性读取配置
            val apiKey = System.getProperty("BUILTIN_MODEL_API_KEY") ?: return emptyList()
            if (apiKey.isBlank() || apiKey == "your-encrypted-api-key-here") {
                return emptyList()
            }
            
            listOf(
                ModelConfiguration(
                    id = System.getProperty("BUILTIN_MODEL_ID") ?: "default-model",
                    name = System.getProperty("BUILTIN_MODEL_NAME") ?: "defaultModel",
                    description = System.getProperty("BUILTIN_MODEL_DESCRIPTION") ?: "Default model configuration",
                    apiBaseUrl = System.getProperty("BUILTIN_MODEL_API_URL") ?: "http://api.example.com/v1",
                    modelName = System.getProperty("BUILTIN_MODEL_NAME_VALUE") ?: "gpt-3.5-turbo",
                    temperature = System.getProperty("BUILTIN_MODEL_TEMPERATURE")?.toDoubleOrNull() ?: 0.1,
                    maxTokens = System.getProperty("BUILTIN_MODEL_MAX_TOKENS")?.toIntOrNull() ?: 8000,
                    enabled = true,
                    modelType = ModelType.OPENAI_COMPATIBLE,
                    connectTimeoutSeconds = 30,
                    readTimeoutSeconds = 120,
                    retryCount = 2,
                    streamResponse = false,
                    customHeaders = emptyMap(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    lastUsedAt = null,
                    usageCount = 0,
                    apiKey = apiKey,
                    isBuiltIn = true
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 检查指定ID是否为内置模型
     * @param id 模型配置ID
     * @return 如果是内置模型返回true
     */
    fun isBuiltInModel(id: String): Boolean {
        return getBuiltInModels().any { it.id == id }
    }
    
    /**
     * 根据ID获取内置模型
     * @param id 模型配置ID
     * @return 内置模型配置,如果不存在返回null
     */
    fun getBuiltInModel(id: String): ModelConfiguration? {
        return getBuiltInModels().find { it.id == id }
    }
    
    /**
     * 获取内置模型数量
     * @return 内置模型数量
     */
    fun getBuiltInModelCount(): Int {
        return getBuiltInModels().size
    }
}
