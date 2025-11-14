package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

/**
 * 持久化状态数据类
 */
@Serializable
@com.intellij.util.xmlb.annotations.Tag("State")
class ConfigurationState {
    var modelConfigurations: MutableList<ModelConfiguration> = mutableListOf()
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("defaultModelConfigId")
    var defaultModelConfigId: String? = null
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("autoBackupEnabled")
    var autoBackupEnabled: Boolean = true
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("maxBackupFiles")
    var maxBackupFiles: Int = 10
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("lastBackupTime")
    var lastBackupTime: String? = null
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("configVersion")
    var configVersion: String = "1.0"
        get() = field
        set(value) { field = value }
    
    var globalSettings: GlobalSettings = GlobalSettings()
        get() = field
        set(value) { field = value }
    
    var loggingConfig: LoggingConfigState = LoggingConfigState()
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Property(surroundWithTag = false)
    var commitSettings: CommitSettings = CommitSettings.createDefault()
        get() = field
        set(value) { field = value }
    
    // 默认构造函数
    constructor()
    
    // 复制构造函数
    constructor(
        modelConfigurations: MutableList<ModelConfiguration> = mutableListOf(),
        defaultModelConfigId: String? = null,
        autoBackupEnabled: Boolean = true,
        maxBackupFiles: Int = 10,
        lastBackupTime: String? = null,
        configVersion: String = "1.0",
        globalSettings: GlobalSettings = GlobalSettings(),
        loggingConfig: LoggingConfigState = LoggingConfigState(),
        commitSettings: CommitSettings = CommitSettings.createDefault()
    ) {
        this.modelConfigurations = modelConfigurations
        this.defaultModelConfigId = defaultModelConfigId
        this.autoBackupEnabled = autoBackupEnabled
        this.maxBackupFiles = maxBackupFiles
        this.lastBackupTime = lastBackupTime
        this.configVersion = configVersion
        this.globalSettings = globalSettings
        this.loggingConfig = loggingConfig
        this.commitSettings = commitSettings
    }
}

/**
 * 全局设置
 */
@Serializable
@com.intellij.util.xmlb.annotations.Tag("GlobalSettings")
class GlobalSettings {
    @com.intellij.util.xmlb.annotations.Attribute("enableLogging")
    var enableLogging: Boolean = true
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("logLevel")
    var logLevel: String = "INFO"
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("connectionTimeoutMs")
    var connectionTimeoutMs: Long = 30000
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("readTimeoutMs")
    var readTimeoutMs: Long = 60000
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("retryAttempts")
    var retryAttempts: Int = 3
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("retryDelayMs")
    var retryDelayMs: Long = 1000
        get() = field
        set(value) { field = value }
    
    // 更新设置
    @com.intellij.util.xmlb.annotations.Attribute("enableAutoUpdate")
    var enableAutoUpdate: Boolean = false
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("updateInterval")
    var updateInterval: String = "每天一次"
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("updateCheckIntervalHours")
    var updateCheckIntervalHours: Int = 24
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("lastUpdateCheckTime")
    var lastUpdateCheckTime: Long = 0L
        get() = field
        set(value) { field = value }

    // 显示语言：system/zh_CN/en_US
    @com.intellij.util.xmlb.annotations.Attribute("displayLanguage")
    var displayLanguage: String = "system"
        get() = field
        set(value) { field = value }
    
    // 缓存设置
    @com.intellij.util.xmlb.annotations.Attribute("enableCache")
    var enableCache: Boolean = false
        get() = field
        set(value) { field = value }

    // 默认缓存TTL（分钟）
    @com.intellij.util.xmlb.annotations.Attribute("cacheDefaultTtlMinutes")
    var cacheDefaultTtlMinutes: Int = 15
        get() = field
        set(value) { field = value }
    
    // 默认构造函数
    constructor()
    
    // 复制构造函数
    constructor(
        enableLogging: Boolean = true,
        logLevel: String = "INFO",
        connectionTimeoutMs: Long = 30000,
        readTimeoutMs: Long = 60000,
        retryAttempts: Int = 3,
        retryDelayMs: Long = 1000,
        enableAutoUpdate: Boolean = false,
        updateInterval: String = "每天一次",
        updateCheckIntervalHours: Int = 24,
        lastUpdateCheckTime: Long = 0L,
        enableCache: Boolean = false,
        cacheDefaultTtlMinutes: Int = 15,
        displayLanguage: String = "system"
    ) {
        this.enableLogging = enableLogging
        this.logLevel = logLevel
        this.connectionTimeoutMs = connectionTimeoutMs
        this.readTimeoutMs = readTimeoutMs
        this.retryAttempts = retryAttempts
        this.retryDelayMs = retryDelayMs
        this.enableAutoUpdate = enableAutoUpdate
        this.updateInterval = updateInterval
        this.updateCheckIntervalHours = updateCheckIntervalHours
        this.lastUpdateCheckTime = lastUpdateCheckTime
        this.enableCache = enableCache
        this.cacheDefaultTtlMinutes = cacheDefaultTtlMinutes
        this.displayLanguage = displayLanguage
    }
}

/**
 * 日志配置状态
 */
@Serializable
@com.intellij.util.xmlb.annotations.Tag("LoggingConfigState")
class LoggingConfigState {
    @com.intellij.util.xmlb.annotations.Attribute("enabled")
    var enabled: Boolean = true
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("minLevel")
    var minLevel: String = "INFO"
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("logToFile")
    var logToFile: Boolean = true
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("logToConsole")
    var logToConsole: Boolean = true
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("logFilePath")
    var logFilePath: String = "logs/application.log"
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("maxFileSize")
    var maxFileSize: Long = 10485760 // 10MB
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("maxFileCount")
    var maxFileCount: Int = 10
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("retentionTimeMs")
    var retentionTimeMs: Long = 604800000 // 7天
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("enablePerformanceLogging")
    var enablePerformanceLogging: Boolean = true
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("enableSecurityLogging")
    var enableSecurityLogging: Boolean = true
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("logSensitiveData")
    var logSensitiveData: Boolean = false
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Attribute("logFormat")
    var logFormat: String = "JSON"
        get() = field
        set(value) { field = value }
    
    // 默认构造函数
    constructor()
    
    // 复制构造函数
    constructor(
        enabled: Boolean = false,
        minLevel: String = "INFO",
        logToFile: Boolean = true,
        logToConsole: Boolean = true,
        logFilePath: String = "logs/application.log",
        maxFileSize: Long = 10485760,
        maxFileCount: Int = 10,
        retentionTimeMs: Long = 604800000,
        enablePerformanceLogging: Boolean = true,
        enableSecurityLogging: Boolean = true,
        logSensitiveData: Boolean = false,
        logFormat: String = "JSON"
    ) {
        this.enabled = enabled
        this.minLevel = minLevel
        this.logToFile = logToFile
        this.logToConsole = logToConsole
        this.logFilePath = logFilePath
        this.maxFileSize = maxFileSize
        this.maxFileCount = maxFileCount
        this.retentionTimeMs = retentionTimeMs
        this.enablePerformanceLogging = enablePerformanceLogging
        this.enableSecurityLogging = enableSecurityLogging
        this.logSensitiveData = logSensitiveData
        this.logFormat = logFormat
    }
}