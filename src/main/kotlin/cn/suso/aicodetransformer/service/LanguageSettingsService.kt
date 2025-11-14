package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.i18n.LanguageManager
import cn.suso.aicodetransformer.model.CommitSettings
import cn.suso.aicodetransformer.model.GlobalSettings

/**
 * 封装语言偏好管理与持久化逻辑，避免各个 UI 面板重复实现与语言相关的配置同步流程。
 */
class LanguageSettingsService(private val configurationService: ConfigurationService) {

    /** 使用已持久化的配置初始化 LanguageManager，并同步与语言相关的默认值。 */
    fun initializeLanguageFromSettings() {
        val settings = configurationService.getGlobalSettings()
        val normalized = normalizeLanguageCode(settings.displayLanguage)
        if (LanguageManager.getLanguageCode() != normalized) {
            LanguageManager.setLanguage(normalized)
        }
        persistDisplayLanguageIfNeeded(normalized, settings)
        syncLanguageDependentDefaults()
    }

    /** 应用新的语言偏好并持久化，同时刷新依赖语言的默认值。 */
    fun applyLanguage(requestedCode: String) {
        val normalized = normalizeLanguageCode(requestedCode)
        if (LanguageManager.getLanguageCode() != normalized) {
            LanguageManager.setLanguage(normalized)
        }
        persistDisplayLanguageIfNeeded(normalized)
        syncLanguageDependentDefaults()
    }

    /** 确保提交模板等默认数据与当前语言保持一致。 */
    fun syncLanguageDependentDefaults() {
        runCatching {
            val currentCommitSettings = configurationService.getCommitSettings()
            val normalizedTemplates = CommitSettings.Companion.normalizeTemplates(currentCommitSettings)
            if (normalizedTemplates != currentCommitSettings) {
                configurationService.saveCommitSettings(normalizedTemplates)
            }
        }
    }

    /** 将用户输入的语言代码映射为配置中使用的规范编码。 */
    fun normalizeLanguageCode(code: String): String = when (code.lowercase()) {
        "en", "en_us" -> "en_US"
        "zh", "zh_cn", "cn", "zh-hans" -> "zh_CN"
        "system" -> "system"
        else -> code
    }

    fun isCurrentLanguage(code: String): Boolean = normalizeLanguageCode(code) == normalizeLanguageCode(LanguageManager.getLanguageCode())

    private fun persistDisplayLanguageIfNeeded(
        languageCode: String,
        existingSettings: GlobalSettings? = null
    ) {
        val currentSettings = existingSettings ?: configurationService.getGlobalSettings()
        if (currentSettings.displayLanguage == languageCode) {
            return
        }
        val updatedSettings = cloneSettings(currentSettings).apply {
            displayLanguage = languageCode
        }
        configurationService.updateGlobalSettings(updatedSettings)
    }

    /**
     * 创建 [GlobalSettings] 的独立副本，调用方可安全修改字段而不影响 ConfigurationService 内部状态。
     */
    fun cloneSettings(source: GlobalSettings): GlobalSettings = GlobalSettings(
        enableLogging = source.enableLogging,
        logLevel = source.logLevel,
        connectionTimeoutMs = source.connectionTimeoutMs,
        readTimeoutMs = source.readTimeoutMs,
        retryAttempts = source.retryAttempts,
        retryDelayMs = source.retryDelayMs,
        enableAutoUpdate = source.enableAutoUpdate,
        updateInterval = source.updateInterval,
        updateCheckIntervalHours = source.updateCheckIntervalHours,
        lastUpdateCheckTime = source.lastUpdateCheckTime,
        enableCache = source.enableCache,
        cacheDefaultTtlMinutes = source.cacheDefaultTtlMinutes,
        displayLanguage = source.displayLanguage
    )
}