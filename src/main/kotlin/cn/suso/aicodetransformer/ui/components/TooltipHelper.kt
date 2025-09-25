package cn.suso.aicodetransformer.ui.components

import com.intellij.ui.components.JBLabel
import javax.swing.JComponent
import javax.swing.JToolTip
import javax.swing.SwingConstants

/**
 * 工具提示帮助类 - 为UI组件添加提示信息
 */
object TooltipHelper {
    
    /**
     * 为组件设置工具提示
     */
    fun setTooltip(component: JComponent, text: String) {
        component.toolTipText = text
    }
    
    /**
     * 为组件设置多行工具提示
     */
    fun setMultilineTooltip(component: JComponent, lines: List<String>) {
        val html = "<html>" + lines.joinToString("<br>") + "</html>"
        component.toolTipText = html
    }
    
    /**
     * 创建带有提示图标的标签
     */
    fun createLabelWithTooltip(text: String, tooltip: String): JBLabel {
        val label = JBLabel(text)
        label.toolTipText = tooltip
        return label
    }
    
    /**
     * 创建帮助图标标签
     */
    fun createHelpIcon(tooltip: String): JBLabel {
        val helpLabel = JBLabel("?")
        helpLabel.toolTipText = tooltip
        helpLabel.horizontalAlignment = SwingConstants.CENTER
        helpLabel.isOpaque = true
        return helpLabel
    }
    
    /**
     * 常用提示文本
     */
    object CommonTooltips {
        const val API_KEY = "用于访问AI服务的认证密钥，请确保密钥有效且有足够的配额"
        const val ENDPOINT = "AI服务的API端点地址，例如：https://api.openai.com/v1"
        const val MODEL_ID = "具体的模型标识符，例如：gpt-3.5-turbo、gpt-4等"
        const val TEMPERATURE = "控制输出的随机性，范围0.0-1.0，值越高输出越随机"
        const val MAX_TOKENS = "限制AI响应的最大令牌数，影响输出长度和API费用"
        const val TIMEOUT = "API调用的超时时间（秒），建议设置为30-120秒"
        
        const val TEMPLATE_NAME = "模板的显示名称，用于在列表中识别"
        const val TEMPLATE_DESCRIPTION = "模板的详细描述，说明其用途和功能"
        const val TEMPLATE_CONTENT = "模板的具体内容，支持{{selectedCode}}等变量占位符"
        const val TEMPLATE_SHORTCUT = "快捷键格式：Ctrl+Alt+字母 或 Ctrl+Shift+字母"
        
        const val SEARCH_FILTER = "输入关键词过滤列表项，支持模糊匹配"
        const val REFRESH_DATA = "刷新数据，重新加载最新的配置信息"
        const val IMPORT_CONFIG = "从文件导入配置数据，支持JSON格式"
        const val EXPORT_CONFIG = "将当前配置导出到文件，便于备份和分享"
    }
    
    /**
     * 模型配置相关提示
     */
    object ModelConfigTooltips {
        const val ADD_MODEL = "添加新的AI模型配置"
        const val EDIT_MODEL = "编辑选中的模型配置"
        const val DELETE_MODEL = "删除选中的模型配置（不可恢复）"
        const val TEST_CONNECTION = "测试与AI服务的连接是否正常"
        const val SET_DEFAULT = "将此模型设置为默认模型"
        
        fun getModelTypeTooltip(type: String): String {
            return when (type) {
                "OpenAI" -> "OpenAI官方API服务，支持GPT-3.5、GPT-4等模型"
                "Azure" -> "Microsoft Azure OpenAI服务"
                "Claude" -> "Anthropic Claude AI模型"
                "Custom" -> "自定义API端点，兼容OpenAI API格式"
                else -> "选择AI服务提供商类型"
            }
        }
    }
    
    /**
     * 模板管理相关提示
     */
    object TemplateTooltips {
        const val ADD_TEMPLATE = "创建新的Prompt模板"
        const val EDIT_TEMPLATE = "编辑选中的模板内容"
        const val DELETE_TEMPLATE = "删除选中的模板（不可恢复）"
        const val DUPLICATE_TEMPLATE = "复制选中的模板创建新模板"
        const val PREVIEW_TEMPLATE = "预览模板的渲染效果"
        
        val TEMPLATE_VARIABLES = """模板内容支持以下内置变量：
• {{selectedCode}} - 当前选中的代码
• {{fileName}} - 当前文件名
• {{language}} - 当前文件的编程语言
• {{projectName}} - 项目名称
• {{filePath}} - 当前文件路径
• {{className}} - 当前类名
• {{methodName}} - 当前方法名
• {{packageName}} - 当前包名
• {{requestParams}} - 方法请求参数信息（所有参数）
• {{responseParams}} - 方法返回参数信息
• {{firstRequestParam}} - 第一个请求参数信息""".trimIndent()
        
        val BUILT_IN_TEMPLATES = """
            内置模板包括：
            • 代码解释 - 解释代码功能
            • 代码优化 - 优化性能和可读性
            • 添加注释 - 生成详细注释
            • 重构建议 - 提供重构建议
            • 错误修复 - 检查并修复错误
        """.trimIndent()
    }
    
    /**
     * 快捷键相关提示
     */
    object ShortcutTooltips {
        val SHORTCUT_FORMAT = """
            快捷键格式说明：
            • Ctrl+Alt+字母：主要快捷键
            • Ctrl+Shift+字母：次要快捷键
            • 避免与IDE内置快捷键冲突
        """.trimIndent()
        
        val DEFAULT_SHORTCUTS = """
            默认快捷键：
            • Ctrl+Alt+E - 代码解释
            • Ctrl+Alt+O - 代码优化
            • Ctrl+Alt+C - 添加注释
            • Ctrl+Alt+R - 重构建议
            • Ctrl+Alt+F - 错误修复
        """.trimIndent()
    }
    
    /**
     * 模板操作提示
     */
    object TemplateActionTooltips {
        const val TEMPLATE_ADD = "添加新模板"
        const val TEMPLATE_REMOVE = "删除选中的模板"
        const val TEMPLATE_EDIT = "编辑选中的模板"
        const val TEMPLATE_MOVE_UP = "向上移动模板"
        const val TEMPLATE_MOVE_DOWN = "向下移动模板"
    }
}