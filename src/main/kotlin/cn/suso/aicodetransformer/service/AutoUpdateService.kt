package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.constants.UpdateStatus
import cn.suso.aicodetransformer.model.BackupInfo
import cn.suso.aicodetransformer.model.UpdateInfo
import cn.suso.aicodetransformer.model.UpdateRecord

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
     * 在后台线程启动下载任务
     */
    fun downloadUpdateAsync(updateInfo: UpdateInfo, onProgress: (Int) -> Unit = {})
    
    /**
     * 安装更新
     * @param updateInfo 更新信息
     * @return 安装是否成功
     */
    suspend fun installUpdate(updateInfo: UpdateInfo): Boolean

    /**
     * 在后台线程启动安装任务
     */
    fun installUpdateAsync(updateInfo: UpdateInfo)
    
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
     * @param triggerSource 触发源：manual（手动）或 timer（定时任务）
     */
    fun startAutoUpdate(triggerSource: String = "manual")
    
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
     * 获取当前更新信息
     * @return 当前可用的更新信息，如果没有则返回null
     */
    fun getCurrentUpdateInfo(): UpdateInfo?
    
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

    /**
     * 取消当前下载
     * @return 是否成功取消下载
     */
    fun cancelDownload(): Boolean
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










