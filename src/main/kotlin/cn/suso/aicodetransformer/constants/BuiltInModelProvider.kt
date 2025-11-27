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
            // 从资源文件读取配置
            val properties = java.util.Properties()
            BuiltInModelProvider::class.java.getResourceAsStream("/builtin-models.properties")?.use {
                properties.load(it)
            } ?: return emptyList()
            
            val apiKey = properties.getProperty("builtin.model.api.key")
            // 检查是否包含未替换的占位符或为空
            if (apiKey.isNullOrBlank() || apiKey.contains("\${")) {
                return emptyList()
            }
            
            listOf(
                ModelConfiguration(
                    id = properties.getProperty("builtin.model.id") ?: "default-model",
                    name = properties.getProperty("builtin.model.name") ?: "defaultModel",
                    description = properties.getProperty("builtin.model.description") ?: "Default model configuration",
                    apiBaseUrl = properties.getProperty("builtin.model.api.url") ?: "http://api.example.com/v1",
                    modelName = properties.getProperty("builtin.model.name.value") ?: "gpt-3.5-turbo",
                    temperature = properties.getProperty("builtin.model.temperature")?.toDoubleOrNull() ?: 0.1,
                    maxTokens = properties.getProperty("builtin.model.max.tokens")?.toIntOrNull() ?: 8000,
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
