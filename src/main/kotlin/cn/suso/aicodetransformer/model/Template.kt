package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

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
    
    /** 模板标签 */
    val tags: List<String> = emptyList(),
    
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
    
    /**
     * 验证变量值
     * @param variableValues 变量值映射
     * @return 验证结果
     */
    fun validateVariables(@Suppress("UNUSED_PARAMETER") variableValues: Map<String, String>): TemplateValidationResult {
        return TemplateValidationResult(
            isValid = true,
            errors = emptyList()
        )
    }
    
    /**
     * 获取所有变量名
     */
    fun getVariableNames(): List<String> {
        return emptyList()
    }
    
    /**
     * 检查是否匹配搜索条件
     */
    fun matchesSearch(query: String): Boolean {
        val lowerQuery = query.lowercase()
        return name.lowercase().contains(lowerQuery) ||
                description.lowercase().contains(lowerQuery) ||
                tags.any { it.lowercase().contains(lowerQuery) } ||
                category.displayName.lowercase().contains(lowerQuery)
    }
}



/**
 * 模板分类
 */
@Serializable
enum class TemplateCategory(val displayName: String, val description: String) {
    CODE_CONVERSION("代码转换", "不同编程语言之间的代码转换"),
    CODE_OPTIMIZATION("代码优化", "代码性能和质量优化"),
    CODE_REVIEW("代码审查", "代码质量检查和建议"),
    DOCUMENTATION("文档生成", "生成代码文档和注释"),
    TESTING("测试生成", "生成单元测试和测试用例"),
    REFACTORING("重构", "代码重构和结构优化"),
    BUG_FIXING("错误修复", "识别和修复代码错误"),
    API_GENERATION("API生成", "生成API接口和文档"),
    DATABASE("数据库", "数据库相关的代码生成"),
    FRONTEND("前端开发", "前端代码生成和优化"),
    BACKEND("后端开发", "后端代码生成和优化"),
    MOBILE("移动开发", "移动应用开发相关"),
    DEVOPS("运维部署", "部署和运维相关代码"),
    ALGORITHM("算法实现", "算法和数据结构实现"),
    GIT_OPERATIONS("Git操作", "Git提交、分支管理等版本控制相关"),
    CUSTOM("自定义", "用户自定义模板")
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
    
    /** 标签过滤 */
    val tags: List<String> = emptyList(),
    
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