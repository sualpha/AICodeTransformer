package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Attribute
import cn.suso.aicodetransformer.constants.TemplateConstants
import cn.suso.aicodetransformer.service.java.JavaPsiHelperLoader
import java.time.LocalDateTime
import java.util.*

/**
 * Prompt模板数据类
 * 用于存储用户自定义的AI指令模板
 */
@Serializable
@Tag("PromptTemplate")
data class PromptTemplate(
    /** 模板ID，唯一标识 */
    @Attribute("id")
    val id: String = UUID.randomUUID().toString(),
    
    /** 模板名称 */
    @Attribute("name")
    val name: String = "",
    
    /** 模板描述 */
    @Attribute("description")
    val description: String? = null,
    
    /** Prompt内容，支持变量替换 */
    @Attribute("content")
    val content: String = "",
    
    /** 模板分类 */
    @Attribute("category")
    val category: String = TemplateCategory.CUSTOM.displayName,
    
    /** 是否启用 */
    @Attribute("enabled")
    val enabled: Boolean = true,
    
    /** 绑定的快捷键 */
    @Attribute("shortcutKey")
    val shortcutKey: String? = null,
    

    
    /** 使用的模型配置ID */
    @Attribute("modelConfigId")
    val modelConfigId: String? = null,
    
    /** 创建时间 */
    @Attribute("createdAt")
    val createdAt: String = LocalDateTime.now().toString(),
    
    /** 最后修改时间 */
    @Attribute("updatedAt")
    val updatedAt: String = LocalDateTime.now().toString(),
    
    /** 使用次数 */
    @Attribute("usageCount")
    val usageCount: Int = 0,
    
    /** 最后使用时间 */
    @Attribute("lastUsedAt")
    val lastUsedAt: String? = null,
    
    /** 是否为内置模板 */
    @Attribute("isBuiltIn")
    val isBuiltIn: Boolean = false,
    
    /** 模板版本 */
    @Attribute("version")
    val version: String = "1.0",
    
    /** 作者 */
    @Attribute("author")
    val author: String? = null
) {

    
    /**
     * 模板验证结果
     */
    data class TemplateValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    )
    
    companion object {
        /** 支持的内置变量列表 */
        val BUILT_IN_VARIABLES: List<Pair<String, String>> = TemplateConstants.getBuiltInVariablesMap().toList()


        /** 创建驼峰命名转换模板 */
        fun createCamelCaseConvertTemplate(): PromptTemplate {
            val config = TemplateConstants.TemplateConfig.CAMEL_CASE_CONVERT
            return PromptTemplate(
                id = config.id,
                name = config.displayName,
                content = config.content,
                description = config.description,
                category = config.category.displayName,
                shortcutKey = null,
                isBuiltIn = config.isBuiltIn
            )
        }

        /** 创建变量命名生成模板 */
        fun createVariableNameGeneratorTemplate(): PromptTemplate {
            val config = TemplateConstants.TemplateConfig.VARIABLE_NAME_GENERATOR
            return PromptTemplate(
                id = config.id,
                name = config.displayName,
                content = config.content,
                description = config.description,
                category = config.category.displayName,
                shortcutKey = null,
                isBuiltIn = config.isBuiltIn
            )
        }
        
        /** 创建对象转换模板 */
        fun createObjectConvertTemplate(): PromptTemplate {
            val config = TemplateConstants.TemplateConfig.OBJECT_CONVERT
            return PromptTemplate(
                id = config.id,
                name = config.displayName,
                content = config.content,
                description = config.description,
                category = config.category.displayName,
                shortcutKey = null,
                isBuiltIn = config.isBuiltIn
            )
        }
        
        /** 创建JSON格式化模板 */
        fun createJsonFormatterTemplate(): PromptTemplate {
            val config = TemplateConstants.TemplateConfig.JSON_FORMATTER
            return PromptTemplate(
                id = config.id,
                name = config.displayName,
                content = config.content,
                description = config.description,
                category = config.category.displayName,
                shortcutKey = null,
                isBuiltIn = config.isBuiltIn
            )
        }

        /** 创建SQL格式化模板 */
        fun createSqlFormatterTemplate(): PromptTemplate {
            val config = TemplateConstants.TemplateConfig.SQL_FORMATTER
            return PromptTemplate(
                id = config.id,
                name = config.displayName,
                content = config.content,
                description = config.description,
                category = config.category.displayName,
                shortcutKey = null,
                isBuiltIn = config.isBuiltIn
            )
        }
        
        /**
         * 创建翻译转换模板
         */
        private fun createTranslationConverterTemplate(): PromptTemplate {
            val config = TemplateConstants.TemplateConfig.TRANSLATION_CONVERTER
            return PromptTemplate(
                id = config.id,
                name = config.displayName,
                content = config.content,
                category = config.category.displayName,
                description = config.description,
                shortcutKey = null,
                isBuiltIn = config.isBuiltIn
            )
        }
        
        /**
         * 获取所有内置模板
         */
        fun getBuiltInTemplates(): List<PromptTemplate> {
            val templates = mutableListOf(
                createVariableNameGeneratorTemplate(),
                createCamelCaseConvertTemplate(),
                createJsonFormatterTemplate(),
                createSqlFormatterTemplate(),
                createTranslationConverterTemplate()
            )
            if (supportsObjectConversion()) {
                templates.add(2, createObjectConvertTemplate())
            }
            return templates
        }

        private fun supportsObjectConversion(): Boolean = JavaPsiHelperLoader.helper() != null
    }
    
    /**
     * 验证模板
     */
    fun validate(): TemplateValidationResult {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("模板名称不能为空")
        }
        
        if (content.isBlank()) {
            errors.add("模板内容不能为空")
        }
        
        // 验证快捷键格式
        shortcutKey?.let { key ->
            if (!isValidShortcutKey(key)) {
                errors.add("快捷键格式不正确: $key")
            }
        }
        
        return TemplateValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * 验证模板内容是否有效（兼容旧版本）
     */
    fun isValid(): Boolean {
        return validate().isValid
    }
    
    /**
     * 替换模板变量
     */
    fun render(variableValues: Map<String, Any>): String {
        var result = content
        
        // 处理所有传入的变量
        variableValues.forEach { (name, value) ->
            val valueStr = value.toString()
            result = result.replace("{{$name}}", valueStr)
            result = result.replace("\${$name}", valueStr)
        }
        
        // 处理条件块 {{#variableName}} ... {{/variableName}}
        val conditionalPattern = """\{\{#(\w+)\}\}([\s\S]*?)\{\{/\1\}\}""".toRegex()
        result = conditionalPattern.replace(result) { matchResult ->
            val variableName = matchResult.groupValues[1]
            val blockContent = matchResult.groupValues[2]
            val value = variableValues[variableName]
            
            when {
                value is Boolean && value -> blockContent
                value is String && value.isNotBlank() -> blockContent
                value != null && value.toString() != "false" -> blockContent
                else -> ""
            }
        }
        
        return result.trim()
    }


    private fun isValidShortcutKey(key: String): Boolean {
        // 简单的快捷键格式验证
        val pattern = """^(Ctrl|Alt|Shift|Meta)(\+(Ctrl|Alt|Shift|Meta))*\+[A-Z0-9]$""".toRegex()
        return pattern.matches(key)
    }
    

}