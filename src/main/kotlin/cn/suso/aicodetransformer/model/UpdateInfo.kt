package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

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