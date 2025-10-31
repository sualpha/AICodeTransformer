package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

/**
 * 内置变量定义
 */
data class CommitVariable(
    val name: String,
    val description: String,
    val example: String
)

/**
 * 提交信息模板类型
 */
enum class CommitTemplateType(val displayName: String, val description: String) {
    /** 简洁模式 - 生成简短的提交信息 */
    SIMPLE("简洁模式", "生成简洁的Git提交信息（30字符以内）")
}

/**
 * Commit设置数据模型 - 包含单个模板管理
 */
@Serializable
@com.intellij.util.xmlb.annotations.Tag("CommitSettings")
class CommitSettings {
    /** 是否启用自动提交 */
    @com.intellij.util.xmlb.annotations.Attribute("autoCommitEnabled")
    var autoCommitEnabled: Boolean = false
        get() = field
        set(value) { field = value }
    
    /** 是否启用自动推送 */
    @com.intellij.util.xmlb.annotations.Attribute("autoPushEnabled")
    var autoPushEnabled: Boolean = false
        get() = field
        set(value) { field = value }
    
    /** 单个文件提示词模板 */
    @com.intellij.util.xmlb.annotations.Tag("singleFileTemplate")
    var singleFileTemplate: String = SIMPLE_TEMPLATE
        get() = field
        set(value) { field = value }
    
    /** 汇总提示词模板 */
    @com.intellij.util.xmlb.annotations.Tag("summaryTemplate")
    var summaryTemplate: String = SUMMARY_TEMPLATE
        get() = field
        set(value) { field = value }
    
    /** Commit消息模板 (已废弃，保留向后兼容) */
    @Deprecated("使用 singleFileTemplate 和 summaryTemplate 替代")
    @com.intellij.util.xmlb.annotations.Tag("commitTemplate")
    var commitTemplate: String = DEFAULT_TEMPLATE
        get() = field
        set(value) { field = value }
    
    /** 提交信息模板类型 (已废弃，保留向后兼容) */
    @Deprecated("使用 singleFileTemplate 和 summaryTemplate 替代")
    @com.intellij.util.xmlb.annotations.Attribute("templateType")
    var templateType: CommitTemplateType = CommitTemplateType.SIMPLE
        get() = field
        set(value) { field = value }
    
    /** 输入长度处理策略：true=分批处理，false=智能截断 */
    @com.intellij.util.xmlb.annotations.Attribute("useBatchProcessing")
    var useBatchProcessing: Boolean = true
        get() = field
        set(value) { field = value }
    
    /** 分批处理时每批次的最大文件数量 */
    @com.intellij.util.xmlb.annotations.Attribute("batchSize")
    var batchSize: Int = 5
        get() = field
        set(value) { field = value }
    
    /** 分批处理时单个文件的最大字符数 */
    @com.intellij.util.xmlb.annotations.Attribute("maxFileContentLength")
    var maxFileContentLength: Int = 10000
        get() = field
        set(value) { field = value }
    
    /** 智能截断时的最大总字符数 */
    @com.intellij.util.xmlb.annotations.Attribute("maxTotalContentLength")
    var maxTotalContentLength: Int = 100000
        get() = field
        set(value) { field = value }

    // 默认构造函数
    constructor()

    // 复制构造函数
    constructor(
        autoCommitEnabled: Boolean = false,
        autoPushEnabled: Boolean = false,
        singleFileTemplate: String = SIMPLE_TEMPLATE,
        summaryTemplate: String = SUMMARY_TEMPLATE,
        useBatchProcessing: Boolean = true,
        batchSize: Int = 5,
        maxFileContentLength: Int = 10000,
        maxTotalContentLength: Int = 100000
    ) {
        this.autoCommitEnabled = autoCommitEnabled
        this.autoPushEnabled = autoPushEnabled
        this.singleFileTemplate = singleFileTemplate
        this.summaryTemplate = summaryTemplate
        this.useBatchProcessing = useBatchProcessing
        this.batchSize = batchSize
        this.maxFileContentLength = maxFileContentLength
        this.maxTotalContentLength = maxTotalContentLength
    }

    // 复制方法
    fun copy(
        autoCommitEnabled: Boolean = this.autoCommitEnabled,
        autoPushEnabled: Boolean = this.autoPushEnabled,
        singleFileTemplate: String = this.singleFileTemplate,
        summaryTemplate: String = this.summaryTemplate,
        useBatchProcessing: Boolean = this.useBatchProcessing,
        batchSize: Int = this.batchSize,
        maxFileContentLength: Int = this.maxFileContentLength,
        maxTotalContentLength: Int = this.maxTotalContentLength
    ): CommitSettings {
        return CommitSettings(
            autoCommitEnabled,
            autoPushEnabled,
            singleFileTemplate,
            summaryTemplate,
            useBatchProcessing,
            batchSize,
            maxFileContentLength,
            maxTotalContentLength
        )
    }
    companion object {

        
        /**
         * 简洁模板常量 - 生成简短的提交信息
         */
        const val SIMPLE_TEMPLATE = """根据以下代码变更生成简洁的Git提交信息：

变更文件：{{changedFiles}}
文件差异：{{fileDiffs}}

要求：
1. 格式：<type>: <description>
2. type类型：feat/fix/docs/style/refactor/test/chore
3. description：简洁描述变更内容（30字符以内）
4. 使用中文，直接输出提交信息，无需其他说明

示例：
feat: 添加用户登录功能
fix: 修复数据保存异常
refactor: 优化代码结构"""

        /**
         * 汇总模板常量 - 用于将多个批次的提交信息汇总成最终提交信息
         */
        const val SUMMARY_TEMPLATE = """你是一个专业的代码提交信息生成助手。现在需要你将多个批次的提交信息汇总成一个简洁、清晰的最终提交信息。

以下是各个批次的提交信息：
{{batchCommitMessages}}

要求：
1. 格式：<type>(<scope>): <subject>
2. type类型：feat/fix/docs/style/refactor/test/chore
3. scope：主要影响的模块或功能
4. subject：简洁描述整体变更内容（50字符以内）
5. 如果涉及多个类型，选择最主要的类型
6. 如果涉及多个模块，选择最主要的模块作为scope
7. 使用中文，直接输出最终的提交信息，无需其他说明

示例：
feat(user): 完善用户管理功能
fix(api): 修复接口相关问题
refactor(core): 优化核心业务逻辑"""

        /**
         * 默认模板常量 - 向后兼容
         */
        const val DEFAULT_TEMPLATE = SIMPLE_TEMPLATE
        
        /**
         * 获取默认模板
         */
        fun getDefaultTemplate(): String {
            return DEFAULT_TEMPLATE
        }
        
        /**
         * 根据模板类型获取模板内容
         */
        fun getTemplateByType(type: CommitTemplateType): String {
            return when (type) {
                CommitTemplateType.SIMPLE -> SIMPLE_TEMPLATE
            }
        }
        
        /**
         * 创建默认设置
         */
        fun createDefault(): CommitSettings {
            return CommitSettings()
        }
        

        

        
        /**
         * 从Map创建设置
         */
        fun fromMap(map: Map<String, Any>): CommitSettings {
            return CommitSettings(
                autoCommitEnabled = map["autoCommitEnabled"] as? Boolean ?: false,
                autoPushEnabled = map["autoPushEnabled"] as? Boolean ?: false,
                singleFileTemplate = map["singleFileTemplate"] as? String ?: SIMPLE_TEMPLATE,
                summaryTemplate = map["summaryTemplate"] as? String ?: SUMMARY_TEMPLATE,
                useBatchProcessing = map["useBatchProcessing"] as? Boolean ?: true,
                batchSize = map["batchSize"] as? Int ?: 5,
                maxFileContentLength = map["maxFileContentLength"] as? Int ?: 10000,
                maxTotalContentLength = map["maxTotalContentLength"] as? Int ?: 100000
            )
        }
    }
    
    /**
     * 转换为Map
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "autoCommitEnabled" to autoCommitEnabled,
            "autoPushEnabled" to autoPushEnabled,
            "singleFileTemplate" to singleFileTemplate,
            "summaryTemplate" to summaryTemplate,
            "useBatchProcessing" to useBatchProcessing,
            "batchSize" to batchSize,
            "maxFileContentLength" to maxFileContentLength,
            "maxTotalContentLength" to maxTotalContentLength
        )
    }
}