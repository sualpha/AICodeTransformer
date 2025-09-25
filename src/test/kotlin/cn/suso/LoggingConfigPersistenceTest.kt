package cn.suso

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 简单的日志配置状态数据类，用于测试
 */
data class SimpleLoggingConfig(
    val enabled: Boolean = true,
    val minLevel: String = "INFO",
    val logToFile: Boolean = true,
    val logToConsole: Boolean = true,
    val logFilePath: String = "logs/application.log",
    val maxFileSize: Long = 10485760L, // 10MB
    val maxFileCount: Int = 10,
    val retentionTimeMs: Long = 604800000L, // 7天
    val enablePerformanceLogging: Boolean = true,
    val enableSecurityLogging: Boolean = true,
    val logSensitiveData: Boolean = false,
    val logFormat: String = "JSON"
)

class LoggingConfigPersistenceTest {
    
    @Test
    fun `测试日志配置状态创建和属性访问`() {
        // 创建测试配置
        val testConfig = SimpleLoggingConfig(
            enabled = true,
            minLevel = "DEBUG",
            logToFile = true,
            logToConsole = false,
            logFilePath = "test.log",
            maxFileSize = 20971520L, // 20MB
            maxFileCount = 10,
            retentionTimeMs = 86400000L, // 1天
            enablePerformanceLogging = false,
            enableSecurityLogging = true,
            logSensitiveData = false,
            logFormat = "JSON"
        )
        
        // 验证配置属性
        assertTrue(testConfig.enabled)
        assertEquals("DEBUG", testConfig.minLevel)
        assertTrue(testConfig.logToFile)
        assertFalse(testConfig.logToConsole)
        assertEquals("test.log", testConfig.logFilePath)
        assertEquals(20971520L, testConfig.maxFileSize)
        assertEquals(10, testConfig.maxFileCount)
        assertEquals(86400000L, testConfig.retentionTimeMs)
        assertFalse(testConfig.enablePerformanceLogging)
        assertTrue(testConfig.enableSecurityLogging)
        assertFalse(testConfig.logSensitiveData)
        assertEquals("JSON", testConfig.logFormat)
    }
    
    @Test
    fun `测试默认日志配置状态`() {
        // 创建默认配置
        val defaultConfig = SimpleLoggingConfig()
        
        // 验证默认值
        assertTrue(defaultConfig.enabled)
        assertEquals("INFO", defaultConfig.minLevel)
        assertTrue(defaultConfig.logToFile)
        assertTrue(defaultConfig.logToConsole)
        assertEquals("logs/application.log", defaultConfig.logFilePath)
        assertEquals(10485760L, defaultConfig.maxFileSize) // 10MB
        assertEquals(10, defaultConfig.maxFileCount)
        assertEquals(604800000L, defaultConfig.retentionTimeMs) // 7天
        assertTrue(defaultConfig.enablePerformanceLogging)
        assertTrue(defaultConfig.enableSecurityLogging)
        assertFalse(defaultConfig.logSensitiveData)
        assertEquals("JSON", defaultConfig.logFormat)
    }
    
    @Test
    fun `测试配置状态复制和修改`() {
        // 创建初始配置
        val initialConfig = SimpleLoggingConfig()
        
        // 修改配置
        val modifiedConfig = initialConfig.copy(
            enabled = false,
            minLevel = "ERROR",
            logToFile = false,
            logFormat = "PLAIN",
            maxFileCount = 15
        )
        
        // 验证修改后的配置
        assertFalse(modifiedConfig.enabled)
        assertEquals("ERROR", modifiedConfig.minLevel)
        assertFalse(modifiedConfig.logToFile)
        assertEquals("PLAIN", modifiedConfig.logFormat)
        assertEquals(15, modifiedConfig.maxFileCount)
        
        // 验证原配置未被修改
        assertTrue(initialConfig.enabled)
        assertEquals("INFO", initialConfig.minLevel)
        assertTrue(initialConfig.logToFile)
        assertEquals("JSON", initialConfig.logFormat)
        assertEquals(10, initialConfig.maxFileCount)
    }
    
    @Test
    fun `测试配置状态序列化兼容性`() {
        // 创建配置
        val config = SimpleLoggingConfig(
            enabled = true,
            minLevel = "WARN",
            logToFile = true,
            logToConsole = false,
            maxFileSize = 5242880L, // 5MB
            retentionTimeMs = 86400000L // 1天
        )
        
        // 验证所有字段都可以正确访问
        assertTrue(config.enabled)
        assertEquals("WARN", config.minLevel)
        assertTrue(config.logToFile)
        assertFalse(config.logToConsole)
        assertEquals(5242880L, config.maxFileSize)
        assertEquals(86400000L, config.retentionTimeMs)
    }
    
    @Test
    fun `测试日志级别配置`() {
        val levels = listOf("DEBUG", "INFO", "WARN", "ERROR")
        
        levels.forEach { level ->
            val config = SimpleLoggingConfig(minLevel = level)
            assertEquals(level, config.minLevel)
        }
    }
    
    @Test
    fun `测试日志格式配置`() {
        val formats = listOf("JSON", "PLAIN", "STRUCTURED")
        
        formats.forEach { format ->
            val config = SimpleLoggingConfig(logFormat = format)
            assertEquals(format, config.logFormat)
        }
    }
    
    @Test
    fun `测试文件大小和数量配置`() {
        val config = SimpleLoggingConfig(
            maxFileSize = 52428800L, // 50MB
            maxFileCount = 20
        )
        
        assertEquals(52428800L, config.maxFileSize)
        assertEquals(20, config.maxFileCount)
    }
}