package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.service.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
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
        private val logger = Logger.getInstance(AutoUpdateServiceImpl::class.java)
        private const val UPDATE_CHECK_URL = "https://api.github.com/repos/sualpha/AICodeTransformer/releases/latest"
        private const val CURRENT_VERSION = "1.0.0" // 从插件配置中读取
        private const val UPDATE_HISTORY_FILE = "update_history.json"
    }
    
    private val configurationService: ConfigurationService = service()
    private val loggingService: LoggingService = service()
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private var currentStatus = UpdateStatus.IDLE
    private val listeners = CopyOnWriteArrayList<UpdateStatusListener>()
    private var updateJob: Job? = null
    private val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateTimer: Timer? = null
    
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
            try {
                updateStatus(UpdateStatus.DOWNLOADING, updateInfo)
                notifyProgress(0, "开始下载更新...")
                
                logger.info("开始下载更新: ${updateInfo.version}")
                
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
                
                // 下载文件
                val success = downloadFileWithResume(updateInfo.downloadUrl, downloadFile) { progress ->
                    onProgress(progress)
                    notifyProgress(progress, "下载中... $progress%")
                }
                
                if (success) {
                    // 验证文件校验和（如果提供了校验和）
                    if (updateInfo.checksum.isNotEmpty()) {
                        val fileChecksum = calculateChecksum(downloadFile)
                        if (fileChecksum == updateInfo.checksum) {
                            updateStatus(UpdateStatus.DOWNLOADED, updateInfo)
                            notifyProgress(100, "下载完成")
                            logger.info("更新下载完成: ${downloadFile.absolutePath}")
                            loggingService.logInfo("更新下载完成: ${downloadFile.absolutePath}", "自动更新")
                            true
                        } else {
                            logger.error("文件校验和不匹配")
                            loggingService.logError(Exception("文件校验和不匹配"), "自动更新")
                            updateStatus(UpdateStatus.ERROR)
                            notifyError("文件校验和不匹配")
                            downloadFile.delete()
                            false
                        }
                    } else {
                        // 没有校验和，直接认为下载成功
                        updateStatus(UpdateStatus.DOWNLOADED, updateInfo)
                        notifyProgress(100, "下载完成")
                        logger.info("更新下载完成: ${downloadFile.absolutePath}")
                        loggingService.logInfo("更新下载完成: ${downloadFile.absolutePath}", "自动更新")
                        true
                    }
                } else {
                    updateStatus(UpdateStatus.ERROR)
                    val errorMsg = "下载失败，请检查网络连接或稍后重试"
                    logger.error(errorMsg)
                    loggingService.logError(Exception(errorMsg), "自动更新")
                    notifyError(errorMsg)
                    false
                }
                
            } catch (e: java.net.UnknownHostException) {
                val errorMsg = "网络连接失败，无法连接到下载服务器"
                logger.error(errorMsg, e)
                loggingService.logError(e, "网络连接失败")
                updateStatus(UpdateStatus.ERROR)
                notifyError("$errorMsg: ${e.message}")
                false
            } catch (e: java.net.SocketTimeoutException) {
                val errorMsg = "下载超时，请检查网络连接"
                logger.error(errorMsg, e)
                loggingService.logError(e, "下载超时")
                updateStatus(UpdateStatus.ERROR)
                notifyError("$errorMsg: ${e.message}")
                false
            } catch (e: java.io.IOException) {
                val errorMsg = "文件操作失败"
                logger.error(errorMsg, e)
                loggingService.logError(e, "文件操作失败")
                updateStatus(UpdateStatus.ERROR)
                notifyError("$errorMsg: ${e.message}")
                false
            } catch (e: Exception) {
                val errorMsg = "下载更新失败"
                logger.error(errorMsg, e)
                loggingService.logError(e, "下载更新失败")
                updateStatus(UpdateStatus.ERROR)
                notifyError("$errorMsg: ${e.message}")
                false
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
                val fileName = "aicodetransformer-${updateInfo.version}.jar"
                val downloadedFile = File(downloadDir, fileName)
                
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
                    if (!verifyInstallation(currentPluginPath, updateInfo)) {
                        throw Exception("安装验证失败")
                    }
                    
                    notifyProgress(90, "清理临时文件...")
                    
                    // 7. 清理下载的临时文件
                    downloadedFile.delete()
                    
                    // 8. 记录更新历史
                    recordUpdateHistory(updateInfo, UpdateRecordStatus.SUCCESS)
                    
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
                recordUpdateHistory(updateInfo, UpdateRecordStatus.FAILED, e.message)
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
                val fileName = "aicodetransformer-${updateInfo.version}.jar"
                val downloadedFile = File(downloadDir, fileName)
                
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
                    if (!verifyInstallation(currentPluginPath, updateInfo)) {
                        throw Exception("安装验证失败")
                    }
                    
                    notifyProgress(90, "清理临时文件...")
                    
                    // 7. 清理下载的临时文件
                    downloadedFile.delete()
                    
                    // 8. 记录更新历史
                    recordUpdateHistory(updateInfo, UpdateRecordStatus.SUCCESS)
                    
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
                recordUpdateHistory(updateInfo, UpdateRecordStatus.FAILED, e.message)
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
    
    override fun startAutoUpdate() {
        val settings = configurationService.getGlobalSettings()
        if (settings.enableAutoUpdate) {
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
            }, 0, intervalMs)
            
            loggingService.logInfo("自动更新已启动，检查间隔：${settings.updateInterval}")
        }
    }
    
    /**
     * 执行完整的自动更新流程：检查 -> 下载 -> 安装
     */
    private suspend fun performFullAutoUpdate() {
        try {
            logger.info("开始执行完整的自动更新流程")
            
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
    
    override fun addStatusListener(listener: UpdateStatusListener) {
        listeners.add(listener)
    }
    
    override fun removeStatusListener(listener: UpdateStatusListener) {
        listeners.remove(listener)
    }
    
    private fun updateStatus(newStatus: UpdateStatus, updateInfo: UpdateInfo? = null) {
        val oldStatus = currentStatus
        currentStatus = newStatus
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
        var lastException: Exception? = null
        
        // 重试机制：最多重试3次
        repeat(3) { attempt ->
            try {
                logger.info("开始检查GitHub最新版本 (尝试 ${attempt + 1}/3)")
                
                // 调用GitHub API获取最新版本信息
                val url = URL(UPDATE_CHECK_URL)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "AICodeTransformer-Plugin/1.0")
                    setRequestProperty("Connection", "close")
                    connectTimeout = 15000  // 增加到15秒
                    readTimeout = 30000     // 增加到30秒
                }
                
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
                        
                        logger.info("获取到最新版本: ${releaseInfo.version}")
                        return releaseInfo
                    }
                    403 -> {
                        logger.warn("GitHub API访问受限 (403)，可能触发了速率限制")
                        lastException = Exception("GitHub API访问受限，请稍后重试")
                    }
                    404 -> {
                        logger.error("GitHub仓库或发布版本不存在 (404)")
                        return null // 404错误不重试
                    }
                    else -> {
                        logger.warn("GitHub API返回错误状态码: $responseCode")
                        lastException = Exception("GitHub API返回错误状态码: $responseCode")
                    }
                }
                
                connection.disconnect()
                
            } catch (e: java.net.SocketTimeoutException) {
                logger.warn("网络连接超时 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = Exception("网络连接超时，请检查网络连接")
            } catch (e: java.net.UnknownHostException) {
                logger.warn("无法解析域名 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = Exception("无法连接到GitHub，请检查网络连接")
            } catch (e: java.net.ConnectException) {
                logger.warn("连接被拒绝 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = Exception("无法连接到GitHub服务器")
            } catch (e: Exception) {
                logger.warn("检查远程版本失败 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = e
            }
            
            // 如果不是最后一次尝试，等待后重试
            if (attempt < 2) {
                delay(2000L * (attempt + 1)) // 递增延迟：2秒、4秒
            }
        }
        
        // 所有重试都失败了
        logger.error("检查远程版本失败，已重试3次", lastException)
        loggingService.logError(lastException ?: Exception("未知错误"), "检查远程版本失败")
        return null
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
        var lastException: Exception? = null
        
        // 重试机制：最多重试3次
        repeat(3) { attempt ->
            try {
                logger.info("开始下载文件 (尝试 ${attempt + 1}/3): ${file.name}")
                
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
                    return true
                } else {
                    lastException = Exception("下载失败，未知原因")
                }
                
            } catch (e: java.net.SocketTimeoutException) {
                logger.warn("下载超时 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = Exception("下载超时，请检查网络连接")
            } catch (e: java.net.UnknownHostException) {
                logger.warn("无法解析下载地址 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = Exception("无法连接到下载服务器")
            } catch (e: java.io.IOException) {
                logger.warn("下载IO错误 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = Exception("下载过程中发生IO错误: ${e.message}")
            } catch (e: Exception) {
                logger.warn("下载失败 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = e
            }
            
            // 如果不是最后一次尝试，等待后重试
            if (attempt < 2) {
                delay(3000L * (attempt + 1)) // 递增延迟：3秒、6秒
            }
        }
        
        // 所有重试都失败了
        logger.error("文件下载失败，已重试3次: ${file.name}", lastException)
        return false
    }
    
    /**
     * 单线程断点续传下载
     */
    private fun downloadFileResume(url: String, file: File, resumeFrom: Long, totalSize: Long, onProgress: (Int) -> Unit): Boolean {
        var connection: HttpURLConnection? = null
        var inputStream: java.io.InputStream? = null
        var outputStream: java.io.OutputStream? = null
        
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            
            // 配置连接
            connection.connectTimeout = 60000
            connection.readTimeout = 120000
            connection.setRequestProperty("User-Agent", "AICodeTransformer-Plugin/1.0")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Connection", "keep-alive")
            
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
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
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
            
            logger.info("文件下载完成，总字节数: $totalBytesRead")
            true
            
        } catch (e: java.net.SocketTimeoutException) {
            logger.error("下载超时: ${e.message}")
            false
        } catch (e: java.net.UnknownHostException) {
            logger.error("无法解析主机: ${e.message}")
            false
        } catch (e: java.io.IOException) {
            logger.error("IO错误: ${e.message}")
            false
        } catch (e: Exception) {
            logger.error("断点续传下载失败", e)
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
            logger.error("下载文件失败", e)
            false
        }
    }
    
    /**
     * 检查服务器是否支持Range请求
     */
    private fun checkRangeSupport(url: String): Boolean {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("User-Agent", "AICodeTransformer-Plugin/1.0")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
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
            val connection = URL(url).openConnection() as HttpURLConnection
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
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 60000
            connection.readTimeout = 120000
            connection.setRequestProperty("User-Agent", "AICodeTransformer-Plugin/1.0")
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
            logger.error("下载分块失败", e)
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
        var lastException: Exception? = null
        
        // 重试机制：最多重试3次
        repeat(3) { attempt ->
            var connection: HttpURLConnection? = null
            var inputStream: java.io.InputStream? = null
            var outputStream: java.io.OutputStream? = null
            
            try {
                logger.info("开始单线程下载 (尝试 ${attempt + 1}/3): ${file.name}")
                
                connection = URL(url).openConnection() as HttpURLConnection
                
                // 优化连接配置
                connection.connectTimeout = 60000  // 增加连接超时到60秒
                connection.readTimeout = 120000    // 增加读取超时到120秒
                connection.setRequestProperty("User-Agent", "AICodeTransformer-Plugin/1.0")
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty("Connection", "keep-alive")
                
                // 检查响应码
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    logger.error("下载失败，HTTP响应码: $responseCode")
                    lastException = Exception("HTTP错误: $responseCode")
                    return@repeat
                }
                
                val fileSize = connection.contentLengthLong  // 使用Long类型支持大文件
                if (fileSize <= 0) {
                    logger.warn("无法获取文件大小")
                    lastException = Exception("无法获取文件大小")
                    return@repeat
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
                
                logger.info("单线程下载成功: ${file.name}, 总字节数: $totalBytesRead")
                return true
                
            } catch (e: java.net.SocketTimeoutException) {
                logger.warn("下载超时 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = Exception("下载超时，请检查网络连接")
            } catch (e: java.net.UnknownHostException) {
                logger.warn("无法解析下载地址 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = Exception("无法连接到下载服务器")
            } catch (e: java.io.IOException) {
                logger.warn("下载IO错误 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = Exception("下载过程中发生IO错误: ${e.message}")
            } catch (e: Exception) {
                logger.warn("下载失败 (尝试 ${attempt + 1}/3): ${e.message}")
                lastException = e
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
            
            // 如果不是最后一次尝试，等待后重试
            if (attempt < 2) {
                Thread.sleep((2000 * (attempt + 1)).toLong()) // 递增延迟：2秒、4秒
            }
        }
        
        // 所有重试都失败了
        logger.error("单线程下载失败，已重试3次: ${file.name}", lastException)
        return false
    }
    
    private fun calculateChecksum(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = file.readBytes()
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.error("计算文件校验和失败", e)
            ""
        }
    }
    
    private fun recordUpdateHistory(updateInfo: UpdateInfo, status: UpdateRecordStatus, errorMessage: String? = null) {
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
            while (currentDir != null && currentDir.exists()) {
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
     * 安装新版本
     */
    private fun installNewVersion(newVersionFile: File, targetPath: File) {
        if (targetPath.isDirectory) {
            // 如果目标是目录，需要先清空目录再解压新版本
            targetPath.deleteRecursively()
            targetPath.mkdirs()
            unzipFile(newVersionFile, targetPath)
        } else {
            // 如果目标是文件，直接替换
            val parentDir = targetPath.parentFile
            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }
            newVersionFile.copyTo(targetPath, overwrite = true)
        }
        
        logger.info("新版本安装完成: ${targetPath.absolutePath}")
    }
    
    /**
     * 验证安装是否成功
     */
    private fun verifyInstallation(installedPath: File, updateInfo: UpdateInfo): Boolean {
        return try {
            logger.info("开始验证安装: ${installedPath.absolutePath}")
            
            // 1. 检查文件是否存在
            if (!installedPath.exists()) {
                logger.error("安装验证失败：文件不存在")
                return false
            }
            
            // 2. 检查文件大小
            if (installedPath.isFile) {
                val fileSize = installedPath.length()
                if (fileSize == 0L) {
                    logger.error("安装验证失败：文件大小为0")
                    return false
                }
                
                // 检查文件大小是否合理（至少1KB）
                if (fileSize < 1024) {
                    logger.warn("安装验证警告：文件大小过小 ($fileSize bytes)")
                }
                
                logger.info("文件大小验证通过: ${fileSize / 1024} KB")
            }
            
            // 3. 检查文件可读性
            if (!installedPath.canRead()) {
                logger.error("安装验证失败：文件不可读")
                return false
            }
            
            // 4. 如果是JAR文件，验证JAR文件完整性
            if (installedPath.name.endsWith(".jar")) {
                if (!verifyJarFile(installedPath)) {
                    logger.error("安装验证失败：JAR文件完整性检查失败")
                    return false
                }
            }
            
            // 5. 如果是目录，验证目录结构
            if (installedPath.isDirectory) {
                if (!verifyDirectoryStructure(installedPath)) {
                    logger.error("安装验证失败：目录结构验证失败")
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
            if (!directory.isDirectory) {
                return false
            }
            
            val files = directory.listFiles()
            if (files == null || files.isEmpty()) {
                logger.warn("目录为空: ${directory.absolutePath}")
                return false
            }
            
            // 检查是否有必要的文件
            var hasValidFiles = false
            files.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    hasValidFiles = true
                }
            }
            
            if (!hasValidFiles) {
                logger.error("目录中没有有效文件")
                return false
            }
            
            logger.info("目录结构验证通过，包含 ${files.size} 个项目")
            true
        } catch (e: Exception) {
            logger.error("目录结构验证失败", e)
            false
        }
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
                
                logger.info("已显示重启提醒通知")
            }
        } catch (e: Exception) {
             logger.error("显示重启提醒失败", e)
         }
     }
     
     override suspend fun rollbackToPreviousVersion(): Boolean {
         return withContext(Dispatchers.IO) {
             try {
                 updateStatus(UpdateStatus.INSTALLING)
                 notifyProgress(0, "准备回滚到上一版本...")
                 
                 logger.info("开始回滚到上一版本")
                 
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
                 recordUpdateHistory(rollbackInfo, UpdateRecordStatus.SUCCESS)
                 
                 updateStatus(UpdateStatus.INSTALLED)
                 logger.info("回滚完成")
                 loggingService.logInfo("回滚到上一版本完成", "自动更新")
                 
                 // 7. 提示用户重启IDE
                 showRollbackRestartPrompt()
                 
                 true
                 
             } catch (e: Exception) {
                 logger.error("回滚失败", e)
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
             logger.error("获取备份列表失败", e)
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
                logger.info("备份文件数量 ${backupFiles.size} 未超过限制 $keepCount，无需清理")
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
                        logger.info("删除旧备份文件: ${file.name}")
                    } else {
                        logger.warn("无法删除备份文件: ${file.name}")
                    }
                } catch (e: Exception) {
                    logger.warn("删除备份文件时发生错误: ${file.name}", e)
                }
            }
            
            if (deletedCount > 0) {
                val sizeInMB = deletedSize / (1024.0 * 1024.0)
                logger.info("备份清理完成: 删除了 $deletedCount 个文件，释放空间 ${String.format("%.2f", sizeInMB)} MB")
            }
            
        } catch (e: Exception) {
            logger.warn("备份清理过程中发生错误", e)
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
             logger.error("显示回滚重启提示失败", e)
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
                 logger.info("已显示回滚重启提醒通知")
             }
         } catch (e: Exception) {
             logger.error("显示回滚重启提醒失败", e)
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
             logger.error("解析GitHub API响应失败", e)
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
             logger.error("解析changelog失败", e)
             listOf("新版本发布")
         }
     }
}