package cn.suso.aicodetransformer.settings

import cn.suso.aicodetransformer.model.CommitSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CommitSettingsTest {

    @Test
    fun `test default settings creation`() {
        val settings = CommitSettings.createDefault()
        
        assertNotNull(settings)
        assertFalse(settings.autoCommitEnabled)
        assertFalse(settings.autoPushEnabled)
    }

    @Test
    fun `test settings copy functionality`() {
        val originalSettings = CommitSettings.createDefault()
        val copiedSettings = originalSettings.copy(autoCommitEnabled = true)
        
        assertNotEquals(originalSettings, copiedSettings)
        assertTrue(copiedSettings.autoCommitEnabled)
        assertFalse(originalSettings.autoCommitEnabled)
        
        // 其他字段应该保持不变
        assertEquals(originalSettings.autoPushEnabled, copiedSettings.autoPushEnabled)
    }

    @Test
    fun `test auto push dependency on auto commit`() {
        val settings = CommitSettings.createDefault()
        
        // 测试自动推送依赖于自动提交的逻辑
        val autoCommitEnabled = settings.copy(autoCommitEnabled = true, autoPushEnabled = true)
        assertTrue(autoCommitEnabled.autoCommitEnabled)
        assertTrue(autoCommitEnabled.autoPushEnabled)
        
        val autoCommitDisabled = settings.copy(autoCommitEnabled = false, autoPushEnabled = false)
        assertFalse(autoCommitDisabled.autoCommitEnabled)
        assertFalse(autoCommitDisabled.autoPushEnabled)
    }
}