package cn.suso.aicodetransformer.constants

import cn.suso.aicodetransformer.model.TemplateCategory

/**
 * 模板相关常量定义
 * 统一管理所有模板ID、内置变量、分类等硬编码值
 */
object TemplateConstants {
    
    /**
     * 统一的模板配置枚举
     * 整合了模板ID、显示名称、描述、分类、标签、内容等所有相关配置
     */
    enum class TemplateConfig(
        val id: String,
        val displayName: String,
        val description: String,
        val category: TemplateCategory,
        val tags: List<String>,
        val content: String,
        val supportedLanguages: List<String> = listOf("java", "kotlin", "javascript", "typescript", "python"),
        val version: String = "1.0.0",
        val isBuiltIn: Boolean = true
    ) {
        CAMEL_CASE_CONVERT(
            id = "camel-case-convert",
            displayName = "驼峰命名转换",
            description = "将选中的文本转换为驼峰命名格式",
            category = TemplateCategory.CODE_CONVERSION,
            tags = listOf("命名转换", "驼峰", "格式化"),
            content = "请将以下文本转换为驼峰命名格式，只返回转换结果：\n\n```\n{{selectedCode}}\n```"
        ),
        
        OBJECT_CONVERT(
            id = "object-convert",
            displayName = "对象转换",
            description = "生成Java对象之间的转换方法，基于字段分析的逐字段显式转换，支持VO/DTO/DOMAIN/ENTITY转换",
            category = TemplateCategory.CODE_CONVERSION,
            tags = listOf("对象转换", "VO", "DTO", "DOMAIN", "ENTITY", "hutool", "显式转换", "字段分析"),
            content = "请根据以下选中的代码生成对象转换代码：\n\n{{selectedCode}}\n\n方法请求参数信息：\n{{firstRequestParam}}\n\n方法返回参数信息：\n{{responseParams}}\n\n转换要求：\n1. 将 BeanUtil.copyProperties() 方法替换为逐个字段的显式转换\n2. 只需要从请求参数转换到响应参数的单向转换\n3. 对于每个字段，使用明确的 getter/setter 方法进行转换\n4. 字段转换规则：\n   - 如果源字段和目标字段都是String类型，直接复制赋值\n   - 如果类型不同，使用hutool工具进行转换\n   - 时间类型转换默认使用年月日格式（yyyy-MM-dd）\n5. 保持代码的可读性和维护性\n6. 根据上述请求参数和返回参数信息进行精确的字段映射\n7. 如果目标对象中的字段在源对象中不存在，则直接忽略该字段，不要设置为空值或默认值\n8. 只转换存在对应关系的字段\n9. 对于需要类型转换的字段，优先使用hutool的工具类\n\n注意：请直接返回纯Java代码，不要包含任何代码块标记（如```java、```等），不要包含import语句说明，不需要额外的文字说明。",
            supportedLanguages = listOf("java", "kotlin")
        ),
        
        JSON_FORMATTER(
            id = "json-formatter",
            displayName = "JSON格式化",
            description = "智能JSON格式化工具：自动处理特殊字符、补全缺失符号、修复语法错误并美化格式",
            category = TemplateCategory.CODE_OPTIMIZATION,
            tags = listOf("JSON", "格式化", "特殊字符处理", "自动补全", "语法修复", "美化"),
            content = "请将以下文本转换为标准的JSON格式。\n\n处理要求：\n1. 自动清理特殊字符（\\n, \\r, \\t, \\/, \\\\等转义字符）\n2. 智能补全缺失的大括号 {} 和方括号 []\n3. 修正常见JSON语法错误：\n   - 为缺少引号的键名添加双引号\n   - 将单引号替换为双引号\n   - 移除尾随逗号\n   - 补全缺失的逗号\n4. 格式化为美观的缩进格式\n5. 验证JSON语法的正确性\n\n原始文本：\n```\n{{selectedCode}}\n```\n\n请直接返回格式化后的标准JSON，确保语法正确且格式美观。",
            supportedLanguages = listOf("json", "javascript", "typescript")
        ),
        
        TRANSLATION_CONVERTER(
            id = "translation-converter",
            displayName = "智能翻译转换",
            description = "智能识别选中代码的语言并进行翻译转换：英文转简体中文，中文转英文，专注于计算机软件行业术语",
            category = TemplateCategory.CODE_CONVERSION,
            tags = listOf("翻译", "中英文转换", "文档翻译"),
            content = "请对以下选中的代码内容进行智能翻译转换：\n\n```\n{{selectedCode}}\n```\n\n翻译要求：\n1. 智能识别语言：\n   - 如果是英文内容，翻译成简体中文\n   - 如果是中文内容，翻译成英文,并且用驼峰命名\n\n2. 翻译规则：\n   - 专业术语使用计算机软件行业标准翻译\n\n3. 输出格式：\n   - 直接返回翻译后的内容\n   - 不要添加任何解释说明\n\n请开始翻译：",
            supportedLanguages = listOf("java", "kotlin", "javascript", "typescript", "python", "cpp", "csharp", "go", "rust", "swift")
        );
        
        companion object {
            /**
             * 根据ID查找模板配置
             */
            fun findById(id: String): TemplateConfig? {
                return values().find { it.id == id }
            }
            
            /**
             * 根据分类获取模板配置列表
             */
            fun findByCategory(category: TemplateCategory): List<TemplateConfig> {
                return values().filter { it.category == category }
            }
            
            /**
             * 根据标签获取模板配置列表
             */
            fun findByTag(tag: String): List<TemplateConfig> {
                return values().filter { tag in it.tags }
            }
            
            /**
             * 根据支持的语言获取模板配置列表
             */
            fun findByLanguage(language: String): List<TemplateConfig> {
                return values().filter { language in it.supportedLanguages }
            }
            
            /**
             * 获取所有内置模板配置
             */
            fun getBuiltInTemplates(): List<TemplateConfig> {
                return values().filter { it.isBuiltIn }
            }
            
            /**
             * 获取所有模板ID列表
             */
            fun getAllTemplateIds(): List<String> {
                return values().map { it.id }
            }
            
            /**
             * 检查是否为内置模板ID
             */
            fun isBuiltInTemplate(id: String): Boolean {
                return values().any { it.id == id && it.isBuiltIn }
            }
        }
    }
    
    /**
     * 内置变量定义
     */
    /**
     * 模板内置变量
     */
    enum class TemplateBuiltInVariable(val variable: String, val description: String) {
        SELECTED_CODE("{{selectedCode}}", "当前选中的代码"),
        FILE_NAME("{{fileName}}", "当前文件名"),
        LANGUAGE("{{language}}", "当前文件的编程语言"),
        PROJECT_NAME("{{projectName}}", "项目名称"),
        FILE_PATH("{{filePath}}", "当前文件路径"),
        CLASS_NAME("{{className}}", "当前类名"),
        METHOD_NAME("{{methodName}}", "当前方法名"),
        PACKAGE_NAME("{{packageName}}", "当前包名"),
        REQUEST_PARAMS("{{requestParams}}", "方法请求参数信息"),
        RESPONSE_PARAMS("{{responseParams}}", "方法返回参数信息"),
        FIRST_REQUEST_PARAM("{{firstRequestParam}}", "第一个请求参数信息")
    }

    /**
     * Git内置变量
     */
    enum class GitBuiltInVariable(val variable: String, val description: String) {
        CHANGED_FILES("{{changedFiles}}", "Git变更文件列表"),
        FILE_DIFFS("{{fileDiffs}}", "文件差异详情"),
        BATCH_COMMIT_MESSAGES("{{batchCommitMessages}}", "多个批次的提交信息")
    }


    
    /**
     * 模板标签常量
     */
    object Tags {
        const val NAMING_CONVERSION = "命名转换"
        const val CAMEL_CASE = "驼峰"
        const val FORMATTING = "格式化"
        const val OBJECT_CONVERSION = "对象转换"
        const val VO = "VO"
        const val DTO = "DTO"
        const val DOMAIN = "DOMAIN"
        const val ENTITY = "ENTITY"
        const val HUTOOL = "hutool"
        const val EXPLICIT_CONVERSION = "显式转换"
        const val FIELD_ANALYSIS = "字段分析"
        const val JSON = "JSON"
        const val SPECIAL_CHAR_HANDLING = "特殊字符处理"
        const val AUTO_COMPLETION = "自动补全"
        const val SYNTAX_REPAIR = "语法修复"
        const val BEAUTIFICATION = "美化"
    }
    

    
    /**
     * 获取所有内置变量的映射
     */
    fun getBuiltInVariablesMap(): Map<String, String> {
        val templateVariables = TemplateBuiltInVariable.values().associate { it.variable to it.description }
        val gitVariables = GitBuiltInVariable.values().associate { it.variable to it.description }
        return templateVariables + gitVariables
    }
    

}