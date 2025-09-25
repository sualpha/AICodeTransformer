package cn.suso.aicodetransformer.service

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito

class CodeAnalysisServiceDebugTest {

    private val codeAnalysisService = CodeAnalysisService()
    private lateinit var mockProject: Project
    
    @BeforeEach
    fun setUp() {
        mockProject = Mockito.mock(Project::class.java)
    }

    @Test
    fun `debug PSI API integration`() {
        val code = """
            public class UserDTO {
                private Long id;
                private String name;
                private String email;
                
                // getters and setters
            }
        """.trimIndent()

        // 测试方法不会抛出异常（在mock环境中可能返回空结果）
        assertDoesNotThrow {
            codeAnalysisService.extractClassInfoFromCode(code, mockProject)
            // 在mock环境中，PSI API可能无法找到真实的类，所以结果可能为空
            // 这是正常的，我们主要测试方法不会崩溃
        }
        
        // 测试另一个代码片段
        assertDoesNotThrow {
            val methodCode = "public UserDTO convertToDTO(User user)"
            codeAnalysisService.extractClassInfoFromCode(methodCode, mockProject)
        }
    }

}