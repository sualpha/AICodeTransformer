package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.service.CodeReplacementService
import cn.suso.aicodetransformer.service.ErrorContext
import cn.suso.aicodetransformer.service.ErrorHandlingService
import cn.suso.aicodetransformer.service.ReplacementResult
import cn.suso.aicodetransformer.service.SelectionInfo
import cn.suso.aicodetransformer.service.ValidationResult
import cn.suso.aicodetransformer.service.ExecutionContext
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager

/**
 * 代码替换服务实现类
 */
class CodeReplacementServiceImpl : CodeReplacementService {
    
    companion object {
        private val logger = Logger.getInstance(CodeReplacementServiceImpl::class.java)
        
        fun getInstance(): CodeReplacementService = service<CodeReplacementService>()
    }
    
    private val errorHandlingService: ErrorHandlingService = service()
    
    override fun getSelectedText(editor: Editor): String? {
        val selectionModel = editor.selectionModel
        return if (selectionModel.hasSelection()) {
            selectionModel.selectedText
        } else {
            null
        }
    }
    
    override fun replaceSelectedText(editor: Editor, newText: String): ReplacementResult {
        // 在ReadAction中获取选择信息
        val selectionInfo = ReadAction.compute<Triple<Boolean, Int, Int>, RuntimeException> {
            val selectionModel = editor.selectionModel
            if (!selectionModel.hasSelection()) {
                Triple(false, 0, 0)
            } else {
                Triple(true, selectionModel.selectionStart, selectionModel.selectionEnd)
            }
        }
        
        if (!selectionInfo.first) {
            return ReplacementResult(
                success = false,
                errorMessage = "没有选中的文本"
            )
        }
        
        val startOffset = selectionInfo.second
        val endOffset = selectionInfo.third
        val originalText = ReadAction.compute<String?, RuntimeException> {
            editor.selectionModel.selectedText
        }
        
        return replaceText(editor, startOffset, endOffset, newText).copy(
            originalText = originalText
        )
    }
    
    override fun replaceTextWithContext(context: ExecutionContext, newText: String): ReplacementResult {
        val editor = context.editor ?: return ReplacementResult(
            success = false,
            errorMessage = "编辑器不可用"
        )
        
        // 检查是否有保存的位置信息
        if (context.selectionStartOffset == -1 || context.selectionEndOffset == -1) {
            return ReplacementResult(
                success = false,
                errorMessage = "没有保存的选中位置信息"
            )
        }
        
        // 使用保存的位置信息进行替换
        return replaceText(editor, context.selectionStartOffset, context.selectionEndOffset, newText).copy(
            originalText = context.selectedText
        )
    }
    
    override fun replaceText(
        editor: Editor,
        startOffset: Int,
        endOffset: Int,
        newText: String
    ): ReplacementResult {
        return try {
            val document = editor.document
            val originalText = document.getText(TextRange(startOffset, endOffset))
            
            // 验证偏移量
            if (startOffset < 0 || endOffset > document.textLength || startOffset > endOffset) {
                return ReplacementResult(
                    success = false,
                    errorMessage = "无效的文本范围: [$startOffset, $endOffset]"
                )
            }
            
            // 验证替换内容
            val validationResult = validateReplacementInternal(newText)
            if (!validationResult.isValid) {
                return ReplacementResult(
                    success = false,
                    errorMessage = validationResult.errorMessage
                )
            }
            
            var success = false
            var errorMessage: String? = null
            val newEndOffset = startOffset + newText.length
            val project = editor.project
            
            if (project != null) {
                WriteCommandAction.runWriteCommandAction(project, "AI Code Replacement", null, Runnable {
                    try {
                        document.replaceString(startOffset, endOffset, newText)
                        success = true
                    } catch (e: Exception) {
                        logger.error("文本替换失败", e)
                        errorMessage = e.message ?: "文本替换失败"
                    }
                })
            } else {
                // 如果没有项目上下文，回退到runWriteAction
                ApplicationManager.getApplication().runWriteAction {
                    try {
                        document.replaceString(startOffset, endOffset, newText)
                        success = true
                    } catch (e: Exception) {
                        logger.error("文本替换失败", e)
                        errorMessage = e.message ?: "文本替换失败"
                    }
                }
            }
            
            ReplacementResult(
                success = success,
                errorMessage = errorMessage,
                originalText = originalText,
                newText = newText,
                startOffset = startOffset,
                endOffset = endOffset,
                newStartOffset = startOffset,
                newEndOffset = newEndOffset
            )
            
        } catch (e: Exception) {
            val handlingResult = errorHandlingService.handleCodeReplacementError(e, null, newText)
            ReplacementResult(
                success = false,
                errorMessage = handlingResult.userMessage
            )
        }
    }
    
    override fun insertText(editor: Editor, offset: Int, text: String): ReplacementResult {
        return try {
            val document = editor.document
            
            // 验证偏移量
            if (offset < 0 || offset > document.textLength) {
                return ReplacementResult(
                    success = false,
                    errorMessage = "无效的插入位置: $offset"
                )
            }
            
            // 验证插入内容
            val validationResult = validateReplacementInternal(text)
            if (!validationResult.isValid) {
                return ReplacementResult(
                    success = false,
                    errorMessage = validationResult.errorMessage
                )
            }
            
            var success = false
            var errorMessage: String? = null
            val project = editor.project
            
            if (project != null) {
                WriteCommandAction.runWriteCommandAction(project, "AI Code Insertion", null, Runnable {
                    try {
                        document.insertString(offset, text)
                        success = true
                    } catch (e: Exception) {
                        logger.error("文本插入失败", e)
                        errorMessage = e.message ?: "文本插入失败"
                    }
                })
            } else {
                // 如果没有项目上下文，回退到runWriteAction
                ApplicationManager.getApplication().runWriteAction {
                    try {
                        document.insertString(offset, text)
                        success = true
                    } catch (e: Exception) {
                        logger.error("文本插入失败", e)
                        errorMessage = e.message ?: "文本插入失败"
                    }
                }
            }
            
            ReplacementResult(
                success = success,
                errorMessage = errorMessage,
                originalText = null,
                newText = text,
                startOffset = offset,
                endOffset = offset,
                newStartOffset = offset,
                newEndOffset = offset + text.length
            )
            
        } catch (e: Exception) {
            val handlingResult = errorHandlingService.handleCodeReplacementError(e, null, text)
            ReplacementResult(
                success = false,
                errorMessage = handlingResult.userMessage
            )
        }
    }
    
    override fun getSelectionInfo(editor: Editor): SelectionInfo? {
        var startOffset = 0
        var endOffset = 0
        
        return try {
            val selectionModel = editor.selectionModel
            val document = editor.document
            
            if (!selectionModel.hasSelection()) {
                val caretModel = editor.caretModel
                val offset = caretModel.offset
                val line = document.getLineNumber(offset)
                val column = offset - document.getLineStartOffset(line)
                
                return SelectionInfo(
                    hasSelection = false,
                    selectedText = null,
                    startOffset = offset,
                    endOffset = offset,
                    startLine = line,
                    endLine = line,
                    startColumn = column,
                    endColumn = column
                )
            }
            
            startOffset = selectionModel.selectionStart
            endOffset = selectionModel.selectionEnd
            val selectedText = selectionModel.selectedText
            
            val startLine = document.getLineNumber(startOffset)
            val endLine = document.getLineNumber(endOffset)
            val startColumn = startOffset - document.getLineStartOffset(startLine)
            val endColumn = endOffset - document.getLineStartOffset(endLine)
            
            SelectionInfo(
                hasSelection = true,
                selectedText = selectedText,
                startOffset = startOffset,
                endOffset = endOffset,
                startLine = startLine,
                endLine = endLine,
                startColumn = startColumn,
                endColumn = endColumn
            )
            
        } catch (e: Exception) {
            val errorContext = ErrorContext(
                operation = "获取选择信息",
                component = "CodeReplacementService",
                additionalInfo = mapOf(
                    "startOffset" to startOffset.toString(),
                    "endOffset" to endOffset.toString()
                )
            )
            errorHandlingService.handleException(e, errorContext)
            null
        }
    }
    
    override fun validateReplacement(editor: Editor, newText: String): ValidationResult {
        return try {
            val selectionModel = editor.selectionModel
            
            if (!selectionModel.hasSelection()) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "没有选中的文本"
                )
            }
            
            validateReplacementInternal(newText)
            
        } catch (e: Exception) {
            logger.error("验证替换失败", e)
            ValidationResult(
                isValid = false,
                errorMessage = e.message ?: "验证替换失败"
            )
        }
    }
    
    override fun executeWithUndo(editor: Editor, actionName: String, action: () -> Unit) {
        val project = editor.project
        if (project != null) {
            WriteCommandAction.runWriteCommandAction(project, actionName, null, action)
        } else {
            ApplicationManager.getApplication().runWriteAction(action)
        }
    }
    
    override fun formatCode(
        editor: Editor,
        project: Project,
        startOffset: Int,
        endOffset: Int
    ): ReplacementResult {
        return try {
            val document = editor.document
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            
            if (psiFile == null) {
                return ReplacementResult(
                    success = false,
                    errorMessage = "无法获取PSI文件"
                )
            }
            
            // 验证范围
            if (startOffset < 0 || endOffset > document.textLength || startOffset > endOffset) {
                return ReplacementResult(
                    success = false,
                    errorMessage = "无效的格式化范围: [$startOffset, $endOffset]"
                )
            }
            
            val originalText = document.getText(TextRange(startOffset, endOffset))
            var success = false
            var errorMessage: String? = null
            var newText: String? = null
            
            ApplicationManager.getApplication().runWriteAction {
                try {
                    // 提交文档更改到PSI
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    
                    // 格式化代码
                    val codeStyleManager = CodeStyleManager.getInstance(project)
                    codeStyleManager.reformatText(psiFile, startOffset, endOffset)
                    
                    // 获取格式化后的文本
                    newText = document.getText(TextRange(startOffset, endOffset))
                    success = true
                    
                } catch (e: Exception) {
                    logger.error("代码格式化失败", e)
                    errorMessage = e.message ?: "代码格式化失败"
                }
            }
            
            ReplacementResult(
                success = success,
                errorMessage = errorMessage,
                originalText = originalText,
                newText = newText,
                startOffset = startOffset,
                endOffset = endOffset,
                newStartOffset = startOffset,
                newEndOffset = startOffset + (newText?.length ?: 0)
            )
            
        } catch (e: Exception) {
            logger.error("格式化代码时发生异常", e)
            ReplacementResult(
                success = false,
                errorMessage = e.message ?: "格式化代码时发生异常"
            )
        }
    }
    
    /**
     * 内部验证替换内容
     */
    private fun validateReplacementInternal(newText: String): ValidationResult {
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        
        // 检查文本长度
        if (newText.length > 100000) {
            return ValidationResult(
                isValid = false,
                errorMessage = "替换文本过长（超过100,000字符）"
            )
        }
        
        // 检查是否包含不可见字符
        if (newText.contains("\u0000")) {
            return ValidationResult(
                isValid = false,
                errorMessage = "文本包含空字符（\\u0000）"
            )
        }
        
        // 检查行结束符
        val hasWindowsLineEndings = newText.contains("\r\n")
        val hasUnixLineEndings = newText.contains("\n") && !hasWindowsLineEndings
        val hasMacLineEndings = newText.contains("\r") && !hasWindowsLineEndings
        
        if (hasWindowsLineEndings && (hasUnixLineEndings || hasMacLineEndings)) {
            warnings.add("文本包含混合的行结束符")
            suggestions.add("建议统一使用一种行结束符格式")
        }
        
        // 检查制表符和空格混用
        val hasTabsAndSpaces = newText.contains("\t") && newText.contains("    ")
        if (hasTabsAndSpaces) {
            warnings.add("文本包含制表符和空格的混用")
            suggestions.add("建议统一使用制表符或空格进行缩进")
        }
        
        // 检查尾随空白
        val lines = newText.split("\n")
        val hasTrailingWhitespace = lines.any { it.endsWith(" ") || it.endsWith("\t") }
        if (hasTrailingWhitespace) {
            warnings.add("文本包含尾随空白字符")
            suggestions.add("建议移除行尾的空白字符")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings,
            suggestions = suggestions
        )
    }
}