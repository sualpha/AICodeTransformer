package cn.suso.aicodetransformer.ui.settings.model

import cn.suso.aicodetransformer.i18n.I18n
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.ModelType
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

/**
 * 模型配置列表单元格渲染器
 */
class ModelConfigurationListCellRenderer : ColoredListCellRenderer<ModelConfiguration>() {
    
    override fun customizeCellRenderer(
        list: JList<out ModelConfiguration>,
        value: ModelConfiguration?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        if (value != null) {
            // 显示配置名称
            append(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            
            // 显示模型类型
            val typeLabel = when (value.modelType) {
                ModelType.OPENAI_COMPATIBLE -> tr("model.config.list.type.openai")
                ModelType.CLAUDE -> tr("model.config.list.type.claude")
                ModelType.LOCAL -> tr("model.config.list.type.local")
            }
            append(" ($typeLabel)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            
            // 如果是默认配置，添加标记
            if (value.isDefault()) {
                append(" ${tr("model.config.list.defaultTag")}", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }
            
            // 如果是内置模型,添加标记
            if (value.isBuiltIn) {
                append(" ${tr("model.builtin.suffix")}", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }
            
            // 如果配置被禁用，使用灰色显示
            if (!value.enabled) {
                append(" ${tr("model.config.list.disabledTag")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }

    private fun tr(key: String, vararg params: Any): String = I18n.t(key, *params)
}