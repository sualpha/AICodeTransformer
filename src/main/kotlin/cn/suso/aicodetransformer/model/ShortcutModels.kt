package cn.suso.aicodetransformer.model

/**
 * 快捷键验证结果
 */
data class ShortcutValidationResult(
    val isValid: Boolean,
    val missingShortcuts: List<String>,
    val conflictingShortcuts: List<String>,
    val message: String
)