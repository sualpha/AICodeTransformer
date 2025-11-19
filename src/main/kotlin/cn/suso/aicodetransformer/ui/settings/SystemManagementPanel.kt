package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.constants.UpdateStatus
import cn.suso.aicodetransformer.model.UpdateInfo
import cn.suso.aicodetransformer.model.CacheConfig
import cn.suso.aicodetransformer.service.LoggingService
import cn.suso.aicodetransformer.model.LoggingConfig
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.AutoUpdateService
import cn.suso.aicodetransformer.service.UpdateStatusListener
import cn.suso.aicodetransformer.service.CacheService
import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.i18n.LanguageManager
import cn.suso.aicodetransformer.service.LanguageSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.TitledBorder
import java.text.MessageFormat

/**
 * 系统设置面板 - 整合日志管理和更新设置
 */
class SystemManagementPanel(
    private val project: Project,
    private val configurationService: ConfigurationService,
    private val languageController: LanguageSettingsService
) : JPanel(BorderLayout()) {
    
    private val loggingService: LoggingService = service()
    private val autoUpdateService: AutoUpdateService = service()
    private val cacheService: CacheService = service()
    private val logPathLabel = JBLabel()
    private val logSizeLabel = JBLabel()
    private val logCountLabel = JBLabel()
    
    // 日志配置UI组件
    private lateinit var retentionSpinner: JSpinner
    private lateinit var autoCleanCheckBox: JCheckBox
    private lateinit var logSensitiveDataCheckBox: JCheckBox
    
    // 更新设置相关组件
    private lateinit var enableAutoUpdateCheckBox: JCheckBox
    private lateinit var updateIntervalComboBox: JComboBox<String>
    private lateinit var updateStatusLabel: JBLabel
    private lateinit var checkUpdateButton: JButton
    private lateinit var downloadUpdateButton: JButton
    private lateinit var cancelDownloadButton: JButton
    private lateinit var installUpdateButton: JButton
    
    // 缓存配置相关组件
    private lateinit var enableCacheCheckBox: JCheckBox
    private lateinit var cacheTtlSpinner: JSpinner
    private lateinit var clearCacheButton: JButton
    
    // 日志面板中的按钮
    private lateinit var openFolderButton: JButton
    private lateinit var clearLogButton: JButton
    
    // 更新频率下拉框
    private lateinit var updateIntervalItems: Array<String>
    
    // 当前可用的更新信息
    private var currentUpdateInfo: UpdateInfo? = null
    private var currentUpdateStatus: UpdateStatus = UpdateStatus.IDLE
    private var currentProgressMessage: String? = null
    private var currentProgressValue: Int? = null
    private var currentErrorMessage: String? = null
    
    // 协程作用域，用于管理后台任务
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 当前下载任务的Job
    private var downloadJob: Job? = null

    private val updateStatusListener = object : UpdateStatusListener {
        override fun onStatusChanged(oldStatus: UpdateStatus, newStatus: UpdateStatus, updateInfo: UpdateInfo?) {
            SwingUtilities.invokeLater {
                if (!::downloadUpdateButton.isInitialized || !::checkUpdateButton.isInitialized) {
                    return@invokeLater
                }
                // 保存当前更新信息
                currentUpdateInfo = updateInfo

                currentUpdateStatus = newStatus
                currentProgressMessage = null
                currentProgressValue = null
                currentErrorMessage = null

                // 控制按钮状态
                checkUpdateButton.isEnabled = newStatus == UpdateStatus.IDLE || newStatus == UpdateStatus.ERROR || newStatus == UpdateStatus.UP_TO_DATE

                // 控制下载按钮
                downloadUpdateButton.isVisible = newStatus == UpdateStatus.AVAILABLE
                downloadUpdateButton.isEnabled = newStatus == UpdateStatus.AVAILABLE

                // 控制取消下载按钮
                cancelDownloadButton.isVisible = newStatus == UpdateStatus.DOWNLOADING
                cancelDownloadButton.isEnabled = newStatus == UpdateStatus.DOWNLOADING

                // 控制安装按钮
                installUpdateButton.isVisible = newStatus == UpdateStatus.DOWNLOADED
                installUpdateButton.isEnabled = newStatus == UpdateStatus.DOWNLOADED

                refreshStatusLabelText()

                // 重新布局以适应按钮的显示/隐藏
                revalidate()
                repaint()
            }
        }

        override fun onProgressChanged(progress: Int, message: String) {
            SwingUtilities.invokeLater {
                currentProgressMessage = message
                currentProgressValue = progress.takeIf { it > 0 }
                currentErrorMessage = null
                refreshStatusLabelText()
            }
        }

        override fun onError(error: String) {
            SwingUtilities.invokeLater {
                currentUpdateStatus = UpdateStatus.ERROR
                currentErrorMessage = error
                currentProgressMessage = null
                currentProgressValue = null
                refreshStatusLabelText()
                if (::checkUpdateButton.isInitialized) {
                    checkUpdateButton.isEnabled = true
                }
            }
        }
    }

    // 语言设置控件
    private var suppressLanguageEvents = false
    private lateinit var languageZhRadio: JRadioButton
    private lateinit var languageEnRadio: JRadioButton
    private lateinit var languageButtonGroup: ButtonGroup
    private lateinit var updateSettingsPanel: JPanel
    private lateinit var systemTitleLabel: JBLabel
    
    // 面板引用，用于语言切换时更新标题
    private lateinit var logInfoPanel: JPanel
    private lateinit var logConfigPanel: JPanel
    private lateinit var cacheSettingsPanel: JPanel
    private lateinit var languageSettingsPanel: JPanel
    
    // 静态标签引用，用于语言切换时更新文本
    private lateinit var logPathStaticLabel: JBLabel
    private lateinit var logSizeStaticLabel: JBLabel
    private lateinit var logCountStaticLabel: JBLabel
    private lateinit var logRetentionStaticLabel: JBLabel
    private lateinit var logDaysStaticLabel: JBLabel
    private lateinit var cacheTtlStaticLabel: JBLabel
    private lateinit var updateFrequencyStaticLabel: JBLabel
    private lateinit var languageDisplayStaticLabel: JBLabel
    
    // 语言变化监听器引用，用于在dispose时移除
    private val languageChangeListener: () -> Unit = {
        SwingUtilities.invokeLater {
            refreshAllTexts()
            updateLanguageSelection(LanguageManager.getLanguageCode())
        }
    }
    
    init {
        setupUI()
        refreshLogInfo()
        loadConfiguration()
        loadUpdateSettings()
        setupLanguageChangeListener()
    }
    
    private fun setupUI() {
        border = EmptyBorder(JBUI.insets(8))
        
        // 使用垂直布局，将所有内容紧凑排列在顶部
        val mainPanel = JBPanel<JBPanel<*>>()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        
        // 标题面板
        val headerPanel = createHeaderPanel()
        mainPanel.add(headerPanel)
        mainPanel.add(Box.createVerticalStrut(8))
        
        // 日志信息面板
        logInfoPanel = createLogInfoPanel()
        mainPanel.add(logInfoPanel)
        mainPanel.add(Box.createVerticalStrut(8))
        
        // 日志配置面板
        logConfigPanel = createLogConfigPanel()
        mainPanel.add(logConfigPanel)
        mainPanel.add(Box.createVerticalStrut(8))
        
        // 缓存配置面板（位于日志配置下面）
        cacheSettingsPanel = createCacheSettingsPanel()
        mainPanel.add(cacheSettingsPanel)
        mainPanel.add(Box.createVerticalStrut(8))
        
        // 更新设置面板
        updateSettingsPanel = createUpdateSettingsPanel()
        mainPanel.add(updateSettingsPanel)

        // 显示语言设置面板
        languageSettingsPanel = createLanguageSettingsPanel()
        mainPanel.add(languageSettingsPanel)
        
        // 添加垂直间距，将内容推到顶部
        mainPanel.add(Box.createVerticalGlue())
        
        add(mainPanel, BorderLayout.NORTH)
    }
    
    private fun createHeaderPanel(): JPanel {
        val headerPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.border = EmptyBorder(JBUI.insets(0, 0, 8, 0))
        
        val titleLabel = JBLabel(I18n.t("header.systemSettings"))
        systemTitleLabel = titleLabel
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        titleLabel.foreground = UIUtil.getLabelForeground()
        
        headerPanel.add(titleLabel)
        return headerPanel
    }
    

    
    private fun createLogInfoPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder(I18n.t("log.info"))
        
        // 日志文件路径和统计信息合并到一行
        val infoPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        logPathStaticLabel = JBLabel(I18n.t("log.path"))
        infoPanel.add(logPathStaticLabel)
        logPathLabel.foreground = UIUtil.getLabelForeground()
        infoPanel.add(logPathLabel)
        
        openFolderButton = JButton(I18n.t("log.open"))
        openFolderButton.addActionListener { openLogFolder() }
        infoPanel.add(openFolderButton)
        
        infoPanel.add(Box.createHorizontalStrut(15))
        logSizeStaticLabel = JBLabel(I18n.t("log.size"))
        infoPanel.add(logSizeStaticLabel)
        infoPanel.add(logSizeLabel)
        infoPanel.add(Box.createHorizontalStrut(15))
        logCountStaticLabel = JBLabel(I18n.t("log.count"))
        infoPanel.add(logCountStaticLabel)
        infoPanel.add(logCountLabel)
        
        panel.add(infoPanel)
        
        return panel
    }
    
    private fun createLogConfigPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder(I18n.t("log.config"))
        
        // 将所有配置项合并到一行
        val configPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        
        //configPanel.add(Box.createHorizontalStrut(8))
        logRetentionStaticLabel = JBLabel(I18n.t("log.retention"))
        configPanel.add(logRetentionStaticLabel)
        retentionSpinner = JSpinner(SpinnerNumberModel(30, 1, 365, 1))
        configPanel.add(retentionSpinner)
        logDaysStaticLabel = JBLabel(I18n.t("log.days"))
        configPanel.add(logDaysStaticLabel)
        
        configPanel.add(Box.createHorizontalStrut(15))
        logSensitiveDataCheckBox = JCheckBox(I18n.t("log.record.calls"), false)
        configPanel.add(logSensitiveDataCheckBox)

        configPanel.add(Box.createHorizontalStrut(15))
        autoCleanCheckBox = JCheckBox(I18n.t("log.auto.clean"), false)
        configPanel.add(autoCleanCheckBox)

        configPanel.add(Box.createHorizontalStrut(10))
        clearLogButton = JButton(I18n.t("log.clear"))
        clearLogButton.addActionListener { clearLogs() }
        configPanel.add(clearLogButton)

        panel.add(configPanel)
        
        return panel
    }
    
    private fun createCacheSettingsPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder(I18n.t("cache.settings"))

        val cachePanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))

        // 与日志配置保持一致的左侧间距
       // cachePanel.add(Box.createHorizontalStrut(15))
        // TTL（分钟）放第一个
        cacheTtlStaticLabel = JBLabel(I18n.t("cache.ttl"))
        cachePanel.add(cacheTtlStaticLabel)
        cacheTtlSpinner = JSpinner(SpinnerNumberModel(15, 1, 1440, 1))
        cachePanel.add(cacheTtlSpinner)

        // 启用缓存放第二个
        cachePanel.add(Box.createHorizontalStrut(15))
        enableCacheCheckBox = JCheckBox(I18n.t("cache.enable"), true)
        enableCacheCheckBox.toolTipText = I18n.t("cache.enable.tooltip")
        cachePanel.add(enableCacheCheckBox)

        // 清理缓存放第三个
        cachePanel.add(Box.createHorizontalStrut(10))
        clearCacheButton = JButton(I18n.t("cache.clear"))
        clearCacheButton.toolTipText = I18n.t("cache.clear.tooltip")
        clearCacheButton.addActionListener {
            try {
                val result = Messages.showOkCancelDialog(
                    this,
                    I18n.t("cache.clear.confirm.message"),
                    I18n.t("cache.clear.confirm.title"),
                    I18n.t("cache.clear.ok"),
                    I18n.t("cache.clear.cancel"),
                    Messages.getQuestionIcon()
                )
                if (result == Messages.OK) {
                    cacheService.clearAllCache()
                    Messages.showInfoMessage(this, I18n.t("cache.cleared"), I18n.t("action.done"))
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(this, MessageFormat.format(I18n.t("cache.clear.fail"), e.message ?: ""), I18n.t("action.fail"))
            }
        }
        cachePanel.add(clearCacheButton)

        panel.add(cachePanel)
        return panel
    }
    
    private fun createUpdateSettingsPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder(I18n.t("update.settings"))
        
        // 第一行：自动更新配置
        val updateConfigPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        
        // 自动更新勾选框
        enableAutoUpdateCheckBox = JCheckBox(I18n.t("update.enable.auto"), false)
        enableAutoUpdateCheckBox.addActionListener { 
            updateIntervalComboBox.isEnabled = enableAutoUpdateCheckBox.isSelected
            if (enableAutoUpdateCheckBox.isSelected) {
                autoUpdateService.startAutoUpdate("timer")
            } else {
                autoUpdateService.stopAutoUpdate()
            }
        }
        updateConfigPanel.add(enableAutoUpdateCheckBox)
        
        updateConfigPanel.add(Box.createHorizontalStrut(15))
        updateFrequencyStaticLabel = JBLabel(I18n.t("update.frequency"))
        updateConfigPanel.add(updateFrequencyStaticLabel)
        
        // 检查频率下拉框
        updateIntervalItems = arrayOf(I18n.t("update.daily"), I18n.t("update.weekly"), I18n.t("update.monthly"))
        updateIntervalComboBox = JComboBox(updateIntervalItems)
        updateIntervalComboBox.selectedIndex = 0 // 默认选择
        updateConfigPanel.add(updateIntervalComboBox)
        
        panel.add(updateConfigPanel)
        
        // 已将缓存配置移动到独立的“缓存配置”面板中
        
        // 第三行：手动检查和状态显示
        val statusPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        
        // 手动检查更新按钮
        checkUpdateButton = JButton(I18n.t("update.check.now"))
        checkUpdateButton.addActionListener { checkForUpdatesManually() }
        statusPanel.add(checkUpdateButton)
        
        statusPanel.add(Box.createHorizontalStrut(15))
        
        // 更新状态显示
        updateStatusLabel = JBLabel(I18n.t("status.unchecked"))
        statusPanel.add(updateStatusLabel)
        
        panel.add(statusPanel)
        
        // 第四行：下载和安装更新按钮
        val updateActionPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        
        // 下载更新按钮
        downloadUpdateButton = JButton(I18n.t("update.download"))
        downloadUpdateButton.isVisible = false // 初始隐藏
        downloadUpdateButton.addActionListener { downloadUpdateManually() }
        updateActionPanel.add(downloadUpdateButton)
        
        updateActionPanel.add(Box.createHorizontalStrut(10))
        
        // 取消下载按钮
        cancelDownloadButton = JButton(I18n.t("update.cancel"))
        cancelDownloadButton.isVisible = false // 初始隐藏
        cancelDownloadButton.addActionListener { cancelDownloadManually() }
        updateActionPanel.add(cancelDownloadButton)
        
        updateActionPanel.add(Box.createHorizontalStrut(10))
        
        // 安装更新按钮
        installUpdateButton = JButton(I18n.t("update.install"))
        installUpdateButton.isVisible = false // 初始隐藏
        installUpdateButton.addActionListener { installUpdateManually() }
        updateActionPanel.add(installUpdateButton)
        
        panel.add(updateActionPanel)
        
        // 注册更新状态监听器
        autoUpdateService.addStatusListener(updateStatusListener)
        
        updateSettingsPanel = panel
        return panel
    }
    

    
    private fun refreshLogInfo() {
        try {
            // 获取日志文件路径
            val logFilePath = getLogFilePath()
            logPathLabel.text = logFilePath
            
            // 获取日志文件大小
            val logFile = File(logFilePath)
            if (logFile.exists()) {
                val sizeInBytes = logFile.length()
                val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
                logSizeLabel.text = String.format("%.2f MB", sizeInMB)
                
                // 估算日志条目数量（简单估算，每行约100字节）
                val estimatedLines = sizeInBytes / 100
                logCountLabel.text = estimatedLines.toString()
            } else {
                logSizeLabel.text = "0 MB"
                logCountLabel.text = "0"
            }
        } catch (e: Exception) {
            logPathLabel.text = "获取日志路径失败"
            logSizeLabel.text = "未知"
            logCountLabel.text = "未知"
        }
    }
    
    private fun getLogFilePath(): String {
        // 获取日志文件路径，从LoggingService获取实际配置
        return try {
            val config = loggingService.getLoggingConfig()
            val logPath = config.logFilePath
            // 如果是相对路径，转换为绝对路径
            if (File(logPath).isAbsolute) {
                logPath
            } else {
                File(System.getProperty("user.dir"), logPath).absolutePath
            }
        } catch (e: Exception) {
            // 如果获取配置失败，使用默认路径
            File(System.getProperty("user.dir"), "logs/application.log").absolutePath
        }
    }
    
    private fun openLogFolder() {
        try {
            val logFilePath = getLogFilePath()
            val logFile = File(logFilePath)
            val logFolder = logFile.parentFile
            
            if (logFolder.exists()) {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(logFolder)
                } else {
                    Messages.showInfoMessage(
                        project,
                        "日志文件夹路径：${logFolder.absolutePath}",
                        "日志文件夹"
                    )
                }
            } else {
                Messages.showWarningDialog(
                    project,
                    "日志文件夹不存在：${logFolder.absolutePath}",
                    "文件夹不存在"
                )
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "打开日志文件夹失败：${e.message}",
                "操作失败"
            )
        }
    }
    
    // 移除查看日志功能，保留打开日志文件夹与清空功能
    

    
    private fun clearLogs() {
        val result = Messages.showOkCancelDialog(
            project,
            I18n.t("log.clear.confirm.message"),
            I18n.t("log.clear.confirm.title"),
            I18n.t("log.clear.confirm.ok"),
            I18n.t("log.clear.confirm.cancel"),
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.OK) {
            try {
                val logFilePath = getLogFilePath()
                val logFile = File(logFilePath)
                
                if (logFile.exists()) {
                    logFile.delete()
                    refreshLogInfo()
                    Messages.showInfoMessage(
                        project,
                        I18n.t("log.clear.success.message"),
                        I18n.t("log.clear.success.title")
                    )
                } else {
                    Messages.showInfoMessage(
                        project,
                        I18n.t("log.clear.noFile.message"),
                        I18n.t("log.clear.noFile.title")
                    )
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    I18n.t("log.clear.error.message", e.message ?: ""),
                    I18n.t("log.clear.error.title")
                )
            }
        }
    }
    
    /**
     * 加载当前日志配置
     */
    private fun loadConfiguration() {
        try {
            val config = loggingService.getLoggingConfig()
            
            // 设置保留天数（从毫秒转换为天数）
            val retentionDays = (config.retentionTimeMs / (24 * 60 * 60 * 1000)).toInt()
            retentionSpinner.value = retentionDays
            
            // 设置自动清理（这里假设启用日志记录就是自动清理）
            autoCleanCheckBox.isSelected = config.enabled
            
            // 设置敏感信息记录
            logSensitiveDataCheckBox.isSelected = config.logSensitiveData
            
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "加载日志配置失败：${e.message}",
                "配置加载失败"
            )
        }
    }
    
    /**
     * 保存日志配置
     */
    private fun saveConfiguration() {
        try {
            val currentConfig = loggingService.getLoggingConfig()
            
            // 获取保留天数（转换为毫秒）
            val retentionDays = retentionSpinner.value as Int
            val retentionTimeMs = retentionDays * 24L * 60L * 60L * 1000L
            
            // 创建新的配置
            val newConfig = LoggingConfig(
                enabled = autoCleanCheckBox.isSelected,
                minLevel = currentConfig.minLevel,
                logToFile = currentConfig.logToFile,
                logToConsole = currentConfig.logToConsole,
                logFilePath = currentConfig.logFilePath,
                maxFileSize = currentConfig.maxFileSize,
                maxFileCount = currentConfig.maxFileCount,
                retentionTimeMs = retentionTimeMs,
                enablePerformanceLogging = currentConfig.enablePerformanceLogging,
                enableSecurityLogging = currentConfig.enableSecurityLogging,
                logSensitiveData = logSensitiveDataCheckBox.isSelected,
                logFormat = currentConfig.logFormat
            )
            
            // 保存配置
            loggingService.setLoggingConfig(newConfig)
            
            // 刷新日志信息
            refreshLogInfo()
            
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "保存日志配置失败：${e.message}",
                "配置保存失败"
            )
        }
    }
    
    /**
     * 保存更新设置
     */
    private fun saveUpdateSettings() {
        try {
            val currentSettings = configurationService.getGlobalSettings()
            
            // 获取检查间隔字符串
            val intervalIndex = updateIntervalComboBox.selectedIndex
            val intervalString = when (intervalIndex) {
                0 -> "每天一次"
                1 -> "每周一次"
                2 -> "每月一次"
                else -> "每天一次"
            }
            
            // 获取检查间隔（小时）
            val intervalHours = when (intervalString) {
                "每天一次" -> 24
                "每周一次" -> 24 * 7
                "每月一次" -> 24 * 30
                else -> 24
            }
            
            // 获取缓存TTL（分钟）
            val ttlMinutes = (cacheTtlSpinner.value as Int)
            
            // 语言选择
            val selectedLanguage = when {
                languageZhRadio.isSelected -> "zh_CN"
                languageEnRadio.isSelected -> "en_US"
                else -> LanguageManager.getLanguageCode()
            }
            val normalizedLanguage = languageController.normalizeLanguageCode(selectedLanguage)
            
            // 创建新的全局设置
            val newSettings = languageController.cloneSettings(currentSettings).apply {
                enableAutoUpdate = enableAutoUpdateCheckBox.isSelected
                updateInterval = intervalString
                updateCheckIntervalHours = intervalHours
                lastUpdateCheckTime = if (enableAutoUpdateCheckBox.isSelected) System.currentTimeMillis() else 0L
                enableCache = enableCacheCheckBox.isSelected
                cacheDefaultTtlMinutes = ttlMinutes
                displayLanguage = normalizedLanguage
            }
            
            // 保存设置
            configurationService.updateGlobalSettings(newSettings)
            
            // 根据设置启动或停止自动更新服务
            if (enableAutoUpdateCheckBox.isSelected) {
                autoUpdateService.startAutoUpdate("timer")
            } else {
                autoUpdateService.stopAutoUpdate()
            }
            
            // 更新UI状态
            updateIntervalComboBox.isEnabled = enableAutoUpdateCheckBox.isSelected

            // 应用缓存TTL到缓存服务
            try {
                val ttlSeconds = ttlMinutes.toLong() * 60
                cacheService.setCacheConfig(
                    CacheConfig(
                        defaultTtlSeconds = ttlSeconds,
                        enabled = enableCacheCheckBox.isSelected
                    )
                )
            } catch (e: Exception) {
                // 应用缓存配置失败不阻塞主流程，仅提示
                Messages.showWarningDialog(
                    project,
                    "应用缓存配置失败：${e.message}",
                    "缓存配置"
                )
            }
            
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "保存更新设置失败：${e.message}",
                "设置保存失败"
            )
        }
    }
    
    /**
     * 加载更新设置
     */
    private fun loadUpdateSettings() {
        try {
            val settings = configurationService.getGlobalSettings()
            
            enableAutoUpdateCheckBox.isSelected = settings.enableAutoUpdate
            updateIntervalComboBox.isEnabled = settings.enableAutoUpdate
            enableCacheCheckBox.isSelected = settings.enableCache
            
            // 设置缓存TTL（分钟）
            val ttlMinutes = if (settings.cacheDefaultTtlMinutes <= 0) 15 else settings.cacheDefaultTtlMinutes
            cacheTtlSpinner.value = ttlMinutes
            
            // 根据间隔字符串设置下拉框
            val intervalIndex = when (settings.updateInterval) {
                "每天一次" -> 0
                "每周一次" -> 1
                "每月一次" -> 2
                else -> 0 // 默认每天一次
            }
            updateIntervalComboBox.selectedIndex = intervalIndex

            // 加载语言选择
            updateLanguageSelection(settings.displayLanguage)
            
            // 设置按钮初始状态
            val currentStatus = autoUpdateService.getUpdateStatus()
            currentUpdateStatus = currentStatus
            currentProgressMessage = null
            currentProgressValue = null
            currentErrorMessage = null
            checkUpdateButton.isEnabled = currentStatus == UpdateStatus.IDLE || currentStatus == UpdateStatus.ERROR || currentStatus == UpdateStatus.UP_TO_DATE
            
            // 获取当前更新信息
            val currentUpdateInfo = autoUpdateService.getCurrentUpdateInfo()
            if (currentUpdateInfo != null && (currentStatus == UpdateStatus.AVAILABLE || currentStatus == UpdateStatus.DOWNLOADED)) {
                // 恢复更新信息
                this.currentUpdateInfo = currentUpdateInfo
            }
            
            // 设置下载和安装按钮的初始状态
            downloadUpdateButton.isVisible = currentStatus == UpdateStatus.AVAILABLE
            downloadUpdateButton.isEnabled = currentStatus == UpdateStatus.AVAILABLE
            installUpdateButton.isVisible = currentStatus == UpdateStatus.DOWNLOADED
            installUpdateButton.isEnabled = currentStatus == UpdateStatus.DOWNLOADED
            
            refreshStatusLabelText()
            refreshAllTexts()
            
            // 注意：不在这里自动启动更新服务
            // 自动更新服务只应该通过用户手动操作或定时任务触发
            
        } catch (e: Exception) {
            // 如果加载失败，使用默认值
            enableAutoUpdateCheckBox.isSelected = false
            updateIntervalComboBox.selectedIndex = 0
            updateIntervalComboBox.isEnabled = false
            enableCacheCheckBox.isSelected = false // 默认禁用缓存
            cacheTtlSpinner.value = 15
            checkUpdateButton.isEnabled = true // 默认启用按钮
            downloadUpdateButton.isVisible = false // 默认隐藏
            installUpdateButton.isVisible = false // 默认隐藏
            currentUpdateStatus = UpdateStatus.IDLE
            currentProgressMessage = null
            currentProgressValue = null
            currentErrorMessage = null
            refreshStatusLabelText()
        }
    }
    
    /**
     * 手动检查更新
     */
    private fun checkForUpdatesManually() {
        checkUpdateButton.isEnabled = false
        currentUpdateStatus = UpdateStatus.CHECKING
        currentProgressMessage = null
        currentProgressValue = null
        currentErrorMessage = null
        refreshStatusLabelText()
        
        // 使用协程在后台执行更新检查
        coroutineScope.launch {
            try {
                autoUpdateService.checkForUpdates()
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    val errorMessage = when (e) {
                        is java.net.UnknownHostException -> "网络连接失败，请检查网络连接"
                        is java.net.SocketTimeoutException -> "连接超时，请稍后重试"
                        is java.net.ConnectException -> "无法连接到更新服务器"
                        else -> "检查更新失败: ${e.message}"
                    }
                    
                    currentUpdateStatus = UpdateStatus.ERROR
                    currentErrorMessage = errorMessage
                    currentProgressMessage = null
                    currentProgressValue = null
                    refreshStatusLabelText()
                    checkUpdateButton.isEnabled = true
                    
                    // 显示详细的错误对话框，并提供重试选项
                    val result = Messages.showOkCancelDialog(
                        "$errorMessage\n\n解决建议：\n" +
                        "1. 检查网络连接是否正常\n" +
                        "2. 确认防火墙未阻止网络访问\n" +
                        "3. 稍后重试检查更新\n" +
                        "4. 如问题持续，请联系技术支持\n\n" +
                        I18n.t("update.retry.prompt"),
                        I18n.t("update.check.fail.title"),
                        I18n.t("update.retry"),
                        I18n.t("action.cancel"),
                        Messages.getQuestionIcon()
                    )
                    
                    // 如果用户选择重试，则重新检查更新
                    if (result == Messages.OK) {
                        checkForUpdatesManually()
                    }
                }
            }
        }
    }
    
    /**
     * 手动下载更新
     */
    private fun downloadUpdateManually() {
        val updateInfo = currentUpdateInfo
        if (updateInfo == null) {
            Messages.showErrorDialog(this, I18n.t("update.no.info"), I18n.t("update.download.fail"))
            return
        }
        
        downloadUpdateButton.isEnabled = false
        currentUpdateStatus = UpdateStatus.DOWNLOADING
        currentProgressMessage = null
        currentProgressValue = null
        currentErrorMessage = null
        refreshStatusLabelText()
        
        autoUpdateService.downloadUpdateAsync(updateInfo) { progress ->
            SwingUtilities.invokeLater {
                currentProgressMessage = I18n.t("status.downloading")
                currentProgressValue = progress
                currentErrorMessage = null
                refreshStatusLabelText()
            }
        }
    }
    
    /**
     * 手动安装更新
     */
    private fun installUpdateManually() {
        val updateInfo = currentUpdateInfo
        if (updateInfo == null) {
            Messages.showErrorDialog(this, I18n.t("update.no.info"), I18n.t("update.install.fail"))
            return
        }
        
        // 确认安装对话框
        val result = Messages.showOkCancelDialog(
            this,
            MessageFormat.format(I18n.t("update.install.confirm"), updateInfo.version),
            I18n.t("update.install.confirm.title"),
            I18n.t("update.install"),
            I18n.t("action.cancel"),
            Messages.getQuestionIcon()
        )
        
        if (result != Messages.OK) {
            return
        }
        
        installUpdateButton.isEnabled = false
        currentUpdateStatus = UpdateStatus.INSTALLING
        currentProgressMessage = null
        currentProgressValue = null
        currentErrorMessage = null
        refreshStatusLabelText()
        
        autoUpdateService.installUpdateAsync(updateInfo)
    }
    
    /**
     * 取消下载更新
     */
    private fun cancelDownloadManually() {
        try {
            // 取消UI层的下载协程
            downloadJob?.let { job ->
                if (job.isActive) {
                    job.cancel()
                }
            }
            downloadJob = null
            
            // 取消服务层的下载任务
            val success = autoUpdateService.cancelDownload()
            
            // 重置UI状态
            SwingUtilities.invokeLater {
                downloadUpdateButton.isEnabled = true
                checkUpdateButton.isEnabled = true
                currentUpdateStatus = UpdateStatus.ERROR
                currentErrorMessage = I18n.t("update.download.canceled.status")
                currentProgressMessage = null
                currentProgressValue = null
                refreshStatusLabelText()
            }
            
            if (success) {
                Messages.showInfoMessage(
                    this,
                    I18n.t("update.download.canceled"),
                    I18n.t("update.cancel")
                )
            } else {
                Messages.showWarningDialog(
                    this,
                    I18n.t("update.download.none"),
                    I18n.t("update.cancel")
                )
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                this,
                MessageFormat.format(I18n.t("update.cancel.fail.msg"), e.message ?: ""),
                I18n.t("update.cancel.fail.title")
            )
        }
    }
    
    /**
     * 检查是否有修改
     */
    fun isModified(): Boolean {
        try {
            // 检查日志配置是否有修改
            val currentLogConfig = loggingService.getLoggingConfig()
            
            val retentionDays = retentionSpinner.value as Int
            val retentionTimeMs = retentionDays * 24L * 60L * 60L * 1000L
            
            val logConfigModified = currentLogConfig.retentionTimeMs != retentionTimeMs ||
                    currentLogConfig.enabled != autoCleanCheckBox.isSelected ||
                    currentLogConfig.logSensitiveData != logSensitiveDataCheckBox.isSelected
            
            // 检查更新设置是否有修改
            val currentSettings = configurationService.getGlobalSettings()
            val intervalString = updateIntervalComboBox.selectedItem as String
            val ttlMinutes = cacheTtlSpinner.value as Int
            
            val updateSettingsModified = currentSettings.enableAutoUpdate != enableAutoUpdateCheckBox.isSelected ||
                    currentSettings.updateInterval != intervalString ||
                    currentSettings.enableCache != enableCacheCheckBox.isSelected ||
                    currentSettings.cacheDefaultTtlMinutes != ttlMinutes
            
            return logConfigModified || updateSettingsModified
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * 应用更改
     */
    fun apply() {
        try {
            // 保存日志配置
            saveConfiguration()
            
            // 保存更新设置
            saveUpdateSettings()
        } catch (e: Exception) {
            throw e
        }
    }

    private fun setupLanguageChangeListener() {
        LanguageManager.addChangeListener(languageChangeListener)
    }
    
    private fun refreshAllTexts() {
        // 刷新标题
        systemTitleLabel.text = I18n.t("header.systemSettings")
        
        // 刷新面板边框标题
        logInfoPanel.border = TitledBorder(I18n.t("log.info"))
        logConfigPanel.border = TitledBorder(I18n.t("log.config"))
        cacheSettingsPanel.border = TitledBorder(I18n.t("cache.settings"))
        updateSettingsPanel.border = TitledBorder(I18n.t("update.settings"))
        languageSettingsPanel.border = TitledBorder(I18n.t("language.display"))
        
        // 刷新按钮文本
        checkUpdateButton.text = I18n.t("update.check.now")
        downloadUpdateButton.text = I18n.t("update.download")
        cancelDownloadButton.text = I18n.t("update.cancel")
        installUpdateButton.text = I18n.t("update.install")
        clearCacheButton.text = I18n.t("cache.clear")
        openFolderButton.text = I18n.t("log.open")
        clearLogButton.text = I18n.t("log.clear")
        
        // 刷新复选框文本
        enableAutoUpdateCheckBox.text = I18n.t("update.enable.auto")
        enableCacheCheckBox.text = I18n.t("cache.enable")
        autoCleanCheckBox.text = I18n.t("log.auto.clean")
        logSensitiveDataCheckBox.text = I18n.t("log.record.calls")
        
        // 刷新语言选项文本
        languageZhRadio.text = I18n.t("language.zh")
        languageEnRadio.text = I18n.t("language.en")
        
        // 刷新静态标签文本
        logPathStaticLabel.text = I18n.t("log.path")
        logSizeStaticLabel.text = I18n.t("log.size")
        logCountStaticLabel.text = I18n.t("log.count")
        logRetentionStaticLabel.text = I18n.t("log.retention")
        logDaysStaticLabel.text = I18n.t("log.days")
        cacheTtlStaticLabel.text = I18n.t("cache.ttl")
        updateFrequencyStaticLabel.text = I18n.t("update.frequency")
        languageDisplayStaticLabel.text = I18n.t("language.display.label")
        
        // 刷新下拉框选项
        val currentIndex = updateIntervalComboBox.selectedIndex
        updateIntervalComboBox.removeAllItems()
        updateIntervalComboBox.addItem(I18n.t("update.daily"))
        updateIntervalComboBox.addItem(I18n.t("update.weekly"))
        updateIntervalComboBox.addItem(I18n.t("update.monthly"))
        updateIntervalComboBox.selectedIndex = currentIndex
        
        refreshStatusLabelText()

        // 重新布局
        revalidate()
        repaint()
    }

    private fun refreshStatusLabelText() {
        if (!::updateStatusLabel.isInitialized) {
            return
        }

        val newText = when {
            currentErrorMessage != null -> MessageFormat.format(I18n.t("status.error.details"), currentErrorMessage)
            currentProgressMessage != null -> {
                val baseMessage = currentProgressMessage!!
                val progressValue = currentProgressValue
                val hasStatusPrefix = baseMessage.startsWith("Status:") || baseMessage.startsWith("状态：")
                val progressSuffix = progressValue?.takeIf { it > 0 }?.let { "${it}%" }

                when {
                    hasStatusPrefix && progressSuffix != null -> "$baseMessage ${progressSuffix}"
                    hasStatusPrefix -> baseMessage
                    progressSuffix != null -> MessageFormat.format(I18n.t("status.progress"), baseMessage, progressSuffix)
                    else -> MessageFormat.format(I18n.t("status.progress"), baseMessage, "")
                }
            }
            else -> when (currentUpdateStatus) {
                UpdateStatus.IDLE -> I18n.t("status.idle")
                UpdateStatus.CHECKING -> I18n.t("status.checking")
                UpdateStatus.AVAILABLE -> MessageFormat.format(I18n.t("status.available"), currentUpdateInfo?.version ?: "")
                UpdateStatus.DOWNLOADING -> I18n.t("status.downloading")
                UpdateStatus.DOWNLOADED -> I18n.t("status.downloaded")
                UpdateStatus.INSTALLING -> I18n.t("status.installing")
                UpdateStatus.INSTALLED -> I18n.t("status.installed")
                UpdateStatus.UP_TO_DATE -> I18n.t("status.up_to_date")
                UpdateStatus.ERROR -> I18n.t("status.error")
            }
        }

        updateStatusLabel.text = newText
    }
    
    private fun createLanguageSettingsPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        panel.border = TitledBorder(I18n.t("language.display"))

        languageZhRadio = JRadioButton(I18n.t("language.zh"))
        languageEnRadio = JRadioButton(I18n.t("language.en"))

        languageButtonGroup = ButtonGroup()
        languageButtonGroup.add(languageZhRadio)
        languageButtonGroup.add(languageEnRadio)

        // 切换语言后实时刷新部分文案并立即保存
        val onChange: () -> Unit = onChange@{
            if (suppressLanguageEvents) {
                return@onChange
            }
            val requestedCode = when {
                languageZhRadio.isSelected -> "zh_CN"
                languageEnRadio.isSelected -> "en_US"
                else -> LanguageManager.getLanguageCode()
            }

            if (languageController.isCurrentLanguage(requestedCode)) {
                updateLanguageSelection(requestedCode)
                return@onChange
            }

            SwingUtilities.invokeLater {
                languageController.applyLanguage(requestedCode)
                refreshAllTexts()
                updateLanguageSelection(requestedCode)
            }
        }

        languageZhRadio.addActionListener { onChange() }
        languageEnRadio.addActionListener { onChange() }

        languageDisplayStaticLabel = JBLabel(I18n.t("language.display.label"))
        panel.add(languageDisplayStaticLabel)
        panel.add(languageZhRadio)
        panel.add(languageEnRadio)
        updateLanguageSelection(LanguageManager.getLanguageCode())
        return panel
    }

    private fun updateLanguageSelection(languageCode: String) {
        if (!::languageZhRadio.isInitialized || !::languageEnRadio.isInitialized) {
            return
        }

        val normalized = languageController.normalizeLanguageCode(languageCode)
        suppressLanguageEvents = true
        try {
            when (normalized) {
                "en_US" -> {
                    if (!languageEnRadio.isSelected) {
                        languageEnRadio.isSelected = true
                    }
                }
                else -> {
                    if (!languageZhRadio.isSelected) {
                        languageZhRadio.isSelected = true
                    }
                }
            }
        } finally {
            suppressLanguageEvents = false
        }
    }

    /**
     * 清理资源，取消所有正在运行的协程
     */
    fun dispose() {
        // 移除语言变化监听器
        LanguageManager.removeChangeListener(languageChangeListener)

        // 如果有正在进行的下载，先取消下载
        // 取消所有协程
        coroutineScope.cancel()

        autoUpdateService.removeStatusListener(updateStatusListener)
    }
}