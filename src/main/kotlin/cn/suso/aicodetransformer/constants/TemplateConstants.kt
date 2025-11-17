package cn.suso.aicodetransformer.constants

import cn.suso.aicodetransformer.i18n.I18n
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
        private val displayNameKey: String,
        private val descriptionKey: String,
        val category: TemplateCategory,
        private val contentKey: String,
        val supportedLanguages: List<String> = listOf("java", "kotlin", "javascript", "typescript", "python"),
        val version: String = "1.0.0",
        val isBuiltIn: Boolean = true
    ) {
        VARIABLE_NAME_GENERATOR(
            id = "variable-name-generator",
            displayNameKey = "template.variableNameGenerator.name",
            descriptionKey = "template.variableNameGenerator.description",
            category = TemplateCategory.CODE_CONVERSION,
            contentKey = "template.variableNameGenerator.content",
            supportedLanguages = listOf("java", "kotlin", "javascript", "typescript", "python", "go", "rust", "csharp", "swift")
        ),
        CAMEL_CASE_CONVERT(
            id = "camel-case-convert",
            displayNameKey = "template.camelCaseConvert.name",
            descriptionKey = "template.camelCaseConvert.description",
            category = TemplateCategory.CODE_CONVERSION,
            contentKey = "template.camelCaseConvert.content"
        ),

        OBJECT_CONVERT(
            id = "object-convert",
            displayNameKey = "template.objectConvert.name",
            descriptionKey = "template.objectConvert.description",
            category = TemplateCategory.CODE_CONVERSION,
            contentKey = "template.objectConvert.content",
            supportedLanguages = listOf("java", "kotlin")
        ),

        JSON_FORMATTER(
            id = "json-formatter",
            displayNameKey = "template.jsonFormatter.name",
            descriptionKey = "template.jsonFormatter.description",
            category = TemplateCategory.CODE_OPTIMIZATION,
            contentKey = "template.jsonFormatter.content",
            supportedLanguages = listOf("json", "javascript", "typescript")
        ),

        COMMENT_DRIVEN_GENERATOR(
            id = "comment-driven-generator",
            displayNameKey = "template.commentDrivenGenerator.name",
            descriptionKey = "template.commentDrivenGenerator.description",
            category = TemplateCategory.CODE_GENERATION,
            contentKey = "template.commentDrivenGenerator.content",
            supportedLanguages = listOf(
                "java", "kotlin", "javascript", "typescript", "python", "go", "csharp",
                "cpp", "swift", "php", "ruby", "rust"
            )
        ),

        TRANSLATION_CONVERTER(
            id = "translation-converter",
            displayNameKey = "template.translationConverter.name",
            descriptionKey = "template.translationConverter.description",
            category = TemplateCategory.CODE_CONVERSION,
            contentKey = "template.translationConverter.content",
            supportedLanguages = listOf("java", "kotlin", "javascript", "typescript", "python", "cpp", "csharp", "go", "rust", "swift")
        );

        val displayName: String
            get() = I18n.t(displayNameKey)

        val description: String
            get() = I18n.t(descriptionKey)

        val content: String
            get() = I18n.t(contentKey)

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
    enum class TemplateBuiltInVariable(val variable: String, private val descriptionKey: String) {
        SELECTED_CODE("{{selectedCode}}", "prompt.variable.selectedCode"),
        FILE_NAME("{{fileName}}", "prompt.variable.fileName"),
        LANGUAGE("{{language}}", "prompt.variable.language"),
        PROJECT_NAME("{{projectName}}", "prompt.variable.projectName"),
        FILE_PATH("{{filePath}}", "prompt.variable.filePath"),
        CLASS_NAME("{{className}}", "prompt.variable.className"),
        METHOD_NAME("{{methodName}}", "prompt.variable.methodName"),
        PACKAGE_NAME("{{packageName}}", "prompt.variable.packageName"),
        REQUEST_PARAMS("{{requestParams}}", "prompt.variable.requestParams"),
        RESPONSE_PARAMS("{{responseParams}}", "prompt.variable.responseParams"),
        FIRST_REQUEST_PARAM("{{firstRequestParam}}", "prompt.variable.firstRequestParam")
        ;

        val description: String
            get() = I18n.t(descriptionKey)
    }

    /**
     * Git内置变量
     */
    enum class GitBuiltInVariable(val variable: String, private val descriptionKey: String) {
        CHANGED_FILES("{{changedFiles}}", "prompt.variable.changedFiles"),
        FILE_DIFFS("{{fileDiffs}}", "prompt.variable.fileDiffs"),
        BATCH_COMMIT_MESSAGES("{{batchCommitMessages}}", "prompt.variable.batchCommitMessages")
        ;

        val description: String
            get() = I18n.t(descriptionKey)
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