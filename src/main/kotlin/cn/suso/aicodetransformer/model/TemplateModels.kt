package cn.suso.aicodetransformer.model

/**
 * 模板渲染结果
 */
data class TemplateRenderResult(
    /** 是否成功 */
    val success: Boolean,
    
    /** 渲染后的内容 */
    val content: String? = null,
    
    /** 错误信息 */
    val error: String? = null,
    
    /** 使用的模板 */
    val template: Template? = null
)

/**
 * 模板导入结果
 */
data class TemplateImportResult(
    /** 导入成功的数量 */
    val successCount: Int,
    
    /** 导入失败的数量 */
    val failureCount: Int,
    
    /** 跳过的数量（已存在） */
    val skippedCount: Int,
    
    /** 错误信息列表 */
    val errors: List<String> = emptyList(),
    
    /** 导入的模板ID列表 */
    val importedTemplateIds: List<String> = emptyList()
) {
    val totalCount: Int
        get() = successCount + failureCount + skippedCount
        
    val isSuccess: Boolean
        get() = failureCount == 0
}

/**
 * 模板统计信息
 */
data class TemplateStats(
    /** 总模板数 */
    val totalTemplates: Int,
    
    /** 启用的模板数 */
    val enabledTemplates: Int,
    
    /** 内置模板数 */
    val builtInTemplates: Int,
    
    /** 自定义模板数 */
    val customTemplates: Int,
    
    /** 各分类的模板数量 */
    val templatesByCategory: Map<TemplateCategory, Int>,
    
    /** 总使用次数 */
    val totalUsageCount: Long,
    
    /** 平均评分 */
    val averageRating: Double,
    
    /** 最受欢迎的模板ID */
    val mostPopularTemplateId: String?,
    
    /** 最新创建的模板ID */
    val newestTemplateId: String?
)