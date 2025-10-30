package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.ConfigurationState
import cn.suso.aicodetransformer.model.GlobalSettings
import cn.suso.aicodetransformer.model.LoggingConfigState
import cn.suso.aicodetransformer.model.CommitSettings
import cn.suso.aicodetransformer.service.ConfigurationChangeListener
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.ErrorHandlingService

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 配置管理服务实现类
 */
@State(
    name = "AICodeTransformerConfiguration",
    storages = [Storage("aicodetransformer-config.xml")]
)
class ConfigurationServiceImpl : ConfigurationService, PersistentStateComponent<ConfigurationState> {
    
    companion object {
        private const val SERVICE_NAME = "AICodeTransformer"
        private const val BACKUP_DIR = "aicodetransformer-backups"
        private const val MAX_BACKUP_FILES = 10
        private val logger = Logger.getInstance(ConfigurationServiceImpl::class.java)
        
        fun getInstance(): ConfigurationService = service<ConfigurationService>()
    }
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val errorHandlingService: ErrorHandlingService = service()
    private val listeners = CopyOnWriteArrayList<ConfigurationChangeListener>()
    private var state = ConfigurationState()
    private val lock = ReentrantReadWriteLock()
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    

    
    override fun getState(): ConfigurationState = lock.read { state }
    
    override fun loadState(state: ConfigurationState) {
        lock.write {
            this.state = state
            logger.info("Configuration state loaded with ${state.modelConfigurations.size} configurations")
            
            // 不再自动初始化默认配置，让用户手动添加
            if (this.state.modelConfigurations.isEmpty()) {
                logger.info("No configurations found, user needs to add configurations manually")
            }
            
            // 验证配置完整性
            validateStateIntegrity()
        }
    }
    
    override fun getModelConfigurations(): List<ModelConfiguration> {
        return lock.read { state.modelConfigurations.toList() }
    }
    
    override fun getModelConfiguration(id: String): ModelConfiguration? {
        return lock.read { state.modelConfigurations.find { it.id == id } }
    }
    
    override fun saveModelConfiguration(config: ModelConfiguration) {
        lock.write {
            try {
                // 验证配置
                val validationError = validateModelConfiguration(config)
                if (validationError != null) {
                    logger.warn("Configuration validation failed for ${config.id}: $validationError")
                    throw IllegalArgumentException(validationError)
                }
                
                val existingIndex = state.modelConfigurations.indexOfFirst { it.id == config.id }
                val oldConfig = if (existingIndex >= 0) state.modelConfigurations[existingIndex] else null
                
                // 创建备份
                if (state.autoBackupEnabled && oldConfig != null) {
                    createBackup()
                }
                
                if (existingIndex >= 0) {
                    state.modelConfigurations[existingIndex] = config
                    logger.info("Updated configuration: ${config.id} (${config.name})")
                    listeners.forEach { it.onConfigurationUpdated(oldConfig!!, config) }
                } else {
                    state.modelConfigurations.add(config)
                    logger.info("Added new configuration: ${config.id} (${config.name})")
                    listeners.forEach { it.onConfigurationAdded(config) }
                }
            } catch (e: Exception) {
                // 使用ErrorHandlingService处理异常
                errorHandlingService.handleConfigurationError(e, "ModelConfiguration", null)
                throw e
            }
        }
    }
    
    override fun deleteModelConfiguration(id: String): Boolean {
        return lock.write {
            try {
                val config = state.modelConfigurations.find { it.id == id }
                if (config != null) {
                    // 创建备份
                    if (state.autoBackupEnabled) {
                        createBackup()
                    }
                    
                    state.modelConfigurations.removeIf { it.id == id }
                    
                    // 删除对应的API密钥
                    deleteApiKey(id)
                    
                    // 如果删除的是默认配置，选择新的默认配置
                    if (state.defaultModelConfigId == id) {
                        state.defaultModelConfigId = getEnabledModelConfigurations().firstOrNull()?.id
                        logger.info("Default configuration changed to: ${state.defaultModelConfigId}")
                    }
                    
                    logger.info("Deleted configuration: $id (${config.name})")
                    listeners.forEach { it.onConfigurationDeleted(config) }
                    true
                } else {
                    logger.warn("Attempted to delete non-existent configuration: $id")
                    false
                }
            } catch (e: Exception) {
                logger.error("Failed to delete configuration $id", e)
                false
            }
        }
    }
    
    override fun getEnabledModelConfigurations(): List<ModelConfiguration> {
        return lock.read { state.modelConfigurations.filter { it.enabled } }
    }
    
    override fun getDefaultModelConfiguration(): ModelConfiguration? {
        return state.defaultModelConfigId?.let { getModelConfiguration(it) }
            ?: getEnabledModelConfigurations().firstOrNull()
    }
    
    override fun setDefaultModelConfiguration(id: String) {
        val config = getModelConfiguration(id)
        if (config != null) {
            state.defaultModelConfigId = id
            listeners.forEach { it.onDefaultConfigurationChanged(config) }
        }
    }
    
    override fun validateModelConfiguration(config: ModelConfiguration): String? {
        return when {
            config.name.isBlank() -> "模型名称不能为空"
            config.apiBaseUrl.isBlank() -> "API基础URL不能为空"
            config.modelName.isBlank() -> "模型名称不能为空"
            config.temperature < 0 || config.temperature > 2 -> "温度参数必须在0-2之间"
            config.maxTokens <= 0 -> "最大Token数必须大于0"
            config.connectTimeoutSeconds <= 0 -> "超时时间必须大于0秒"
            !isValidUrl(config.apiBaseUrl) -> "API基础URL格式不正确"

            state.modelConfigurations.any { it.id != config.id && it.name == config.name } -> "配置名称已存在"
            else -> null
        }
    }
    
    override fun getApiKey(configId: String): String? {
        return lock.read {
            state.modelConfigurations.find { it.id == configId }?.apiKey?.takeIf { it.isNotEmpty() }
        }
    }
    
    override fun saveApiKey(configId: String, apiKey: String) {
        try {
            lock.write {
                val configIndex = state.modelConfigurations.indexOfFirst { it.id == configId }
                if (configIndex >= 0) {
                    val oldConfig = state.modelConfigurations[configIndex]
                    val updatedConfig = oldConfig.copy(apiKey = apiKey, updatedAt = System.currentTimeMillis())
                    state.modelConfigurations[configIndex] = updatedConfig
                    logger.info("API密钥已保存，配置ID: $configId")
                } else {
                    logger.warn("配置不存在，无法保存API密钥，配置ID: $configId")
                    throw IllegalArgumentException("配置不存在: $configId")
                }
            }
        } catch (e: Exception) {
            logger.error("保存API密钥失败，配置ID: $configId", e)
            throw e
        }
    }
    
    override fun deleteApiKey(configId: String) {
        try {
            lock.write {
                val configIndex = state.modelConfigurations.indexOfFirst { it.id == configId }
                if (configIndex >= 0) {
                    val oldConfig = state.modelConfigurations[configIndex]
                    val updatedConfig = oldConfig.copy(apiKey = "", updatedAt = System.currentTimeMillis())
                    state.modelConfigurations[configIndex] = updatedConfig
                    logger.info("API密钥已删除，配置ID: $configId")
                } else {
                    logger.warn("配置不存在，无法删除API密钥，配置ID: $configId")
                }
            }
        } catch (e: Exception) {
            logger.error("删除API密钥失败，配置ID: $configId", e)
            throw e
        }
    }
    
    override fun exportConfigurations(): String {
        return json.encodeToString(state.modelConfigurations)
    }
    
    override fun importConfigurations(configJson: String): Int {
        return try {
            val importedConfigs = json.decodeFromString<List<ModelConfiguration>>(configJson)
            var importedCount = 0
            
            importedConfigs.forEach { config ->
                if (validateModelConfiguration(config) == null) {
                    saveModelConfiguration(config)
                    importedCount++
                }
            }
            
            importedCount
        } catch (e: Exception) {
            // 使用ErrorHandlingService处理异常
            errorHandlingService.handleConfigurationError(e, "ConfigurationImport", null)
            0
        }
    }
    
    override fun resetToDefaults() {
        // 清除现有配置
        val oldConfigs = state.modelConfigurations.toList()
        state.modelConfigurations.clear()
        state.defaultModelConfigId = null
        
        // 清除API密钥
        oldConfigs.forEach { deleteApiKey(it.id) }
        
        // 不再自动创建默认配置，让用户手动添加
        logger.info("All configurations cleared, user needs to add configurations manually")
        
        // 通知监听器
        oldConfigs.forEach { listeners.forEach { listener -> listener.onConfigurationDeleted(it) } }
    }
    
    override fun addConfigurationChangeListener(listener: ConfigurationChangeListener) {
        listeners.add(listener)
    }
    
    override fun removeConfigurationChangeListener(listener: ConfigurationChangeListener) {
        listeners.remove(listener)
    }
    
    override fun getLoggingConfig(): LoggingConfigState {
        return lock.read {
            state.loggingConfig
        }
    }
    
    override fun saveLoggingConfig(config: LoggingConfigState) {
        lock.write {
            state.loggingConfig = config
            logger.info("日志配置已保存到持久化存储")
        }
    }
    

    

    
    /**
     * 验证URL格式
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            java.net.URI(url).toURL()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 创建配置备份
     */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun createBackup() {
        GlobalScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val backupDir = File(System.getProperty("user.home"), ".aicodetransformer/$BACKUP_DIR")
                    if (!backupDir.exists()) {
                        backupDir.mkdirs()
                    }
                    
                    val timestamp = LocalDateTime.now().format(dateTimeFormatter)
                    val backupFile = File(backupDir, "config-backup-$timestamp.json")
                    
                    val backupData = json.encodeToString(state)
                    backupFile.writeText(backupData)
                    
                    state.lastBackupTime = timestamp
                    logger.info("Configuration backup created: ${backupFile.absolutePath}")
                    
                    // 清理旧备份文件
                    cleanupOldBackups(backupDir)
                }
            } catch (e: Exception) {
                // 使用ErrorHandlingService处理异常
                errorHandlingService.handleConfigurationError(e, "ConfigurationBackup", null)
            }
        }
    }
    
    /**
     * 清理旧备份文件
     */
    private suspend fun cleanupOldBackups(backupDir: File) {
        try {
            val backupFiles = backupDir.listFiles { _, name -> 
                name.startsWith("config-backup-") && name.endsWith(".json")
            }?.sortedByDescending { it.lastModified() }
            
            if (backupFiles != null && backupFiles.size > state.maxBackupFiles) {
                val filesToDelete = backupFiles.drop(state.maxBackupFiles)
                filesToDelete.forEach { file ->
                    if (file.delete()) {
                        logger.info("Deleted old backup: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to cleanup old backups", e)
        }
    }
    
    /**
     * 验证状态完整性
     */
    private fun validateStateIntegrity() {
        try {
            // 检查重复ID
            val ids = state.modelConfigurations.map { it.id }
            val duplicateIds = ids.groupBy { it }.filter { it.value.size > 1 }.keys
            if (duplicateIds.isNotEmpty()) {
                logger.warn("Found duplicate configuration IDs: $duplicateIds")
                // 移除重复项，保留第一个
                val uniqueConfigs = state.modelConfigurations.distinctBy { it.id }.toMutableList()
                state.modelConfigurations.clear()
                state.modelConfigurations.addAll(uniqueConfigs)
            }
            
            // 检查默认配置是否存在
            if (state.defaultModelConfigId != null && 
                !state.modelConfigurations.any { it.id == state.defaultModelConfigId }) {
                logger.warn("Default configuration ID not found, resetting to first enabled configuration")
                state.defaultModelConfigId = getEnabledModelConfigurations().firstOrNull()?.id
            }
            
            // 验证每个配置
            val invalidConfigs = mutableListOf<String>()
            state.modelConfigurations.forEach { config ->
                val validationError = validateModelConfiguration(config)
                if (validationError != null) {
                    invalidConfigs.add("${config.id}: $validationError")
                }
            }
            
            if (invalidConfigs.isNotEmpty()) {
                logger.warn("Found invalid configurations: $invalidConfigs")
            }
        } catch (e: Exception) {
            errorHandlingService.handleConfigurationError(e, "ConfigurationValidation", null)
        }
    }
    
    /**
     * 批量保存配置
     */
    fun saveModelConfigurations(configs: List<ModelConfiguration>): List<String> {
        val errors = mutableListOf<String>()
        
        lock.write {
            try {
                if (state.autoBackupEnabled) {
                    createBackup()
                }
                
                configs.forEach { config ->
                    try {
                        val validationError = validateModelConfiguration(config)
                        if (validationError != null) {
                            errors.add("${config.id}: $validationError")
                        } else {
                            val existingIndex = state.modelConfigurations.indexOfFirst { it.id == config.id }
                            if (existingIndex >= 0) {
                                state.modelConfigurations[existingIndex] = config
                            } else {
                                state.modelConfigurations.add(config)
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("${config.id}: ${e.message}")
                    }
                }
                
                logger.info("Batch saved ${configs.size - errors.size} configurations, ${errors.size} errors")
            } catch (e: Exception) {
                // 使用ErrorHandlingService处理异常
                val handlingResult = errorHandlingService.handleConfigurationError(e, "BatchConfigurationSave", null)
                errors.add("Batch operation failed: ${handlingResult.userMessage}")
            }
        }
        
        return errors
    }
    
    /**
     * 获取全局设置
     */
    override fun getGlobalSettings(): GlobalSettings {
        return lock.read { state.globalSettings }
    }
    
    /**
     * 更新全局设置
     */
    override fun updateGlobalSettings(settings: GlobalSettings) {
        lock.write {
            state.globalSettings = settings
            logger.info("Global settings updated")
        }
    }
    
    /**
     * 获取Commit设置
     */
    override fun getCommitSettings(): CommitSettings {
        return lock.read { state.commitSettings }
    }
    
    /**
     * 保存Commit设置
     */
    override fun saveCommitSettings(settings: CommitSettings) {
        lock.write {
            state.commitSettings = settings
            logger.info("Commit settings saved")
        }
    }
}