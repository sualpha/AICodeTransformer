package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.service.LoggingService
import cn.suso.aicodetransformer.service.LogLevel
import cn.suso.aicodetransformer.service.LoggingConfig
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.AutoUpdateService
import cn.suso.aicodetransformer.service.UpdateStatus
import cn.suso.aicodetransformer.service.UpdateInfo
import cn.suso.aicodetransformer.service.UpdateStatusListener
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.TitledBorder

/**
 * 系统设置面板 - 整合日志管理和更新设置
 */
class SystemManagementPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val loggingService: LoggingService = service()
    private val configurationService: ConfigurationService = service()
    private val autoUpdateService: AutoUpdateService = service()
    private val logPathLabel = JBLabel()
    private val logSizeLabel = JBLabel()
    private val logCountLabel = JBLabel()
    
    // 日志配置UI组件
    private lateinit var levelComboBox: JComboBox<String>
    private lateinit var retentionSpinner: JSpinner
    private lateinit var autoCleanCheckBox: JCheckBox
    private lateinit var logSensitiveDataCheckBox: JCheckBox
    
    // 更新设置相关组件
    private lateinit var enableAutoUpdateCheckBox: JCheckBox
    private lateinit var updateIntervalComboBox: JComboBox<String>
    private lateinit var updateStatusLabel: JBLabel
    private lateinit var checkUpdateButton: JButton
    private lateinit var downloadUpdateButton: JButton
    private lateinit var installUpdateButton: JButton
    
    // 当前可用的更新信息
    private var currentUpdateInfo: UpdateInfo? = null
    
    init {
        setupUI()
        refreshLogInfo()
        loadConfiguration()
        loadUpdateSettings()
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
        val infoPanel = createLogInfoPanel()
        mainPanel.add(infoPanel)
        mainPanel.add(Box.createVerticalStrut(8))
        
        // 日志配置面板
        val configPanel = createLogConfigPanel()
        mainPanel.add(configPanel)
        mainPanel.add(Box.createVerticalStrut(12))
        
        // 更新设置面板
        val updatePanel = createUpdateSettingsPanel()
        mainPanel.add(updatePanel)
        
        // 添加垂直间距，将内容推到顶部
        mainPanel.add(Box.createVerticalGlue())
        
        add(mainPanel, BorderLayout.NORTH)
    }
    
    private fun createHeaderPanel(): JPanel {
        val headerPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.border = EmptyBorder(JBUI.insets(0, 0, 8, 0))
        
        val titleLabel = JBLabel("系统设置")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        titleLabel.foreground = UIUtil.getLabelForeground()
        
        headerPanel.add(titleLabel)
        return headerPanel
    }
    

    
    private fun createLogInfoPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder("日志信息")
        
        // 日志文件路径和统计信息合并到一行
        val infoPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        infoPanel.add(JBLabel("路径："))
        logPathLabel.foreground = UIUtil.getLabelForeground()
        infoPanel.add(logPathLabel)
        
        val openFolderButton = JButton("打开")
        openFolderButton.addActionListener { openLogFolder() }
        infoPanel.add(openFolderButton)
        
        infoPanel.add(Box.createHorizontalStrut(15))
        infoPanel.add(JBLabel("大小："))
        infoPanel.add(logSizeLabel)
        infoPanel.add(Box.createHorizontalStrut(15))
        infoPanel.add(JBLabel("条目："))
        infoPanel.add(logCountLabel)
        
        panel.add(infoPanel)
        
        return panel
    }
    
    private fun createLogConfigPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder("日志配置")
        
        // 将所有配置项合并到一行
        val configPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        
        configPanel.add(JBLabel("级别："))
        val logLevels = arrayOf("DEBUG", "INFO", "WARNING", "ERROR")
        levelComboBox = JComboBox(logLevels)
        configPanel.add(levelComboBox)
        
        configPanel.add(Box.createHorizontalStrut(15))
        configPanel.add(JBLabel("保留："))
        retentionSpinner = JSpinner(SpinnerNumberModel(30, 1, 365, 1))
        configPanel.add(retentionSpinner)
        configPanel.add(JBLabel("天"))
        
        configPanel.add(Box.createHorizontalStrut(15))
        autoCleanCheckBox = JCheckBox("自动清理", true)
        configPanel.add(autoCleanCheckBox)
        
        configPanel.add(Box.createHorizontalStrut(15))
        logSensitiveDataCheckBox = JCheckBox("记录调用日志", false)
        configPanel.add(logSensitiveDataCheckBox)
        
        // 添加保存按钮
        configPanel.add(Box.createHorizontalStrut(15))
        val saveConfigButton = JButton("保存配置")
        saveConfigButton.addActionListener { saveConfiguration() }
        configPanel.add(saveConfigButton)
        
        panel.add(configPanel)
        
        // 添加日志操作按钮
        panel.add(Box.createVerticalStrut(8))
        val logOperationPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        
        val refreshButton = JButton("刷新")
        refreshButton.addActionListener { refreshLogInfo() }
        logOperationPanel.add(refreshButton)
        
        val viewLogsButton = JButton("查看")
        viewLogsButton.addActionListener { viewLogs() }
        logOperationPanel.add(viewLogsButton)
        
        val clearButton = JButton("清空")
        clearButton.addActionListener { clearLogs() }
        logOperationPanel.add(clearButton)
        
        panel.add(logOperationPanel)
        
        return panel
    }
    
    private fun createUpdateSettingsPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder("更新设置")
        
        // 第一行：自动更新配置
        val updateConfigPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        
        // 自动更新勾选框
        enableAutoUpdateCheckBox = JCheckBox("启用自动更新检查", false)
        enableAutoUpdateCheckBox.addActionListener { 
            updateIntervalComboBox.isEnabled = enableAutoUpdateCheckBox.isSelected
            if (enableAutoUpdateCheckBox.isSelected) {
                autoUpdateService.startAutoUpdate()
            } else {
                autoUpdateService.stopAutoUpdate()
            }
        }
        updateConfigPanel.add(enableAutoUpdateCheckBox)
        
        updateConfigPanel.add(Box.createHorizontalStrut(15))
        updateConfigPanel.add(JBLabel("检查频率："))
        
        // 检查频率下拉框
        val updateIntervals = arrayOf("每天一次", "每周一次", "每月一次")
        updateIntervalComboBox = JComboBox(updateIntervals)
        updateIntervalComboBox.selectedIndex = 0 // 默认选择"每天一次"
        updateConfigPanel.add(updateIntervalComboBox)
        
        // 添加保存按钮
        updateConfigPanel.add(Box.createHorizontalStrut(15))
        val saveUpdateButton = JButton("保存更新设置")
        saveUpdateButton.addActionListener { saveUpdateSettings() }
        updateConfigPanel.add(saveUpdateButton)
        
        panel.add(updateConfigPanel)
        
        // 第二行：手动检查和状态显示
        val statusPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        
        // 手动检查更新按钮
        checkUpdateButton = JButton("立即检查更新")
        checkUpdateButton.addActionListener { checkForUpdatesManually() }
        statusPanel.add(checkUpdateButton)
        
        statusPanel.add(Box.createHorizontalStrut(15))
        
        // 更新状态显示
        updateStatusLabel = JBLabel("状态：未检查")
        statusPanel.add(updateStatusLabel)
        
        panel.add(statusPanel)
        
        // 第三行：下载和安装更新按钮
        val updateActionPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 2))
        
        // 下载更新按钮
        downloadUpdateButton = JButton("下载更新")
        downloadUpdateButton.isVisible = false // 初始隐藏
        downloadUpdateButton.addActionListener { downloadUpdateManually() }
        updateActionPanel.add(downloadUpdateButton)
        
        updateActionPanel.add(Box.createHorizontalStrut(10))
        
        // 安装更新按钮
        installUpdateButton = JButton("安装更新")
        installUpdateButton.isVisible = false // 初始隐藏
        installUpdateButton.addActionListener { installUpdateManually() }
        updateActionPanel.add(installUpdateButton)
        
        panel.add(updateActionPanel)
        
        // 注册更新状态监听器
        autoUpdateService.addStatusListener(object : UpdateStatusListener {
            override fun onStatusChanged(oldStatus: UpdateStatus, newStatus: UpdateStatus, updateInfo: UpdateInfo?) {
                SwingUtilities.invokeLater {
                    // 保存当前更新信息
                    currentUpdateInfo = updateInfo
                    
                    updateStatusLabel.text = when (newStatus) {
                        UpdateStatus.IDLE -> "状态：空闲"
                        UpdateStatus.CHECKING -> "状态：检查中..."
                        UpdateStatus.AVAILABLE -> "状态：发现新版本 ${updateInfo?.version ?: ""}"
                        UpdateStatus.DOWNLOADING -> "状态：下载中..."
                        UpdateStatus.DOWNLOADED -> "状态：下载完成"
                        UpdateStatus.INSTALLING -> "状态：安装中..."
                        UpdateStatus.INSTALLED -> "状态：安装完成"
                        UpdateStatus.UP_TO_DATE -> "状态：已是最新版本"
                        UpdateStatus.ERROR -> "状态：检查失败"
                        else -> "状态：未知"
                    }
                    
                    // 控制按钮状态
                    checkUpdateButton.isEnabled = newStatus == UpdateStatus.IDLE
                    
                    // 控制下载按钮
                    downloadUpdateButton.isVisible = newStatus == UpdateStatus.AVAILABLE
                    downloadUpdateButton.isEnabled = newStatus == UpdateStatus.AVAILABLE
                    
                    // 控制安装按钮
                    installUpdateButton.isVisible = newStatus == UpdateStatus.DOWNLOADED
                    installUpdateButton.isEnabled = newStatus == UpdateStatus.DOWNLOADED
                    
                    // 重新布局以适应按钮的显示/隐藏
                    revalidate()
                    repaint()
                }
            }
            
            override fun onProgressChanged(progress: Int, message: String) {
                SwingUtilities.invokeLater {
                    if (progress > 0) {
                        updateStatusLabel.text = "状态：$message ${progress}%"
                    } else {
                        updateStatusLabel.text = "状态：$message"
                    }
                }
            }
            
            override fun onError(error: String) {
                SwingUtilities.invokeLater {
                    updateStatusLabel.text = "状态：错误 - $error"
                    checkUpdateButton.isEnabled = true
                }
            }
        })
        
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
    
    private fun viewLogs() {
        try {
            val logFilePath = getLogFilePath()
            val logFile = File(logFilePath)
            
            if (logFile.exists()) {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(logFile)
                } else {
                    Messages.showInfoMessage(
                        project,
                        "请手动打开日志文件：${logFile.absolutePath}",
                        "查看日志"
                    )
                }
            } else {
                Messages.showWarningDialog(
                    project,
                    "日志文件不存在，可能还没有API调用记录",
                    "文件不存在"
                )
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "打开日志文件失败：${e.message}",
                "操作失败"
            )
        }
    }
    

    
    private fun clearLogs() {
        val result = Messages.showYesNoDialog(
            project,
            "确定要清空所有日志吗？此操作不可恢复。",
            "确认清空",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            try {
                val logFilePath = getLogFilePath()
                val logFile = File(logFilePath)
                
                if (logFile.exists()) {
                    logFile.delete()
                    refreshLogInfo()
                    Messages.showInfoMessage(
                        project,
                        "日志已成功清空",
                        "操作成功"
                    )
                } else {
                    Messages.showInfoMessage(
                        project,
                        "日志文件不存在，无需清空",
                        "提示"
                    )
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "清空日志失败：${e.message}",
                    "操作失败"
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
            
            // 设置日志级别
            levelComboBox.selectedItem = when (config.minLevel) {
                LogLevel.DEBUG -> "DEBUG"
                LogLevel.INFO -> "INFO"
                LogLevel.WARNING -> "WARNING"
                LogLevel.ERROR -> "ERROR"
            }
            
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
            
            // 获取选择的日志级别
            val selectedLevel = when (levelComboBox.selectedItem as String) {
                "DEBUG" -> LogLevel.DEBUG
                "INFO" -> LogLevel.INFO
                "WARNING" -> LogLevel.WARNING
                "ERROR" -> LogLevel.ERROR
                else -> LogLevel.INFO
            }
            
            // 获取保留天数（转换为毫秒）
            val retentionDays = retentionSpinner.value as Int
            val retentionTimeMs = retentionDays * 24L * 60L * 60L * 1000L
            
            // 创建新的配置
            val newConfig = LoggingConfig(
                enabled = autoCleanCheckBox.isSelected,
                minLevel = selectedLevel,
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
            
            Messages.showInfoMessage(
                project,
                "日志配置已成功保存",
                "配置保存成功"
            )
            
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
            val intervalString = updateIntervalComboBox.selectedItem as String
            
            // 获取检查间隔（小时）
            val intervalHours = when (intervalString) {
                "每天一次" -> 24
                "每周一次" -> 24 * 7
                "每月一次" -> 24 * 30
                else -> 24
            }
            
            // 创建新的全局设置
            val newSettings = currentSettings.copy(
                enableAutoUpdate = enableAutoUpdateCheckBox.isSelected,
                updateInterval = intervalString,
                updateCheckIntervalHours = intervalHours,
                lastUpdateCheckTime = if (enableAutoUpdateCheckBox.isSelected) System.currentTimeMillis() else 0L
            )
            
            // 保存设置
            configurationService.updateGlobalSettings(newSettings)
            
            // 根据设置启动或停止自动更新服务
            if (enableAutoUpdateCheckBox.isSelected) {
                autoUpdateService.startAutoUpdate()
            } else {
                autoUpdateService.stopAutoUpdate()
            }
            
            // 更新UI状态
            updateIntervalComboBox.isEnabled = enableAutoUpdateCheckBox.isSelected
            
            Messages.showInfoMessage(
                project,
                "更新设置已成功保存",
                "设置保存成功"
            )
            
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
            
            // 根据间隔字符串设置下拉框
            val intervalIndex = when (settings.updateInterval) {
                "每天一次" -> 0
                "每周一次" -> 1
                "每月一次" -> 2
                else -> 0 // 默认每天一次
            }
            updateIntervalComboBox.selectedIndex = intervalIndex
            
            // 设置按钮初始状态
            val currentStatus = autoUpdateService.getUpdateStatus()
            checkUpdateButton.isEnabled = currentStatus == UpdateStatus.IDLE
            
            // 设置下载和安装按钮的初始状态
            downloadUpdateButton.isVisible = currentStatus == UpdateStatus.AVAILABLE
            downloadUpdateButton.isEnabled = currentStatus == UpdateStatus.AVAILABLE
            installUpdateButton.isVisible = currentStatus == UpdateStatus.DOWNLOADED
            installUpdateButton.isEnabled = currentStatus == UpdateStatus.DOWNLOADED
            
            updateStatusLabel.text = when (currentStatus) {
                UpdateStatus.IDLE -> "状态：空闲"
                UpdateStatus.CHECKING -> "状态：检查中..."
                UpdateStatus.AVAILABLE -> "状态：发现新版本"
                UpdateStatus.DOWNLOADING -> "状态：下载中..."
                UpdateStatus.DOWNLOADED -> "状态：下载完成"
                UpdateStatus.INSTALLING -> "状态：安装中..."
                UpdateStatus.INSTALLED -> "状态：安装完成"
                UpdateStatus.UP_TO_DATE -> "状态：已是最新版本"
                UpdateStatus.ERROR -> "状态：检查失败"
                else -> "状态：未检查"
            }
            
            // 如果启用了自动更新，启动服务
            if (settings.enableAutoUpdate) {
                autoUpdateService.startAutoUpdate()
            }
            
        } catch (e: Exception) {
            // 如果加载失败，使用默认值
            enableAutoUpdateCheckBox.isSelected = false
            updateIntervalComboBox.selectedIndex = 0
            updateIntervalComboBox.isEnabled = false
            checkUpdateButton.isEnabled = true // 默认启用按钮
            downloadUpdateButton.isVisible = false // 默认隐藏
            installUpdateButton.isVisible = false // 默认隐藏
            updateStatusLabel.text = "状态：未检查"
        }
    }
    
    /**
     * 手动检查更新
     */
    private fun checkForUpdatesManually() {
        checkUpdateButton.isEnabled = false
        updateStatusLabel.text = "状态：检查中..."
        
        // 使用协程在后台执行更新检查
        GlobalScope.launch {
            try {
                autoUpdateService.checkForUpdates()
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    updateStatusLabel.text = "状态：检查失败 - ${e.message}"
                    checkUpdateButton.isEnabled = true
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
            Messages.showErrorDialog(this, "没有可用的更新信息", "下载失败")
            return
        }
        
        downloadUpdateButton.isEnabled = false
        updateStatusLabel.text = "状态：准备下载..."
        
        // 使用协程在后台执行下载
        GlobalScope.launch {
            try {
                val success = autoUpdateService.downloadUpdate(updateInfo) { progress ->
                    SwingUtilities.invokeLater {
                        updateStatusLabel.text = "状态：下载中... $progress%"
                    }
                }
                
                if (!success) {
                    SwingUtilities.invokeLater {
                        Messages.showErrorDialog(this@SystemManagementPanel, "下载失败，请检查网络连接", "下载失败")
                        downloadUpdateButton.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(this@SystemManagementPanel, "下载失败: ${e.message}", "下载失败")
                    downloadUpdateButton.isEnabled = true
                }
            }
        }
    }
    
    /**
     * 手动安装更新
     */
    private fun installUpdateManually() {
        val updateInfo = currentUpdateInfo
        if (updateInfo == null) {
            Messages.showErrorDialog(this, "没有可用的更新信息", "安装失败")
            return
        }
        
        // 确认安装对话框
        val result = Messages.showYesNoDialog(
            this,
            "确定要安装更新到版本 ${updateInfo.version} 吗？\n安装完成后需要重启 IntelliJ IDEA。",
            "确认安装更新",
            Messages.getQuestionIcon()
        )
        
        if (result != Messages.YES) {
            return
        }
        
        installUpdateButton.isEnabled = false
        updateStatusLabel.text = "状态：准备安装..."
        
        // 使用协程在后台执行安装
        GlobalScope.launch {
            try {
                val success = autoUpdateService.installUpdate(updateInfo)
                
                SwingUtilities.invokeLater {
                    if (success) {
                        Messages.showInfoMessage(
                            this@SystemManagementPanel,
                            "更新安装完成！请重启 IntelliJ IDEA 以使用新版本。",
                            "安装完成"
                        )
                    } else {
                        Messages.showErrorDialog(this@SystemManagementPanel, "安装失败，请稍后重试", "安装失败")
                        installUpdateButton.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(this@SystemManagementPanel, "安装失败: ${e.message}", "安装失败")
                    installUpdateButton.isEnabled = true
                }
            }
        }
    }
}