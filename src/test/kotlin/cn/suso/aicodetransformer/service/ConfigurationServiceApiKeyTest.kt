package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.service.impl.ConfigurationServiceImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.Assert.*

/**
 * API密钥保存功能测试（基于ModelConfiguration存储）
 */
class ConfigurationServiceApiKeyTest : BasePlatformTestCase() {
    
    private lateinit var configurationService: ConfigurationServiceImpl
    
    override fun setUp() {
        super.setUp()
        configurationService = ConfigurationServiceImpl()
    }
    
    @Test
    fun testSaveAndGetApiKeyWithConfiguration() {
        val configId = "test-config-id"
        val apiKey = "test-api-key-12345"
        
        // 先创建一个配置
        val config = ModelConfiguration.createOpenAIDefault().copy(
            id = configId,
            name = "Test Config",
            apiKey = ""
        )
        configurationService.saveModelConfiguration(config)
        
        // 保存API密钥
        configurationService.saveApiKey(configId, apiKey)
        
        // 获取API密钥
        val retrievedApiKey = configurationService.getApiKey(configId)
        
        // 验证
        assertEquals("API密钥应该被正确保存和获取", apiKey, retrievedApiKey)
    }
    
    @Test
    fun testDeleteApiKeyWithConfiguration() {
        val configId = "test-config-id-delete"
        val apiKey = "test-api-key-to-delete"
        
        // 先创建一个配置
        val config = ModelConfiguration.createOpenAIDefault().copy(
            id = configId,
            name = "Test Config for Delete",
            apiKey = ""
        )
        configurationService.saveModelConfiguration(config)
        
        // 保存API密钥
        configurationService.saveApiKey(configId, apiKey)
        assertNotNull("API密钥应该存在", configurationService.getApiKey(configId))
        
        // 删除API密钥
        configurationService.deleteApiKey(configId)
        
        // 验证已删除（返回null因为apiKey为空字符串）
        assertNull("API密钥应该被删除", configurationService.getApiKey(configId))
    }
    
    @Test
    fun testGetNonExistentApiKey() {
        val configId = "non-existent-config"
        
        // 获取不存在的API密钥
        val apiKey = configurationService.getApiKey(configId)
        
        // 验证返回null
        assertNull("不存在的API密钥应该返回null", apiKey)
    }
    
    @Test
    fun testModelConfigurationWithApiKey() {
        val configId = "test-config-with-apikey"
        val apiKey = "test-api-key-in-config"
        
        // 创建包含API密钥的配置
        val config = ModelConfiguration.createOpenAIDefault().copy(
            id = configId,
            name = "Test Config with API Key",
            apiKey = apiKey
        )
        configurationService.saveModelConfiguration(config)
        
        // 获取API密钥
        val retrievedApiKey = configurationService.getApiKey(configId)
        
        // 验证
        assertEquals("配置中的API密钥应该被正确获取", apiKey, retrievedApiKey)
        
        // 验证配置本身也包含API密钥
        val retrievedConfig = configurationService.getModelConfiguration(configId)
        assertNotNull("配置应该存在", retrievedConfig)
        assertEquals("配置中的API密钥应该正确", apiKey, retrievedConfig?.apiKey)
    }
}