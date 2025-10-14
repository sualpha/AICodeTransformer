package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

/**
 * 持久化状态数据类
 */
@Serializable
@com.intellij.util.xmlb.annotations.Tag("State")
data class ConfigurationState(
    var modelConfigurations: MutableList<ModelConfiguration> = mutableListOf(),
    @com.intellij.util.xmlb.annotations.Attribute("defaultModelConfigId")
    var defaultModelConfigId: String? = null,
    @com.intellij.util.xmlb.annotations.Attribute("autoBackupEnabled")
    var autoBackupEnabled: Boolean = true,
    @com.intellij.util.xmlb.annotations.Attribute("maxBackupFiles")
    var maxBackupFiles: Int = 10,
    @com.intellij.util.xmlb.annotations.Attribute("lastBackupTime")
    var lastBackupTime: String? = null,
    @com.intellij.util.xmlb.annotations.Attribute("configVersion")
    var configVersion: String = "1.0",
    var globalSettings: GlobalSettings = GlobalSettings(),
    var loggingConfig: LoggingConfigState = LoggingConfigState()
)

/**
 * 全局设置
 */
@Serializable
@com.intellij.util.xmlb.annotations.Tag("GlobalSettings")
data class GlobalSettings(
    @com.intellij.util.xmlb.annotations.Attribute("enableLogging")
    var enableLogging: Boolean = true,
    @com.intellij.util.xmlb.annotations.Attribute("logLevel")
    var logLevel: String = "INFO",
    @com.intellij.util.xmlb.annotations.Attribute("connectionTimeoutMs")
    var connectionTimeoutMs: Long = 30000,
    @com.intellij.util.xmlb.annotations.Attribute("readTimeoutMs")
    var readTimeoutMs: Long = 60000,
    @com.intellij.util.xmlb.annotations.Attribute("retryAttempts")
    var retryAttempts: Int = 3,
    @com.intellij.util.xmlb.annotations.Attribute("retryDelayMs")
    var retryDelayMs: Long = 1000,
    // 更新设置
    @com.intellij.util.xmlb.annotations.Attribute("enableAutoUpdate")
    var enableAutoUpdate: Boolean = false,
    @com.intellij.util.xmlb.annotations.Attribute("updateInterval")
    var updateInterval: String = "每天一次",
    @com.intellij.util.xmlb.annotations.Attribute("updateCheckIntervalHours")
    var updateCheckIntervalHours: Int = 24,
    @com.intellij.util.xmlb.annotations.Attribute("lastUpdateCheckTime")
    var lastUpdateCheckTime: Long = 0L
)

/**
 * 日志配置状态
 */
@Serializable
@com.intellij.util.xmlb.annotations.Tag("LoggingConfigState")
data class LoggingConfigState(
    @com.intellij.util.xmlb.annotations.Attribute("enabled")
    var enabled: Boolean = true,
    @com.intellij.util.xmlb.annotations.Attribute("minLevel")
    var minLevel: String = "INFO",
    @com.intellij.util.xmlb.annotations.Attribute("logToFile")
    var logToFile: Boolean = true,
    @com.intellij.util.xmlb.annotations.Attribute("logToConsole")
    var logToConsole: Boolean = true,
    @com.intellij.util.xmlb.annotations.Attribute("logFilePath")
    var logFilePath: String = "logs/application.log",
    @com.intellij.util.xmlb.annotations.Attribute("maxFileSize")
    var maxFileSize: Long = 10485760, // 10MB
    @com.intellij.util.xmlb.annotations.Attribute("maxFileCount")
    var maxFileCount: Int = 10,
    @com.intellij.util.xmlb.annotations.Attribute("retentionTimeMs")
    var retentionTimeMs: Long = 604800000, // 7天
    @com.intellij.util.xmlb.annotations.Attribute("enablePerformanceLogging")
    var enablePerformanceLogging: Boolean = true,
    @com.intellij.util.xmlb.annotations.Attribute("enableSecurityLogging")
    var enableSecurityLogging: Boolean = true,
    @com.intellij.util.xmlb.annotations.Attribute("logSensitiveData")
    var logSensitiveData: Boolean = false,
    @com.intellij.util.xmlb.annotations.Attribute("logFormat")
    var logFormat: String = "JSON"
)