package cn.suso.aicodetransformer.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the Template Debugger tool window
 */
class TemplateDebuggerToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val debuggerPanel = TemplateDebuggerPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(debuggerPanel, "", false)
        toolWindow.contentManager.addContent(content)
        
        // Set initial title
        toolWindow.stripeTitle = cn.suso.aicodetransformer.i18n.I18n.t("toolwindow.debugger.title")
        
        // Update title on language change
        cn.suso.aicodetransformer.i18n.LanguageManager.addChangeListener {
            toolWindow.stripeTitle = cn.suso.aicodetransformer.i18n.I18n.t("toolwindow.debugger.title")
        }
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}
