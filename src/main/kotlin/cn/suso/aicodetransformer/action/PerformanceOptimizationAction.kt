package cn.suso.aicodetransformer.action

import cn.suso.aicodetransformer.ui.PerformanceOptimizationPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/**
 * 性能优化Action
 * 在IDE菜单中提供性能优化入口
 */
class PerformanceOptimizationAction : AnAction("性能优化", "打开AI代码转换器性能优化面板", null) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 打开性能优化面板
        val panel = PerformanceOptimizationPanel(project)
        panel.show()
    }
    
    override fun update(e: AnActionEvent) {
        // 只有在项目打开时才启用
        e.presentation.isEnabled = e.project != null
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        // 使用EDT线程进行UI更新
        return ActionUpdateThread.EDT
    }
}