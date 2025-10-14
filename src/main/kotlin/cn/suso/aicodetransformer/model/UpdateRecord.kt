package cn.suso.aicodetransformer.model

import cn.suso.aicodetransformer.constants.UpdateRecordStatusConstants
import kotlinx.serialization.Serializable

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
    val status: UpdateRecordStatusConstants,

    /** 错误信息（如果有） */
    val errorMessage: String? = null
)