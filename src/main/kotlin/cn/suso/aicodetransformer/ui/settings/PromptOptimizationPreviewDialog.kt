package cn.suso.aicodetransformer.ui.settings

import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.model.PromptOptimizationResult
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 预览 AI 优化结果，让用户对比原始内容与 AI 结果后再确认是否应用。
 */
class PromptOptimizationPreviewDialog(
    private val original: TemplateSnapshot,
    optimized: PromptOptimizationResult
) : DialogWrapper(true) {

    enum class Decision {
        APPLY, REGENERATE, CANCEL
    }

    var decision: Decision = Decision.CANCEL
        private set

    var onRegenerateRequested: (() -> Unit)? = null

    private var currentOptimizedResult: PromptOptimizationResult = optimized
    private lateinit var optimizedNameLabel: JBLabel
    private lateinit var optimizedDescriptionLabel: JBLabel
    private lateinit var optimizedContentArea: JBTextArea
    private var regenerateButton: JButton? = null

    data class TemplateSnapshot(
        val name: String,
        val description: String,
        val content: String
    )

    init {
        title = I18n.t("prompt.aiOptimize.preview.title")
        setOKButtonText(I18n.t("prompt.aiOptimize.preview.apply"))
        setCancelButtonText(I18n.t("prompt.aiOptimize.preview.cancel"))
        isResizable = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val splitter = JBSplitter(true, 0.5f)
        splitter.firstComponent = createTemplatePanel(
            original,
            I18n.t("prompt.aiOptimize.preview.original")
        )
        splitter.secondComponent = createTemplatePanel(
            TemplateSnapshot(
                name = currentOptimizedResult.name ?: "",
                description = currentOptimizedResult.description ?: "",
                content = currentOptimizedResult.content
            ),
            I18n.t("prompt.aiOptimize.preview.generated"),
            captureOptimized = true
        )

        updateOptimizedResult(currentOptimizedResult)

        val rootPanel = JPanel(BorderLayout())
        rootPanel.add(splitter, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            border = JBUI.Borders.empty(10, 0, 0, 0)
        }
        val button = JButton(I18n.t("prompt.aiOptimize.preview.regenerate")).apply {
            addActionListener { onRegenerateRequested?.invoke() }
        }
        regenerateButton = button
        buttonPanel.add(button)
        rootPanel.add(buttonPanel, BorderLayout.SOUTH)

        return rootPanel
    }

    private fun createTemplatePanel(
        snapshot: TemplateSnapshot,
        title: String,
        captureOptimized: Boolean = false
    ): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = IdeBorderFactory.createTitledBorder(title, false)

        val nameLabel = JBLabel(I18n.t("prompt.name.label") + snapshot.name)
        val descriptionLabel = JBLabel(I18n.t("prompt.description.label") + snapshot.description)

        val infoPanel = JPanel(GridLayout(0, 1, 0, 4))
        infoPanel.add(nameLabel)
        infoPanel.add(descriptionLabel)

        val contentArea = JBTextArea(snapshot.content).apply {
            lineWrap = true
            wrapStyleWord = true
            isEditable = false
        }

        val scrollPane = JBScrollPane(contentArea)
        panel.add(infoPanel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)

        if (captureOptimized) {
            optimizedNameLabel = nameLabel
            optimizedDescriptionLabel = descriptionLabel
            optimizedContentArea = contentArea
        }

        return panel
    }

    fun updateOptimizedResult(newResult: PromptOptimizationResult) {
        currentOptimizedResult = newResult
        if (::optimizedNameLabel.isInitialized) {
            optimizedNameLabel.text = I18n.t("prompt.name.label") + (newResult.name ?: "")
        }
        if (::optimizedDescriptionLabel.isInitialized) {
            optimizedDescriptionLabel.text = I18n.t("prompt.description.label") + (newResult.description ?: "")
        }
        if (::optimizedContentArea.isInitialized) {
            optimizedContentArea.text = newResult.content
            optimizedContentArea.caretPosition = 0
        }
    }

    fun setRegenerateEnabled(enabled: Boolean) {
        regenerateButton?.isEnabled = enabled
    }

    fun getCurrentOptimizedResult(): PromptOptimizationResult = currentOptimizedResult

    override fun doOKAction() {
        decision = Decision.APPLY
        super.doOKAction()
    }

    override fun doCancelAction() {
        decision = Decision.CANCEL
        super.doCancelAction()
    }
}
