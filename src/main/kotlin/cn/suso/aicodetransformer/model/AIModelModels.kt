package cn.suso.aicodetransformer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// OpenAI API数据类
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
    val stream: Boolean = false
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String? = null
)

@Serializable
data class OpenAIResponse(
    val choices: List<OpenAIChoice>
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

// Claude API数据类
@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<ClaudeMessage>
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
data class ClaudeResponse(
    val content: List<ClaudeContent>
)

@Serializable
data class ClaudeContent(
    val text: String
)

// 本地模型API数据类
@Serializable
data class LocalModelRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: LocalModelOptions
)

@Serializable
data class LocalModelOptions(
    val temperature: Double,
    @SerialName("num_predict") val numPredict: Int
)

@Serializable
data class LocalModelResponse(
    val response: String
)