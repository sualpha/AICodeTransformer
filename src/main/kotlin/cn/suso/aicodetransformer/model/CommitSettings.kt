package cn.suso.aicodetransformer.model

import kotlinx.serialization.Serializable

/**
 * 提交信息模板类型
 */
enum class CommitTemplateType(val description: String) {
    /** 简洁模式 - 生成简短的提交信息 */
    SIMPLE("生成简洁的Git提交信息（30字符以内）")
}

/**
 * Commit设置数据模型 - 包含单个模板管理
 */
@Serializable
@com.intellij.util.xmlb.annotations.Tag("CommitSettings")
data class CommitSettings(
    /** 是否启用自动提交 */
    @com.intellij.util.xmlb.annotations.Attribute("autoCommitEnabled")
    var autoCommitEnabled: Boolean = false,
    
    /** 是否启用自动推送 */
    @com.intellij.util.xmlb.annotations.Attribute("autoPushEnabled")
    var autoPushEnabled: Boolean = false,
    
    /** 单个文件提示词模板 */
    @com.intellij.util.xmlb.annotations.Tag("singleFileTemplate")
    var singleFileTemplate: String = SIMPLE_TEMPLATE,
    
    /** 汇总提示词模板 */
    @com.intellij.util.xmlb.annotations.Tag("summaryTemplate")
    var summaryTemplate: String = SUMMARY_TEMPLATE,
    
    /** Commit消息模板 (已废弃，保留向后兼容) */
    @Deprecated("使用 singleFileTemplate 和 summaryTemplate 替代")
    @com.intellij.util.xmlb.annotations.Tag("commitTemplate")
    var commitTemplate: String = DEFAULT_TEMPLATE,
    
    /** 提交信息模板类型 (已废弃，保留向后兼容) */
    @Deprecated("使用 singleFileTemplate 和 summaryTemplate 替代")
    @com.intellij.util.xmlb.annotations.Attribute("templateType")
    var templateType: CommitTemplateType = CommitTemplateType.SIMPLE,
    
    /** 输入长度处理策略：true=分批处理，false=智能截断 */
    @com.intellij.util.xmlb.annotations.Attribute("useBatchProcessing")
    var useBatchProcessing: Boolean = true,
    
    /** 分批处理时每批次的最大文件数量 */
    @com.intellij.util.xmlb.annotations.Attribute("batchSize")
    var batchSize: Int = 5,
    
    /** 分批处理时单个文件的最大字符数 */
    @com.intellij.util.xmlb.annotations.Attribute("maxFileContentLength")
    var maxFileContentLength: Int = 10000,
    
    /** 智能截断时的最大总字符数 */
    @com.intellij.util.xmlb.annotations.Attribute("maxTotalContentLength")
    var maxTotalContentLength: Int = 100000
) {

    // 数据类会自动生成构造函数、equals、hashCode、toString和copy方法
    
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
长度建议30字以内

标识::数字（强制格式）：
格式：任意标识::数字
标识规则：支持中文、英文、数字、下划线、横线、点、空格
数字：至少5位数字

输出示例：
feat: 添加用户登录功能 登录模块::102321
fix: 修复支付页面崩溃问题 BUG-支付::456
docs: 更新API文档说明 文档更新::789
refactor: 优化数据库查询性能 性能优化::101112

重要提醒：
必须包含 标识::数字 格式
标识可以是需求编号、任务ID、模块名称等
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
长度建议30字以内

标识::数字（强制格式）：
格式：任意标识::数字
标识规则：支持中文、英文、数字、下划线、横线、点、空格
数字：至少5位数字

输出示例：
feat: 添加用户登录功能 登录模块::102321
fix: 修复支付页面崩溃问题 BUG-支付::456
docs: 更新API文档说明 文档更新::789
refactor: 优化数据库查询性能 性能优化::101112

汇总规则：
若批次信息类型一致且属于同一功能模块，可合并为一条
若类型或功能不同，应分别输出多条提交信息
"""

        /**
         * 默认模板常量 - 向后兼容
         */
        const val DEFAULT_TEMPLATE = SIMPLE_TEMPLATE

        
        /**
         * 创建默认设置
         */
        fun createDefault(): CommitSettings {
            return CommitSettings()
        }
    }
}