package cn.suso.aicodetransformer.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CodeAnalysisServiceTest {
    
    private val codeAnalysisService = CodeAnalysisService()
    
    @Test
    fun `test valid method with parameters and return value`() {
        val code = """
            public String processUserData(String username, int age, boolean isActive) {
                if (username == null || username.isEmpty()) {
                    return "Invalid username";
                }
                return "User " + username + " (age: " + age + ") is " + (isActive ? "active" : "inactive");
            }
        """.trimIndent()
        
        val result = codeAnalysisService.analyzeSelectedCode(code)
        
        assertTrue(result.isMethod, "应该识别为方法")
        assertTrue(result.hasRequestParameters, "应该有请求参数")
        assertTrue(result.hasResponseParameters, "应该有返回参数")
        assertNull(result.errorMessage, "不应该有错误信息")
        
        assertNotNull(result.methodInfo)
        assertEquals("processUserData", result.methodInfo?.name)
        assertEquals("String", result.methodInfo?.returnType)
        assertEquals(3, result.methodInfo?.parameters?.size)
    }
    
    @Test
    fun `test method without parameters`() {
        val code = """
            public String getCurrentTime() {
                return java.time.LocalDateTime.now().toString();
            }
        """.trimIndent()
        
        val result = codeAnalysisService.analyzeSelectedCode(code)
        
        assertTrue(result.isMethod, "应该识别为方法")
        assertFalse(result.hasRequestParameters, "不应该有请求参数")
        assertTrue(result.hasResponseParameters, "应该有返回参数")
        assertNotNull(result.errorMessage, "应该有错误信息")
        assertTrue(result.errorMessage!!.contains("没有参数"), "错误信息应该提到没有参数")
    }
    
    @Test
    fun `test method without return value`() {
        val code = """
            public void updateUserStatus(String userId, boolean status) {
                System.out.println("Updating user " + userId + " status to " + status);
            }
        """.trimIndent()
        
        val result = codeAnalysisService.analyzeSelectedCode(code)
        
        assertTrue(result.isMethod, "应该识别为方法")
        assertTrue(result.hasRequestParameters, "应该有请求参数")
        assertFalse(result.hasResponseParameters, "不应该有返回参数")
        assertNotNull(result.errorMessage, "应该有错误信息")
        assertTrue(result.errorMessage!!.contains("没有返回值"), "错误信息应该提到没有返回值")
    }
    
    @Test
    fun `test method without parameters and return value`() {
        val code = """
            public void printWelcomeMessage() {
                System.out.println("Welcome to the application!");
            }
        """.trimIndent()
        
        val result = codeAnalysisService.analyzeSelectedCode(code)
        
        assertTrue(result.isMethod, "应该识别为方法")
        assertFalse(result.hasRequestParameters, "不应该有请求参数")
        assertFalse(result.hasResponseParameters, "不应该有返回参数")
        assertNotNull(result.errorMessage, "应该有错误信息")
        assertTrue(result.errorMessage!!.contains("没有参数且没有返回值"), "错误信息应该提到既没有参数也没有返回值")
    }
    
    @Test
    fun `test invalid code that is not a method`() {
        val code = """
            String username = "test";
            int age = 25;
            boolean isActive = true;
        """.trimIndent()
        
        val result = codeAnalysisService.analyzeSelectedCode(code)
        
        assertFalse(result.isMethod, "不应该识别为方法")
        assertFalse(result.hasRequestParameters, "不应该有请求参数")
        assertFalse(result.hasResponseParameters, "不应该有返回参数")
        assertNotNull(result.errorMessage, "应该有错误信息")
        assertTrue(result.errorMessage!!.contains("不是一个有效的方法"), "错误信息应该提到不是有效方法")
    }
    
    @Test
    fun `test complex method with generic types`() {
        val code = """
            public List<Map<String, Object>> processComplexData(
                    List<String> inputList, 
                    Map<String, Integer> configMap,
                    Optional<String> optionalParam
            ) {
                List<Map<String, Object>> result = new ArrayList<>();
                return result;
            }
        """.trimIndent()
        
        val result = codeAnalysisService.analyzeSelectedCode(code)
        
        assertTrue(result.isMethod, "应该识别为方法")
        assertTrue(result.hasRequestParameters, "应该有请求参数")
        assertTrue(result.hasResponseParameters, "应该有返回参数")
        assertNull(result.errorMessage, "不应该有错误信息")
        
        assertNotNull(result.methodInfo)
        assertEquals("processComplexData", result.methodInfo?.name)
        assertEquals("List<Map<String, Object>>", result.methodInfo?.returnType)
        assertEquals(3, result.methodInfo?.parameters?.size)
    }
}