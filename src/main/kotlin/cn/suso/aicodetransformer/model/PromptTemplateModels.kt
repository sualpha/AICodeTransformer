package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

/**
 * 持久化状态数据类
 */
@com.intellij.util.xmlb.annotations.Tag("State")
class PromptTemplateState {
    @com.intellij.util.xmlb.annotations.Transient
    var templates: MutableList<PromptTemplate> = mutableListOf()
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Transient
    var usageStatistics: MutableMap<String, Int> = mutableMapOf()
        get() = field
        set(value) { field = value }
    
    // 用于XML序列化的JSON字符串字段
    @com.intellij.util.xmlb.annotations.Tag("templatesXml")
    var templatesXml: String = ""
        get() = field
        set(value) { field = value }
    
    @com.intellij.util.xmlb.annotations.Tag("usageStatisticsXml")
    var usageStatisticsXml: String = ""
        get() = field
        set(value) { field = value }

    @com.intellij.util.xmlb.annotations.Tag("lastPluginVersion")
    var lastPluginVersion: String = ""
        get() = field
        set(value) { field = value }
    
    // 默认构造函数
    constructor()
    
    // 复制构造函数
    constructor(
        templates: MutableList<PromptTemplate> = mutableListOf(),
        usageStatistics: MutableMap<String, Int> = mutableMapOf(),
        templatesXml: String = "",
        usageStatisticsXml: String = "",
        lastPluginVersion: String = ""
    ) {
        this.templates = templates
        this.usageStatistics = usageStatistics
        this.templatesXml = templatesXml
        this.usageStatisticsXml = usageStatisticsXml
        this.lastPluginVersion = lastPluginVersion
    }
    
    // 复制方法
    fun copy(
        templates: MutableList<PromptTemplate> = this.templates,
        usageStatistics: MutableMap<String, Int> = this.usageStatistics,
        templatesXml: String = this.templatesXml,
        usageStatisticsXml: String = this.usageStatisticsXml,
        lastPluginVersion: String = this.lastPluginVersion
    ): PromptTemplateState {
        return PromptTemplateState(templates, usageStatistics, templatesXml, usageStatisticsXml, lastPluginVersion)
    }
}

/**
 * 导出数据的元数据
 */
@Serializable
data class ExportMetadata(
    val totalCount: Int,
    val categories: List<String>,
    val exportedBy: String
)

/**
 * 模板导出数据结构
 */
@Serializable
data class TemplateExportData(
    val version: String,
    val exportTime: String,
    val templates: List<PromptTemplate>,
    val metadata: ExportMetadata
)