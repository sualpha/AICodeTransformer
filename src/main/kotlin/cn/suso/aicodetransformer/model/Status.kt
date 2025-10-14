package cn.suso.aicodetransformer.model

import cn.suso.aicodetransformer.constants.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

/**
 * 通知操作
 */
data class NotificationAction(
    val text: String,
    val action: () -> Unit
)

/**
 * 状态信息
 */
data class StatusInfo(
    val message: String?,
    val isProgressVisible: Boolean,
    val progressMessage: String?,
    val progressValue: Int,
    val activeExecutions: Map<String, ExecutionStatusInfo>
)

/**
 * 执行状态信息
 */
data class ExecutionStatusInfo(
    val executionId: String,
    val status: ExecutionStatus,
    val message: String,
    val progress: Int,
    val startTime: Long
)

/**
 * 执行上下文
 */
data class ExecutionContext(
    val executionId: String,
    val template: PromptTemplate,
    val templateName: String,
    val selectedText: String,
    val project: Project?,
    val editor: Editor? = null,
    val startTime: Long = System.currentTimeMillis(),
    var status: ExecutionStatus = ExecutionStatus.PENDING,
    var progress: Int = 0,
    var progressMessage: String = "",
    var result: ExecutionResult? = null,
    var error: String? = null,
    val selectionStartOffset: Int = -1,
    val selectionEndOffset: Int = -1
)