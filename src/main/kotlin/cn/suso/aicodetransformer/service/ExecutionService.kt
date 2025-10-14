package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.model.ExecutionContext
import cn.suso.aicodetransformer.constants.ExecutionStatus
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

/**
 * 执行服务接口
 * 负责协调整个AI代码转换的执行流程
 */
interface ExecutionService {
    
    /**
     * 执行模板处理
     * @param template 提示模板
     * @param selectedText 选中的代码文本
     * @param project 当前项目
     * @param editor 编辑器实例
     * @return 执行结果
     */
    fun executeTemplate(
        template: PromptTemplate,
        selectedText: String,
        project: Project,
        editor: Editor
    ): ExecutionResult
    
    /**
     * 异步执行模板处理
     * @param template 提示模板
     * @param selectedText 选中的代码文本
     * @param project 当前项目
     * @param editor 编辑器实例
     * @param callback 执行完成回调
     */
    fun executeTemplateAsync(
        template: PromptTemplate,
        selectedText: String,
        project: Project,
        editor: Editor,
        callback: (ExecutionResult) -> Unit
    )
    
    /**
     * 通过模板ID异步执行模板处理
     * @param templateId 模板ID
     * @param selectedText 选中的代码文本
     * @param project 当前项目
     * @param callback 执行完成回调
     */
    fun executeTemplateAsync(
        templateId: String,
        selectedText: String,
        project: Project?,
        callback: (ExecutionResult) -> Unit
    )
    
    /**
     * 取消正在执行的任务
     * @param executionId 执行ID
     * @return 是否成功取消
     */
    fun cancelExecution(executionId: String): Boolean
    
    /**
     * 获取正在执行的任务列表
     * @return 执行ID列表
     */
    fun getActiveExecutions(): List<String>
    
    /**
     * 获取执行状态
     * @param executionId 执行ID
     * @return 执行状态
     */
    fun getExecutionStatus(executionId: String): ExecutionStatus?
    
    /**
     * 添加执行监听器
     * @param listener 监听器
     */
    fun addExecutionListener(listener: ExecutionListener)
    
    /**
     * 移除执行监听器
     * @param listener 监听器
     */
    fun removeExecutionListener(listener: ExecutionListener)
    
    /**
     * 清理资源
     */
    fun cleanup()
}



/**
 * 执行监听器接口
 */
interface ExecutionListener {
    /**
     * 执行开始时调用
     * @param templateId 模板ID
     * @param context 执行上下文
     */
    fun onExecutionStarted(templateId: String, context: ExecutionContext)
    
    /**
     * 执行进度更新时调用
     * @param templateId 模板ID
     * @param progress 进度百分比 (0-100)
     * @param message 进度消息
     */
    fun onExecutionProgress(templateId: String, progress: Int, message: String)
    
    /**
     * 执行完成时调用
     * @param templateId 模板ID
     * @param result 生成的代码结果
     */
    fun onExecutionCompleted(templateId: String, result: String)
    
    /**
     * 执行失败时调用
     * @param templateId 模板ID
     * @param error 错误信息
     */
    fun onExecutionFailed(templateId: String, error: String)
    
    /**
     * 执行取消时调用
     * @param templateId 模板ID
     */
    fun onExecutionCancelled(templateId: String)
}