package cn.suso.aicodetransformer.constants

/**
 * 验证规则相关常量定义
 * 统一管理所有验证规则中的硬编码限制值和验证消息
 */
object ValidationConstants {
    
    /**
     * 模板验证规则
     */
    object TemplateValidation {
        const val MIN_NAME_LENGTH = 1
        const val MAX_NAME_LENGTH = 100
        const val MIN_DESCRIPTION_LENGTH = 0
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MIN_CONTENT_LENGTH = 1
        const val MAX_CONTENT_LENGTH = 10000
        const val MAX_TAGS_COUNT = 10
        const val MAX_TAG_LENGTH = 50
        const val MAX_SHORTCUT_KEY_LENGTH = 20
        
        // 模板名称验证正则
        const val TEMPLATE_NAME_PATTERN = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-\\s]+$"
        // 快捷键验证正则
        const val SHORTCUT_KEY_PATTERN = "^(ctrl|alt|shift|meta)\\+[a-zA-Z0-9]$"
        // 标签验证正则
        const val TAG_PATTERN = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-]+$"
    }
    
    /**
     * 模型配置验证规则
     */
    object ModelConfigValidation {
        const val MIN_CONFIG_NAME_LENGTH = 1
        const val MAX_CONFIG_NAME_LENGTH = 100
        const val MIN_DESCRIPTION_LENGTH = 0
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MIN_MODEL_NAME_LENGTH = 1
        const val MAX_MODEL_NAME_LENGTH = 100
        const val MIN_API_KEY_LENGTH = 1
        const val MAX_API_KEY_LENGTH = 500
        
        const val MIN_TEMPERATURE = 0.0
        const val MAX_TEMPERATURE = 2.0
        const val MIN_MAX_TOKENS = 1
        const val MAX_MAX_TOKENS = 200000
        const val MIN_CONNECT_TIMEOUT = 1
        const val MAX_CONNECT_TIMEOUT = 600
        const val MIN_READ_TIMEOUT = 1
        const val MAX_READ_TIMEOUT = 600
        const val MIN_RETRY_COUNT = 0
        const val MAX_RETRY_COUNT = 10
        
        // URL验证正则
        const val URL_PATTERN = "^https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?$"
        // 模型名称验证正则
        const val MODEL_NAME_PATTERN = "^[a-zA-Z0-9_\\-\\.]+$"
        // API密钥验证正则（基本格式）
        const val API_KEY_PATTERN = "^[a-zA-Z0-9_\\-\\.]+$"
    }
    
    /**
     * 性能配置验证规则
     */
    object PerformanceValidation {
        const val MIN_MAX_CONNECTIONS = 1
        const val MAX_MAX_CONNECTIONS = 1000
        const val MIN_MAX_CONNECTIONS_PER_ROUTE = 1
        const val MAX_MAX_CONNECTIONS_PER_ROUTE = 100
        const val MIN_TIMEOUT_MS = 100
        const val MAX_TIMEOUT_MS = 300000
        const val MIN_CACHE_SIZE = 1
        const val MAX_CACHE_SIZE = 10000
        const val MIN_CACHE_EXPIRE_MINUTES = 1L
        const val MAX_CACHE_EXPIRE_MINUTES = 10080L // 7天
        const val MIN_BATCH_SIZE = 1
        const val MAX_BATCH_SIZE = 100
        const val MIN_THREAD_POOL_SIZE = 1
        const val MAX_THREAD_POOL_SIZE = 200
        const val MIN_QUEUE_CAPACITY = 1
        const val MAX_QUEUE_CAPACITY = 10000
        
        const val MIN_PERCENTAGE = 0.0
        const val MAX_PERCENTAGE = 100.0
        const val MIN_RESPONSE_TIME_MS = 1L
        const val MAX_RESPONSE_TIME_MS = 3600000L // 1小时
    }
    
    /**
     * 用户输入验证规则
     */
    object UserInputValidation {
        const val MIN_USERNAME_LENGTH = 3
        const val MAX_USERNAME_LENGTH = 50
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_PASSWORD_LENGTH = 128
        const val MIN_EMAIL_LENGTH = 5
        const val MAX_EMAIL_LENGTH = 254
        const val MIN_PHONE_LENGTH = 10
        const val MAX_PHONE_LENGTH = 15
        
        // 用户名验证正则
        const val USERNAME_PATTERN = "^[a-zA-Z0-9_\\-]+$"
        // 邮箱验证正则
        const val EMAIL_PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
        // 手机号验证正则
        const val PHONE_PATTERN = "^[0-9+\\-\\s()]+$"
        // 密码强度验证正则（至少包含字母和数字）
        const val PASSWORD_PATTERN = "^(?=.*[a-zA-Z])(?=.*\\d)[a-zA-Z\\d@$!%*?&]{6,}$"
    }
    
    /**
     * 文件验证规则
     */
    object FileValidation {
        const val MAX_FILE_SIZE_MB = 10
        const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024
        const val MIN_FILENAME_LENGTH = 1
        const val MAX_FILENAME_LENGTH = 255
        
        // 支持的文件扩展名
        val SUPPORTED_TEXT_EXTENSIONS = setOf("txt", "json", "xml", "yaml", "yml", "md")
        val SUPPORTED_CODE_EXTENSIONS = setOf("java", "kt", "js", "ts", "py", "go", "rs", "cpp", "c", "h")
        val SUPPORTED_CONFIG_EXTENSIONS = setOf("properties", "conf", "ini", "cfg")
        
        // 文件名验证正则
        const val FILENAME_PATTERN = "^[\\w\\-\\. ]+$"
        // 路径验证正则
        const val PATH_PATTERN = "^[\\w\\-\\./\\\\: ]+$"
    }
    
    /**
     * 验证错误消息
     */
    object ErrorMessages {
        // 通用错误消息
        const val FIELD_REQUIRED = "此字段为必填项"
        const val FIELD_TOO_SHORT = "字段长度不能少于 %d 个字符"
        const val FIELD_TOO_LONG = "字段长度不能超过 %d 个字符"
        const val INVALID_FORMAT = "格式不正确"
        const val VALUE_OUT_OF_RANGE = "值必须在 %s 到 %s 之间"
        const val INVALID_NUMBER = "请输入有效的数字"
        const val INVALID_URL = "请输入有效的URL地址"
        const val INVALID_EMAIL = "请输入有效的邮箱地址"
        const val INVALID_PHONE = "请输入有效的手机号码"
        
        // 模板相关错误消息
        const val TEMPLATE_NAME_REQUIRED = "模板名称不能为空"
        const val TEMPLATE_NAME_INVALID = "模板名称只能包含中文、英文、数字、下划线、连字符和空格"
        const val TEMPLATE_CONTENT_REQUIRED = "模板内容不能为空"
        const val TEMPLATE_CONTENT_TOO_LONG = "模板内容不能超过 %d 个字符"
        const val SHORTCUT_KEY_INVALID = "快捷键格式不正确，应为 ctrl+字母 或 alt+字母 格式"
        const val SHORTCUT_KEY_CONFLICT = "快捷键已被其他模板使用"
        const val TAG_INVALID = "标签只能包含中文、英文、数字、下划线和连字符"
        const val TOO_MANY_TAGS = "标签数量不能超过 %d 个"
        
        // 模型配置相关错误消息
        const val CONFIG_NAME_REQUIRED = "配置名称不能为空"
        const val MODEL_NAME_REQUIRED = "模型名称不能为空"
        const val MODEL_NAME_INVALID = "模型名称只能包含英文、数字、下划线、连字符和点号"
        const val API_KEY_REQUIRED = "API密钥不能为空"
        const val API_KEY_INVALID = "API密钥格式不正确"
        const val API_BASE_URL_REQUIRED = "API基础URL不能为空"
        const val TEMPERATURE_OUT_OF_RANGE = "温度参数必须在 %.1f 到 %.1f 之间"
        const val MAX_TOKENS_OUT_OF_RANGE = "最大Token数必须在 %d 到 %d 之间"
        const val TIMEOUT_OUT_OF_RANGE = "超时时间必须在 %d 到 %d 秒之间"
        const val RETRY_COUNT_OUT_OF_RANGE = "重试次数必须在 %d 到 %d 之间"
        
        // 性能配置相关错误消息
        const val CONNECTION_COUNT_OUT_OF_RANGE = "连接数必须在 %d 到 %d 之间"
        const val CACHE_SIZE_OUT_OF_RANGE = "缓存大小必须在 %d 到 %d 之间"
        const val PERCENTAGE_OUT_OF_RANGE = "百分比必须在 %.1f%% 到 %.1f%% 之间"
        const val BATCH_SIZE_OUT_OF_RANGE = "批处理大小必须在 %d 到 %d 之间"
        const val THREAD_POOL_SIZE_OUT_OF_RANGE = "线程池大小必须在 %d 到 %d 之间"
        
        // 文件相关错误消息
        const val FILE_TOO_LARGE = "文件大小不能超过 %d MB"
        const val FILE_EXTENSION_NOT_SUPPORTED = "不支持的文件扩展名"
        const val FILENAME_INVALID = "文件名包含非法字符"
        const val FILE_NOT_FOUND = "文件不存在"
        const val FILE_READ_ERROR = "文件读取失败"
        const val FILE_WRITE_ERROR = "文件写入失败"
    }
    
    /**
     * 验证成功消息
     */
    object SuccessMessages {
        const val VALIDATION_PASSED = "验证通过"
        const val TEMPLATE_VALID = "模板验证通过"
        const val CONFIG_VALID = "配置验证通过"
        const val INPUT_VALID = "输入验证通过"
        const val FILE_VALID = "文件验证通过"
    }
    
    /**
     * 验证工具方法
     */
    object ValidationUtils {
        /**
         * 检查字符串长度是否在指定范围内
         */
        fun isLengthValid(value: String?, minLength: Int, maxLength: Int): Boolean {
            return value != null && value.length >= minLength && value.length <= maxLength
        }
        
        /**
         * 检查数值是否在指定范围内
         */
        fun isNumberInRange(value: Number, min: Number, max: Number): Boolean {
            val doubleValue = value.toDouble()
            return doubleValue >= min.toDouble() && doubleValue <= max.toDouble()
        }
        
        /**
         * 检查字符串是否匹配指定正则表达式
         */
        fun matchesPattern(value: String?, pattern: String): Boolean {
            return value != null && value.matches(Regex(pattern))
        }
        
        /**
         * 检查文件扩展名是否被支持
         */
        fun isSupportedFileExtension(filename: String, supportedExtensions: Set<String>): Boolean {
            val extension = filename.substringAfterLast('.', "")
            return extension.isNotEmpty() && supportedExtensions.contains(extension.lowercase())
        }
        
        /**
         * 获取格式化的错误消息
         */
        fun formatErrorMessage(template: String, vararg args: Any): String {
            return template.format(*args)
        }
    }
}