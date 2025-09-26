package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.service.LoggingService
import cn.suso.aicodetransformer.service.LogLevel
import cn.suso.aicodetransformer.service.LoggingConfig
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.TitledBorder

/**
 * 日志管理面板
 */
class LogManagementPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val loggingService: LoggingService = service()
    private val logPathLabel = JBLabel()
    private val logSizeLabel = JBLabel()
    private val logCountLabel = JBLabel()
    
    // 配置UI组件
    private lateinit var levelComboBox: JComboBox<String>
    private lateinit var retentionSpinner: JSpinner
    private lateinit var autoCleanCheckBox: JCheckBox
    private lateinit var logSensitiveDataCheckBox: JCheckBox
    
    init {
        setupUI()
        refreshLogInfo()
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
        
        // 操作按钮面板
        val buttonPanel = createButtonPanel()
        mainPanel.add(buttonPanel)
        
        // 添加垂直间距，将内容推到顶部
        mainPanel.add(Box.createVerticalGlue())
        
        add(mainPanel, BorderLayout.NORTH)
    }
    
    private fun createHeaderPanel(): JPanel {
        val headerPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.border = EmptyBorder(JBUI.insets(0, 0, 8, 0))
        
        val titleLabel = JBLabel("日志管理")
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
        panel.border = TitledBorder("配置")
        
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
        
        // 加载当前配置
        loadConfiguration()
        
        return panel
    }
    
    private fun createButtonPanel(): JPanel {
        val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 5))
        buttonPanel.border = EmptyBorder(JBUI.insets(8, 0, 0, 0))
        
        val refreshButton = JButton("刷新")
        refreshButton.addActionListener { refreshLogInfo() }
        buttonPanel.add(refreshButton)
        
        val viewLogsButton = JButton("查看")
        viewLogsButton.addActionListener { viewLogs() }
        buttonPanel.add(viewLogsButton)
        
        val clearButton = JButton("清空")
        clearButton.addActionListener { clearLogs() }
        buttonPanel.add(clearButton)
        
        return buttonPanel
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
}