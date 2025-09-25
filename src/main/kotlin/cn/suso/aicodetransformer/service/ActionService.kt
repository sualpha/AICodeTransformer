package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.PromptTemplate
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.keymap.KeymapManager
import javax.swing.KeyStroke

/**
 * 动作服务接口
 * 负责管理动态创建的Action和快捷键绑定
 */
interface ActionService {
    
    /**
     * 为模板创建Action
     * @param template 提示模板
     * @return 创建的Action
     */
    fun createActionForTemplate(template: PromptTemplate): AnAction
    
    /**
     * 注册Action到IDE
     * @param action Action实例
     * @param actionId Action ID
     * @param shortcut 快捷键
     * @return 是否注册成功
     */
    fun registerAction(action: AnAction, actionId: String, shortcut: String?): Boolean
    
    /**
     * 注销Action
     * @param actionId Action ID
     * @return 是否注销成功
     */
    fun unregisterAction(actionId: String): Boolean
    
    /**
     * 更新Action的快捷键
     * @param actionId Action ID
     * @param newShortcut 新的快捷键
     * @return 是否更新成功
     */
    fun updateActionShortcut(actionId: String, newShortcut: String?): Boolean
    
    /**
     * 获取已注册的Action列表
     * @return Action ID列表
     */
    fun getRegisteredActions(): List<String>
    
    /**
     * 检查快捷键是否已被使用
     * @param shortcut 快捷键字符串
     * @return 是否已被使用
     */
    fun isShortcutInUse(shortcut: String): Boolean
    
    /**
     * 验证快捷键格式
     * @param shortcut 快捷键字符串
     * @return 是否有效
     */
    fun validateShortcut(shortcut: String): Boolean
    
    /**
     * 解析快捷键字符串为KeyStroke
     * @param shortcut 快捷键字符串
     * @return KeyStroke对象，如果解析失败返回null
     */
    fun parseShortcut(shortcut: String): KeyStroke?
    
    /**
     * 获取快捷键的显示文本
     * @param shortcut 快捷键字符串
     * @return 显示文本
     */
    fun getShortcutDisplayText(shortcut: String): String
    
    /**
     * 刷新所有模板Action
     * 重新创建和注册所有模板的Action
     */
    fun refreshTemplateActions()
    
    /**
     * 清理所有注册的Action
     */
    fun cleanup()
    
    /**
     * 添加Action状态监听器
     * @param listener 监听器
     */
    fun addActionListener(listener: ActionListener)
    
    /**
     * 移除Action状态监听器
     * @param listener 监听器
     */
    fun removeActionListener(listener: ActionListener)
}

/**
 * Action状态监听器接口
 */
interface ActionListener {
    /**
     * Action注册时调用
     * @param actionId Action ID
     * @param template 关联的模板
     */
    fun onActionRegistered(actionId: String, template: PromptTemplate) {}
    
    /**
     * Action注销时调用
     * @param actionId Action ID
     */
    fun onActionUnregistered(actionId: String) {}
    
    /**
     * Action执行时调用
     * @param actionId Action ID
     * @param template 关联的模板
     * @param selectedText 选中的文本
     */
    fun onActionExecuted(actionId: String, template: PromptTemplate, selectedText: String) {}
    
    /**
     * Action执行失败时调用
     * @param actionId Action ID
     * @param error 错误信息
     */
    fun onActionFailed(actionId: String, error: String) {}
}

/**
 * Action执行结果
 */
data class ActionExecutionResult(
    /** 是否执行成功 */
    val success: Boolean,
    
    /** 错误信息 */
    val errorMessage: String? = null,
    
    /** 执行耗时（毫秒） */
    val executionTimeMs: Long = 0
)