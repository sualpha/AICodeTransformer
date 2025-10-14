package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

/**
 * 持久化状态数据类
 */
@Serializable
@com.intellij.util.xmlb.annotations.Tag("State")
data class PromptTemplateState(
    @com.intellij.util.xmlb.annotations.Transient
    var templates: MutableList<PromptTemplate> = mutableListOf(),
    @com.intellij.util.xmlb.annotations.Transient
    var usageStatistics: MutableMap<String, Int> = mutableMapOf(),
    
    // 用于XML序列化的JSON字符串字段
    @com.intellij.util.xmlb.annotations.Tag("templatesXml")
    var templatesXml: String = "",
    @com.intellij.util.xmlb.annotations.Tag("usageStatisticsXml")
    var usageStatisticsXml: String = ""
)

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