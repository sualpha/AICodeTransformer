package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * 快捷键恢复服务实现
 */
class ShortcutRecoveryServiceImpl : ShortcutRecoveryService {
    
    companion object {
        private val logger = Logger.getInstance(ShortcutRecoveryServiceImpl::class.java)
        private const val BACKUP_KEY = "AICodeTransformer.ShortcutBackup"
        private const val BACKUP_VERSION_KEY = "AICodeTransformer.ShortcutBackup.Version"
        private const val CURRENT_VERSION = "1.0"
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    private val propertiesComponent = PropertiesComponent.getInstance()
    private val promptTemplateService: PromptTemplateService = service()
    private val actionService: ActionService = service()
    
    override fun backupShortcuts() {
        try {
            val templates = promptTemplateService.getTemplates()
            val shortcutBackup = ShortcutBackup(
                version = CURRENT_VERSION,
                timestamp = System.currentTimeMillis(),
                shortcuts = templates.filter { !it.shortcutKey.isNullOrBlank() }
                    .map { ShortcutInfo(it.id, it.name, it.shortcutKey!!) }
            )
            
            val backupJson = json.encodeToString(shortcutBackup)
            propertiesComponent.setValue(BACKUP_KEY, backupJson)
            propertiesComponent.setValue(BACKUP_VERSION_KEY, CURRENT_VERSION)
            
            logger.info("快捷键备份完成，共备份 ${shortcutBackup.shortcuts.size} 个快捷键")
            
        } catch (e: Exception) {
            logger.error("备份快捷键失败", e)
        }
    }
    
    override fun restoreShortcuts(): Boolean {
        return try {
            if (!hasBackup()) {
                logger.info("没有找到快捷键备份")
                return false
            }
            
            val backupJson = propertiesComponent.getValue(BACKUP_KEY) ?: return false
            val backup = json.decodeFromString<ShortcutBackup>(backupJson)
            
            var restoredCount = 0
            backup.shortcuts.forEach { shortcutInfo ->
                val template = promptTemplateService.getTemplate(shortcutInfo.templateId)
                if (template != null) {
                    // 检查快捷键是否冲突
                    if (!actionService.isShortcutInUse(shortcutInfo.shortcut)) {
                        val updatedTemplate = template.copy(shortcutKey = shortcutInfo.shortcut)
                        promptTemplateService.saveTemplate(updatedTemplate)
                        restoredCount++
                        logger.info("恢复快捷键: ${shortcutInfo.templateName} -> ${shortcutInfo.shortcut}")
                    } else {
                        logger.warn("快捷键冲突，跳过恢复: ${shortcutInfo.templateName} -> ${shortcutInfo.shortcut}")
                    }
                } else {
                    logger.warn("模板不存在，跳过恢复: ${shortcutInfo.templateName}")
                }
            }
            
            logger.info("快捷键恢复完成，共恢复 $restoredCount 个快捷键")
            true
            
        } catch (e: Exception) {
            logger.error("恢复快捷键失败", e)
            false
        }
    }
    
    override fun hasBackup(): Boolean {
        val backupJson = propertiesComponent.getValue(BACKUP_KEY)
        val version = propertiesComponent.getValue(BACKUP_VERSION_KEY)
        return !backupJson.isNullOrBlank() && version == CURRENT_VERSION
    }
    
    override fun clearBackup() {
        propertiesComponent.unsetValue(BACKUP_KEY)
        propertiesComponent.unsetValue(BACKUP_VERSION_KEY)
        logger.info("快捷键备份已清除")
    }
    
    override fun getBackupShortcuts(): List<PromptTemplate> {
        return try {
            if (!hasBackup()) return emptyList()
            
            val backupJson = propertiesComponent.getValue(BACKUP_KEY) ?: return emptyList()
            val backup = json.decodeFromString<ShortcutBackup>(backupJson)
            
            backup.shortcuts.mapNotNull { shortcutInfo ->
                promptTemplateService.getTemplate(shortcutInfo.templateId)?.copy(
                    shortcutKey = shortcutInfo.shortcut
                )
            }
            
        } catch (e: Exception) {
            logger.error("获取备份快捷键失败", e)
            emptyList()
        }
    }
    
    override fun autoRecoverShortcuts(): Int {
        return try {
            if (!hasBackup()) {
                logger.info("没有快捷键备份，跳过自动恢复")
                return 0
            }
            
            val currentTemplates = promptTemplateService.getTemplates()
            val templatesWithoutShortcuts = currentTemplates.filter { it.shortcutKey.isNullOrBlank() }
            
            if (templatesWithoutShortcuts.isEmpty()) {
                logger.info("所有模板都已有快捷键，跳过自动恢复")
                return 0
            }
            
            val backupJson = propertiesComponent.getValue(BACKUP_KEY) ?: return 0
            val backup = json.decodeFromString<ShortcutBackup>(backupJson)
            
            var recoveredCount = 0
            backup.shortcuts.forEach { shortcutInfo ->
                val template = templatesWithoutShortcuts.find { it.id == shortcutInfo.templateId }
                if (template != null && !actionService.isShortcutInUse(shortcutInfo.shortcut)) {
                    val updatedTemplate = template.copy(shortcutKey = shortcutInfo.shortcut)
                    promptTemplateService.saveTemplate(updatedTemplate)
                    recoveredCount++
                    logger.info("自动恢复快捷键: ${template.name} -> ${shortcutInfo.shortcut}")
                }
            }
            
            logger.info("自动恢复完成，共恢复 $recoveredCount 个快捷键")
            recoveredCount
            
        } catch (e: Exception) {
            logger.error("自动恢复快捷键失败", e)
            0
        }
    }
    
    override fun validateShortcutIntegrity(): ShortcutValidationResult {
        return try {
            val templates = promptTemplateService.getTemplates()
            val missingShortcuts = mutableListOf<String>()
            val conflictingShortcuts = mutableListOf<String>()
            
            templates.forEach { template ->
                if (template.shortcutKey.isNullOrBlank()) {
                    missingShortcuts.add(template.name)
                } else if (actionService.isShortcutInUse(template.shortcutKey)) {
                    conflictingShortcuts.add("${template.name}: ${template.shortcutKey}")
                }
            }
            
            val isValid = missingShortcuts.isEmpty() && conflictingShortcuts.isEmpty()
            val message = when {
                isValid -> "快捷键配置完整且无冲突"
                missingShortcuts.isNotEmpty() && conflictingShortcuts.isNotEmpty() -> 
                    "发现 ${missingShortcuts.size} 个缺失快捷键和 ${conflictingShortcuts.size} 个冲突快捷键"
                missingShortcuts.isNotEmpty() -> "发现 ${missingShortcuts.size} 个缺失快捷键"
                else -> "发现 ${conflictingShortcuts.size} 个冲突快捷键"
            }
            
            ShortcutValidationResult(
                isValid = isValid,
                missingShortcuts = missingShortcuts,
                conflictingShortcuts = conflictingShortcuts,
                message = message
            )
            
        } catch (e: Exception) {
            logger.error("验证快捷键完整性失败", e)
            ShortcutValidationResult(
                isValid = false,
                missingShortcuts = emptyList(),
                conflictingShortcuts = emptyList(),
                message = "验证失败: ${e.message}"
            )
        }
    }
}

/**
 * 快捷键备份数据
 */
@Serializable
data class ShortcutBackup(
    val version: String,
    val timestamp: Long,
    val shortcuts: List<ShortcutInfo>
)

/**
 * 快捷键信息
 */
@Serializable
data class ShortcutInfo(
    val templateId: String,
    val templateName: String,
    val shortcut: String
)