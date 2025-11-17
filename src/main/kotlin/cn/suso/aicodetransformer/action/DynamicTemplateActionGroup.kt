package cn.suso.aicodetransformer.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import cn.suso.aicodetransformer.service.PromptTemplateService
import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.model.TemplateCategory
import cn.suso.aicodetransformer.service.ActionService
import cn.suso.aicodetransformer.service.impl.PromptTemplateServiceImpl

/**
 * 动态模板动作组
 * 用于在右键菜单中显示所有可用的prompt模板
 */
class DynamicTemplateActionGroup : ActionGroup() {
    
    private val logger = Logger.getInstance(DynamicTemplateActionGroup::class.java)
    private val promptTemplateService: PromptTemplateService = PromptTemplateServiceImpl.getInstance()
    private val actionService: ActionService = service()
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        try {
            // 获取所有启用的模板
            val enabledTemplates = promptTemplateService.getEnabledTemplates()
            
            if (enabledTemplates.isEmpty()) {
                return arrayOf(createNoTemplatesAction())
            }
            
            // 为每个模板创建动作
            val actions = mutableListOf<AnAction>()
            
            // 按分类分组
            val templatesByCategory = enabledTemplates.groupBy { template ->
                TemplateCategory.fromDisplayName(template.category)?.displayName
                    ?: template.category.ifEmpty { I18n.t("template.category.custom.name") }
            }
            
            templatesByCategory.forEach { (category, templates) ->
                if (templatesByCategory.size > 1) {
                    // 如果有多个分类，添加分类标题
                    actions.add(Separator.create(category))
                }
                
                templates.forEach { template ->
                    val action = actionService.createActionForTemplate(template)
                    actions.add(action)
                }
                
                if (templatesByCategory.size > 1) {
                    actions.add(Separator.create())
                }
            }
            
            // 移除最后一个分隔符
            if (actions.isNotEmpty() && actions.last() is Separator) {
                actions.removeAt(actions.size - 1)
            }
            
            return actions.toTypedArray()
            
        } catch (e: Exception) {
            logger.error("创建动态模板动作失败", e)
            return arrayOf(createErrorAction(e.message ?: "未知错误"))
        }
    }
    
    override fun update(e: AnActionEvent) {
        super.update(e)
        
        // 只在编辑器中且有选中文本时显示
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        
        e.presentation.isVisible = editor != null
        e.presentation.isEnabled = hasSelection
        
        // 更新菜单文本
        val templateCount = promptTemplateService.getEnabledTemplates().size
        e.presentation.text = if (templateCount > 0) {
            I18n.t("template.dynamic.group.title.count", templateCount)
        } else {
            I18n.t("template.dynamic.group.title.empty")
        }
    }
    
    /**
     * 创建"无模板"提示动作
     */
    private fun createNoTemplatesAction(): AnAction {
        return object : AnAction(I18n.t("template.dynamic.group.empty"), I18n.t("template.dynamic.group.empty.desc"), null) {
            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
            
            override fun actionPerformed(e: AnActionEvent) {
                // 打开设置页面
                val project = e.project
                if (project != null) {
                    com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, I18n.t("settings.title"))
                }
            }
            
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = true
            }
        }
    }
    
    /**
     * 创建错误提示动作
     */
    private fun createErrorAction(errorMessage: String): AnAction {
        return object : AnAction(I18n.t("template.dynamic.group.error"), errorMessage, null) {
            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
            
            override fun actionPerformed(e: AnActionEvent) {
                // 不执行任何操作
            }
            
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = false
            }
        }
    }
}