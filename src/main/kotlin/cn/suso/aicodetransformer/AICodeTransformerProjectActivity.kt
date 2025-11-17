package cn.suso.aicodetransformer

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.ErrorContext
import cn.suso.aicodetransformer.service.*
import cn.suso.aicodetransformer.service.impl.*
import cn.suso.aicodetransformer.constants.ExecutionStatus
import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.model.ErrorHandlingResult
import cn.suso.aicodetransformer.model.ExecutionContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.messages.MessageBusConnection

/**
 * AI代码转换器项目活动
 * 负责初始化和协调所有服务
 */
class AICodeTransformerProjectActivity : ProjectActivity {
    
    companion object {
        private val logger = Logger.getInstance(AICodeTransformerProjectActivity::class.java)
        
        // 项目级别的组件实例缓存
        private val projectInstances = mutableMapOf<Project, AICodeTransformerProjectActivity>()
        
        fun getInstance(project: Project): AICodeTransformerProjectActivity {
            return projectInstances.getOrPut(project) {
                val instance = AICodeTransformerProjectActivity()
                // 确保实例被初始化
                if (!instance.initialized) {
                    instance.initializeServices(project)
                    instance.setupServiceIntegration(project)
                    instance.registerTemplateActions()
                    instance.setupProjectCloseListener(project)
                    instance.initialized = true
                    logger.info("AI Code Transformer 项目活动初始化完成")
                }
                instance
            }
        }
        
        /**
         * 清理项目实例缓存
         */
        internal fun cleanupProject(project: Project) {
            projectInstances.remove(project)?.let { instance ->
                instance.cleanup()
                logger.info("AI Code Transformer 项目实例已清理: ${project.name}")
            }
        }
    }
    
    // 服务实例
    private lateinit var configurationService: ConfigurationService
    private lateinit var promptTemplateService: PromptTemplateService
    private lateinit var aiModelService: AIModelService
    private lateinit var actionService: ActionService
    private lateinit var executionService: ExecutionService
    private lateinit var codeReplacementService: CodeReplacementService
    private lateinit var statusService: StatusService
    private lateinit var errorHandlingService: ErrorHandlingService

    private var initialized = false
    private var messageBusConnection: MessageBusConnection? = null

    private fun tr(key: String, vararg params: Any): String = I18n.t(key, *params)
    
    override suspend fun execute(project: Project) {
        logger.info("AI Code Transformer ProjectActivity.execute() 被调用，项目: ${project.name}")
        if (!initialized) {
            logger.info("开始初始化 AI Code Transformer 服务...")
            initializeServices(project)
            setupServiceIntegration(project)
            registerTemplateActions()
            initialized = true
            logger.info("AI Code Transformer 项目活动初始化完成")
        } else {
            logger.info("AI Code Transformer 已经初始化，跳过重复初始化")
        }
    }
    
    /**
     * 初始化所有服务
     */
    private fun initializeServices(project: Project) {
        try {
            logger.info("initializeServices() 开始执行，项目: ${project.name}")
            
            // 获取应用级服务实例
            val application = ApplicationManager.getApplication()
            logger.info("获取 ApplicationManager 实例成功")
            
            configurationService = application.service<ConfigurationService>()
            logger.info("ConfigurationService 初始化完成")
            
            promptTemplateService = application.service<PromptTemplateService>()
            logger.info("PromptTemplateService 初始化完成")
            
            aiModelService = application.service<AIModelService>()
            logger.info("AIModelService 初始化完成")
            
            actionService = application.service<ActionService>()
            logger.info("ActionService 初始化完成")
            
            executionService = application.service<ExecutionService>()
            logger.info("ExecutionService 初始化完成")
            
            codeReplacementService = application.service<CodeReplacementService>()
            logger.info("CodeReplacementService 初始化完成")
            
            statusService = application.service<StatusService>()
            logger.info("StatusService 初始化完成")
            
            errorHandlingService = application.service<ErrorHandlingService>()
            logger.info("ErrorHandlingService 初始化完成")
            
            // 确保内置模板已初始化（不设置快捷键）
            logger.info("开始初始化内置模板...")
            ensureBuiltInTemplatesInitialized()
            logger.info("内置模板初始化完成（不设置快捷键）")
            
            logger.info("所有服务初始化完成")
            
        } catch (e: Exception) {
            logger.error("服务初始化失败", e)
            throw e
        }
    }
    
    /**
     * 设置服务间的集成关系
     */
    private fun setupServiceIntegration(project: Project) {
        try {
            // 设置模板变更监听器
            promptTemplateService.addTemplateChangeListener(object : TemplateChangeListener {
                override fun onTemplateAdded(template: cn.suso.aicodetransformer.model.PromptTemplate) {
                    refreshTemplateActions()
                }
                
                override fun onTemplateUpdated(
                    oldTemplate: cn.suso.aicodetransformer.model.PromptTemplate,
                    newTemplate: cn.suso.aicodetransformer.model.PromptTemplate
                ) {
                    refreshTemplateActions()
                }
                
                override fun onTemplateDeleted(template: cn.suso.aicodetransformer.model.PromptTemplate) {
                    refreshTemplateActions()
                }
                
                override fun onTemplateShortcutChanged(
                    template: cn.suso.aicodetransformer.model.PromptTemplate,
                    oldShortcut: String?,
                    newShortcut: String?
                ) {
                    refreshTemplateActions()
                }
            })
            
            // 设置配置变更监听器
            configurationService.addConfigurationChangeListener(object : ConfigurationChangeListener {
                override fun onConfigurationAdded(config: ModelConfiguration) {
                    logger.info("模型配置已添加: ${config.name}")
                }
                
                override fun onConfigurationUpdated(oldConfig: ModelConfiguration, newConfig: ModelConfiguration) {
                    logger.info("模型配置已更新: ${newConfig.name}")
                }
                
                override fun onConfigurationDeleted(config: ModelConfiguration) {
                    logger.info("模型配置已删除: ${config.name}")
                }
                
                override fun onDefaultConfigurationChanged(config: ModelConfiguration?) {
                    logger.info("默认模型配置已更改")
                }
            })
            
            // 设置执行状态监听器
            executionService.addExecutionListener(object : ExecutionListener {
                override fun onExecutionStarted(templateId: String, context: ExecutionContext) {
                    statusService.updateExecutionStatus(
                        context.executionId,
                        ExecutionStatus.RUNNING,
                        tr("status.execution.runningTemplate", templateId),
                        0,
                        project
                    )
                }
                
                override fun onExecutionProgress(templateId: String, progress: Int, message: String) {
                    statusService.updateExecutionStatus(
                        templateId,
                        ExecutionStatus.RUNNING,
                        message,
                        progress,
                        project
                    )
                }
                
                override fun onExecutionCompleted(templateId: String, result: String) {
                    statusService.updateExecutionStatus(
                        templateId,
                        ExecutionStatus.COMPLETED,
                        tr("status.execution.completed"),
                        100,
                        project
                    )
                }
                
                override fun onExecutionFailed(templateId: String, error: String) {
                    statusService.updateExecutionStatus(
                        templateId,
                        ExecutionStatus.FAILED,
                        tr("status.execution.failedWithReason", error),
                        0,
                        project
                    )
                }
                
                override fun onExecutionCancelled(templateId: String) {
                    statusService.updateExecutionStatus(
                        templateId,
                        ExecutionStatus.CANCELLED,
                        tr("status.execution.cancelled"),
                        0,
                        project
                    )
                }
            })
            
            // 设置错误处理监听器
            errorHandlingService.addErrorListener(object : ErrorListener {
                override fun onErrorOccurred(
                    exception: Throwable,
                    context: ErrorContext,
                    result: ErrorHandlingResult
                ) {
                    // 更新状态显示
                    statusService.updateExecutionStatus(
                        "error-${System.currentTimeMillis()}",
                        ExecutionStatus.FAILED,
                        "操作失败: ${result.userMessage}",
                        0,
                        project
                    )
                }
                
                override fun onRetryStarted(context: ErrorContext, attempt: Int) {
                    statusService.updateExecutionStatus(
                        "retry-${System.currentTimeMillis()}",
                        ExecutionStatus.RUNNING,
                        "正在重试操作 (第${attempt}次)...",
                        0,
                        project
                    )
                }
                
                override fun onRetrySucceeded(context: ErrorContext, attempts: Int) {
                    // 重试成功的处理
                }
                
                override fun onRetryFailed(context: ErrorContext, finalException: Throwable, attempts: Int) {
                    // 重试失败的处理
                }
            })
            
            logger.info("服务集成设置完成")
            
        } catch (e: Exception) {
            logger.error("服务集成设置失败", e)
            errorHandlingService.handleException(
                e,
                ErrorContext(
                    operation = "服务集成设置",
                    component = "AICodeTransformerProjectActivity"
                )
            )
        }
    }
    
    /**
     * 注册模板动作
     */
    private fun registerTemplateActions() {
        try {
            // 获取现有模板
            val templates = promptTemplateService.getTemplates()
            
            if (templates.isEmpty()) {
                // 如果没有模板，初始化默认模板（不设置快捷键）
                promptTemplateService.resetToDefaults(false)
                logger.info("初始化默认模板（不设置快捷键）")
            }
            
            // 使用 ActionService 的 refreshTemplateActions 方法来注册所有模板动作
            // 这样可以避免重复注册，因为 ActionService 已经作为 TemplateChangeListener 监听模板变更
            actionService.refreshTemplateActions()
            
            logger.info("模板动作注册完成")
            
        } catch (e: Exception) {
            logger.error("注册模板动作失败", e)
        }
    }
    
    /**
     * 刷新模板动作
     * 当模板配置发生变化时调用
     */
    fun refreshTemplateActions() {
        try {
            // 直接使用 ActionService 的 refreshTemplateActions 方法
            actionService.refreshTemplateActions()
            
            logger.info("模板动作刷新完成")
            
        } catch (e: Exception) {
            logger.error("刷新模板动作失败", e)
            errorHandlingService.handleException(
                e,
                ErrorContext(
                    operation = "刷新模板动作",
                    component = "AICodeTransformerProjectActivity"
                )
            )
        }
    }

    
    /**
     * 设置项目关闭监听器
     */
    private fun setupProjectCloseListener(project: Project) {
        try {
            messageBusConnection = project.messageBus.connect()
            messageBusConnection?.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
                override fun projectClosed(closedProject: Project) {
                    if (closedProject == project) {
                        logger.info("项目关闭，开始清理资源: ${project.name}")
                        cleanupProject(project)
                    }
                }
            })
            logger.debug("项目关闭监听器设置完成")
        } catch (e: Exception) {
            logger.error("设置项目关闭监听器失败", e)
        }
    }
    
    /**
     * 确保内置模板已初始化
     */
    private fun ensureBuiltInTemplatesInitialized() {
        try {
            val currentTemplates = promptTemplateService.getTemplates()
            val builtInTemplates = promptTemplateService.getDefaultTemplates()
            
            // 检查是否缺少内置模板
            val missingTemplates = builtInTemplates.filter { builtIn ->
                currentTemplates.none { it.id == builtIn.id }
            }
            
            if (missingTemplates.isNotEmpty()) {
                logger.info("发现缺失的内置模板，开始初始化: ${missingTemplates.map { it.name }}")
                
                // 添加缺失的内置模板
                missingTemplates.forEach { template ->
                    promptTemplateService.saveTemplate(template)
                    logger.debug("添加内置模板: ${template.name} (${template.id})")
                }
                
                logger.info("内置模板初始化完成，共添加 ${missingTemplates.size} 个模板")
            } else {
                logger.debug("所有内置模板已存在，无需初始化")
            }
            
        } catch (e: Exception) {
            logger.error("内置模板初始化失败", e)
            // 不抛出异常，避免影响插件启动
        }
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            // 断开消息总线连接
            messageBusConnection?.disconnect()
            messageBusConnection = null
            
            // 清理各个服务
            if (::actionService.isInitialized) {
                actionService.cleanup()
            }
            
            if (::executionService.isInitialized) {
                executionService.cleanup()
            }
            
            if (::aiModelService.isInitialized && aiModelService is AIModelServiceImpl) {
                (aiModelService as AIModelServiceImpl).dispose()
            }
            
            logger.info("资源清理完成")
        } catch (e: Exception) {
            logger.error("清理资源时发生错误", e)
        }
    }
    
}