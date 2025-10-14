package cn.suso.aicodetransformer.constants

import kotlinx.serialization.Serializable

/**
 * 更新记录状态
 */
@Serializable
enum class UpdateRecordStatusConstants {
    SUCCESS,    // 更新成功
    FAILED,     // 更新失败
}