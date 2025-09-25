package cn.suso.aicodetransformer.debug

import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.ExecutionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages

/**
 * 执行调试助手
 * 用于诊断执行过程中的问题
 */
class ExecutionDebugAction : AnAction("调试执行过程", "调试AI代码转换的执行过程", null) {
    
    companion object {
        private val logger = Logger.getInstance(ExecutionDebugAction::class.java)
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText
        
        if (selectedText.isNullOrBlank()) {
            Messages.showWarningDialog(project, "请先选择要转换的代码", "调试执行")
            return
        }
        
        logger.info("=== 开始调试执行过程 ===")
        logger.info("选中文本长度: ${selectedText.length}")
        logger.info("项目: ${project.name}")
        
        // 创建一个简单的测试模板
        val testTemplate = PromptTemplate(
            id = "debug_template",
            name = "调试模板",
            description = "用于调试的测试模板",
            content = "请将以下代码转换为驼峰命名法：\n\n${'$'}{selectedCode}\n\n只返回转换后的代码，不要添加任何解释。",
            category = "调试",
            enabled = true
        )
        
        val executionService = ApplicationManager.getApplication().service<ExecutionService>()
        
        // 使用进度对话框执行
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "调试执行过程", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    logger.info("=== 开始执行调试任务 ===")
                    indicator.text = "正在执行调试任务..."
                    indicator.fraction = 0.0
                    
                    // 检查取消状态
                    if (indicator.isCanceled) {
                        logger.warn("任务在开始前就被取消了")
                        return
                    }
                    
                    logger.info("调用ExecutionService.executeTemplate...")
                    val result = executionService.executeTemplate(testTemplate, selectedText, project, editor)
                    
                    logger.info("=== 执行结果 ===")
                    logger.info("成功: ${result.success}")
                    logger.info("内容长度: ${result.content?.length ?: 0}")
                    logger.info("错误信息: ${result.errorMessage ?: "无"}")
                    logger.info("执行时间: ${result.executionTimeMs}ms")
                    logger.info("模型配置ID: ${result.modelConfigId ?: "无"}")
                    logger.info("Token使用量: ${result.tokensUsed}")
                    
                    ApplicationManager.getApplication().invokeLater {
                        if (result.success) {
                            Messages.showInfoMessage(
                                project,
                                "执行成功！\n执行时间: ${result.executionTimeMs}ms\n内容长度: ${result.content?.length ?: 0}",
                                "调试结果"
                            )
                        } else {
                            Messages.showErrorDialog(
                                project,
                                "执行失败: ${result.errorMessage}",
                                "调试结果"
                            )
                        }
                    }
                    
                } catch (e: Exception) {
                    logger.error("调试执行过程中发生异常", e)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "调试执行失败: ${e.message}",
                            "调试错误"
                        )
                    }
                }
            }
            
            override fun onCancel() {
                logger.warn("=== 调试任务被取消 ===")
            }
            
            override fun onSuccess() {
                logger.info("=== 调试任务成功完成 ===")
            }
            
            override fun onThrowable(error: Throwable) {
                logger.error("=== 调试任务发生错误 ===", error)
            }
        })
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val selectedText = editor?.selectionModel?.selectedText
        
        e.presentation.isEnabledAndVisible = project != null && editor != null && !selectedText.isNullOrBlank()
    }
}