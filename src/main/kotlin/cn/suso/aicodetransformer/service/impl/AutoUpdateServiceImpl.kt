package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.constants.DownloadState
import cn.suso.aicodetransformer.constants.UpdateRecordStatusConstants
import cn.suso.aicodetransformer.constants.UpdateStatus
import cn.suso.aicodetransformer.model.BackupInfo
import cn.suso.aicodetransformer.model.UpdateInfo
import cn.suso.aicodetransformer.model.UpdateRecord
import cn.suso.aicodetransformer.service.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * 自动更新服务实现类
 */
@Service(Service.Level.APP)
class AutoUpdateServiceImpl : AutoUpdateService {
    
    companion object {
        private const val UPDATE_CHECK_URL = "https://api.github.com/repos/sualpha/AICodeTransformer/releases/latest"
        private const val CURRENT_VERSION = "1.0.0" // 从插件配置中读取
        private const val UPDATE_HISTORY_FILE = "update_history.json"
    }
    
    private val configurationService: ConfigurationService = service()
    private val loggingService: LoggingService = service()
    
    // Logger instance for this service
    private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java)
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private var currentStatus = UpdateStatus.IDLE
    private var currentUpdateInfo: UpdateInfo? = null
    private val listeners = CopyOnWriteArrayList<UpdateStatusListener>()
    private var updateJob: Job? = null
    private var downloadJob: Job? = null
    private val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateTimer: Timer? = null
    
    /**
     * 统一的网络异常处理方法
     */
    private fun handleNetworkException(e: Exception, operation: String): String {
        return when (e) {
            is SocketTimeoutException -> {
                logger.error("${operation}超时", e)
                "${operation}超时，请检查网络连接"
            }
            is UnknownHostException -> {
                logger.error("${operation}失败：无法解析主机", e)
                "${operation}失败：无法连接到服务器，请检查网络连接"
            }
            is ConnectException -> {
                logger.error("${operation}失败：连接被拒绝", e)
                "${operation}失败：无法连接到服务器"
            }
            is IOException -> {
                logger.error("${operation}失败：IO异常", e)
                "${operation}失败：网络IO错误"
            }
            else -> {
                logger.error("${operation}失败：未知错误", e)
                "${operation}失败：${e.message ?: "未知错误"}"
            }
        }
    }
    
    /**
     * 统一的日志记录工具方法
     */
    private fun logOperationStart(operation: String, details: String = "") {
        val message = if (details.isNotEmpty()) "$operation - $details" else operation
        logger.info("开始$message")
    }
    
    private fun logOperationSuccess(operation: String, details: String = "") {
        val message = if (details.isNotEmpty()) "$operation - $details" else operation
        logger.info("${message}成功")
    }
    
    private fun logOperationError(operation: String, error: String, exception: Exception? = null) {
        val message = "$operation 失败: $error"
        if (exception != null) {
            logger.error(message, exception)
        } else {
            logger.error(message)
        }
    }
    
    // 防重复下载机制
    private var isDownloading = false
    private var isAutoUpdateRunning = false
    private var currentDownloadFile: File? = null
    private var downloadStartTime: Long = 0
    private val downloadLock = Any()
    
    // 下载进度监控
    @Volatile
    private var lastProgressTime: Long = 0
    @Volatile
    private var lastProgressBytes: Long = 0
    @Volatile
    private var currentProgressBytes: Long = 0
    @Volatile
    private var totalBytes: Long = 0
    private val progressMonitoringInterval = 5000L // 5秒检查一次进度
    private val downloadStuckThreshold = 30000L // 30秒无进度视为卡住
    

    
    @Volatile
    private var currentDownloadState = DownloadState.IDLE
    
    init {
        logger.info("AutoUpdateService 初始化完成")
    }
    
    override suspend fun checkForUpdates(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                updateStatus(UpdateStatus.CHECKING)
                notifyProgress(0, "正在检查更新...")
                
                logger.info("开始检查更新")
                
                // 模拟检查更新（实际应该调用真实的API）
                val updateInfo = checkRemoteVersion()
                
                if (updateInfo != null) {
                    val currentVersion = getCurrentVersion()
                    if (isNewerVersion(updateInfo.version, currentVersion)) {
                        updateStatus(UpdateStatus.AVAILABLE, updateInfo)
                        notifyProgress(100, "发现新版本: ${updateInfo.version}")
                        logger.info("发现新版本: ${updateInfo.version}")
                        loggingService.logInfo("发现新版本: ${updateInfo.version}", "自动更新")
                        return@withContext updateInfo
                    } else {
                        updateStatus(UpdateStatus.UP_TO_DATE)
                        notifyProgress(100, "已是最新版本")
                        logger.info("当前已是最新版本")
                        return@withContext null
                    }
                } else {
                    updateStatus(UpdateStatus.UP_TO_DATE)
                    notifyProgress(100, "检查完成，已是最新版本")
                    return@withContext null
                }
                
            } catch (e: Exception) {
                logger.error("检查更新失败", e)
                loggingService.logError(e, "检查更新失败")
                updateStatus(UpdateStatus.ERROR)
                notifyError("检查更新失败: ${e.message}")
                null
            }
        }
    }
    
    override suspend fun downloadUpdate(updateInfo: UpdateInfo, onProgress: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            // 保存当前下载协程的Job
            downloadJob = coroutineContext[Job]
            // 防重复下载检查
            synchronized(downloadLock) {
                if (isDownloading) {
                    logger.warn("下载已在进行中，跳过重复下载请求")
                    loggingService.logInfo("下载已在进行中，跳过重复下载请求", "自动更新")
                    return@withContext false
                }
                updateDownloadState(DownloadState.PREPARING, "开始准备下载")
                downloadStartTime = System.currentTimeMillis()
            }
            
            try {
                updateStatus(UpdateStatus.DOWNLOADING, updateInfo)
                notifyProgress(0, "开始下载更新...")
                
                logger.info("开始下载更新: ${updateInfo.version}")
                loggingService.logInfo("开始下载更新: ${updateInfo.version}", "自动更新")
                
                val downloadDir = File(System.getProperty("java.io.tmpdir"), "aicodetransformer_updates")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                
                // 支持.jar和.zip文件
                val urlFileName = updateInfo.downloadUrl.substringAfterLast("/")
                val fileExtension = when {
                    urlFileName.endsWith(".jar") -> ".jar"
                    urlFileName.endsWith(".zip") -> ".zip"
                    else -> ".jar" // 默认为jar
                }
                val fileName = "aicodetransformer-${updateInfo.version}$fileExtension"
                val downloadFile = File(downloadDir, fileName)
                
                // 设置当前下载文件
                currentDownloadFile = downloadFile
                
                // 检查是否已存在完整的下载文件
                if (downloadFile.exists()) {
                    notifyProgress(50, "检查已下载文件...")
                    val existingChecksum = calculateChecksum(downloadFile)
                    if (existingChecksum == updateInfo.checksum && updateInfo.checksum.isNotEmpty()) {
                        updateStatus(UpdateStatus.DOWNLOADED, updateInfo)
                        notifyProgress(100, "文件已存在，跳过下载")
                        logger.info("文件已存在且校验通过，跳过下载: ${downloadFile.absolutePath}")
                        return@withContext true
                    } else {
                        // 文件损坏或校验不匹配，删除重新下载
                        downloadFile.delete()
                        logger.info("已存在文件校验失败，重新下载")
                    }
                }
                
                // 启动进度监控
                val progressMonitorJob = startProgressMonitoring()
                
                // 更新状态为下载中
                updateDownloadState(DownloadState.DOWNLOADING, "开始下载文件")
                
                // 下载文件
                val success = downloadFileWithResume(updateInfo.downloadUrl, downloadFile) { progress ->
                    onProgress(progress)
                }
                
                // 停止进度监控
                progressMonitorJob.cancel()
                
                if (success) {
                    // 更新状态为校验中
                    updateDownloadState(DownloadState.VERIFYING, "验证文件完整性")
                    notifyProgress(90, "验证文件完整性...")
                    val verificationResult = verifyFileIntegrity(downloadFile, updateInfo.checksum)
                    
                    if (verificationResult) {
                        updateDownloadState(DownloadState.COMPLETED, "下载并校验完成")
                        updateStatus(UpdateStatus.DOWNLOADED, updateInfo)
                        notifyProgress(100, "下载完成")
                        logger.info("更新下载完成: ${downloadFile.absolutePath}")
                        loggingService.logInfo("更新下载完成: ${downloadFile.absolutePath}", "自动更新")
                        true
                    } else {
                        updateDownloadState(DownloadState.FAILED, "文件完整性校验失败")
                        logger.error("文件完整性校验失败")
                        loggingService.logError(Exception("文件完整性校验失败"), "自动更新")
                        updateStatus(UpdateStatus.ERROR)
                        notifyError("文件完整性校验失败，请重试下载")
                        
                        // 删除校验失败的文件
                        try {
                            if (downloadFile.exists()) {
                                downloadFile.delete()
                                logger.info("已删除校验失败的文件: ${downloadFile.name}")
                            }
                        } catch (e: Exception) {
                            logger.warn("删除校验失败的文件时出错", e)
                        }
                         false
                    }
                } else {
                    updateDownloadState(DownloadState.FAILED, "下载失败")
                    updateStatus(UpdateStatus.ERROR)
                    val errorMsg = "下载失败，请检查网络连接或稍后重试"
                    logger.error(errorMsg)
                    loggingService.logError(Exception(errorMsg), "自动更新")
                    notifyError(errorMsg)
                    false
                }
                
            } catch (e: java.net.UnknownHostException) {
                updateDownloadState(DownloadState.FAILED, "网络连接失败")
                val errorMsg = "网络连接失败，无法连接到下载服务器"
                logger.error(errorMsg, e)
                loggingService.logError(e, "网络连接失败")
                updateStatus(UpdateStatus.ERROR)
                notifyError("$errorMsg: ${e.message}")
                false
            } catch (e: java.net.SocketTimeoutException) {
                updateDownloadState(DownloadState.FAILED, "下载超时")
                val errorMsg = "下载超时，请检查网络连接"
                logger.error(errorMsg, e)
                loggingService.logError(e, "下载超时")
                updateStatus(UpdateStatus.ERROR)
                notifyError("$errorMsg: ${e.message}")
                false
            } catch (e: java.io.IOException) {
                updateDownloadState(DownloadState.FAILED, "文件操作失败")
                val errorMsg = "文件操作失败"
                logger.error(errorMsg, e)
                loggingService.logError(e, "文件操作失败")
                updateStatus(UpdateStatus.ERROR)
                notifyError("$errorMsg: ${e.message}")
                false
            } catch (e: Exception) {
                updateDownloadState(DownloadState.FAILED, "下载异常")
                val errorMsg = "下载更新失败"
                logger.error(errorMsg, e)
                loggingService.logError(e, "下载更新失败")
                updateStatus(UpdateStatus.ERROR)
                notifyError("$errorMsg: ${e.message}")
                false
            } finally {
                // 清理下载状态
                synchronized(downloadLock) {
                    // 如果状态不是已完成，则重置为空闲
                    if (currentDownloadState != DownloadState.COMPLETED) {
                        updateDownloadState(DownloadState.IDLE, "下载任务结束")
                    }
                    val downloadDuration = System.currentTimeMillis() - downloadStartTime
                    logger.info("下载任务结束，耗时: ${downloadDuration}ms")
                    loggingService.logInfo("下载任务结束，耗时: ${downloadDuration}ms", "自动更新")
                }
                // 清理下载Job
                downloadJob = null
            }
        }
    }
    
    override suspend fun installUpdate(updateInfo: UpdateInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                updateStatus(UpdateStatus.INSTALLING, updateInfo)
                notifyProgress(0, "准备安装更新...")
                
                logger.info("开始安装更新: ${updateInfo.version}")
                
                // 1. 查找下载的更新文件
                val downloadDir = File(System.getProperty("java.io.tmpdir"), "aicodetransformer_updates")
                
                // 支持.jar和.zip文件，与下载逻辑保持一致
                val urlFileName = updateInfo.downloadUrl.substringAfterLast("/")
                val fileExtension = when {
                    urlFileName.endsWith(".jar") -> ".jar"
                    urlFileName.endsWith(".zip") -> ".zip"
                    else -> ".jar" // 默认为jar
                }
                val fileNameTemp = "aicodetransformer-${updateInfo.version}$fileExtension"
                val downloadedFile = File(downloadDir, fileNameTemp)
                
                if (!downloadedFile.exists()) {
                    throw Exception("更新文件不存在: ${downloadedFile.absolutePath}")
                }
                
                notifyProgress(10, "验证更新文件...")
                
                // 2. 验证文件完整性
                if (updateInfo.checksum.isNotEmpty()) {
                    // 如果有提供校验和，进行校验
                    val fileChecksum = calculateChecksum(downloadedFile)
                    if (fileChecksum != updateInfo.checksum) {
                        throw Exception("更新文件校验失败: 期望=${updateInfo.checksum}, 实际=$fileChecksum")
                    }
                    logger.info("文件校验通过")
                } else {
                    // 如果没有提供校验和，进行基本的文件完整性检查
                    if (downloadedFile.length() == 0L) {
                        throw Exception("下载文件为空")
                    }
                    
                    // 检查文件是否为有效的jar或zip文件
                    val fileName = downloadedFile.name.lowercase()
                    if (!fileName.endsWith(".jar") && !fileName.endsWith(".zip")) {
                        throw Exception("文件格式不正确: ${downloadedFile.name}")
                    }
                    
                    logger.info("文件基本完整性检查通过（未提供校验和）")
                }
                
                notifyProgress(20, "准备安装环境...")
                
                // 3. 获取当前插件路径
                val currentPluginPath = getCurrentPluginPath()
                if (currentPluginPath == null) {
                    throw Exception("无法确定当前插件路径")
                }
                
                notifyProgress(30, "备份当前插件...")
                
                // 4. 备份当前插件
                val backupFile = createBackup(currentPluginPath)
                
                notifyProgress(50, "安装新版本...")
                
                try {
                    // 5. 复制新版本到插件目录
                    installNewVersion(downloadedFile, currentPluginPath)
                    
                    notifyProgress(80, "验证安装...")
                    
                    // 6. 验证安装是否成功
                    if (!verifyInstallation(currentPluginPath)) {
                        throw Exception("安装验证失败")
                    }
                    
                    notifyProgress(90, "清理临时文件...")
                    
                    // 7. 清理下载的临时文件
                    downloadedFile.delete()
                    
                    // 8. 记录更新历史
                    recordUpdateHistory(updateInfo, UpdateRecordStatusConstants.SUCCESS)
                    
                    updateStatus(UpdateStatus.INSTALLED, updateInfo)
                    notifyProgress(100, "安装完成")
                    logger.info("更新安装完成: ${updateInfo.version}")
                    loggingService.logInfo("更新安装完成: ${updateInfo.version}", "自动更新")
                    
                    // 9. 提示用户重启IDE
                    showRestartPrompt(updateInfo)
                    
                    true
                    
                } catch (installException: Exception) {
                    // 安装失败，尝试恢复备份
                    logger.warn("安装失败，尝试恢复备份", installException)
                    try {
                        restoreBackup(backupFile, currentPluginPath)
                        logger.info("已恢复到原版本")
                    } catch (restoreException: Exception) {
                        logger.error("恢复备份失败", restoreException)
                    }
                    throw installException
                }
                
            } catch (e: Exception) {
                logger.error("安装更新失败", e)
                loggingService.logError(e, "安装更新失败")
                recordUpdateHistory(updateInfo, UpdateRecordStatusConstants.FAILED, e.message)
                updateStatus(UpdateStatus.ERROR)
                notifyError("安装失败: ${e.message}")
                false
            }
        }
    }
    
    /**
     * 自动安装更新（简化版本，无用户确认对话框）
     */
    private suspend fun installUpdateAutomatically(updateInfo: UpdateInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                updateStatus(UpdateStatus.INSTALLING, updateInfo)
                notifyProgress(0, "准备安装更新...")
                
                logger.info("开始自动安装更新: ${updateInfo.version}")
                
                // 1. 查找下载的更新文件
                val downloadDir = File(System.getProperty("java.io.tmpdir"), "aicodetransformer_updates")
                
                // 支持.jar和.zip文件，与下载逻辑保持一致
                val urlFileName = updateInfo.downloadUrl.substringAfterLast("/")
                val fileExtension = when {
                    urlFileName.endsWith(".jar") -> ".jar"
                    urlFileName.endsWith(".zip") -> ".zip"
                    else -> ".jar" // 默认为jar
                }
                val fileNameTemp = "aicodetransformer-${updateInfo.version}$fileExtension"
                val downloadedFile = File(downloadDir, fileNameTemp)
                
                if (!downloadedFile.exists()) {
                    throw Exception("更新文件不存在: ${downloadedFile.absolutePath}")
                }
                
                notifyProgress(10, "验证更新文件...")
                
                // 2. 验证文件完整性
                if (updateInfo.checksum.isNotEmpty()) {
                    val fileChecksum = calculateChecksum(downloadedFile)
                    if (fileChecksum != updateInfo.checksum) {
                        throw Exception("更新文件校验失败: 期望=${updateInfo.checksum}, 实际=$fileChecksum")
                    }
                    logger.info("文件校验通过")
                } else {
                    // 基本的文件完整性检查
                    if (downloadedFile.length() == 0L) {
                        throw Exception("下载文件为空")
                    }
                    val fileName = downloadedFile.name.lowercase()
                    if (!fileName.endsWith(".jar") && !fileName.endsWith(".zip")) {
                        throw Exception("文件格式不正确: ${downloadedFile.name}")
                    }
                    logger.info("文件基本完整性检查通过")
                }
                
                notifyProgress(20, "准备安装环境...")
                
                // 3. 获取当前插件路径
                val currentPluginPath = getCurrentPluginPath()
                if (currentPluginPath == null) {
                    throw Exception("无法确定当前插件路径")
                }
                
                notifyProgress(30, "备份当前插件...")
                
                // 4. 备份当前插件
                val backupFile = createBackup(currentPluginPath)
                
                notifyProgress(50, "安装新版本...")
                
                try {
                    // 5. 复制新版本到插件目录
                    installNewVersion(downloadedFile, currentPluginPath)
                    
                    notifyProgress(80, "验证安装...")
                    
                    // 6. 验证安装是否成功
                    if (!verifyInstallation(currentPluginPath)) {
                        throw Exception("安装验证失败")
                    }
                    
                    notifyProgress(90, "清理临时文件...")
                    
                    // 7. 清理下载的临时文件
                    downloadedFile.delete()
                    
                    // 8. 记录更新历史
                    recordUpdateHistory(updateInfo, UpdateRecordStatusConstants.SUCCESS)
                    
                    updateStatus(UpdateStatus.INSTALLED, updateInfo)
                    notifyProgress(100, "安装完成")
                    logger.info("自动更新安装完成: ${updateInfo.version}")
                    loggingService.logInfo("自动更新安装完成: ${updateInfo.version}", "自动更新")
                    
                    // 9. 显示简化的重启提醒（仅通知，无确认对话框）
                    showAutoUpdateRestartNotification(updateInfo)
                    
                    true
                    
                } catch (installException: Exception) {
                    // 安装失败，尝试恢复备份
                    logger.warn("自动安装失败，尝试恢复备份", installException)
                    try {
                        restoreBackup(backupFile, currentPluginPath)
                        logger.info("已恢复到原版本")
                    } catch (restoreException: Exception) {
                        logger.error("恢复备份失败", restoreException)
                    }
                    throw installException
                }
                
            } catch (e: Exception) {
                logger.error("自动安装更新失败", e)
                loggingService.logError(e, "自动安装更新失败")
                recordUpdateHistory(updateInfo, UpdateRecordStatusConstants.FAILED, e.message)
                updateStatus(UpdateStatus.ERROR)
                notifyError("自动安装失败: ${e.message}")
                false
            }
        }
    }
    
    override fun getCurrentVersion(): String {
        return CURRENT_VERSION
    }
    
    override fun getUpdateHistory(): List<UpdateRecord> {
        return try {
            val historyFile = File(UPDATE_HISTORY_FILE)
            if (historyFile.exists()) {
                val content = historyFile.readText()
                json.decodeFromString<List<UpdateRecord>>(content)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.error("读取更新历史失败", e)
            emptyList()
        }
    }
    
    override fun startAutoUpdate(triggerSource: String) {
        val settings = configurationService.getGlobalSettings()
        
        // 只有在手动启用且触发源为timer时才启动定时任务
        if (settings.enableAutoUpdate && triggerSource == "timer") {
            val intervalMs = when (settings.updateInterval) {
                "每小时一次" -> 60 * 60 * 1000L
                "每天一次" -> 24 * 60 * 60 * 1000L
                "每周一次" -> 7 * 24 * 60 * 60 * 1000L
                else -> 24 * 60 * 60 * 1000L // 默认每天一次
            }
            
            updateTimer = Timer("AutoUpdateTimer", true)
            updateTimer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    updateScope.launch {
                        performFullAutoUpdate()
                    }
                }
            }, intervalMs, intervalMs) // 修改：第一次延迟执行，不立即执行
            
            loggingService.logInfo("定时自动更新已启动，检查间隔：${settings.updateInterval}")
        } else if (triggerSource == "manual") {
            loggingService.logInfo("手动触发自动更新设置，但不启动定时任务")
        } else {
            loggingService.logInfo("自动更新未启用或触发源无效：$triggerSource")
        }
    }
    
    /**
     * 执行完整的自动更新流程：检查 -> 下载 -> 安装
     */
    private suspend fun performFullAutoUpdate() {
        // 防重复执行检查
        synchronized(downloadLock) {
            if (isAutoUpdateRunning) {
                logger.warn("自动更新流程已在运行中，跳过本次执行")
                loggingService.logInfo("自动更新流程已在运行中，跳过本次执行", "自动更新")
                return
            }
            if (isDownloading) {
                logger.warn("下载任务正在进行中，跳过本次自动更新")
                loggingService.logInfo("下载任务正在进行中，跳过本次自动更新", "自动更新")
                return
            }
            isAutoUpdateRunning = true
        }
        
        try {
            logger.info("开始执行完整的自动更新流程")
            loggingService.logInfo("开始执行完整的自动更新流程", "自动更新")
            
            // 1. 检查更新
            val updateInfo = checkForUpdates()
            if (updateInfo == null) {
                logger.info("没有可用更新")
                return
            }
            
            logger.info("发现新版本: ${updateInfo.version}，开始自动更新")
            loggingService.logInfo("发现新版本: ${updateInfo.version}，开始自动更新", "自动更新")
            
            // 2. 下载更新
            val downloadSuccess = downloadUpdate(updateInfo) { progress ->
                logger.debug("下载进度: $progress%")
            }
            
            if (!downloadSuccess) {
                logger.error("下载更新失败")
                loggingService.logError(Exception("下载更新失败"), "自动更新")
                return
            }
            
            logger.info("更新下载完成，开始安装")
            
            // 3. 自动安装更新（简化版本，无用户确认）
            val installSuccess = installUpdateAutomatically(updateInfo)
            
            if (installSuccess) {
                logger.info("自动更新完成: ${updateInfo.version}")
                loggingService.logInfo("自动更新完成: ${updateInfo.version}", "自动更新")
            } else {
                logger.error("安装更新失败")
                loggingService.logError(Exception("安装更新失败"), "自动更新")
            }
            
        } catch (e: Exception) {
            logger.error("自动更新流程失败", e)
            loggingService.logError(e, "自动更新流程失败")
            updateStatus(UpdateStatus.ERROR)
            notifyError("自动更新失败: ${e.message}")
        } finally {
            // 清理自动更新状态
            synchronized(downloadLock) {
                isAutoUpdateRunning = false
                logger.info("自动更新流程结束")
                loggingService.logInfo("自动更新流程结束", "自动更新")
            }
        }
    }
    
    override fun stopAutoUpdate() {
        updateTimer?.cancel()
        updateTimer = null
        loggingService.logInfo("自动更新已停止")
    }
    
    override fun getUpdateStatus(): UpdateStatus {
        return currentStatus
    }
    
    override fun getCurrentUpdateInfo(): UpdateInfo? {
        return currentUpdateInfo
    }
    
    override fun addStatusListener(listener: UpdateStatusListener) {
        listeners.add(listener)
    }
    
    override fun removeStatusListener(listener: UpdateStatusListener) {
        listeners.remove(listener)
    }
    
    private fun updateStatus(newStatus: UpdateStatus, updateInfo: UpdateInfo? = null) {
        val oldStatus = currentStatus
        currentStatus = newStatus
        
        // 保存更新信息
        if (updateInfo != null) {
            currentUpdateInfo = updateInfo
        } else if (newStatus == UpdateStatus.IDLE || newStatus == UpdateStatus.ERROR) {
            // 当状态变为IDLE或ERROR时，清除更新信息
            currentUpdateInfo = null
        }
        
        listeners.forEach { listener: UpdateStatusListener ->
            try {
                listener.onStatusChanged(oldStatus, newStatus, updateInfo)
            } catch (e: Exception) {
                logger.error("通知状态监听器失败", e)
            }
        }
    }
    
    private fun notifyProgress(progress: Int, message: String) {
        listeners.forEach { listener: UpdateStatusListener ->
            try {
                listener.onProgressChanged(progress, message)
            } catch (e: Exception) {
                logger.error("通知进度监听器失败", e)
            }
        }
    }
    
    private fun notifyError(error: String) {
        listeners.forEach { listener: UpdateStatusListener ->
            try {
                listener.onError(error)
            } catch (e: Exception) {
                logger.error("通知错误监听器失败", e)
            }
        }
    }
    
    private suspend fun checkRemoteVersion(): UpdateInfo? {
        try {
            logOperationStart("检查GitHub最新版本")
            
            // 调用GitHub API获取最新版本信息
            val url = java.net.URI(UPDATE_CHECK_URL).toURL()
            val connection = url.openConnection() as HttpURLConnection
            configureGitHubApiConnection(connection)
            
            val responseCode = connection.responseCode
            when (responseCode) {
                200 -> {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    logger.info("GitHub API响应成功: ${response.take(200)}...")
                    
                    // 解析GitHub API响应
                    val releaseInfo = parseGitHubRelease(response)
                    if (releaseInfo == null) {
                        logger.warn("无法解析GitHub API响应")
                        return null
                    }
                    
                    logOperationSuccess("获取最新版本", releaseInfo.version)
                    return releaseInfo
                }
                403 -> {
                    logOperationError("GitHub API访问", "访问受限 (403)，可能触发了速率限制")
                    return null
                }
                404 -> {
                     logOperationError("GitHub API访问", "仓库或发布版本不存在 (404)")
                     return null
                 }
                 else -> {
                     logOperationError("GitHub API访问", "返回错误状态码: $responseCode")
                     return null
                 }
            }
            
        } catch (e: Exception) {
            handleNetworkException(e, "检查远程版本")
            loggingService.logError(e, "检查远程版本失败")
            return null
        }
    }
    
    private fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        // 简单的版本比较逻辑
        // 实际应该使用更复杂的版本比较算法
        return try {
            val remoteParts = remoteVersion.split(".").map { it.toInt() }
            val currentParts = currentVersion.split(".").map { it.toInt() }
            
            for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
                val remote = remoteParts.getOrElse(i) { 0 }
                val current = currentParts.getOrElse(i) { 0 }
                
                when {
                    remote > current -> return true
                    remote < current -> return false
                }
            }
            false
        } catch (e: Exception) {
            logger.error("版本比较失败", e)
            false
        }
    }
    
    /**
     * 支持断点续传的下载方法
     */
    private suspend fun downloadFileWithResume(url: String, file: File, onProgress: (Int) -> Unit): Boolean {
        return try {
            logger.info("开始下载文件: ${file.name}")
                
                // 检查是否存在部分下载的文件
                val tempFile = File(file.parent, "${file.name}.tmp")
                val resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
                
                // 获取文件总大小
                val totalSize = getFileSize(url)
                if (totalSize <= 0) {
                    logger.warn("无法获取文件大小，使用普通下载")
                    return downloadFile(url, file, onProgress)
                }
                
                // 如果临时文件已经完整，直接重命名
                if (resumeFrom >= totalSize) {
                    tempFile.renameTo(file)
                    onProgress(100)
                    return true
                }
                
                // 检查服务器是否支持Range请求
                val supportsRange = checkRangeSupport(url)
                if (!supportsRange && resumeFrom > 0) {
                    // 不支持断点续传，删除临时文件重新下载
                    logger.info("服务器不支持断点续传，重新下载")
                    tempFile.delete()
                    return downloadFile(url, file, onProgress)
                }
                
                // 执行断点续传下载
                val success = if (supportsRange && totalSize > 5 * 1024 * 1024) {
                    // 大文件使用分块下载
                    downloadFileWithChunks(url, file, totalSize, onProgress)
                } else {
                    // 小文件或不支持分块的使用单线程断点续传
                    downloadFileResume(url, tempFile, resumeFrom, totalSize, onProgress)
                }
                
                if (success && tempFile.exists()) {
                    tempFile.renameTo(file)
                }
                
            if (success) {
                logger.info("文件下载成功: ${file.name}")
                true
            } else {
                logger.error("文件下载失败: ${file.name}")
                false
            }
            
        } catch (e: java.net.SocketTimeoutException) {
            logger.error("下载超时: ${e.message}")
            throw Exception("下载超时，请检查网络连接")
        } catch (e: java.net.UnknownHostException) {
            logger.error("无法解析下载地址: ${e.message}")
            throw Exception("无法连接到下载服务器")
        } catch (e: java.io.IOException) {
            logger.error("下载IO错误: ${e.message}")
            throw Exception("下载过程中发生IO错误: ${e.message}")
        } catch (e: Exception) {
            logger.error("下载失败: ${e.message}")
            throw Exception("下载失败: ${e.message}")
        }
    }
    
    /**
     * 启动下载进度监控
     */
    private fun startProgressMonitoring(): Job {
        return updateScope.launch {
            while (isDownloading) {
                delay(progressMonitoringInterval)
                
                val currentTime = System.currentTimeMillis()
                val timeSinceLastProgress = currentTime - lastProgressTime
                
                // 检查是否卡住
                if (timeSinceLastProgress > downloadStuckThreshold && lastProgressTime > 0) {
                    logger.warn("下载可能卡住：${timeSinceLastProgress}ms 无进度更新")
                    loggingService.logInfo("下载可能卡住：${timeSinceLastProgress}ms 无进度更新", "下载监控")
                    
                    // 计算下载速度
                    val bytesDownloaded = currentProgressBytes - lastProgressBytes
                    val speed = if (timeSinceLastProgress > 0) {
                        (bytesDownloaded * 1000.0 / timeSinceLastProgress / 1024).toInt() // KB/s
                    } else 0
                    
                    if (speed == 0) {
                        logger.warn("下载速度为0，可能网络连接有问题")
                        loggingService.logInfo("下载速度为0，可能网络连接有问题", "下载监控")
                    }
                }
                
                // 记录当前进度用于下次比较
                lastProgressBytes = currentProgressBytes
                lastProgressTime = currentTime
                
                // 计算并记录下载统计
                if (totalBytes > 0) {
                    val progressPercent = (currentProgressBytes * 100 / totalBytes).toInt()
                    val downloadDuration = currentTime - downloadStartTime
                    val avgSpeed = if (downloadDuration > 0) {
                        (currentProgressBytes * 1000.0 / downloadDuration / 1024).toInt() // KB/s
                    } else 0
                    
                    logger.debug("下载进度: $progressPercent%, 平均速度: ${avgSpeed}KB/s")
                }
            }
        }
    }
    
    /**
     * 更新下载进度
     */
    private fun updateDownloadProgress(bytesDownloaded: Long, totalSize: Long) {
        currentProgressBytes = bytesDownloaded
        totalBytes = totalSize
        lastProgressTime = System.currentTimeMillis()
    }
    
    /**
     * 更新下载状态
     */
    private fun updateDownloadState(newState: DownloadState, message: String = "") {
        val oldState = currentDownloadState
        currentDownloadState = newState
        
        logger.info("下载状态变更: $oldState -> $newState${if (message.isNotEmpty()) " ($message)" else ""}")
        loggingService.logInfo("下载状态变更: $oldState -> $newState${if (message.isNotEmpty()) " ($message)" else ""}", "下载状态")
        
        // 根据状态更新相关标志
        when (newState) {
            DownloadState.IDLE -> {
                isDownloading = false
                currentDownloadFile = null
            }
            DownloadState.PREPARING -> {
                isDownloading = true
            }
            DownloadState.DOWNLOADING -> {
                isDownloading = true
            }
            DownloadState.VERIFYING -> {
                // 保持下载状态，但标记为校验中
            }
            DownloadState.COMPLETED, DownloadState.FAILED, DownloadState.CANCELLED -> {
                isDownloading = false
            }
        }
    }
    
    /**
      * 获取当前下载状态信息
      */
     private fun getDownloadStatusInfo(): String {
         return when (currentDownloadState) {
             DownloadState.IDLE -> "空闲"
             DownloadState.PREPARING -> "准备下载中..."
             DownloadState.DOWNLOADING -> {
                 if (totalBytes > 0) {
                     val progress = (currentProgressBytes * 100 / totalBytes).toInt()
                     val downloadDuration = System.currentTimeMillis() - downloadStartTime
                     val speed = if (downloadDuration > 0) {
                         (currentProgressBytes * 1000.0 / downloadDuration / 1024).toInt()
                     } else 0
                     "下载中 $progress% (${speed}KB/s)"
                 } else {
                     "下载中..."
                 }
             }
             DownloadState.VERIFYING -> "校验文件完整性..."
             DownloadState.COMPLETED -> "下载完成"
             DownloadState.FAILED -> "下载失败"
             DownloadState.CANCELLED -> "下载已取消"
         }
     }
     
     /**
      * 取消当前下载
      */
     override fun cancelDownload(): Boolean {
         return synchronized(downloadLock) {
             if (isDownloading) {
                 updateDownloadState(DownloadState.CANCELLED, "用户取消下载")
                 logger.info("用户取消下载")
                 loggingService.logInfo("用户取消下载", "自动更新")
                 
                 // 取消下载协程
                 downloadJob?.let { job ->
                     if (job.isActive) {
                         job.cancel()
                         logger.info("已取消下载协程")
                     }
                 }
                 downloadJob = null
                 
                 // 清理当前下载文件
                 currentDownloadFile?.let { file ->
                     try {
                         if (file.exists()) {
                             file.delete()
                             logger.info("已删除未完成的下载文件: ${file.name}")
                         }
                     } catch (e: Exception) {
                         logger.warn("删除未完成下载文件时出错", e)
                     }
                 }
                 
                 updateStatus(UpdateStatus.IDLE)
                 true
             } else {
                 logger.warn("没有正在进行的下载任务")
                 false
             }
         }
     }
    
    /**
     * 单线程断点续传下载
     */
    private fun downloadFileResume(url: String, file: File, resumeFrom: Long, totalSize: Long, onProgress: (Int) -> Unit): Boolean {
        var connection: HttpURLConnection? = null
        var inputStream: java.io.InputStream? = null
        var outputStream: java.io.OutputStream? = null
        
        return try {
            connection = java.net.URI(url).toURL().openConnection() as HttpURLConnection
            
            // 配置下载连接
            val fileSizeMB = if (totalSize > 0) totalSize / (1024 * 1024) else 0
            configureDownloadConnection(connection, fileSizeMB)
            
            // 设置Range请求头进行断点续传
            if (resumeFrom > 0) {
                connection.setRequestProperty("Range", "bytes=$resumeFrom-")
                logger.info("断点续传从位置 $resumeFrom 开始下载")
            }
            
            // 检查响应码
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                logger.error("下载失败，HTTP响应码: $responseCode")
                return false
            }
            
            inputStream = connection.inputStream.buffered(65536)
            outputStream = FileOutputStream(file, resumeFrom > 0).buffered(65536) // append模式
            
            val buffer = ByteArray(65536)
            var totalBytesRead = resumeFrom
            var bytesRead: Int
            var lastProgressUpdate = 0
            var lastUpdateTime = System.currentTimeMillis()
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                // 检查是否被取消
                if (currentDownloadState == DownloadState.CANCELLED) {
                    logger.info("下载被取消，停止下载循环")
                    return false
                }
                
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                // 更新下载进度监控
                updateDownloadProgress(totalBytesRead, totalSize)
                
                // 防止除零错误
                val progress = if (totalSize > 0) {
                    (totalBytesRead * 100 / totalSize).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                val currentTime = System.currentTimeMillis()
                
                // 只在进度变化且距离上次更新超过100ms时更新，减少UI更新频率
                if (progress != lastProgressUpdate && (currentTime - lastUpdateTime) > 100) {
                    onProgress(progress)
                    lastProgressUpdate = progress
                    lastUpdateTime = currentTime
                }
            }
            
            logOperationSuccess("文件下载", "总字节数: $totalBytesRead")
            true
            
        } catch (e: Exception) {
            handleNetworkException(e, "断点续传下载")
            false
        } finally {
            // 确保资源被正确释放
            try {
                outputStream?.close()
            } catch (e: Exception) {
                logger.warn("关闭输出流时出错: ${e.message}")
            }
            try {
                inputStream?.close()
            } catch (e: Exception) {
                logger.warn("关闭输入流时出错: ${e.message}")
            }
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                logger.warn("断开连接时出错: ${e.message}")
            }
        }
    }
    
    private suspend fun downloadFile(url: String, file: File, onProgress: (Int) -> Unit): Boolean {
        return try {
            // 首先检查服务器是否支持分块下载
            val supportsRangeRequests = checkRangeSupport(url)
            val fileSize = getFileSize(url)
            
            if (supportsRangeRequests && fileSize > 5 * 1024 * 1024) { // 大于5MB使用分块下载
                downloadFileWithChunks(url, file, fileSize, onProgress)
            } else {
                downloadFileSingle(url, file, onProgress)
            }
        } catch (e: Exception) {
            handleNetworkException(e, "下载文件")
            false
        }
    }
    
    /**
     * 检查服务器是否支持Range请求
     */
    private fun checkRangeSupport(url: String): Boolean {
        return try {
            val connection = java.net.URI(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            configureHttpConnection(connection)
            
            val acceptRanges = connection.getHeaderField("Accept-Ranges")
            connection.disconnect()
            
            acceptRanges == "bytes"
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取文件大小
     */
    private fun getFileSize(url: String): Long {
        return try {
            val connection = java.net.URI(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("User-Agent", "AICodeTransformer-Plugin/1.0")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val size = connection.contentLengthLong
            connection.disconnect()
            size
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * 分块下载文件
     */
    private suspend fun downloadFileWithChunks(url: String, file: File, fileSize: Long, onProgress: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val chunkCount = 4 // 使用4个线程
                val chunkSize = fileSize / chunkCount
                val tempFiles = mutableListOf<File>()
                val downloadJobs = mutableListOf<Deferred<Boolean>>()
                
                // 使用原子变量跟踪总下载进度
                val totalDownloaded = AtomicLong(0)
                var lastReportedProgress = 0
                var lastUpdateTime = System.currentTimeMillis()
                
                // 创建临时文件
                for (i in 0 until chunkCount) {
                    val tempFile = File(file.parent, "${file.name}.part$i")
                    tempFiles.add(tempFile)
                }
                
                // 启动多个下载任务
                for (i in 0 until chunkCount) {
                    val start = i * chunkSize
                    val end = if (i == chunkCount - 1) fileSize - 1 else (i + 1) * chunkSize - 1
                    
                    val job = async {
                        downloadChunk(url, tempFiles[i], start, end) { bytesDownloaded ->
                            // 更新总下载字节数
                            val currentTotal = totalDownloaded.addAndGet(bytesDownloaded)
                            val progress = (currentTotal * 100 / fileSize).toInt().coerceIn(0, 100)
                            val currentTime = System.currentTimeMillis()
                            
                            // 只在进度变化且距离上次更新超过100ms时更新，避免频繁更新
                            if (progress != lastReportedProgress && (currentTime - lastUpdateTime) > 100) {
                                lastReportedProgress = progress
                                lastUpdateTime = currentTime
                                onProgress(progress)
                            }
                        }
                    }
                    downloadJobs.add(job)
                }
                
                // 等待所有下载完成
                val results = downloadJobs.awaitAll()
                val allSuccess = results.all { it }
                
                if (allSuccess) {
                    // 合并文件
                    mergeChunks(tempFiles, file)
                    // 清理临时文件
                    tempFiles.forEach { it.delete() }
                    onProgress(100) // 确保最终进度为100%
                    true
                } else {
                    // 清理临时文件
                    tempFiles.forEach { it.delete() }
                    false
                }
            } catch (e: Exception) {
                logger.error("分块下载失败", e)
                false
            }
        }
    }
    
    /**
     * 下载单个分块
     */
    private fun downloadChunk(url: String, file: File, start: Long, end: Long, onProgress: (Long) -> Unit): Boolean {
        return try {
            val connection = java.net.URI(url).toURL().openConnection() as HttpURLConnection
            configureDownloadConnection(connection, 0)
            connection.setRequestProperty("Range", "bytes=$start-$end")
            
            val inputStream = connection.inputStream.buffered(65536)
            val outputStream = FileOutputStream(file).buffered(65536)
            
            val buffer = ByteArray(65536)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                // 报告增量字节数，而不是百分比
                onProgress(bytesRead.toLong())
            }
            
            outputStream.close()
            inputStream.close()
            connection.disconnect()
            
            true
        } catch (e: Exception) {
            handleNetworkException(e, "下载分块")
            false
        }
    }
    
    /**
     * 合并分块文件
     */
    private fun mergeChunks(tempFiles: List<File>, outputFile: File) {
        val outputStream = FileOutputStream(outputFile).buffered(65536)
        
        for (tempFile in tempFiles) {
            val inputStream = tempFile.inputStream().buffered(65536)
            inputStream.copyTo(outputStream)
            inputStream.close()
        }
        
        outputStream.close()
    }
    
    /**
     * 单线程下载文件（备用方法）
     */
    private fun downloadFileSingle(url: String, file: File, onProgress: (Int) -> Unit): Boolean {
        var connection: HttpURLConnection? = null
        var inputStream: java.io.InputStream? = null
        var outputStream: java.io.OutputStream? = null
        
        return try {
            logOperationStart("单线程下载", file.name)
                
                connection = java.net.URI(url).toURL().openConnection() as HttpURLConnection
                
                // 优化连接配置
                configureDownloadConnection(connection, 0)
                
                // 检查响应码
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    logOperationError("下载文件", "HTTP响应码: $responseCode")
                    throw Exception("HTTP错误: $responseCode")
                }
                
                val fileSize = connection.contentLengthLong  // 使用Long类型支持大文件
                if (fileSize <= 0) {
                    logOperationError("下载文件", "无法获取文件大小")
                    throw Exception("无法获取文件大小")
                }
                
                inputStream = connection.inputStream.buffered(65536)  // 64KB缓冲
                outputStream = FileOutputStream(file).buffered(65536)  // 64KB缓冲
                
                // 使用更大的缓冲区提高下载速度
                val buffer = ByteArray(65536)  // 64KB缓冲区
                var totalBytesRead = 0L
                var bytesRead: Int
                var lastProgressUpdate = 0
                var lastUpdateTime = System.currentTimeMillis()
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    // 检查是否被取消
                    if (currentDownloadState == DownloadState.CANCELLED) {
                        logger.info("下载被取消，停止下载循环")
                        return false
                    }
                    
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    // 防止除零错误
                    val progress = if (fileSize > 0) {
                        (totalBytesRead * 100 / fileSize).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                    val currentTime = System.currentTimeMillis()
                    
                    // 只在进度变化且距离上次更新超过100ms时更新，减少UI更新频率
                    if (progress != lastProgressUpdate && (currentTime - lastUpdateTime) > 100) {
                        onProgress(progress)
                        lastProgressUpdate = progress
                        lastUpdateTime = currentTime
                    }
                }
                
                logOperationSuccess("单线程下载", "${file.name}, 总字节数: $totalBytesRead")
                true
                
            } catch (e: Exception) {
                val errorMessage = handleNetworkException(e, "下载文件")
                throw Exception(errorMessage)
            } finally {
                // 确保资源被正确释放
                try {
                    outputStream?.close()
                } catch (e: Exception) {
                    logger.warn("关闭输出流时出错: ${e.message}")
                }
                try {
                    inputStream?.close()
                } catch (e: Exception) {
                    logger.warn("关闭输入流时出错: ${e.message}")
                }
                try {
                    connection?.disconnect()
                } catch (e: Exception) {
                    logger.warn("断开连接时出错: ${e.message}")
                }
            }
        }
    
    private fun calculateChecksum(file: File): String {
        return try {
            if (!file.exists()) {
                logger.error("文件不存在，无法计算校验和: ${file.absolutePath}")
                return ""
            }
            
            if (file.length() == 0L) {
                logger.error("文件为空，无法计算校验和: ${file.absolutePath}")
                return ""
            }
            
            logger.info("开始计算文件校验和: ${file.name}, 大小: ${file.length()} bytes")
            val startTime = System.currentTimeMillis()
            
            val digest = MessageDigest.getInstance("SHA-256")
            
            // 对于大文件，使用流式读取避免内存溢出
            if (file.length() > 50 * 1024 * 1024) { // 50MB以上使用流式读取
                file.inputStream().buffered(65536).use { input ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // 每处理10MB记录一次进度
                        if (totalBytesRead % (10 * 1024 * 1024) == 0L) {
                            val progress = (totalBytesRead * 100 / file.length()).toInt()
                            logger.debug("校验和计算进度: $progress%")
                        }
                    }
                }
            } else {
                // 小文件直接读取
                val bytes = file.readBytes()
                digest.update(bytes)
            }
            
            val hash = digest.digest()
            val checksum = hash.joinToString("") { "%02x".format(it) }
            
            val duration = System.currentTimeMillis() - startTime
            logger.info("文件校验和计算完成: ${file.name}, 耗时: ${duration}ms, 校验和: $checksum")
            
            checksum
        } catch (e: OutOfMemoryError) {
            logger.error("内存不足，无法计算校验和: ${file.absolutePath}", e)
            loggingService.logError(e, "校验和计算内存不足")
            ""
        } catch (e: Exception) {
            logger.error("计算文件校验和失败: ${file.absolutePath}", e)
            loggingService.logError(e, "校验和计算失败")
            ""
        }
    }
    
    /**
     * 增强的文件校验方法，支持多种校验算法和完整性检查
     */
    internal fun verifyFileIntegrity(file: File, expectedChecksum: String, algorithm: String = "SHA-256"): Boolean {
        return try {
            if (!file.exists()) {
                logger.error("文件不存在，校验失败: ${file.absolutePath}")
                return false
            }
            
            if (file.length() == 0L) {
                logger.error("文件为空，校验失败: ${file.absolutePath}")
                return false
            }
            
            if (expectedChecksum.isEmpty()) {
                logger.warn("未提供期望的校验和，跳过校验")
                return true
            }
            
            logger.info("开始文件完整性校验: ${file.name}")
            
            // 1. 计算文件校验和
            val actualChecksum = when (algorithm.uppercase()) {
                "SHA-256" -> calculateChecksum(file)
                "MD5" -> calculateMD5(file)
                else -> {
                    logger.warn("不支持的校验算法: $algorithm，使用SHA-256")
                    calculateChecksum(file)
                }
            }
            
            if (actualChecksum.isEmpty()) {
                logger.error("无法计算文件校验和")
                return false
            }
            
            // 2. 比较校验和
            val checksumMatch = actualChecksum.equals(expectedChecksum, ignoreCase = true)
            
            if (checksumMatch) {
                logger.info("文件校验成功: ${file.name}")
                loggingService.logInfo("文件校验成功: ${file.name}", "文件校验")
            } else {
                logger.error("文件校验失败: ${file.name}")
                logger.error("期望校验和: $expectedChecksum")
                logger.error("实际校验和: $actualChecksum")
                loggingService.logError(Exception("文件校验失败"), "文件校验失败: ${file.name}")
            }
            
            checksumMatch
            
        } catch (e: Exception) {
            logger.error("文件校验过程中发生错误: ${file.absolutePath}", e)
            loggingService.logError(e, "文件校验错误")
            false
        }
    }
    
    /**
     * 计算MD5校验和
     */
    private fun calculateMD5(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().buffered(65536).use { input ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hash = digest.digest()
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.error("计算MD5校验和失败: ${file.absolutePath}", e)
            ""
        }
    }
    
    private fun recordUpdateHistory(updateInfo: UpdateInfo, status: UpdateRecordStatusConstants, errorMessage: String? = null) {
        try {
            val history = getUpdateHistory().toMutableList()
            val record = UpdateRecord(
                version = updateInfo.version,
                updateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                status = status,
                errorMessage = errorMessage
            )
            history.add(record)
            
            // 只保留最近20条记录
            if (history.size > 20) {
                history.removeAt(0)
            }
            
            val historyFile = File(UPDATE_HISTORY_FILE)
            historyFile.writeText(json.encodeToString(history))
            
        } catch (e: Exception) {
            logger.error("记录更新历史失败", e)
        }
    }
    
    /**
     * 获取当前插件的安装路径
     */
    private fun getCurrentPluginPath(): File? {
        return try {
            // 方法1：使用正确的插件ID获取插件路径
            val pluginId = com.intellij.openapi.extensions.PluginId.getId("cn.suso.AICodeTransformer")
            val pluginDescriptor = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId)
            
            if (pluginDescriptor != null) {
                val pluginPath = pluginDescriptor.pluginPath.toFile()
                logger.info("通过插件描述符获取到插件路径: ${pluginPath.absolutePath}")
                return pluginPath
            } else {
                logger.warn("无法通过插件ID获取插件描述符，尝试备用方法")
            }
            
            // 方法2：通过类路径推断插件路径
            val classPath = this::class.java.protectionDomain.codeSource.location.path
            val classFile = File(classPath)
            logger.debug("类路径: ${classFile.absolutePath}")
            
            // 如果是jar文件，返回jar文件所在目录
            if (classFile.name.endsWith(".jar")) {
                val pluginPath = classFile.parentFile
                logger.info("通过类路径推断插件路径: ${pluginPath.absolutePath}")
                return pluginPath
            }
            
            // 方法3：在开发环境中，尝试查找build目录
            var currentDir = classFile
            while (currentDir.exists()) {
                if (currentDir.name == "classes" && currentDir.parentFile?.name == "kotlin") {
                    // 开发环境：.../build/classes/kotlin/main
                    val buildDir = currentDir.parentFile?.parentFile?.parentFile
                    if (buildDir?.name == "build") {
                        val projectRoot = buildDir.parentFile
                        logger.info("开发环境中推断插件路径: ${projectRoot.absolutePath}")
                        return projectRoot
                    }
                }
                currentDir = currentDir.parentFile
            }
            
            // 方法4：最后的备用方法，返回类文件的父目录
            val fallbackPath = File(classPath).parentFile
            logger.warn("使用备用方法获取插件路径: ${fallbackPath?.absolutePath}")
            return fallbackPath
            
        } catch (e: Exception) {
            logger.error("获取插件路径失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 创建当前插件的备份
     */
    private fun createBackup(pluginPath: File): File {
        val backupDir = File(System.getProperty("java.io.tmpdir"), "aicodetransformer_backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupFile = File(backupDir, "aicodetransformer_backup_$timestamp.backup")
        
        if (pluginPath.isDirectory) {
            // 如果是目录，压缩为jar文件
            zipDirectory(pluginPath, backupFile)
        } else {
            // 如果是jar文件，直接复制
            pluginPath.copyTo(backupFile, overwrite = true)
        }
        
        logger.info("插件备份创建完成: ${backupFile.absolutePath}")
        
        // 清理旧的备份文件，只保留最近的5个备份
        cleanupOldBackups(backupDir, 5)
        
        return backupFile
    }
    
    /**
     * 清理冗余的目录结构
     */
    private fun cleanupRedundantDirectories(targetPath: File) {
        if (!targetPath.exists() || !targetPath.isDirectory) {
            return
        }
        
        try {
            logger.info("检查目录结构是否需要清理: ${targetPath.absolutePath}")
            
            // 检查是否存在重复的插件目录嵌套
            val redundantPath = findRedundantPluginDirectory(targetPath)
            if (redundantPath != null) {
                logger.info("发现冗余目录结构，准备清理: ${redundantPath.absolutePath}")
                loggingService.logInfo("发现冗余目录结构，准备清理: ${redundantPath.absolutePath}", "目录清理")
                
                // 将内容移动到正确位置
                moveDirectoryContents(redundantPath, targetPath)
                
                logger.info("冗余目录结构清理完成")
                loggingService.logInfo("冗余目录结构清理完成", "目录清理")
            }
        } catch (e: Exception) {
            logger.warn("目录结构清理过程中出现异常: ${e.message}", e)
            loggingService.logWarning("目录结构清理过程中出现异常: ${e.message}", "目录清理")
        }
    }
    
    /**
     * 查找冗余的插件目录
     */
    private fun findRedundantPluginDirectory(baseDir: File): File? {
        var currentDir = baseDir
        var depth = 0
        val maxDepth = 5 // 防止无限循环
        
        while (depth < maxDepth) {
            val children = currentDir.listFiles() ?: break
            
            // 如果当前目录只有一个子目录，且子目录名包含插件名
            if (children.size == 1 && children[0].isDirectory) {
                val childDir = children[0]
                if (childDir.name.contains("AICodeTransformer", ignoreCase = true)) {
                    // 检查子目录是否包含插件文件
                    if (hasPluginFiles(childDir)) {
                        return childDir
                    }
                    currentDir = childDir
                    depth++
                } else {
                    break
                }
            } else {
                break
            }
        }
        
        return null
    }
    
    /**
     * 检查目录是否包含插件文件
     */
    private fun hasPluginFiles(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        
        val files = dir.listFiles() ?: return false
        
        return files.any { file ->
            when {
                file.name == "plugin.xml" -> true
                file.name == "lib" && file.isDirectory -> true
                file.name.endsWith(".jar") -> true
                file.isDirectory && file.name == "META-INF" -> true
                else -> false
            }
        }
    }
    
    /**
     * 移动目录内容
     */
    private fun moveDirectoryContents(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) return
        
        val files = sourceDir.listFiles() ?: return
        
        for (file in files) {
            val targetFile = File(targetDir, file.name)
            
            if (file.isDirectory) {
                // 递归移动目录
                if (!targetFile.exists()) {
                    targetFile.mkdirs()
                }
                moveDirectoryContents(file, targetFile)
                file.deleteRecursively()
            } else {
                // 移动文件
                file.copyTo(targetFile, overwrite = true)
                file.delete()
            }
        }
        
        // 删除空的源目录
        if (sourceDir.listFiles()?.isEmpty() == true) {
            sourceDir.delete()
        }
    }
    
    /**
     * 安装后的目录清理
     */
    private fun postInstallDirectoryCleanup(targetPath: File) {
        try {
            logger.info("执行安装后目录结构检查: ${targetPath.absolutePath}")
            
            // 再次检查是否有冗余目录
            cleanupRedundantDirectories(targetPath)
            
            // 验证最终的目录结构
            if (!hasPluginFiles(targetPath)) {
                logger.warn("安装后目录结构验证失败，未找到必要的插件文件")
                loggingService.logWarning("安装后目录结构验证失败，未找到必要的插件文件", "安装验证")
            } else {
                logger.info("安装后目录结构验证通过")
                loggingService.logInfo("安装后目录结构验证通过", "安装验证")
            }
        } catch (e: Exception) {
            logger.warn("安装后目录清理过程中出现异常: ${e.message}", e)
            loggingService.logWarning("安装后目录清理过程中出现异常: ${e.message}", "安装验证")
        }
    }
    
    /**
     * 安装新版本
     */
    private fun installNewVersion(newVersionFile: File, targetPath: File) {
        logger.info("开始安装新版本: ${newVersionFile.absolutePath} -> ${targetPath.absolutePath}")
        loggingService.logInfo("开始安装新版本: ${newVersionFile.absolutePath} -> ${targetPath.absolutePath}", "安装新版本")
        
        // 在安装前检查并清理可能存在的重复目录结构
        cleanupRedundantDirectories(targetPath)
        
        // 判断新版本文件类型
        val isZipFile = newVersionFile.name.endsWith(".zip")
        val isJarFile = newVersionFile.name.endsWith(".jar")
        
        if (isZipFile) {
            // 处理zip文件安装
            if (targetPath.isDirectory) {
                // 目标是目录，清空目录后解压
                logger.info("目标是目录，清空后解压zip文件")
                loggingService.logInfo("目标是目录，清空后解压zip文件", "安装新版本")
                targetPath.deleteRecursively()
                targetPath.mkdirs()
                // 使用智能解压功能
                unzipFileIntelligent(newVersionFile, targetPath)
            } else {
                // 目标是文件（jar），需要替换为目录
                logger.info("目标是jar文件，替换为目录并解压zip文件")
                loggingService.logInfo("目标是jar文件，替换为目录并解压zip文件", "安装新版本")
                
                // 删除原jar文件
                if (targetPath.exists()) {
                    targetPath.delete()
                }
                
                // 创建同名目录
                targetPath.mkdirs()
                
                // 使用智能解压功能
                unzipFileIntelligent(newVersionFile, targetPath)
            }
            
            // 安装后验证并修复目录结构
            postInstallDirectoryCleanup(targetPath)
            
        } else if (isJarFile) {
            // 处理jar文件安装
            if (targetPath.isDirectory) {
                // 目标是目录，需要替换为jar文件
                logger.info("目标是目录，替换为jar文件")
                loggingService.logInfo("目标是目录，替换为jar文件", "安装新版本")
                
                // 删除目录
                targetPath.deleteRecursively()
                
                // 复制jar文件
                newVersionFile.copyTo(targetPath, overwrite = true)
            } else {
                // 目标是文件，直接替换
                logger.info("目标是jar文件，直接替换")
                loggingService.logInfo("目标是jar文件，直接替换", "安装新版本")
                
                val parentDir = targetPath.parentFile
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }
                newVersionFile.copyTo(targetPath, overwrite = true)
            }
        } else {
            // 未知文件类型，按原逻辑处理
            logger.warn("未知文件类型，按原逻辑处理")
            loggingService.logWarning("未知文件类型，按原逻辑处理: ${newVersionFile.name}", "安装新版本")
            
            if (targetPath.isDirectory) {
                targetPath.deleteRecursively()
                targetPath.mkdirs()
                unzipFile(newVersionFile, targetPath)
            } else {
                val parentDir = targetPath.parentFile
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }
                newVersionFile.copyTo(targetPath, overwrite = true)
            }
        }
        
        val successMsg = "新版本安装完成: ${targetPath.absolutePath}"
        logger.info(successMsg)
        loggingService.logInfo(successMsg, "安装新版本")
    }
    
    /**
     * 验证安装是否成功
     */
    private fun verifyInstallation(installedPath: File): Boolean {
        return try {
            logger.info("开始验证安装: ${installedPath.absolutePath}")
            loggingService.logInfo("开始验证安装: ${installedPath.absolutePath}", "安装验证")
            
            // 添加详细的路径信息调试
            logger.info("验证路径详情 - 存在: ${installedPath.exists()}, 是文件: ${installedPath.isFile}, 是目录: ${installedPath.isDirectory}")
            loggingService.logInfo("验证路径详情 - 存在: ${installedPath.exists()}, 是文件: ${installedPath.isFile}, 是目录: ${installedPath.isDirectory}", "安装验证")
            
            // 1. 检查文件是否存在
            if (!installedPath.exists()) {
                val errorMsg = "安装验证失败：文件不存在 - ${installedPath.absolutePath}"
                logger.error(errorMsg)
                loggingService.logError(Exception(errorMsg), "安装验证")
                return false
            }
            
            // 2. 检查文件大小
            if (installedPath.isFile) {
                val fileSize = installedPath.length()
                logger.info("检查文件大小: $fileSize bytes")
                loggingService.logInfo("检查文件大小: $fileSize bytes", "安装验证")
                
                if (fileSize == 0L) {
                    val errorMsg = "安装验证失败：文件大小为0 - ${installedPath.absolutePath}"
                    logger.error(errorMsg)
                    loggingService.logError(Exception(errorMsg), "安装验证")
                    return false
                }
                
                // 检查文件大小是否合理（至少1KB）
                if (fileSize < 1024) {
                    val warnMsg = "安装验证警告：文件大小过小 ($fileSize bytes) - ${installedPath.absolutePath}"
                    logger.warn(warnMsg)
                    loggingService.logWarning(warnMsg, "安装验证")
                }
                
                val sizeMsg = "文件大小验证通过: ${fileSize / 1024} KB"
                logger.info(sizeMsg)
                loggingService.logInfo(sizeMsg, "安装验证")
            }
            
            // 3. 检查文件可读性
            logger.info("检查文件可读性: ${installedPath.canRead()}")
            loggingService.logInfo("检查文件可读性: ${installedPath.canRead()}", "安装验证")
            
            if (!installedPath.canRead()) {
                val errorMsg = "安装验证失败：文件不可读 - ${installedPath.absolutePath}"
                logger.error(errorMsg)
                loggingService.logError(Exception(errorMsg), "安装验证")
                return false
            }
            
            // 4. 如果是JAR文件，验证JAR文件完整性
            if (installedPath.name.endsWith(".jar")) {
                logger.info("检测到JAR文件，开始验证JAR完整性")
                loggingService.logInfo("检测到JAR文件，开始验证JAR完整性", "安装验证")
                
                if (!verifyJarFile(installedPath)) {
                    val errorMsg = "安装验证失败：JAR文件完整性检查失败 - ${installedPath.absolutePath}"
                    logger.error(errorMsg)
                    loggingService.logError(Exception(errorMsg), "安装验证")
                    return false
                }
            }
            
            // 5. 如果是目录，验证目录结构
            if (installedPath.isDirectory) {
                logger.info("检测到目录，开始验证目录结构")
                loggingService.logInfo("检测到目录，开始验证目录结构", "安装验证")
                
                if (!verifyDirectoryStructure(installedPath)) {
                    val errorMsg = "安装验证失败：目录结构验证失败 - ${installedPath.absolutePath}"
                    logger.error(errorMsg)
                    loggingService.logError(Exception(errorMsg), "安装验证")
                    return false
                }
            }
            
            // 6. 验证文件权限
            if (!verifyFilePermissions(installedPath)) {
                logger.warn("安装验证警告：文件权限可能存在问题")
            }
            
            logger.info("安装验证成功")
            true
            
        } catch (e: Exception) {
            logger.error("安装验证过程中发生错误", e)
            false
        }
    }
    
    /**
     * 验证JAR文件完整性
     */
    private fun verifyJarFile(jarFile: File): Boolean {
        return try {
            java.util.jar.JarFile(jarFile).use { jar ->
                // 检查是否有MANIFEST.MF文件
                val manifest = jar.manifest
                if (manifest == null) {
                    logger.warn("JAR文件缺少MANIFEST.MF")
                    return false
                }
                
                // 检查是否有基本的条目
                val entries = jar.entries()
                var entryCount = 0
                while (entries.hasMoreElements()) {
                    entries.nextElement()
                    entryCount++
                }
                
                if (entryCount == 0) {
                    logger.error("JAR文件为空")
                    return false
                }
                
                logger.info("JAR文件验证通过，包含 $entryCount 个条目")
                true
            }
        } catch (e: Exception) {
            logger.error("JAR文件验证失败", e)
            false
        }
    }
    
    /**
     * 验证目录结构
     */
    private fun verifyDirectoryStructure(directory: File): Boolean {
        return try {
            logger.info("开始验证目录结构: ${directory.absolutePath}")
            loggingService.logInfo("开始验证目录结构: ${directory.absolutePath}", "目录结构验证")
            
            if (!directory.isDirectory) {
                val errorMsg = "路径不是目录: ${directory.absolutePath}"
                logger.error(errorMsg)
                loggingService.logError(Exception(errorMsg), "目录结构验证")
                return false
            }
            
            val files = directory.listFiles()
            if (files == null) {
                val errorMsg = "无法读取目录内容: ${directory.absolutePath}"
                logger.error(errorMsg)
                loggingService.logError(Exception(errorMsg), "目录结构验证")
                return false
            }
            
            if (files.isEmpty()) {
                val errorMsg = "目录为空: ${directory.absolutePath}"
                logger.warn(errorMsg)
                loggingService.logWarning(errorMsg, "目录结构验证")
                return false
            }
            
            // 检查是否存在重复的目录嵌套
            val redundantPath = findRedundantPluginDirectory(directory)
            if (redundantPath != null) {
                logger.warn("检测到重复的目录嵌套结构: ${redundantPath.absolutePath}")
                loggingService.logWarning("检测到重复的目录嵌套结构: ${redundantPath.absolutePath}", "目录结构验证")
                
                // 尝试自动修复
                try {
                    cleanupRedundantDirectories(directory)
                    logger.info("已自动修复重复的目录结构")
                    loggingService.logInfo("已自动修复重复的目录结构", "目录结构验证")
                    
                    // 重新获取文件列表
                    val updatedFiles = directory.listFiles()
                    if (updatedFiles != null && updatedFiles.isNotEmpty()) {
                        return verifyDirectoryStructureInternal(directory, updatedFiles)
                    }
                } catch (e: Exception) {
                    logger.warn("自动修复重复目录结构失败: ${e.message}", e)
                    loggingService.logWarning("自动修复重复目录结构失败: ${e.message}", "目录结构验证")
                }
            }
            
            return verifyDirectoryStructureInternal(directory, files)
        } catch (e: Exception) {
            logger.error("目录结构验证失败", e)
            loggingService.logError(e, "目录结构验证")
            false
        }
    }
    
    /**
     * 内部目录结构验证方法
     */
    private fun verifyDirectoryStructureInternal(directory: File, files: Array<File>): Boolean {
        // 记录目录内容详情
        logger.info("验证目录 ${directory.absolutePath} 包含 ${files.size} 个项目:")
        loggingService.logInfo("验证目录 ${directory.absolutePath} 包含 ${files.size} 个项目:", "目录结构验证")
        
        var hasValidFiles = false
        var validFileCount = 0
        var totalFileSize = 0L
        
        files.forEach { file ->
            val fileInfo = if (file.isFile) {
                val size = file.length()
                totalFileSize += size
                if (size > 0) {
                    hasValidFiles = true
                    validFileCount++
                }
                "文件: ${file.name} (${size} bytes)"
            } else if (file.isDirectory) {
                "目录: ${file.name}/"
            } else {
                "其他: ${file.name}"
            }
            logger.info("  - $fileInfo")
            loggingService.logInfo("  - $fileInfo", "目录结构验证")
        }
        
        logger.info("统计信息 - 有效文件数: $validFileCount, 总文件大小: $totalFileSize bytes")
        loggingService.logInfo("统计信息 - 有效文件数: $validFileCount, 总文件大小: $totalFileSize bytes", "目录结构验证")
        
        // 更智能的验证逻辑
        if (!hasValidFiles) {
            // 检查是否有关键的插件文件
            val hasPluginXml = files.any { it.name == "plugin.xml" || it.name.endsWith(".xml") }
            val hasJarFiles = files.any { it.name.endsWith(".jar") }
            val hasLibDirectory = files.any { it.isDirectory && it.name == "lib" }
            val hasClassesDirectory = files.any { it.isDirectory && it.name == "classes" }
            val hasMetaInfDirectory = files.any { it.isDirectory && it.name == "META-INF" }
            
            if (hasPluginXml || hasJarFiles || hasLibDirectory || hasClassesDirectory || hasMetaInfDirectory) {
                logger.info("虽然没有大文件，但发现了插件相关文件结构，验证通过")
                loggingService.logInfo("虽然没有大文件，但发现了插件相关文件结构，验证通过", "目录结构验证")
            } else {
                // 检查子目录中是否有插件文件
                val hasNestedPluginFiles = files.any { file ->
                    file.isDirectory && hasPluginFiles(file)
                }
                
                if (hasNestedPluginFiles) {
                    logger.info("在子目录中发现了插件相关文件结构，验证通过")
                    loggingService.logInfo("在子目录中发现了插件相关文件结构，验证通过", "目录结构验证")
                } else {
                    val errorMsg = "目录中没有有效文件（大小 > 0 的文件）且未发现插件相关结构"
                    logger.error(errorMsg)
                    loggingService.logError(Exception(errorMsg), "目录结构验证")
                    return false
                }
            }
        }
        
        logger.info("目录结构验证通过")
        loggingService.logInfo("目录结构验证通过", "目录结构验证")
        return true
    }
    
    /**
     * 验证文件权限
     */
    private fun verifyFilePermissions(file: File): Boolean {
        return try {
            val canRead = file.canRead()
            val canWrite = file.canWrite()
            val canExecute = file.canExecute()
            
            logger.info("文件权限 - 读: $canRead, 写: $canWrite, 执行: $canExecute")
            
            // 至少需要读权限
            if (!canRead) {
                logger.error("文件缺少读权限")
                return false
            }
            
            true
        } catch (e: Exception) {
            logger.error("文件权限验证失败", e)
            false
        }
    }
    
    /**
     * 恢复备份
     */
    private fun restoreBackup(backupFile: File, targetPath: File) {
        if (!backupFile.exists()) {
            throw Exception("备份文件不存在: ${backupFile.absolutePath}")
        }
        
        if (targetPath.isDirectory) {
            targetPath.deleteRecursively()
            targetPath.mkdirs()
            unzipFile(backupFile, targetPath)
        } else {
            backupFile.copyTo(targetPath, overwrite = true)
        }
        
        logger.info("备份恢复完成: ${targetPath.absolutePath}")
    }
    
    /**
     * 压缩目录为zip文件
     */
    private fun zipDirectory(sourceDir: File, targetZip: File) {
        if (sourceDir.isFile) {
            // 如果源是文件，直接复制
            sourceDir.copyTo(targetZip, overwrite = true)
            return
        }
        
        // 如果源是目录，压缩为zip
        try {
            java.util.zip.ZipOutputStream(FileOutputStream(targetZip)).use { zipOut ->
                zipDirectory(sourceDir, sourceDir.name, zipOut)
            }
            logger.info("目录压缩完成: ${sourceDir.absolutePath} -> ${targetZip.absolutePath}")
        } catch (e: Exception) {
            logger.error("目录压缩失败", e)
            throw e
        }
    }
    
    /**
     * 递归压缩目录
     */
    private fun zipDirectory(sourceDir: File, baseName: String, zipOut: java.util.zip.ZipOutputStream) {
        val files = sourceDir.listFiles() ?: return
        
        for (file in files) {
            val entryName = if (baseName.isEmpty()) file.name else "$baseName/${file.name}"
            
            if (file.isDirectory) {
                // 添加目录条目
                val entry = java.util.zip.ZipEntry("$entryName/")
                zipOut.putNextEntry(entry)
                zipOut.closeEntry()
                
                // 递归压缩子目录
                zipDirectory(file, entryName, zipOut)
            } else {
                // 添加文件条目
                val entry = java.util.zip.ZipEntry(entryName)
                zipOut.putNextEntry(entry)
                
                file.inputStream().use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
    }
    
    /**
     * 解压zip文件到目录
     */
    /**
     * 智能解压文件，自动处理嵌套目录问题
     */
    private fun unzipFileIntelligent(zipFile: File, targetDir: File) {
        if (!zipFile.exists()) {
            throw Exception("压缩文件不存在: ${zipFile.absolutePath}")
        }
        
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        try {
            // 首先分析zip文件结构
            val zipStructure = analyzeZipStructure(zipFile)
            logger.info("Zip文件结构分析: $zipStructure")
            
            // 根据结构决定解压策略
            val shouldSkipRootDir = zipStructure.hasRedundantRootDir
            val rootDirToSkip = zipStructure.rootDirName
            
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                
                while (entry != null) {
                    var entryPath = entry.name
                    
                    // 如果需要跳过根目录，则移除根目录路径
                    if (shouldSkipRootDir && rootDirToSkip != null && entryPath.startsWith("$rootDirToSkip/")) {
                        entryPath = entryPath.substring(rootDirToSkip.length + 1)
                    }
                    
                    // 跳过空路径
                    if (entryPath.isEmpty()) {
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }
                    
                    val entryFile = File(targetDir, entryPath)
                    
                    if (entry.isDirectory) {
                        // 创建目录
                        entryFile.mkdirs()
                    } else {
                        // 创建父目录
                        entryFile.parentFile?.mkdirs()
                        
                        // 解压文件
                        FileOutputStream(entryFile).use { output ->
                            zipIn.copyTo(output)
                        }
                    }
                    
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            logger.info("智能文件解压完成: ${zipFile.absolutePath} -> ${targetDir.absolutePath}")
        } catch (e: Exception) {
            logger.error("智能文件解压失败", e)
            throw e
        }
    }
    
    /**
     * 分析zip文件结构
     */
    private fun analyzeZipStructure(zipFile: File): ZipStructureInfo {
        val entries = mutableListOf<String>()
        var rootDirs = mutableSetOf<String>()
        var hasPluginXml = false
        var hasLibDir = false
        var hasJarFiles = false
        
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                
                // 检查是否有插件相关文件
                if (entry.name.endsWith("plugin.xml")) {
                    hasPluginXml = true
                }
                if (entry.name.contains("/lib/") || entry.name == "lib/") {
                    hasLibDir = true
                }
                if (entry.name.endsWith(".jar")) {
                    hasJarFiles = true
                }
                
                // 收集根目录
                val pathParts = entry.name.split("/")
                if (pathParts.isNotEmpty() && pathParts[0].isNotEmpty()) {
                    rootDirs.add(pathParts[0])
                }
                
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        
        // 判断是否有冗余的根目录
        val hasRedundantRootDir = when {
            rootDirs.size == 1 -> {
                val singleRoot = rootDirs.first()
                // 如果只有一个根目录，且该目录下包含插件文件，则可能是冗余的
                val hasPluginFilesInRoot = entries.any { 
                    it.startsWith("$singleRoot/") && 
                    (it.endsWith("plugin.xml") || it.contains("/lib/") || it.endsWith(".jar"))
                }
                hasPluginFilesInRoot && singleRoot.contains("AICodeTransformer", ignoreCase = true)
            }
            else -> false
        }
        
        return ZipStructureInfo(
            totalEntries = entries.size,
            rootDirs = rootDirs.toList(),
            hasPluginXml = hasPluginXml,
            hasLibDir = hasLibDir,
            hasJarFiles = hasJarFiles,
            hasRedundantRootDir = hasRedundantRootDir,
            rootDirName = if (hasRedundantRootDir) rootDirs.firstOrNull() else null
        )
    }
    
    /**
     * Zip文件结构信息
     */
    private data class ZipStructureInfo(
        val totalEntries: Int,
        val rootDirs: List<String>,
        val hasPluginXml: Boolean,
        val hasLibDir: Boolean,
        val hasJarFiles: Boolean,
        val hasRedundantRootDir: Boolean,
        val rootDirName: String?
    )
    
    private fun unzipFile(zipFile: File, targetDir: File) {
        if (!zipFile.exists()) {
            throw Exception("压缩文件不存在: ${zipFile.absolutePath}")
        }
        
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        try {
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                
                while (entry != null) {
                    val entryFile = File(targetDir, entry.name)
                    
                    if (entry.isDirectory) {
                        // 创建目录
                        entryFile.mkdirs()
                    } else {
                        // 创建父目录
                        entryFile.parentFile?.mkdirs()
                        
                        // 解压文件
                        FileOutputStream(entryFile).use { output ->
                            zipIn.copyTo(output)
                        }
                    }
                    
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            logger.info("文件解压完成: ${zipFile.absolutePath} -> ${targetDir.absolutePath}")
        } catch (e: Exception) {
            logger.error("文件解压失败", e)
            throw e
        }
    }
    
    /**
     * 显示重启提示对话框
     */
    private fun showRestartPrompt(updateInfo: UpdateInfo) {
        try {
            // 在EDT线程中显示对话框
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
                    "插件已成功更新到版本 ${updateInfo.version}。\n\n" +
                    "为了使更新生效，需要重启 IntelliJ IDEA。\n" +
                    "是否现在重启？",
                    "更新完成 - 需要重启",
                    "立即重启",
                    "稍后重启",
                    com.intellij.openapi.ui.Messages.getQuestionIcon()
                )
                
                if (result == com.intellij.openapi.ui.Messages.YES) {
                    // 用户选择立即重启
                    restartIDE()
                } else {
                    // 用户选择稍后重启，显示提醒
                    showRestartReminder()
                }
            }
        } catch (e: Exception) {
            logger.error("显示重启提示失败", e)
        }
    }
    
    /**
     * 显示自动更新重启通知（简化版本，仅通知无确认对话框）
     */
    private fun showAutoUpdateRestartNotification(updateInfo: UpdateInfo) {
        try {
            // 在EDT线程中显示通知
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val notification = com.intellij.notification.Notification(
                    "AICodeTransformer.AutoUpdate",
                    "自动更新完成",
                    "插件已自动更新到版本 ${updateInfo.version}。请重启 IntelliJ IDEA 以应用更改。",
                    com.intellij.notification.NotificationType.INFORMATION
                )
                
                // 添加重启动作
                notification.addAction(object : com.intellij.notification.NotificationAction("立即重启") {
                    override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: com.intellij.notification.Notification) {
                        notification.expire()
                        restartIDE()
                    }
                })
                
                // 显示通知
                com.intellij.notification.Notifications.Bus.notify(notification)
                
                logger.info("已显示自动更新重启通知")
                loggingService.logInfo("已显示自动更新重启通知: ${updateInfo.version}", "自动更新")
            }
        } catch (e: Exception) {
            logger.error("显示自动更新重启通知失败", e)
        }
    }
    
    /**
     * 重启IDE
     */
    private fun restartIDE() {
        try {
            logger.info("用户选择重启IDE以应用更新")
            loggingService.logInfo("用户选择重启IDE以应用更新", "自动更新")
            
            // 使用IntelliJ的重启API
            com.intellij.openapi.application.ApplicationManager.getApplication().restart()
        } catch (e: Exception) {
            logger.error("重启IDE失败", e)
            // 如果自动重启失败，显示手动重启提示
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                com.intellij.openapi.ui.Messages.showWarningDialog(
                    null,
                    "自动重启失败，请手动重启 IntelliJ IDEA 以应用更新。",
                    "重启失败"
                )
            }
        }
    }
    
    /**
     * 显示重启提醒
     */
    private fun showRestartReminder() {
        try {
            // 显示状态栏通知
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val notification = com.intellij.notification.Notification(
                    "AICodeTransformer.Update",
                    "插件更新完成",
                    "插件已更新，请重启 IntelliJ IDEA 以应用更改。",
                    com.intellij.notification.NotificationType.INFORMATION
                )
                
                // 添加重启动作
                notification.addAction(object : com.intellij.notification.NotificationAction("立即重启") {
                    override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: com.intellij.notification.Notification) {
                        notification.expire()
                        restartIDE()
                    }
                })
                
                // 显示通知
                com.intellij.notification.Notifications.Bus.notify(notification)
                
                com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).info("已显示重启提醒通知")
            }
        } catch (e: Exception) {
            com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).error("显示重启提醒失败", e)
        }
    }
    
    override suspend fun rollbackToPreviousVersion(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                updateStatus(UpdateStatus.INSTALLING)
                notifyProgress(0, "准备回滚到上一版本...")

                com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java)
                    .info("开始回滚到上一版本")

                // 1. 获取当前插件路径
                val currentPluginPath = getCurrentPluginPath()
                if (currentPluginPath == null) {
                    throw Exception("无法确定当前插件路径")
                }

                notifyProgress(20, "查找备份文件...")

                // 2. 查找最新的备份文件
                val backupDir = File(System.getProperty("java.io.tmpdir"), "aicodetransformer_backups")
                val latestBackup = findLatestBackup(backupDir)
                if (latestBackup == null) {
                    throw Exception("未找到可用的备份文件")
                }

                notifyProgress(40, "验证备份文件...")

                // 3. 验证备份文件
                if (!latestBackup.exists()) {
                    throw Exception("备份文件不存在: ${latestBackup.absolutePath}")
                }

                notifyProgress(60, "恢复备份...")

                // 4. 恢复备份
                restoreBackup(latestBackup, currentPluginPath)

                notifyProgress(80, "验证回滚...")

                // 5. 验证回滚是否成功
                if (!currentPluginPath.exists()) {
                    throw Exception("回滚验证失败")
                }

                notifyProgress(100, "回滚完成")

                // 6. 记录回滚历史
                val rollbackInfo = UpdateInfo(
                    version = "rollback",
                    versionName = "回滚操作",
                    releaseDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    description = "回滚到上一版本",
                    downloadUrl = "",
                    fileSize = latestBackup.length(),
                    checksum = ""
                )
                recordUpdateHistory(rollbackInfo, UpdateRecordStatusConstants.SUCCESS)

                updateStatus(UpdateStatus.INSTALLED)
                com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).info("回滚完成")
                loggingService.logInfo("回滚到上一版本完成", "自动更新")

                // 7. 提示用户重启IDE
                showRollbackRestartPrompt()

                true

            } catch (e: Exception) {
                com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java)
                    .error("回滚失败", e)
                loggingService.logError(e, "回滚失败")
                updateStatus(UpdateStatus.ERROR)
                notifyError("回滚失败: ${e.message}")
                false
            }
        }
    }
     
     override fun getAvailableBackups(): List<BackupInfo> {
         return try {
             val backupDir = File(System.getProperty("java.io.tmpdir"), "aicodetransformer_backups")
             if (!backupDir.exists()) {
                 return emptyList()
             }
             
             backupDir.listFiles()?.filter { it.isFile && it.name.endsWith(".backup") }
                 ?.sortedByDescending { it.lastModified() }
                 ?.map { backupFile ->
                     BackupInfo(
                         version = extractVersionFromBackupName(backupFile.name),
                         backupTime = LocalDateTime.ofEpochSecond(
                             backupFile.lastModified() / 1000, 0, 
                             java.time.ZoneOffset.systemDefault().rules.getOffset(java.time.Instant.now())
                         ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                         backupPath = backupFile.absolutePath,
                         fileSize = backupFile.length(),
                         description = "插件备份文件"
                     )
                 } ?: emptyList()
         } catch (e: Exception) {
            com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).error("获取备份列表失败", e)
            emptyList()
        }
    }
     
     /**
     * 查找最新的备份文件
     */
    private fun findLatestBackup(backupDir: File): File? {
        if (!backupDir.exists()) {
            return null
        }
        
        return backupDir.listFiles()?.filter { it.isFile && it.name.endsWith(".backup") }
            ?.maxByOrNull { it.lastModified() }
    }
    
    /**
     * 清理旧的备份文件，只保留指定数量的最新备份
     */
    private fun cleanupOldBackups(backupDir: File, keepCount: Int) {
        try {
            if (!backupDir.exists()) {
                return
            }
            
            val backupFiles = backupDir.listFiles()?.filter { 
                it.isFile && it.name.endsWith(".backup") 
            }?.sortedByDescending { it.lastModified() } ?: return
            
            if (backupFiles.size <= keepCount) {
                com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).info("备份文件数量 ${backupFiles.size} 未超过限制 $keepCount，无需清理")
                return
            }
            
            val filesToDelete = backupFiles.drop(keepCount)
            var deletedCount = 0
            var deletedSize = 0L
            
            filesToDelete.forEach { file ->
                try {
                    val fileSize = file.length()
                    if (file.delete()) {
                        deletedCount++
                        deletedSize += fileSize
                        com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).info("删除旧备份文件: ${file.name}")
                    } else {
                        com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).warn("无法删除备份文件: ${file.name}")
                    }
                } catch (e: Exception) {
                    com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).warn("删除备份文件时发生错误: ${file.name}", e)
                }
            }
            
            if (deletedCount > 0) {
                val sizeInMB = deletedSize / (1024.0 * 1024.0)
                com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).info("备份清理完成: 删除了 $deletedCount 个文件，释放空间 ${String.format("%.2f", sizeInMB)} MB")
            }
            
        } catch (e: Exception) {
            com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).warn("备份清理过程中发生错误", e)
        }
    }
     
     /**
     * 从备份文件名中提取版本号
     */
     private fun extractVersionFromBackupName(fileName: String): String {
         return try {
             val pattern = Regex("backup_(\\d+\\.\\d+\\.\\d+)_")
             val matchResult = pattern.find(fileName)
             matchResult?.groupValues?.get(1) ?: "unknown"
         } catch (e: Exception) {
             "unknown"
         }
     }
     
     /**
     * 显示回滚重启提示
     */
     private fun showRollbackRestartPrompt() {
         try {
             com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                 val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
                     "插件已成功回滚到上一版本。\n\n" +
                     "为了使回滚生效，需要重启 IntelliJ IDEA。\n" +
                     "是否现在重启？",
                     "回滚完成 - 需要重启",
                     "立即重启",
                     "稍后重启",
                     com.intellij.openapi.ui.Messages.getQuestionIcon()
                 )
                 
                 if (result == com.intellij.openapi.ui.Messages.YES) {
                     restartIDE()
                 } else {
                     showRollbackRestartReminder()
                 }
             }
         } catch (e: Exception) {
             com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).error("显示回滚重启提示失败", e)
         }
     }
     
     /**
     * 显示回滚重启提醒
     */
     private fun showRollbackRestartReminder() {
         try {
             com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                 val notification = com.intellij.notification.Notification(
                     "AICodeTransformer.Update",
                     "插件回滚完成",
                     "插件已回滚到上一版本，请重启 IntelliJ IDEA 以应用更改。",
                     com.intellij.notification.NotificationType.INFORMATION
                 )
                 
                 notification.addAction(object : com.intellij.notification.NotificationAction("立即重启") {
                     override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: com.intellij.notification.Notification) {
                         notification.expire()
                         restartIDE()
                     }
                 })
                 
                 com.intellij.notification.Notifications.Bus.notify(notification)
                 com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).info("已显示回滚重启提醒通知")
             }
         } catch (e: Exception) {
             com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).error("显示回滚重启提醒失败", e)
         }
     }
     
     /**
     * 解析GitHub API响应
     */
     private fun parseGitHubRelease(response: String): UpdateInfo? {
         return try {
             // 简单的JSON解析，实际项目中应该使用更健壮的JSON库
             val tagNameRegex = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
             val nameRegex = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
             val bodyRegex = "\"body\"\\s*:\\s*\"([^\"]+)\"".toRegex()
             val publishedAtRegex = "\"published_at\"\\s*:\\s*\"([^\"]+)\"".toRegex()
             val downloadUrlRegex = "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.(jar|zip))\"".toRegex()
             
             val tagName = tagNameRegex.find(response)?.groupValues?.get(1) ?: return null
             val name = nameRegex.find(response)?.groupValues?.get(1) ?: tagName
             val body = bodyRegex.find(response)?.groupValues?.get(1) ?: "新版本发布"
             val publishedAt = publishedAtRegex.find(response)?.groupValues?.get(1) ?: LocalDateTime.now().toString()
             val downloadUrl = downloadUrlRegex.find(response)?.groupValues?.get(1)
             
             // 清理版本号（移除v前缀）
             val version = if (tagName.startsWith("v")) tagName.substring(1) else tagName
             
             // 解析changelog
             val changelog = parseChangelog(body)
             
             UpdateInfo(
                 version = version,
                 versionName = name,
                 releaseDate = publishedAt,
                 description = body.replace("\\n", "\n").replace("\\r", ""),
                 downloadUrl = downloadUrl ?: "",
                 fileSize = 0, // 无法从API获取，下载时会更新
                 checksum = "", // 无法从API获取
                 isForced = false,
                 changelog = changelog
             )
         } catch (e: Exception) {
             com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).error("解析GitHub API响应失败", e)
             null
         }
     }
     
     /**
     * 解析更新日志
     */
     private fun parseChangelog(body: String): List<String> {
         return try {
             val cleanBody = body.replace("\\n", "\n").replace("\\r", "")
             val lines = cleanBody.split("\n")
             val changelog = mutableListOf<String>()
             
             for (line in lines) {
                 val trimmed = line.trim()
                 if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                     changelog.add(trimmed.substring(2))
                 } else if (trimmed.startsWith("+ ")) {
                     changelog.add(trimmed.substring(2))
                 } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                     changelog.add(trimmed)
                 }
             }
             
             if (changelog.isEmpty()) {
                 listOf("新版本发布")
             } else {
                 changelog
             }
         } catch (e: Exception) {
             com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).error("解析changelog失败", e)
             listOf("新版本发布")
         }
     }
     
     // ==================== HTTP连接配置工具方法 ====================
     
     /**
      * 配置标准HTTP连接
      */
     private fun configureHttpConnection(
         connection: HttpURLConnection,
         connectTimeoutMs: Int = 15000,
         readTimeoutMs: Int = 30000
     ) {
         connection.apply {
             setRequestProperty("User-Agent", "AICodeTransformer-Plugin/1.0")
             setRequestProperty("Accept", "*/*")
             setRequestProperty("Connection", "keep-alive")
             connectTimeout = connectTimeoutMs
             readTimeout = readTimeoutMs
         }
     }
     
     /**
      * 配置GitHub API连接
      */
     private fun configureGitHubApiConnection(connection: HttpURLConnection) {
         connection.apply {
             requestMethod = "GET"
             setRequestProperty("Accept", "application/vnd.github.v3+json")
             setRequestProperty("User-Agent", "AICodeTransformer-Plugin/1.0")
             setRequestProperty("Connection", "close")
             connectTimeout = 15000
             readTimeout = 30000
         }
     }
     
     /**
      * 配置下载连接（支持动态超时）
      */
     private fun configureDownloadConnection(
         connection: HttpURLConnection,
         fileSizeMB: Long = 0
     ) {
         val connectTimeout = 60000 // 连接超时固定60秒
         val baseReadTimeout = 120000 // 基础读取超时120秒
         val dynamicReadTimeout = if (fileSizeMB > 0) {
             // 根据文件大小动态调整读取超时：每MB增加10秒，最大10分钟
             (baseReadTimeout + fileSizeMB * 10000).toInt().coerceIn(120000, 600000)
         } else {
             baseReadTimeout
         }
         
         connection.apply {
             setRequestProperty("User-Agent", "AICodeTransformer-Plugin/1.0")
             setRequestProperty("Accept", "*/*")
             setRequestProperty("Connection", "keep-alive")
             this.connectTimeout = connectTimeout
             this.readTimeout = dynamicReadTimeout
         }
         
         com.intellij.openapi.diagnostic.Logger.getInstance(AutoUpdateServiceImpl::class.java).info("设置下载超时时间 - 连接: ${connectTimeout}ms, 读取: ${dynamicReadTimeout}ms")
     }
}