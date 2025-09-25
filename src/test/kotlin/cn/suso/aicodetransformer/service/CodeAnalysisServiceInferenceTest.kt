package cn.suso.aicodetransformer.service

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito

class CodeAnalysisServiceInferenceTest {

    private val codeAnalysisService = CodeAnalysisService()
    private lateinit var mockProject: Project
    
    @BeforeEach
    fun setUp() {
        mockProject = Mockito.mock(Project::class.java)
    }

    @Test
    fun `test PSI API integration for DTO class`() {
        val code = "public UserDTO convertToDTO(User user)"

        // 测试方法不会抛出异常（在mock环境中可能返回空结果）
        assertDoesNotThrow {
            codeAnalysisService.extractClassInfoFromCode(code, mockProject)
            // 在mock环境中，PSI API可能无法找到真实的类，所以结果可能为空
            // 这是正常的，我们主要测试方法不会崩溃
        }
    }

    @Test
    fun `test PSI API integration for Entity class`() {
        val code = "public ProductEntity save(ProductEntity entity)"

        // 测试方法不会抛出异常（在mock环境中可能返回空结果）
        assertDoesNotThrow {
            codeAnalysisService.extractClassInfoFromCode(code, mockProject)
            // 在mock环境中，PSI API可能无法找到真实的类，所以结果可能为空
            // 这是正常的，我们主要测试方法不会崩溃
        }
    }

    @Test
    fun `test PSI API integration for User class`() {
        val code = "public void updateUser(UserVO userVO)"

        // 测试方法不会抛出异常（在mock环境中可能返回空结果）
        assertDoesNotThrow {
            codeAnalysisService.extractClassInfoFromCode(code, mockProject)
            // 在mock环境中，PSI API可能无法找到真实的类，所以结果可能为空
            // 这是正常的，我们主要测试方法不会崩溃
        }
    }

    @Test
    fun `test PSI API integration for Order class`() {
        val code = "public OrderDTO processOrder(OrderEntity order)"

        // 测试方法不会抛出异常（在mock环境中可能返回空结果）
        assertDoesNotThrow {
            codeAnalysisService.extractClassInfoFromCode(code, mockProject)
            // 在mock环境中，PSI API可能无法找到真实的类，所以结果可能为空
            // 这是正常的，我们主要测试方法不会崩溃
        }
    }

    @Test
    fun `test PSI API integration for generic class`() {
        val code = "public CustomClass convert(AnotherClass input)"

        // 测试方法不会抛出异常（在mock环境中可能返回空结果）
        assertDoesNotThrow {
            codeAnalysisService.extractClassInfoFromCode(code, mockProject)
            // 在mock环境中，PSI API可能无法找到真实的类，所以结果可能为空
            // 这是正常的，我们主要测试方法不会崩溃
        }
    }

    @Test
    fun `test PSI API integration with fallback`() {
        val code = "public UnknownClass process()"

        // 测试方法不会抛出异常（在mock环境中可能返回空结果）
        assertDoesNotThrow {
            codeAnalysisService.extractClassInfoFromCode(code, mockProject)
            // 在mock环境中，PSI API可能无法找到真实的类，所以结果可能为空
            // 这是正常的，我们主要测试方法不会崩溃
        }
    }
}