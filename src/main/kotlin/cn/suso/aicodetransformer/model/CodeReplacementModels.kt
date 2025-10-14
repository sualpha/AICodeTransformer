package cn.suso.aicodetransformer.model

/**
 * 替换结果
 */
data class ReplacementResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val originalText: String? = null,
    val newText: String? = null,
    val startOffset: Int = -1,
    val endOffset: Int = -1,
    val newStartOffset: Int = -1,
    val newEndOffset: Int = -1
)

/**
 * 选择信息
 */
data class SelectionInfo(
    val hasSelection: Boolean,
    val selectedText: String?,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val endLine: Int,
    val startColumn: Int,
    val endColumn: Int
)

/**
 * 代码验证结果
 */
data class CodeValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val warnings: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)