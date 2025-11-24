package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.constants.TemplateConstants
import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.model.ErrorContext
import cn.suso.aicodetransformer.model.ModelType
import cn.suso.aicodetransformer.model.PromptOptimizationRequest
import cn.suso.aicodetransformer.model.PromptOptimizationResult
import cn.suso.aicodetransformer.service.AIModelService
import cn.suso.aicodetransformer.service.ConfigurationService
import cn.suso.aicodetransformer.service.ErrorHandlingService
import cn.suso.aicodetransformer.service.PromptOptimizationService
import com.intellij.openapi.components.service
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * 默认的 Prompt 优化服务实现。
 */
class PromptOptimizationServiceImpl : PromptOptimizationService {

    companion object {
        fun getInstance(): PromptOptimizationService = service()

        private const val LIST_DELIMITER = "||"

        private fun parseLines(raw: String): List<String> = raw.split(LIST_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private val configurationService: ConfigurationService = service()
    private val aiModelService: AIModelService = service()
    private val errorHandlingService: ErrorHandlingService = service()
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun optimizePrompt(request: PromptOptimizationRequest): PromptOptimizationResult {
        return runCatching {
            val modelConfig = configurationService.getDefaultModelConfiguration()
                ?: error(I18n.t("prompt.aiOptimize.error.noDefaultConfig"))

            val apiKey = configurationService.getApiKey(modelConfig.id)
            if (modelConfig.modelType != ModelType.LOCAL && apiKey.isNullOrBlank()) {
                error(I18n.t("prompt.aiOptimize.error.apiKeyMissing"))
            }

            val prompt = buildPrompt(request)

            val executionResult = runBlocking {
                aiModelService.callModel(modelConfig, prompt, apiKey ?: "")
            }

            if (!executionResult.success || executionResult.content.isNullOrBlank()) {
                error(executionResult.errorMessage ?: I18n.t("prompt.aiOptimize.error.emptyResponse"))
            }

            parseOptimizationResult(executionResult.content)
        }.getOrElse { throwable ->
            val context = ErrorContext(
                operation = "optimizePrompt",
                component = "PromptOptimizationService",
                additionalInfo = mapOf(
                    "category" to (request.category ?: "unknown"),
                    "language" to request.languageCode
                )
            )
            errorHandlingService.handleException(throwable, context)
            PromptOptimizationResult(
                name = request.currentName,
                description = request.currentDescription,
                content = request.currentContent ?: request.userPrompt
            )
        }
    }

    private fun buildPrompt(request: PromptOptimizationRequest): String {
        val variableSection = request.availableVariables.takeIf { it.isNotEmpty() }?.joinToString("\n") {
            "- ${it.placeholder}: ${it.description}"
        } ?: TemplateConstants.getBuiltInVariablesMap().entries.joinToString("\n") {
            "- ${it.key}: ${it.value}"
        }

        val metadata = buildString {
            appendLine(I18n.t("prompt.aiOptimize.prompt.meta.header"))
            appendLine(I18n.t("prompt.aiOptimize.prompt.meta.targetLanguage", request.languageCode))
            val categoryValue = request.category?.takeIf { it.isNotBlank() }
                ?: I18n.t("prompt.aiOptimize.prompt.meta.category.unset")
            appendLine(I18n.t("prompt.aiOptimize.prompt.meta.category", categoryValue))
            if (!request.currentName.isNullOrBlank()) {
                appendLine(I18n.t("prompt.aiOptimize.prompt.meta.currentName", request.currentName))
            }
            if (!request.currentDescription.isNullOrBlank()) {
                appendLine(I18n.t("prompt.aiOptimize.prompt.meta.currentDescription", request.currentDescription))
            }
        }

        val existingContent = request.currentContent?.takeIf { it.isNotBlank() }
        val localization = getLocalization()

        return buildString {
            localization.introLines.forEach { appendLine(it) }
            appendLine()
            appendLine(metadata)
            appendLine()
            appendLine(localization.availableHeader)
            appendLine(variableSection)
            appendLine()
            if (existingContent != null) {
                appendLine(localization.existingHeader)
                appendLine(existingContent)
                appendLine()
            }
            appendLine(localization.outputHeader)
            appendLine(localization.outputInstruction)
        }
    }

    private fun parseOptimizationResult(content: String): PromptOptimizationResult {
        val cleaned = cleanupResponse(content)
        return runCatching {
            json.decodeFromString<PromptOptimizationResult>(cleaned)
        }.getOrElse {
            PromptOptimizationResult(
                name = null,
                description = null,
                content = cleaned
            )
        }
    }

    private fun cleanupResponse(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("```") && trimmed.endsWith("```") && trimmed.length > 6) {
            val withoutFence = trimmed.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            return withoutFence.removeSuffix("```").trim()
        }

        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }

        return trimmed
    }

    private fun getLocalization(): PromptLocalization {
        return PromptLocalization(
            introLines = parseLines(I18n.t("prompt.aiOptimize.prompt.intro")),
            availableHeader = I18n.t("prompt.aiOptimize.prompt.availableHeader"),
            existingHeader = I18n.t("prompt.aiOptimize.prompt.existingHeader"),
            outputHeader = I18n.t("prompt.aiOptimize.prompt.outputHeader"),
            outputInstruction = I18n.t("prompt.aiOptimize.prompt.outputInstruction")
        )
    }

    private data class PromptLocalization(
        val introLines: List<String>,
        val availableHeader: String,
        val existingHeader: String,
        val outputHeader: String,
        val outputInstruction: String
    )

}
