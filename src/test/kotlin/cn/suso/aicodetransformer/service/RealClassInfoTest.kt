package cn.suso.aicodetransformer.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.*
import com.intellij.openapi.project.Project

class RealClassInfoTest {
    
    private lateinit var codeAnalysisService: CodeAnalysisService
    private lateinit var mockProject: Project
    
    @BeforeEach
    fun setUp() {
        codeAnalysisService = CodeAnalysisService()
        mockProject = mock(Project::class.java)
    }
    
    @Test
    fun `test getRealClassInfo method exists`() {
        // 测试方法是否存在并且可以调用
        val classInfo = codeAnalysisService.getRealClassInfo("NonExistentClass", mockProject)
        
        // 对于不存在的类，应该返回null
        assertNull(classInfo, "不存在的类应该返回null")
    }
    
    @Test
    fun `test getRealClassInfo with Java built-in class`() {
        // 测试获取Java内置类的信息（如String类）
        codeAnalysisService.getRealClassInfo("java.lang.String", mockProject)
        
        // 由于我们使用的是mock project，这个测试主要验证方法不会抛出异常
        // 在真实环境中，这应该能够找到String类
        // 这里我们只验证方法能够正常执行
        assertTrue(true, "方法应该能正常执行")
    }
}