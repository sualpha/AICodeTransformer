package cn.suso.aicodetransformer.service

import kotlinx.serialization.Serializable

/**
 * 自动更新服务接口
 * 负责检查、下载和安装插件更新
 */
interface AutoUpdateService {
    
    /**
     * 检查是否有可用更新
     * @return 更新信息，如果没有更新返回null
     */
    suspend fun checkForUpdates(): UpdateInfo?
    
    /**
     * 下载更新
     * @param updateInfo 更新信息
     * @param onProgress 下载进度回调
     * @return 下载是否成功
     */
    suspend fun downloadUpdate(updateInfo: UpdateInfo, onProgress: (Int) -> Unit = {}): Boolean
    
    /**
     * 安装更新
     * @param updateInfo 更新信息
     * @return 安装是否成功
     */
    suspend fun installUpdate(updateInfo: UpdateInfo): Boolean
    
    /**
     * 获取当前版本信息
     * @return 当前版本
     */
    fun getCurrentVersion(): String
    
    /**
     * 获取更新历史
     * @return 更新历史列表
     */
    fun getUpdateHistory(): List<UpdateRecord>
    
    /**
     * 启动自动更新检查
     */
    fun startAutoUpdate()
    
    /**
     * 停止自动更新检查
     */
    fun stopAutoUpdate()
    
    /**
     * 获取更新状态
     * @return 当前更新状态
     */
    fun getUpdateStatus(): UpdateStatus
    
    /**
     * 添加状态监听器
     */
    fun addStatusListener(listener: UpdateStatusListener)
    
    /**
     * 移除状态监听器
     */
    fun removeStatusListener(listener: UpdateStatusListener)
    
    /**
     * 回滚到上一个版本
     * @return 回滚是否成功
     */
    suspend fun rollbackToPreviousVersion(): Boolean
    
    /**
     * 获取可用的备份版本列表
     * @return 备份版本列表
     */
    fun getAvailableBackups(): List<BackupInfo>
}

/**
 * 更新信息
 */
@Serializable
data class UpdateInfo(
    /** 版本号 */
    val version: String,
    
    /** 版本名称 */
    val versionName: String,
    
    /** 发布时间 */
    val releaseDate: String,
    
    /** 更新描述 */
    val description: String,
    
    /** 下载URL */
    val downloadUrl: String,
    
    /** 文件大小（字节） */
    val fileSize: Long,
    
    /** 文件校验和 */
    val checksum: String,
    
    /** 是否为强制更新 */
    val isForced: Boolean = false,
    
    /** 最低兼容版本 */
    val minCompatibleVersion: String? = null,
    
    /** 更新日志 */
    val changelog: List<String> = emptyList()
)

/**
 * 更新记录
 */
@Serializable
data class UpdateRecord(
    /** 版本号 */
    val version: String,
    
    /** 更新时间 */
    val updateTime: String,
    
    /** 更新状态 */
    val status: UpdateRecordStatus,
    
    /** 错误信息（如果有） */
    val errorMessage: String? = null
)

/**
 * 更新记录状态
 */
@Serializable
enum class UpdateRecordStatus {
    SUCCESS,    // 更新成功
    FAILED,     // 更新失败
}

/**
 * 更新状态
 */
@Serializable
enum class UpdateStatus {
    IDLE,           // 空闲状态
    CHECKING,       // 检查更新中
    AVAILABLE,      // 有可用更新
    DOWNLOADING,    // 下载中
    DOWNLOADED,     // 下载完成
    INSTALLING,     // 安装中
    INSTALLED,      // 安装完成
    ERROR,          // 错误状态
    UP_TO_DATE      // 已是最新版本
}

/**
 * 更新状态监听器
 */
interface UpdateStatusListener {
    /**
     * 更新状态改变时调用
     * @param oldStatus 旧状态
     * @param newStatus 新状态
     * @param updateInfo 更新信息（可选）
     */
    fun onStatusChanged(oldStatus: UpdateStatus, newStatus: UpdateStatus, updateInfo: UpdateInfo? = null)
    
    /**
     * 更新进度改变时调用
     * @param progress 进度百分比（0-100）
     * @param message 进度消息
     */
    fun onProgressChanged(progress: Int, message: String)
    
    /**
     * 更新错误时调用
     * @param error 错误信息
     */
    fun onError(error: String)
}

/**
 * 备份信息
 */
@Serializable
data class BackupInfo(
    /** 备份版本号 */
    val version: String,
    
    /** 备份时间 */
    val backupTime: String,
    
    /** 备份文件路径 */
    val backupPath: String,
    
    /** 备份文件大小 */
    val fileSize: Long,
    
    /** 备份描述 */
    val description: String = ""
)