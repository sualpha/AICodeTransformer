package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

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