package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.*
import cn.suso.aicodetransformer.service.ErrorHandlingService
import cn.suso.aicodetransformer.model.ReplacementResult
import cn.suso.aicodetransformer.model.ErrorContext
import cn.suso.aicodetransformer.model.ExecutionContext
import cn.suso.aicodetransformer.constants.ExecutionStatus
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future
import kotlinx.coroutines.runBlocking

/**
 * 执行服务实现类
 */
class ExecutionServiceImpl : ExecutionService, Disposable {
    
    companion object {
        private val logger = Logger.getInstance(ExecutionServiceImpl::class.java)
        
        fun getInstance(): ExecutionService = service<ExecutionService>()
    }
    
    private val aiModelService: AIModelService = service()
    private val configurationService: ConfigurationService = service()
    private val promptTemplateService: PromptTemplateService = service()
    private val codeReplacementService: CodeReplacementService = service()
    private val errorHandlingService: ErrorHandlingService = service()
    private val codeAnalysisService: CodeAnalysisService = service()
    
    private val activeExecutions = ConcurrentHashMap<String, ExecutionContext>()
    private val executionFutures = ConcurrentHashMap<String, Future<*>>()
    private val listeners = CopyOnWriteArrayList<ExecutionListener>()
    
    override fun executeTemplate(
        template: PromptTemplate,
        selectedText: String,
        project: Project,
        editor: Editor
    ): ExecutionResult {
        val executionId = generateExecutionId()
        
        // 获取选中位置信息
        val selectionModel = editor.selectionModel
        val startOffset = if (selectionModel.hasSelection()) selectionModel.selectionStart else -1
        val endOffset = if (selectionModel.hasSelection()) selectionModel.selectionEnd else -1
        
        val context = ExecutionContext(
            executionId = executionId,
            template = template,
            templateName = template.name,
            selectedText = selectedText,
            project = project,
            editor = editor,
            selectionStartOffset = startOffset,
            selectionEndOffset = endOffset
        )
        
        activeExecutions[executionId] = context
        
        return try {
            executeInternal(context)
        } finally {
            activeExecutions.remove(executionId)
        }
    }
    
    override fun executeTemplateAsync(
        template: PromptTemplate,
        selectedText: String,
        project: Project,
        editor: Editor,
        callback: (ExecutionResult) -> Unit
    ) {
        val executionId = generateExecutionId()
        
        // 获取选中位置信息
        val selectionModel = editor.selectionModel
        val startOffset = if (selectionModel.hasSelection()) selectionModel.selectionStart else -1
        val endOffset = if (selectionModel.hasSelection()) selectionModel.selectionEnd else -1
        
        val context = ExecutionContext(
            executionId = executionId,
            template = template,
            templateName = template.name,
            selectedText = selectedText,
            project = project,
            editor = editor,
            selectionStartOffset = startOffset,
            selectionEndOffset = endOffset
        )
        
        activeExecutions[executionId] = context
        
        val task = object : Task.Backgroundable(project, "AI代码转换: ${template.name}", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val result = executeInternal(context, indicator)
                    ApplicationManager.getApplication().invokeLater {
                        callback(result)
                    }
                } catch (e: Exception) {
                    logger.error("异步执行失败", e)
                    val errorResult = ExecutionResult(
                        success = false,
                        content = null,
                        errorMessage = e.message ?: "执行失败",
                        executionTimeMs = System.currentTimeMillis() - context.startTime,
                        modelConfigId = null,
                        tokensUsed = 0
                    )
                    ApplicationManager.getApplication().invokeLater {
                        callback(errorResult)
                    }
                } finally {
                    activeExecutions.remove(executionId)
                    executionFutures.remove(executionId)
                }
            }
            
            override fun onCancel() {
                context.status = ExecutionStatus.CANCELLED
                notifyListeners { it.onExecutionCancelled(context.template.id) }
                activeExecutions.remove(executionId)
            }
        }
        
        ProgressManager.getInstance().run(task)
        // 由于runProcessWithProgressAsynchronously不返回Future，我们创建一个CompletableFuture来跟踪
        val future = CompletableFuture<Void>()
        executionFutures[executionId] = future
    }
    
    override fun executeTemplateAsync(
        templateId: String,
        selectedText: String,
        project: Project?,
        callback: (ExecutionResult) -> Unit
    ) {
        try {
            // 获取模板
            val template = promptTemplateService.getTemplate(templateId)
            if (template == null) {
                val errorResult = ExecutionResult(
                    success = false,
                    content = null,
                    errorMessage = "模板不存在: $templateId",
                    executionTimeMs = 0,
                    modelConfigId = null,
                    tokensUsed = 0
                )
                ApplicationManager.getApplication().invokeLater {
                    callback(errorResult)
                }
                return
            }
            
            val executionId = generateExecutionId()
            val context = ExecutionContext(
                executionId = executionId,
                template = template,
                templateName = template.name,
                selectedText = selectedText,
                project = project
            )
            
            activeExecutions[executionId] = context
            
            val task = object : Task.Backgroundable(project, "AI代码转换: ${template.name}", true) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        val result = executeInternal(context, indicator)
                        ApplicationManager.getApplication().invokeLater {
                            callback(result)
                        }
                    } catch (e: Exception) {
                        logger.error("异步执行失败", e)
                        val errorResult = ExecutionResult(
                            success = false,
                            content = null,
                            errorMessage = e.message ?: "执行失败",
                            executionTimeMs = System.currentTimeMillis() - context.startTime,
                            modelConfigId = null,
                            tokensUsed = 0
                        )
                        ApplicationManager.getApplication().invokeLater {
                            callback(errorResult)
                        }
                    } finally {
                        activeExecutions.remove(executionId)
                        executionFutures.remove(executionId)
                    }
                }
                
                override fun onCancel() {
                    val currentContext = activeExecutions[executionId]
                    activeExecutions.remove(executionId)
                    executionFutures.remove(executionId)
                    notifyListeners { it.onExecutionCancelled(currentContext?.template?.id ?: templateId) }
                }
            }
            
            ProgressManager.getInstance().run(task)
            // 由于runProcessWithProgressAsynchronously不返回Future，我们创建一个CompletableFuture来跟踪
            val future = CompletableFuture<Void>()
            executionFutures[executionId] = future
            
        } catch (e: Exception) {
            val errorContext = ErrorContext(
                operation = "启动异步执行",
                component = "ExecutionService",
                additionalInfo = mapOf(
                    "templateId" to templateId,
                    "selectedTextLength" to selectedText.length.toString()
                )
            )
            val handlingResult = errorHandlingService.handleException(e, errorContext)
            val errorResult = ExecutionResult(
                success = false,
                content = null,
                errorMessage = handlingResult.userMessage,
                executionTimeMs = 0,
                modelConfigId = null,
                tokensUsed = 0
            )
            ApplicationManager.getApplication().invokeLater {
                callback(errorResult)
            }
        }
    }
    
    override fun cancelExecution(executionId: String): Boolean {
        val future = executionFutures[executionId]
        val context = activeExecutions[executionId]
        
        return if (future != null && context != null) {
            future.cancel(true)
            context.status = ExecutionStatus.CANCELLED
            notifyListeners { it.onExecutionCancelled(executionId) }
            activeExecutions.remove(executionId)
            executionFutures.remove(executionId)
            true
        } else {
            false
        }
    }
    
    override fun getActiveExecutions(): List<String> {
        return activeExecutions.keys.toList()
    }
    
    override fun getExecutionStatus(executionId: String): ExecutionStatus? {
        return activeExecutions[executionId]?.status
    }
    
    override fun addExecutionListener(listener: ExecutionListener) {
        listeners.add(listener)
    }
    
    override fun removeExecutionListener(listener: ExecutionListener) {
        listeners.remove(listener)
    }
    
    override fun dispose() {
        cleanup()
    }
    
    override fun cleanup() {
        // 取消所有正在执行的任务
        val executionIds = activeExecutions.keys.toList()
        executionIds.forEach { cancelExecution(it) }
        
        listeners.clear()
    }
    
    /**
     * 内部执行逻辑
     */
    private fun executeInternal(
        context: ExecutionContext,
        indicator: ProgressIndicator? = null
    ): ExecutionResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // 1. 更新状态为运行中
            updateExecutionStatus(context, ExecutionStatus.RUNNING, 0, "开始执行...")
            indicator?.text = "准备AI模型调用..."
            
            // 1.1. 检查选中的文本
            if (context.selectedText.isBlank()) {
                throw RuntimeException("没有选中的文本")
            }
            
            // 1.5. 代码解析变量验证
            if (requiresBuiltInVariableProcessing(context.template)) {
                updateExecutionProgress(context, 5, "验证代码解析条件...")
                if (!validateObjectConversion(context.project, context.selectedText)) {
                    throw IllegalStateException("代码解析验证失败")
                }
            }
            
            // 2. 获取模型配置
            updateExecutionProgress(context, 10, "获取模型配置...")
            logger.info("当前线程: ${Thread.currentThread().name}, 取消状态: ${indicator?.isCanceled}")
            
            val modelConfig = getModelConfiguration()
            
            if (modelConfig == null) {
                throw IllegalStateException("未找到有效的模型配置")
            }
            
            // 检查取消状态
            if (indicator?.isCanceled == true) {
                logger.warn("执行在获取模型配置后被取消")
                throw InterruptedException("执行已取消")
            }
            
            logger.info("开始获取API密钥，当前线程: ${Thread.currentThread().name}")
            val apiKey = getApiKey(modelConfig.id)
            logger.info("API密钥获取完成，长度: ${apiKey.length}")
            
            // 本地模型不需要API密钥
            if (modelConfig.modelType != cn.suso.aicodetransformer.model.ModelType.LOCAL && apiKey.isBlank()) {
                logger.error("API密钥未配置，模型配置ID: ${modelConfig.id}")
                throw IllegalStateException("API密钥未配置")
            }
            
            if (modelConfig.modelType == cn.suso.aicodetransformer.model.ModelType.LOCAL) {
                logger.info("本地模型无需API密钥验证")
            } else {
                logger.info("API密钥验证通过")
            }
            
            // 3. 构建提示词
            updateExecutionProgress(context, 20, "构建提示词...")
            indicator?.text = "构建提示词..."
            logger.info("开始构建提示词，模板: ${context.template.name}")
            val prompt = buildPrompt(context.template, context.selectedText, context.project)
            logger.info("提示词构建完成，长度: ${prompt.length}")
            
            // 4. 调用AI模型
            updateExecutionProgress(context, 30, "调用AI模型...")
            indicator?.text = "调用AI模型..."
            logger.info("开始调用AI模型，配置: ${modelConfig.name}, URL: ${modelConfig.apiBaseUrl}")
            
            // 检查取消状态
            if (indicator?.isCanceled == true) {
                throw InterruptedException("执行已取消")
            }
            
            val aiResult = runBlocking {
                logger.info("正在调用AI模型服务...")
                val result = aiModelService.callModel(modelConfig, prompt, apiKey)
                logger.info("AI模型调用完成，成功: ${result.success}")
                if (!result.success) {
                    logger.error("AI模型调用失败: ${result.errorMessage}")
                }
                result
            }
            
            if (indicator?.isCanceled == true) {
                throw InterruptedException("执行已取消")
            }
            
            if (!aiResult.success) {
                throw RuntimeException(aiResult.errorMessage ?: "AI模型调用失败")
            }
            
            val transformedText = aiResult.content ?: throw RuntimeException("AI模型返回空结果")
            
            // 5. 替换代码
            updateExecutionProgress(context, 80, "替换代码...")
            indicator?.text = "替换代码..."
            
            val replacementResult = context.editor?.let { _ ->
                // 在EDT线程中执行UI操作
                val latch = java.util.concurrent.CountDownLatch(1)
                var result: ReplacementResult? = null
                var exception: Exception? = null
                
                ApplicationManager.getApplication().invokeLater {
                    try {
                        result = codeReplacementService.replaceTextWithContext(
                            context,
                            transformedText
                        )
                    } catch (e: Exception) {
                        exception = e
                    } finally {
                        latch.countDown()
                    }
                }
                
                // 等待EDT线程完成操作
                latch.await()
                
                exception?.let { throw it }
                result ?: ReplacementResult(success = false, errorMessage = "代码替换失败")
            } ?: ReplacementResult(success = true) // 如果没有编辑器，跳过替换
            
            if (!replacementResult.success) {
                throw RuntimeException(replacementResult.errorMessage ?: "代码替换失败")
            }
            
            // 6. 完成
            val executionTime = System.currentTimeMillis() - startTime
            val result = ExecutionResult(
                success = true,
                content = transformedText,
                errorMessage = null,
                executionTimeMs = executionTime,
                modelConfigId = modelConfig.id,
                tokensUsed = aiResult.tokensUsed
            )
            
            context.result = result
            context.status = ExecutionStatus.COMPLETED
            notifyListeners { it.onExecutionCompleted(context.template.id, result.content ?: "") }
            
            return result
            
        } catch (e: InterruptedException) {
            // 处理取消
            context.status = ExecutionStatus.CANCELLED
            context.error = "执行已取消"
            notifyListeners { it.onExecutionCancelled(context.template.id) }
            
            return ExecutionResult(
                success = false,
                content = null,
                errorMessage = "执行已取消",
                executionTimeMs = System.currentTimeMillis() - startTime,
                modelConfigId = null,
                tokensUsed = 0
            )
            
        } catch (e: Exception) {
            // 处理错误
            logger.error("执行失败", e)
            
            val errorMessage = e.message ?: "未知错误"
            context.status = ExecutionStatus.FAILED
            context.error = errorMessage
            notifyListeners { it.onExecutionFailed(context.template.id, errorMessage) }
            
            return ExecutionResult(
                success = false,
                content = null,
                errorMessage = errorMessage,
                executionTimeMs = System.currentTimeMillis() - startTime,
                modelConfigId = null,
                tokensUsed = 0
            )
        }
    }
    
    /**
     * 获取模型配置
     */
    private fun getModelConfiguration(): ModelConfiguration? {
        return try {
            logger.info("开始获取默认模型配置...")
            val allConfigs = configurationService.getModelConfigurations()
            logger.info("所有模型配置数量: ${allConfigs.size}")
            allConfigs.forEach { config ->
                logger.info("配置: ${config.name}, 启用: ${config.enabled}, ID: ${config.id}")
            }
            
            val enabledConfigs = configurationService.getEnabledModelConfigurations()
            logger.info("启用的模型配置数量: ${enabledConfigs.size}")
            
            val defaultConfig = configurationService.getDefaultModelConfiguration()
            if (defaultConfig != null) {
                logger.info("找到默认模型配置: ${defaultConfig.name}, ID: ${defaultConfig.id}")
            } else {
                logger.warn("未找到默认模型配置")
            }
            
            defaultConfig
        } catch (e: Exception) {
            logger.error("获取模型配置失败", e)
            null
        }
    }
    
    /**
     * 获取API密钥
     */
    private fun getApiKey(configId: String): String {
        return try {
            logger.info("开始获取API密钥，配置ID: $configId")
            val apiKey = configurationService.getApiKey(configId)
            if (apiKey.isNullOrBlank()) {
                logger.warn("API密钥为空，配置ID: $configId")
                ""
            } else {
                logger.info("成功获取API密钥，配置ID: $configId，密钥长度: ${apiKey.length}")
                apiKey
            }
        } catch (e: Exception) {
            logger.error("获取API密钥失败，配置ID: $configId", e)
            ""
        }
    }
    
    /**
     * 构建提示词
     */
    private fun buildPrompt(template: PromptTemplate, selectedText: String, project: Project? = null): String {
        // 基础变量
        val variables = mutableMapOf(
            "code" to selectedText,
            "selected_text" to selectedText,
            "selectedCode" to selectedText,
            "input" to selectedText
        )
        
        // 如果模板包含需要特殊处理的内置变量，添加请求参数和返回参数信息
        if (requiresBuiltInVariableProcessing(template) && project != null) {
            try {
                val classInfoList = codeAnalysisService.extractClassInfoFromCode(selectedText, project)
                
                val requestParams = StringBuilder()
                val responseParams = StringBuilder()
                
                classInfoList.forEach { classInfo ->
                    val paramInfo = StringBuilder()
                    paramInfo.append("类名: ${classInfo.name}\n")
                    if (classInfo.packageName.isNotEmpty()) {
                        paramInfo.append("包名: ${classInfo.packageName}\n")
                    }
                    paramInfo.append("字段:\n")
                    classInfo.fields.forEach { field ->
                        paramInfo.append("  - ${field.name}: ${field.type}")
                        if (field.annotations.isNotEmpty()) {
                            paramInfo.append(" ${field.annotations.joinToString(" ")}")
                        }
                        paramInfo.append("\n")
                    }
                    paramInfo.append("\n")
                    
                    // 简单判断：如果类名包含Request或Req，认为是请求参数
                    if (classInfo.name.contains("Request", ignoreCase = true) || 
                        classInfo.name.contains("Req", ignoreCase = true)) {
                        requestParams.append(paramInfo)
                    } else {
                        responseParams.append(paramInfo)
                    }
                }
                
                variables["requestParams"] = if (requestParams.isNotEmpty()) requestParams.toString().trim() else "无请求参数信息"
                variables["responseParams"] = if (responseParams.isNotEmpty()) responseParams.toString().trim() else "无返回参数信息"
                
                // 添加第一个请求参数信息
                val firstRequestParam = classInfoList.firstOrNull { classInfo ->
                    classInfo.name.contains("Request", ignoreCase = true) || 
                    classInfo.name.contains("Req", ignoreCase = true)
                }
                
                if (firstRequestParam != null) {
                    val firstParamInfo = StringBuilder()
                    firstParamInfo.append("类名: ${firstRequestParam.name}\n")
                    if (firstRequestParam.packageName.isNotEmpty()) {
                        firstParamInfo.append("包名: ${firstRequestParam.packageName}\n")
                    }
                    firstParamInfo.append("字段:\n")
                    firstRequestParam.fields.forEach { field ->
                        firstParamInfo.append("  - ${field.name}: ${field.type}")
                        if (field.annotations.isNotEmpty()) {
                            firstParamInfo.append(" ${field.annotations.joinToString(" ")}")
                        }
                        firstParamInfo.append("\n")
                    }
                    variables["firstRequestParam"] = firstParamInfo.toString().trim()
                } else {
                    variables["firstRequestParam"] = "无第一个请求参数信息"
                }
                
            } catch (e: Exception) {
                logger.warn("解析类信息失败", e)
                variables["requestParams"] = "解析请求参数失败: ${e.message}"
                variables["responseParams"] = "解析返回参数失败: ${e.message}"
                variables["firstRequestParam"] = "解析第一个请求参数失败: ${e.message}"
            }
        }
        
        // 使用模板对象而不是模板内容来处理变量替换
        return promptTemplateService.processTemplate(template, variables)
    }
    
    /**
     * 更新执行状态
     */
    private fun updateExecutionStatus(
        context: ExecutionContext,
        status: ExecutionStatus,
        progress: Int,
        message: String
    ) {
        context.status = status
        context.progress = progress
        context.progressMessage = message
        
        when (status) {
            ExecutionStatus.RUNNING -> {
                notifyListeners { it.onExecutionStarted(context.template.id, context) }
            }
            else -> {
                notifyListeners { it.onExecutionProgress(context.template.id, progress, message) }
            }
        }
    }
    
    /**
     * 更新执行进度
     */
    private fun updateExecutionProgress(context: ExecutionContext, progress: Int, message: String) {
        context.progress = progress
        context.progressMessage = message
        notifyListeners { it.onExecutionProgress(context.template.id, progress, message) }
    }
    
    /**
     * 生成执行ID
     */
    private fun generateExecutionId(): String {
        return "exec_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
    }
    
    /**
     * 通知监听器
     */
    private fun notifyListeners(action: (ExecutionListener) -> Unit) {
        listeners.forEach { listener ->
            try {
                action(listener)
            } catch (e: Exception) {
                logger.error("通知执行监听器失败", e)
            }
        }
    }
    
    /**
     * 检查是否为对象转换模板
     */
    private fun isObjectConversionTemplate(template: PromptTemplate): Boolean {
        return template.id == "object-convert" ||
               template.name.contains("对象转换", ignoreCase = true) ||
               template.category.contains("对象转换", ignoreCase = true)
    }
    
    /**
     * 检查模板内容是否包含需要特殊处理的内置变量
     */
    private fun requiresBuiltInVariableProcessing(template: PromptTemplate): Boolean {
        // 驼峰命名模板不需要对象转换验证，只是简单的文本转换
        if (template.id == "camel-case-convert") {
            return false
        }
        
        val content = template.content
        // 只有包含需要复杂代码分析的变量才需要对象转换验证
        val complexVariables = listOf(
            "{{requestParams}}", "{{responseParams}}", "{{firstRequestParam}}",
            "{{className}}", "{{methodName}}", "{{packageName}}"
        )
        return complexVariables.any { content.contains(it) }
    }
    
    /**
     * 验证对象转换的前置条件
     */
    private fun validateObjectConversion(project: Project?, selectedText: String): Boolean {
        try {
            if (project == null) {
                logger.warn("项目为空，跳过对象转换验证")
                return true
            }
            
            // 尝试提取类信息
            val classInfoList = codeAnalysisService.extractClassInfoFromCode(selectedText, project)
            
            // 如果无法提取到任何类信息，直接提示用户
            if (classInfoList.isEmpty()) {
                showObjectConversionError(project, "请在选中代码中包含目标类信息")
                return false // 阻止执行，不调用AI模型
            }
            
            // 检查字段信息（只对能提取到的类进行检查）
            var hasFieldInfo = false
            for (classInfo in classInfoList) {
                if (classInfo.fields.isNotEmpty()) {
                    hasFieldInfo = true
                    break
                }
            }
            
            // 如果所有类都没有字段信息，直接提示用户
            if (!hasFieldInfo) {
                showObjectConversionError(project, "未检测到源类字段信息")
                return false // 阻止执行，不调用AI模型
            }
            
            return true
            
        } catch (e: IllegalStateException) {
            // 捕获字段信息获取失败的异常，直接提示用户
            if (e.message?.contains("字段信息") == true) {
                project?.let { showObjectConversionError(it, "未检测到源类字段信息") }
            } else {
                project?.let { showObjectConversionError(it, "请在选中代码中包含目标类信息") }
            }
            return false // 阻止执行，不调用AI模型
        } catch (e: Exception) {
            logger.error("对象转换验证失败", e)
            // 严重错误时阻止执行
            project?.let { showObjectConversionError(it, "代码分析失败：${e.message}") }
            return false
        }
    }
    
    /**
     * 显示简化的变量替换状态提示
     */
    private fun showObjectConversionError(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.ui.Messages.showErrorDialog(
                project,
                message,
                "变量替换提示"
            )
        }
    }
}