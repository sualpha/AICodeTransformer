package cn.suso.aicodetransformer.ui.settings.model

import cn.suso.aicodetransformer.model.ModelConfiguration
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
            append(" (${value.modelType.displayName})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            
            // 如果是默认配置，添加标记
            if (value.isDefault()) {
                append(" [默认]", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }
            
            // 如果配置被禁用，使用灰色显示
            if (!value.enabled) {
                append(" [已禁用]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
}