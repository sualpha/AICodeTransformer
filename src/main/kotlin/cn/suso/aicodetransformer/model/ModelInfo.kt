package cn.suso.aicodetransformer.model

/**
 * 模型信息数据类
 */
data class ModelInfo(
    /** 模型名称 */
    val name: String,

    /** 模型版本 */
    val version: String? = null,

    /** 最大上下文长度 */
    val maxContextLength: Int? = null,

    /** 支持的功能 */
    val capabilities: List<String> = emptyList(),

    /** 模型描述 */
    val description: String? = null
)