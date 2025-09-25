package cn.suso.aicodetransformer.service.impl

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@Serializable
data class LocalModelResponse(
    val response: String
)

class LocalModelResponseParsingTest {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private fun parseLocalModelResponse(responseBodyText: String): String {
        val responses = mutableListOf<String>()
        
        // 按行分割响应，每行可能是一个JSON对象
        val lines = responseBodyText.trim().split("\n")
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            
            try {
                // 尝试解析每一行作为JSON对象
                val responseObj = json.decodeFromString<LocalModelResponse>(trimmedLine)
                if (responseObj.response.isNotEmpty()) {
                    responses.add(responseObj.response)
                }
            } catch (e: Exception) {
                // 如果单行解析失败，尝试解析整个响应体
                if (lines.size == 1) {
                    val responseObj = json.decodeFromString<LocalModelResponse>(responseBodyText)
                    return responseObj.response
                }
                // 忽略无法解析的行
            }
        }
        
        return responses.joinToString("")
    }
    
    @Test
    fun `测试单个JSON对象响应解析`() {
        val singleResponse = """{"model":"starcoder2:3b","created_at":"2025-01-23T02:35:29.193Z","response":"Hello World","done":true}"""
        
        val result = parseLocalModelResponse(singleResponse)
        
        assertEquals("Hello World", result)
    }
    
    @Test
    fun `测试多个JSON对象响应解析`() {
        val multiResponse = """
            {"model":"starcoder2:3b","created_at":"2025-01-23T02:35:29.193Z","response":"Hello","done":false}
            {"model":"starcoder2:3b","created_at":"2025-01-23T02:35:29.194Z","response":" World","done":true}
        """.trimIndent()
        
        val result = parseLocalModelResponse(multiResponse)
        
        assertEquals("Hello World", result)
    }
    
    @Test
    fun `测试包含空响应的多个JSON对象`() {
        val multiResponse = """
            {"model":"starcoder2:3b","created_at":"2025-01-23T02:35:29.193Z","response":"","done":false}
            {"model":"starcoder2:3b","created_at":"2025-01-23T02:35:29.194Z","response":"Hello","done":false}
            {"model":"starcoder2:3b","created_at":"2025-01-23T02:35:29.195Z","response":" World","done":true}
        """.trimIndent()
        
        val result = parseLocalModelResponse(multiResponse)
        
        assertEquals("Hello World", result)
    }
    
    @Test
    fun `测试包含无效JSON行的响应`() {
        val mixedResponse = """
            {"model":"starcoder2:3b","created_at":"2025-01-23T02:35:29.193Z","response":"Hello","done":false}
            invalid json line
            {"model":"starcoder2:3b","created_at":"2025-01-23T02:35:29.194Z","response":" World","done":true}
        """.trimIndent()
        
        val result = parseLocalModelResponse(mixedResponse)
        
        assertEquals("Hello World", result)
    }
}