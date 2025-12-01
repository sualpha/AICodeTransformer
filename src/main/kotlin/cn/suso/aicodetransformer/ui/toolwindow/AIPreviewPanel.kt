package cn.suso.aicodetransformer.ui.toolwindow

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.DiffRequestPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout

/**
 * AI Transformation Preview Panel
 */
class AIPreviewPanel(private val project: Project) : JBPanel<AIPreviewPanel>(BorderLayout()) {

    private val diffPanel: DiffRequestPanel = DiffManager.getInstance().createRequestPanel(project, {}, null)

    init {
        add(diffPanel.component, BorderLayout.CENTER)
    }

    fun showDiff(originalText: String, newText: String, fileType: FileType?) {
        ApplicationManager.getApplication().invokeLater {
            val content1 = DiffContentFactory.getInstance().create(project, originalText, fileType)
            val content2 = DiffContentFactory.getInstance().create(project, newText, fileType)
            
            val request = SimpleDiffRequest(
                "AI Transformation Preview",
                content1,
                content2,
                "Original",
                "Transformed"
            )
            
            diffPanel.setRequest(request)
        }
    }
    
    // Deprecated: kept for compatibility if needed, but should use showDiff
    fun setText(text: String, fileType: FileType?) {
        showDiff("", text, fileType)
    }

    fun dispose() {
        // DiffPanel handles disposal
    }
}
