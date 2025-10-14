package cn.suso.aicodetransformer.constants

import kotlinx.serialization.Serializable

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
 * 下载状态
 */
@Serializable
enum class DownloadState {
    IDLE,           // 空闲
    PREPARING,      // 准备中
    DOWNLOADING,    // 下载中
    VERIFYING,      // 校验中
    COMPLETED,      // 完成
    FAILED,         // 失败
    CANCELLED       // 取消
}