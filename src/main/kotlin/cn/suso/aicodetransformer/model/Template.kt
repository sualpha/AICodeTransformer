package cn.suso.aicodetransformer.model

import cn.suso.aicodetransformer.i18n.I18n
import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.ResourceBundle

/**
 * AI代码转换模板
 */
@Serializable
data class Template(
    /** 模板ID */
    val id: String,
    
    /** 模板名称 */
    val name: String,
    
    /** 模板描述 */
    val description: String,
    
    /** 模板分类 */
    val category: TemplateCategory,
    
    /** 提示词模板 */
    val promptTemplate: String,
    

    
    /** 示例输入 */
    val exampleInput: String? = null,
    
    /** 示例输出 */
    val exampleOutput: String? = null,
    
    /** 支持的编程语言 */
    val supportedLanguages: List<String> = emptyList(),
    
    /** 模板版本 */
    val version: String = "1.0.0",
    
    /** 创建者 */
    val author: String? = null,
    
    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** 更新时间 */
    val updatedAt: Long = System.currentTimeMillis(),
    
    /** 是否为内置模板 */
    val isBuiltIn: Boolean = false,
    
    /** 是否启用 */
    val enabled: Boolean = true,
    
    /** 使用次数 */
    val usageCount: Long = 0,
    
    /** 评分 */
    val rating: Double = 0.0,
    
    /** 评价数量 */
    val ratingCount: Int = 0
) {
    /**
     * 渲染模板，将变量替换为实际值
     * @param variableValues 变量值映射
     * @return 渲染后的提示词
     */
    fun render(variableValues: Map<String, String>): String {
        var rendered = promptTemplate
        
        // 替换变量
        variableValues.forEach { (name, value) ->
            rendered = rendered.replace("{{$name}}", value)
            rendered = rendered.replace("\${$name}", value)
        }
        
        return rendered
    }
    

}



/**
 * 模板分类
 */
@Serializable
enum class TemplateCategory(private val displayNameKey: String, private val descriptionKey: String) {
    CODE_CONVERSION("template.category.codeConversion.name", "template.category.codeConversion.description"),
    CODE_OPTIMIZATION("template.category.codeOptimization.name", "template.category.codeOptimization.description"),
    CODE_REVIEW("template.category.codeReview.name", "template.category.codeReview.description"),
    DOCUMENTATION("template.category.documentation.name", "template.category.documentation.description"),
    TESTING("template.category.testing.name", "template.category.testing.description"),
    REFACTORING("template.category.refactoring.name", "template.category.refactoring.description"),
    BUG_FIXING("template.category.bugFixing.name", "template.category.bugFixing.description"),
    API_GENERATION("template.category.apiGeneration.name", "template.category.apiGeneration.description"),
    DATABASE("template.category.database.name", "template.category.database.description"),
    FRONTEND("template.category.frontend.name", "template.category.frontend.description"),
    BACKEND("template.category.backend.name", "template.category.backend.description"),
    MOBILE("template.category.mobile.name", "template.category.mobile.description"),
    DEVOPS("template.category.devops.name", "template.category.devops.description"),
    ALGORITHM("template.category.algorithm.name", "template.category.algorithm.description"),
    GIT_OPERATIONS("template.category.gitOperations.name", "template.category.gitOperations.description"),
    CUSTOM("template.category.custom.name", "template.category.custom.description");

    val displayName: String
        get() = I18n.t(displayNameKey)

    val description: String
        get() = I18n.t(descriptionKey)

    companion object {
        private val supportedLocales = listOf(
            Locale.SIMPLIFIED_CHINESE,
            Locale("en", "US")
        )

        fun fromDisplayName(displayName: String): TemplateCategory? {
            return values().firstOrNull { category ->
                supportedLocales.any { locale ->
                    runCatching {
                        ResourceBundle.getBundle("i18n.messages", locale).getString(category.displayNameKey)
                    }.getOrNull() == displayName
                }
            }
        }
    }
}

/**
 * 模板验证结果
 */
data class TemplateValidationResult(
    /** 是否有效 */
    val isValid: Boolean,
    
    /** 错误信息列表 */
    val errors: List<String>
)

/**
 * 模板搜索条件
 */
data class TemplateSearchCriteria(
    /** 搜索关键词 */
    val query: String? = null,
    
    /** 分类过滤 */
    val category: TemplateCategory? = null,
    
    /** 编程语言过滤 */
    val language: String? = null,
    
    /** 是否只显示启用的模板 */
    val enabledOnly: Boolean = true,
    
    /** 排序方式 */
    val sortBy: TemplateSortBy = TemplateSortBy.NAME,
    
    /** 排序方向 */
    val sortDirection: SortDirection = SortDirection.ASC
)

/**
 * 模板排序方式
 */
enum class TemplateSortBy {
    NAME,           // 按名称排序
    CREATED_AT,     // 按创建时间排序
    UPDATED_AT,     // 按更新时间排序
    USAGE_COUNT,    // 按使用次数排序
    RATING          // 按评分排序
}

/**
 * 排序方向
 */
enum class SortDirection {
    ASC,    // 升序
    DESC    // 降序
}