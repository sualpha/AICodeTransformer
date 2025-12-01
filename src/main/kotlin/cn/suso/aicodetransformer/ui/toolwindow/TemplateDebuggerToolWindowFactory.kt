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
        val contentFactory = ContentFactory.getInstance()
        
        // Tab 1: Template Debugger
        val debuggerPanel = TemplateDebuggerPanel(project)
        val debuggerContent = contentFactory.createContent(debuggerPanel, cn.suso.aicodetransformer.i18n.I18n.t("toolwindow.debugger.title"), false)
        toolWindow.contentManager.addContent(debuggerContent)
        
        // Tab 2: AI Transformation Preview
        val previewPanel = AIPreviewPanel(project)
        val previewContent = contentFactory.createContent(previewPanel, cn.suso.aicodetransformer.i18n.I18n.t("toolwindow.preview.title"), false)
        toolWindow.contentManager.addContent(previewContent)
        
        // Set initial title
        toolWindow.stripeTitle = "AI Code Transformer"
        
        // Update title on language change
        cn.suso.aicodetransformer.i18n.LanguageManager.addChangeListener {
            // toolWindow.stripeTitle = "AI Code Transformer" // Title is fixed
            debuggerContent.displayName = cn.suso.aicodetransformer.i18n.I18n.t("toolwindow.debugger.title")
            previewContent.displayName = cn.suso.aicodetransformer.i18n.I18n.t("toolwindow.preview.title")
        }
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}
