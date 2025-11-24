package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

/**
 * 用户请求 AI 帮助优化模板时使用的请求载体。
 */
@Serializable
data class PromptOptimizationRequest(
    val userPrompt: String,
    val category: String? = null,
    val currentName: String? = null,
    val currentDescription: String? = null,
    val currentContent: String? = null,
    val languageCode: String,
    val availableVariables: List<PromptVariableInfo>
)

/**
 * 描述一个内置变量及其含义。
 */
@Serializable
data class PromptVariableInfo(
    val placeholder: String,
    val description: String
)

/**
 * AI 返回的模板优化结果。
 */
@Serializable
data class PromptOptimizationResult(
    val name: String?,
    val description: String?,
    val content: String
)
