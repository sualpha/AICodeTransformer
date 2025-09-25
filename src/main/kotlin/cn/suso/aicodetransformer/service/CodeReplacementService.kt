package cn.suso.aicodetransformer.service

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

/**
 * 代码替换服务接口
 * 负责安全地获取选中代码并替换文本
 */
interface CodeReplacementService {
    
    /**
     * 获取当前选中的文本
     * @param editor 编辑器实例
     * @return 选中的文本，如果没有选中则返回null
     */
    fun getSelectedText(editor: Editor): String?
    
    /**
     * 替换选中的文本
     * @param editor 编辑器实例
     * @param newText 新的文本内容
     * @return 替换结果
     */
    fun replaceSelectedText(editor: Editor, newText: String): ReplacementResult
    
    /**
     * 使用ExecutionContext中保存的位置信息替换文本
     * @param context 执行上下文，包含保存的选中位置信息
     * @param newText 新的文本内容
     * @return 替换结果
     */
    fun replaceTextWithContext(context: ExecutionContext, newText: String): ReplacementResult
    
    /**
     * 替换指定范围的文本
     * @param editor 编辑器实例
     * @param startOffset 开始偏移量
     * @param endOffset 结束偏移量
     * @param newText 新的文本内容
     * @return 替换结果
     */
    fun replaceText(
        editor: Editor,
        startOffset: Int,
        endOffset: Int,
        newText: String
    ): ReplacementResult
    
    /**
     * 在指定位置插入文本
     * @param editor 编辑器实例
     * @param offset 插入位置偏移量
     * @param text 要插入的文本
     * @return 插入结果
     */
    fun insertText(editor: Editor, offset: Int, text: String): ReplacementResult
    
    /**
     * 获取选中文本的范围信息
     * @param editor 编辑器实例
     * @return 选择范围信息
     */
    fun getSelectionInfo(editor: Editor): SelectionInfo?
    
    /**
     * 验证文本替换是否安全
     * @param editor 编辑器实例
     * @param newText 新文本
     * @return 验证结果
     */
    fun validateReplacement(editor: Editor, newText: String): ValidationResult
    
    /**
     * 创建撤销操作
     * @param editor 编辑器实例
     * @param actionName 操作名称
     * @param action 要执行的操作
     */
    fun executeWithUndo(editor: Editor, actionName: String, action: () -> Unit)
    
    /**
     * 格式化代码
     * @param editor 编辑器实例
     * @param project 项目实例
     * @param startOffset 开始偏移量
     * @param endOffset 结束偏移量
     * @return 格式化结果
     */
    fun formatCode(
        editor: Editor,
        project: Project,
        startOffset: Int,
        endOffset: Int
    ): ReplacementResult
}

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
 * 验证结果
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val warnings: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)

/**
 * 文本操作类型
 */
enum class TextOperationType {
    REPLACE,    // 替换
    INSERT,     // 插入
    DELETE,     // 删除
    FORMAT      // 格式化
}