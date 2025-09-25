package cn.suso.aicodetransformer.service.impl

import cn.suso.aicodetransformer.model.PromptTemplate
import cn.suso.aicodetransformer.service.ActionListener
import cn.suso.aicodetransformer.service.ActionService
import cn.suso.aicodetransformer.service.ExecutionService
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.service.TemplateChangeListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.KeyStroke

/**
 * 动作服务实现类
 */
class ActionServiceImpl : ActionService, TemplateChangeListener {
    
    companion object {
        private val logger = Logger.getInstance(ActionServiceImpl::class.java)
        private const val ACTION_ID_PREFIX = "AICodeTransformer.Template."
        
        fun getInstance(): ActionService = service<ActionService>()
    }
    
    private val promptTemplateService: PromptTemplateService = service()
    private val executionService: ExecutionService = service()
    private val actionManager = ActionManager.getInstance()
    private val keymapManager = KeymapManager.getInstance()
    
    private val registeredActions = mutableMapOf<String, AnAction>()
    private val listeners = CopyOnWriteArrayList<ActionListener>()
    
    init {
        // 注册为模板变更监听器
        promptTemplateService.addTemplateChangeListener(this)
    }
    
    override fun createActionForTemplate(template: PromptTemplate): AnAction {
        return TemplateAction(template)
    }
    
    override fun registerAction(action: AnAction, actionId: String, shortcut: String?): Boolean {
        return try {
            // 注册Action到ActionManager
            actionManager.registerAction(actionId, action)
            registeredActions[actionId] = action
            
            // 绑定快捷键
            if (!shortcut.isNullOrBlank()) {
                bindShortcut(actionId, shortcut)
            }
            
            logger.info("Action注册成功: $actionId")
            true
        } catch (e: Exception) {
            logger.error("Action注册失败: $actionId", e)
            false
        }
    }
    
    override fun unregisterAction(actionId: String): Boolean {
        return try {
            // 移除快捷键绑定
            unbindShortcut(actionId)
            
            // 注销Action
            actionManager.unregisterAction(actionId)
            registeredActions.remove(actionId)
            
            notifyListeners { it.onActionUnregistered(actionId) }
            logger.info("Action注销成功: $actionId")
            true
        } catch (e: Exception) {
            logger.error("Action注销失败: $actionId", e)
            false
        }
    }
    
    override fun updateActionShortcut(actionId: String, newShortcut: String?): Boolean {
        return try {
            // 移除旧的快捷键绑定
            unbindShortcut(actionId)
            
            // 绑定新的快捷键
            if (!newShortcut.isNullOrBlank()) {
                bindShortcut(actionId, newShortcut)
            }
            
            logger.info("快捷键更新成功: $actionId -> $newShortcut")
            true
        } catch (e: Exception) {
            logger.error("快捷键更新失败: $actionId", e)
            false
        }
    }
    
    override fun getRegisteredActions(): List<String> {
        return registeredActions.keys.toList()
    }
    
    override fun isShortcutInUse(shortcut: String): Boolean {
        val keyStroke = parseShortcut(shortcut) ?: return false
        val keymap = keymapManager.activeKeymap
        val actionIds = keymap.getActionIds(keyStroke)
        
        // 过滤掉我们自己注册的模板Action，只检查IDE内置和其他插件的快捷键
        val nonTemplateActionIds = actionIds.filter { !it.startsWith(ACTION_ID_PREFIX) }
        return nonTemplateActionIds.isNotEmpty()
    }
    
    override fun validateShortcut(shortcut: String): Boolean {
        // 空白字符串是有效的（表示无快捷键）
        if (shortcut.isBlank()) return true
        return parseShortcut(shortcut) != null
    }
    
    override fun parseShortcut(shortcut: String): KeyStroke? {
        // 空白字符串直接返回null
        if (shortcut.isBlank()) return null
        
        return try {
            // 支持多种快捷键格式
            when {
                shortcut.contains("+") -> {
                    // 格式: Ctrl+Alt+T, Ctrl+Shift+F1等
                    parseComplexShortcut(shortcut)
                }
                shortcut.length == 1 -> {
                    // 单个字符
                    KeyStroke.getKeyStroke(shortcut.uppercase()[0])
                }
                else -> {
                    // 尝试直接解析
                    KeyStroke.getKeyStroke(shortcut)
                }
            }
        } catch (e: Exception) {
            logger.warn("快捷键解析失败: $shortcut", e)
            null
        }
    }
    
    override fun getShortcutDisplayText(shortcut: String): String {
        val keyStroke = parseShortcut(shortcut) ?: return shortcut
        return KeyEvent.getKeyText(keyStroke.keyCode) + 
               if (keyStroke.modifiers != 0) " (${getModifiersText(keyStroke.modifiers)})" else ""
    }
    
    override fun refreshTemplateActions() {
        // 清理现有的模板Action
        val templateActionIds = registeredActions.keys.filter { it.startsWith(ACTION_ID_PREFIX) }
        templateActionIds.forEach { unregisterAction(it) }
        
        // 重新创建和注册所有模板的Action
        val templates = promptTemplateService.getTemplates()
        templates.forEach { template ->
            if (template.enabled) {
                val action = createActionForTemplate(template)
                val actionId = "$ACTION_ID_PREFIX${template.id}"
                registerAction(action, actionId, template.shortcutKey)
                
                notifyListeners { it.onActionRegistered(actionId, template) }
            }
        }
    }
    
    override fun cleanup() {
        val actionIds = registeredActions.keys.toList()
        actionIds.forEach { unregisterAction(it) }
        listeners.clear()
        promptTemplateService.removeTemplateChangeListener(this)
    }
    
    override fun addActionListener(listener: ActionListener) {
        listeners.add(listener)
    }
    
    override fun removeActionListener(listener: ActionListener) {
        listeners.remove(listener)
    }
    
    // TemplateChangeListener 实现
    override fun onTemplateAdded(template: PromptTemplate) {
        if (template.enabled) {
            val action = createActionForTemplate(template)
            val actionId = "$ACTION_ID_PREFIX${template.id}"
            registerAction(action, actionId, template.shortcutKey)
            notifyListeners { it.onActionRegistered(actionId, template) }
        }
    }
    
    override fun onTemplateUpdated(oldTemplate: PromptTemplate, newTemplate: PromptTemplate) {
        val actionId = "$ACTION_ID_PREFIX${newTemplate.id}"
        
        if (newTemplate.enabled) {
            // 如果模板启用，重新创建Action以确保使用最新的模板内容
            if (registeredActions.containsKey(actionId)) {
                // 先移除旧的Action
                unregisterAction(actionId)
            }
            // 创建新Action
            val action = createActionForTemplate(newTemplate)
            registerAction(action, actionId, newTemplate.shortcutKey)
            notifyListeners { it.onActionRegistered(actionId, newTemplate) }
        } else {
            // 如果模板禁用，移除Action
            if (registeredActions.containsKey(actionId)) {
                unregisterAction(actionId)
            }
        }
    }
    
    override fun onTemplateDeleted(template: PromptTemplate) {
        val actionId = "$ACTION_ID_PREFIX${template.id}"
        if (registeredActions.containsKey(actionId)) {
            unregisterAction(actionId)
        }
    }
    
    override fun onTemplateShortcutChanged(template: PromptTemplate, oldShortcut: String?, newShortcut: String?) {
        val actionId = "$ACTION_ID_PREFIX${template.id}"
        if (registeredActions.containsKey(actionId)) {
            // 立即更新快捷键绑定
            updateActionShortcut(actionId, newShortcut)
            logger.info("模板快捷键已更新: ${template.name} -> $newShortcut")
        }
    }
    
    /**
     * 绑定快捷键
     */
    private fun bindShortcut(actionId: String, shortcut: String) {
        val keyStroke = parseShortcut(shortcut) ?: return
        val keymap = keymapManager.activeKeymap
        keymap.addShortcut(actionId, KeyboardShortcut(keyStroke, null))
    }
    
    /**
     * 解绑快捷键
     */
    private fun unbindShortcut(actionId: String) {
        val keymap = keymapManager.activeKeymap
        val shortcuts = keymap.getShortcuts(actionId)
        shortcuts.forEach { shortcut ->
            keymap.removeShortcut(actionId, shortcut)
        }
    }
    
    /**
     * 解析复杂快捷键格式
     */
    private fun parseComplexShortcut(shortcut: String): KeyStroke? {
        val parts = shortcut.split("+").map { it.trim().uppercase() }
        if (parts.isEmpty()) return null
        
        var modifiers = 0
        var keyCode = 0
        
        for (i in 0 until parts.size - 1) {
            when (parts[i]) {
                "CTRL", "CONTROL" -> modifiers = modifiers or InputEvent.CTRL_DOWN_MASK
                "ALT" -> modifiers = modifiers or InputEvent.ALT_DOWN_MASK
                "SHIFT" -> modifiers = modifiers or InputEvent.SHIFT_DOWN_MASK
                "META", "CMD", "COMMAND" -> modifiers = modifiers or InputEvent.META_DOWN_MASK
            }
        }
        
        // 最后一个部分是按键
        val keyPart = parts.last()
        keyCode = when {
            keyPart.length == 1 -> keyPart[0].code
            keyPart.startsWith("F") && keyPart.length > 1 -> {
                // 功能键 F1-F12
                val fNumber = keyPart.substring(1).toIntOrNull()
                if (fNumber != null && fNumber in 1..12) {
                    KeyEvent.VK_F1 + fNumber - 1
                } else 0
            }
            else -> {
                // 其他特殊键
                getSpecialKeyCode(keyPart)
            }
        }
        
        return if (keyCode != 0) KeyStroke.getKeyStroke(keyCode, modifiers) else null
    }
    
    /**
     * 获取特殊按键的键码
     */
    private fun getSpecialKeyCode(keyName: String): Int {
        return when (keyName.uppercase()) {
            "ENTER" -> KeyEvent.VK_ENTER
            "SPACE" -> KeyEvent.VK_SPACE
            "TAB" -> KeyEvent.VK_TAB
            "ESCAPE", "ESC" -> KeyEvent.VK_ESCAPE
            "BACKSPACE" -> KeyEvent.VK_BACK_SPACE
            "DELETE", "DEL" -> KeyEvent.VK_DELETE
            "INSERT", "INS" -> KeyEvent.VK_INSERT
            "HOME" -> KeyEvent.VK_HOME
            "END" -> KeyEvent.VK_END
            "PAGE_UP", "PGUP" -> KeyEvent.VK_PAGE_UP
            "PAGE_DOWN", "PGDN" -> KeyEvent.VK_PAGE_DOWN
            "UP" -> KeyEvent.VK_UP
            "DOWN" -> KeyEvent.VK_DOWN
            "LEFT" -> KeyEvent.VK_LEFT
            "RIGHT" -> KeyEvent.VK_RIGHT
            else -> 0
        }
    }
    
    /**
     * 获取修饰键文本
     */
    private fun getModifiersText(modifiers: Int): String {
        val parts = mutableListOf<String>()
        if (modifiers and InputEvent.CTRL_DOWN_MASK != 0) parts.add("Ctrl")
        if (modifiers and InputEvent.ALT_DOWN_MASK != 0) parts.add("Alt")
        if (modifiers and InputEvent.SHIFT_DOWN_MASK != 0) parts.add("Shift")
        if (modifiers and InputEvent.META_DOWN_MASK != 0) parts.add("Meta")
        return parts.joinToString("+")
    }
    
    /**
     * 通知监听器
     */
    private fun notifyListeners(action: (ActionListener) -> Unit) {
        listeners.forEach { listener ->
            try {
                action(listener)
            } catch (e: Exception) {
                logger.error("通知Action监听器失败", e)
            }
        }
    }
    
    /**
     * 模板Action实现
     */
    private inner class TemplateAction(private val template: PromptTemplate) : AnAction(
        template.name,
        template.description,
        null
    ) {
        
        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            
            try {
                val selectedText = getSelectedText(editor)
                if (selectedText.isNullOrBlank()) {
                    logger.warn("没有选中的文本")
                    return
                }
                
                notifyListeners { it.onActionExecuted("$ACTION_ID_PREFIX${template.id}", template, selectedText) }
                
                // 异步执行模板处理，避免阻塞UI线程
                executionService.executeTemplateAsync(template, selectedText, project, editor) { result ->
                    // 执行结果已在ExecutionService内部处理
                }
                
            } catch (ex: Exception) {
                logger.error("Action执行失败", ex)
                notifyListeners { it.onActionFailed("$ACTION_ID_PREFIX${template.id}", ex.message ?: "未知错误") }
            }
        }
        
        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.BGT
        }
        
        override fun update(e: AnActionEvent) {
            val editor = e.getData(CommonDataKeys.EDITOR)
            val hasSelection = editor?.selectionModel?.hasSelection() == true
            e.presentation.isEnabled = hasSelection && template.enabled
        }
        
        private fun getSelectedText(editor: Editor): String? {
            val selectionModel = editor.selectionModel
            return if (selectionModel.hasSelection()) {
                selectionModel.selectedText
            } else {
                null
            }
        }
    }
}