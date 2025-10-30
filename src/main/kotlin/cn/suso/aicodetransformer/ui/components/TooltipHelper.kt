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
     * 常用提示文本
     */
    object CommonTooltips {
        const val API_KEY = "用于访问AI服务的认证密钥，请确保密钥有效且有足够的配额"
        const val ENDPOINT = "AI服务的API端点地址，例如：https://api.openai.com/v1"
        const val MODEL_ID = "具体的模型标识符，例如：gpt-3.5-turbo、gpt-4等"
        const val TEMPERATURE = "控制输出的随机性，范围0.0-1.0，值越高输出越随机"
        const val MAX_TOKENS = "限制AI响应的最大令牌数，影响输出长度和API费用"

    }
    
    /**
     * 模型配置相关提示
     */
    object ModelConfigTooltips {
        const val ADD_MODEL = "添加新的AI模型配置"
        const val EDIT_MODEL = "编辑选中的模型配置"
        const val DELETE_MODEL = "删除选中的模型配置（不可恢复）"
    }
    
    /**
     * 模板管理相关提示
     */
    object TemplateTooltips {
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