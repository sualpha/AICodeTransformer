package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

/**
 * 快捷键备份数据
 */
@Serializable
data class ShortcutBackup(
    val version: String,
    val timestamp: Long,
    val shortcuts: List<ShortcutInfo>
)

/**
 * 快捷键信息
 */
@Serializable
data class ShortcutInfo(
    val templateId: String,
    val templateName: String,
    val shortcut: String
)