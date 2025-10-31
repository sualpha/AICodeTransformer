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
        const val SIMPLE_TEMPLATE = """角色：专业的Git提交信息编写助手
任务：根据代码变更生成符合规范的提交信息

根据以下代码变更生成简洁的Git提交信息：

变更文件：{{changedFiles}}
文件差异：{{fileDiffs}}

提交信息格式要求：

<类型>: <描述> <标识>::<数字>
详细规范：
类型（必选）：
feat - 新功能
fix - 修复问题
docs - 文档更新
style - 代码格式调整
refactor - 重构代码
test - 测试相关
chore - 构建过程或辅助工具变动

描述（必选）：
简明扼要描述变更内容
使用中文或英文
长度建议20字以内

标识::数字（强制格式）：
格式：任意标识::数字
标识规则：支持中文、英文、数字、下划线、横线、点、空格
标识长度：1-50个字符
数字：至少1位数字
示例：需求::102321、BUG-123::456、ENG_word::789、用户登录::12345

输出示例：
feat: 添加用户登录功能 登录模块::102321
fix: 修复支付页面崩溃问题 BUG-支付::456
docs: 更新API文档说明 文档更新::789
refactor: 优化数据库查询性能 性能优化::101112

重要提醒：
必须包含 标识::数字 格式
标识可以是需求编号、任务ID、模块名称等
数字固定1111
模块功能需要细分开
请根据代码变更内容，生成符合以上规范的提交信息。"""

        /**
         * 汇总模板常量 - 用于将多个批次的提交信息汇总成最终提交信息
         */
        const val SUMMARY_TEMPLATE = """你是一个专业的代码提交信息生成助手。现在需要你将多个批次的提交信息汇总成符合规范的最终提交信息。

以下是各个批次的提交信息：
{{batchCommitMessages}}

提交信息格式要求：

<类型>: <描述> <标识>::<数字>
详细规范：
类型（必选）：
feat - 新功能
fix - 修复问题
docs - 文档更新
style - 代码格式调整
refactor - 重构代码
test - 测试相关
chore - 构建过程或辅助工具变动

描述（必选）：
简明扼要描述变更内容
使用中文或英文
长度建议20字以内

标识::数字（强制格式）：
格式：任意标识::数字
标识规则：支持中文、英文、数字、下划线、横线、点、空格
标识长度：1-50个字符
数字：至少1位数字
示例：需求::102321、BUG-123::456、ENG_word::789、用户登录::12345

输出示例：
feat: 添加用户登录功能 登录模块::102321
fix: 修复支付页面崩溃问题 BUG-支付::456
docs: 更新API文档说明 文档更新::789
refactor: 优化数据库查询性能 性能优化::101112

汇总规则：
若批次信息类型一致且属于同一功能模块，可合并为一条
若类型或功能不同，应分别输出多条提交信息
数字固定1111"""

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