package cn.suso.aicodetransformer.model

/**
 * 文件变更信息
 */
data class FileChangeInfo(
    /**
     * 文件路径
     */
    val filePath: String,
    
    /**
     * 变更类型（新增、修改、删除等）
     */
    val changeType: String,
    
    /**
     * 文件差异内容
     */
    val diff: String = ""
)