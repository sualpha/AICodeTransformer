package cn.suso.aicodetransformer.constants

/**
 * UI界面相关常量定义
 * 统一管理所有UI组件中的硬编码字符串和配置值
 */
object UIConstants {
    
    /**
     * 快捷键绑定面板相关常量
     */
    object ShortcutKeyBinding {
        const val PANEL_TITLE = "快捷键绑定设置"
        const val TEMPLATE_COLUMN_NAME = "模板"
        const val SHORTCUT_COLUMN_NAME = "快捷键"
        const val ACTION_COLUMN_NAME = "操作"
        const val EDIT_BUTTON_TEXT = "编辑"
        const val CLEAR_BUTTON_TEXT = "清除"
        const val SAVE_BUTTON_TEXT = "保存"
        const val CANCEL_BUTTON_TEXT = "取消"
        const val APPLY_BUTTON_TEXT = "应用"
        const val RESET_BUTTON_TEXT = "重置"
        
        const val EDIT_SHORTCUT_DIALOG_TITLE = "编辑快捷键"
        const val SHORTCUT_INPUT_LABEL = "请按下新的快捷键组合："
        const val SHORTCUT_CONFLICT_MESSAGE = "快捷键冲突，请选择其他组合"
        const val SHORTCUT_INVALID_MESSAGE = "无效的快捷键组合"
        const val SHORTCUT_SAVED_MESSAGE = "快捷键已保存"
        const val SHORTCUT_CLEARED_MESSAGE = "快捷键已清除"
        
        const val TOOLTIP_EDIT = "编辑此模板的快捷键"
        const val TOOLTIP_CLEAR = "清除此模板的快捷键"
        const val TOOLTIP_SAVE = "保存所有快捷键设置"
        const val TOOLTIP_RESET = "重置为默认快捷键设置"
    }
    
    /**
     * 模板管理面板相关常量
     */
    object TemplatePanel {
        const val PANEL_TITLE = "模板管理"
        const val CREATE_TEMPLATE_BUTTON = "创建模板"
        const val EDIT_TEMPLATE_BUTTON = "编辑模板"
        const val DELETE_TEMPLATE_BUTTON = "删除模板"
        const val DUPLICATE_TEMPLATE_BUTTON = "复制模板"
        const val IMPORT_TEMPLATE_BUTTON = "导入模板"
        const val EXPORT_TEMPLATE_BUTTON = "导出模板"
        
        const val TEMPLATE_NAME_LABEL = "模板名称："
        const val TEMPLATE_DESCRIPTION_LABEL = "模板描述："
        const val TEMPLATE_CATEGORY_LABEL = "模板分类："
        const val TEMPLATE_CONTENT_LABEL = "模板内容："
        const val TEMPLATE_SHORTCUT_LABEL = "快捷键："
        const val TEMPLATE_TAGS_LABEL = "标签："
        
        const val DELETE_CONFIRMATION_TITLE = "确认删除"
        const val DELETE_CONFIRMATION_MESSAGE = "确定要删除此模板吗？此操作不可撤销。"
        const val TEMPLATE_CREATED_MESSAGE = "模板创建成功"
        const val TEMPLATE_UPDATED_MESSAGE = "模板更新成功"
        const val TEMPLATE_DELETED_MESSAGE = "模板删除成功"
        const val TEMPLATE_DUPLICATED_MESSAGE = "模板复制成功"
    }
    
    /**
     * 模型配置面板相关常量
     */
    object ModelConfigPanel {
        const val PANEL_TITLE = "模型配置"
        const val CREATE_CONFIG_BUTTON = "创建配置"
        const val EDIT_CONFIG_BUTTON = "编辑配置"
        const val DELETE_CONFIG_BUTTON = "删除配置"
        const val TEST_CONNECTION_BUTTON = "测试连接"
        const val SET_DEFAULT_BUTTON = "设为默认"
        
        const val CONFIG_NAME_LABEL = "配置名称："
        const val CONFIG_DESCRIPTION_LABEL = "配置描述："
        const val MODEL_TYPE_LABEL = "模型类型："
        const val API_BASE_URL_LABEL = "API基础URL："
        const val MODEL_NAME_LABEL = "模型名称："
        const val API_KEY_LABEL = "API密钥："
        const val TEMPERATURE_LABEL = "温度参数："
        const val MAX_TOKENS_LABEL = "最大Token数："
        const val TIMEOUT_LABEL = "超时时间（秒）："
        const val RETRY_COUNT_LABEL = "重试次数："
        const val STREAM_RESPONSE_LABEL = "流式响应"
        const val ENABLED_LABEL = "启用配置"
        
        const val CONNECTION_SUCCESS_MESSAGE = "连接测试成功"
        const val CONNECTION_FAILED_MESSAGE = "连接测试失败"
        const val CONFIG_SAVED_MESSAGE = "配置保存成功"
        const val CONFIG_DELETED_MESSAGE = "配置删除成功"
        const val DEFAULT_CONFIG_SET_MESSAGE = "默认配置已设置"
    }
    
    /**
     * 通用UI常量
     */
    object Common {
        const val OK_BUTTON = "确定"
        const val CANCEL_BUTTON = "取消"
        const val YES_BUTTON = "是"
        const val NO_BUTTON = "否"
        const val APPLY_BUTTON = "应用"
        const val CLOSE_BUTTON = "关闭"
        const val SAVE_BUTTON = "保存"
        const val RESET_BUTTON = "重置"
        const val REFRESH_BUTTON = "刷新"
        const val SEARCH_BUTTON = "搜索"
        const val CLEAR_BUTTON = "清除"
        
        const val LOADING_MESSAGE = "加载中..."
        const val SAVING_MESSAGE = "保存中..."
        const val PROCESSING_MESSAGE = "处理中..."
        const val COMPLETED_MESSAGE = "完成"
        const val ERROR_MESSAGE = "错误"
        const val WARNING_MESSAGE = "警告"
        const val INFO_MESSAGE = "信息"
        
        const val REQUIRED_FIELD_INDICATOR = "*"
        const val OPTIONAL_FIELD_INDICATOR = "(可选)"
        const val PLACEHOLDER_TEXT = "请输入..."
        const val NO_DATA_MESSAGE = "暂无数据"
        const val SEARCH_PLACEHOLDER = "搜索..."
    }
    
    /**
     * 错误消息常量
     */
    object ErrorMessages {
        const val FIELD_REQUIRED = "此字段为必填项"
        const val INVALID_INPUT = "输入格式不正确"
        const val NETWORK_ERROR = "网络连接错误"
        const val SERVER_ERROR = "服务器错误"
        const val TIMEOUT_ERROR = "请求超时"
        const val PERMISSION_DENIED = "权限不足"
        const val FILE_NOT_FOUND = "文件未找到"
        const val OPERATION_FAILED = "操作失败"
        const val VALIDATION_FAILED = "验证失败"
        const val DUPLICATE_NAME = "名称已存在"
        const val INVALID_URL = "URL格式不正确"
        const val INVALID_NUMBER = "数字格式不正确"
        const val VALUE_OUT_OF_RANGE = "值超出允许范围"
    }
    
    /**
     * 成功消息常量
     */
    object SuccessMessages {
        const val OPERATION_COMPLETED = "操作完成"
        const val DATA_SAVED = "数据保存成功"
        const val DATA_LOADED = "数据加载成功"
        const val CONNECTION_ESTABLISHED = "连接建立成功"
        const val CONFIGURATION_UPDATED = "配置更新成功"
        const val IMPORT_COMPLETED = "导入完成"
        const val EXPORT_COMPLETED = "导出完成"
        const val VALIDATION_PASSED = "验证通过"
        const val SYNC_COMPLETED = "同步完成"
    }
    
    /**
     * 对话框标题常量
     */
    object DialogTitles {
        const val CONFIRMATION = "确认"
        const val ERROR = "错误"
        const val WARNING = "警告"
        const val INFORMATION = "信息"
        const val SETTINGS = "设置"
        const val PREFERENCES = "首选项"
        const val ABOUT = "关于"
        const val HELP = "帮助"
        const val IMPORT = "导入"
        const val EXPORT = "导出"
        const val EDIT = "编辑"
        const val CREATE = "创建"
        const val DELETE = "删除"
    }
    
    /**
     * 菜单项常量
     */
    object MenuItems {
        const val FILE_MENU = "文件"
        const val EDIT_MENU = "编辑"
        const val VIEW_MENU = "查看"
        const val TOOLS_MENU = "工具"
        const val HELP_MENU = "帮助"
        
        const val NEW_ITEM = "新建"
        const val OPEN_ITEM = "打开"
        const val SAVE_ITEM = "保存"
        const val SAVE_AS_ITEM = "另存为"
        const val IMPORT_ITEM = "导入"
        const val EXPORT_ITEM = "导出"
        const val EXIT_ITEM = "退出"
        
        const val UNDO_ITEM = "撤销"
        const val REDO_ITEM = "重做"
        const val CUT_ITEM = "剪切"
        const val COPY_ITEM = "复制"
        const val PASTE_ITEM = "粘贴"
        const val SELECT_ALL_ITEM = "全选"
        
        const val PREFERENCES_ITEM = "首选项"
        const val SETTINGS_ITEM = "设置"
        const val ABOUT_ITEM = "关于"
    }
    
    /**
     * 工具提示常量
     */
    object Tooltips {
        const val SAVE_TOOLTIP = "保存当前更改"
        const val CANCEL_TOOLTIP = "取消当前操作"
        const val REFRESH_TOOLTIP = "刷新数据"
        const val SEARCH_TOOLTIP = "搜索内容"
        const val CLEAR_TOOLTIP = "清除输入内容"
        const val EDIT_TOOLTIP = "编辑选中项"
        const val DELETE_TOOLTIP = "删除选中项"
        const val ADD_TOOLTIP = "添加新项"
        const val SETTINGS_TOOLTIP = "打开设置"
        const val HELP_TOOLTIP = "查看帮助信息"
    }
    
    /**
     * 表格列名常量
     */
    object TableColumns {
        const val ID_COLUMN = "ID"
        const val NAME_COLUMN = "名称"
        const val DESCRIPTION_COLUMN = "描述"
        const val TYPE_COLUMN = "类型"
        const val STATUS_COLUMN = "状态"
        const val CREATED_DATE_COLUMN = "创建日期"
        const val MODIFIED_DATE_COLUMN = "修改日期"
        const val ACTIONS_COLUMN = "操作"
        const val ENABLED_COLUMN = "启用"
        const val CATEGORY_COLUMN = "分类"
        const val TAGS_COLUMN = "标签"
        const val USAGE_COUNT_COLUMN = "使用次数"
    }
}