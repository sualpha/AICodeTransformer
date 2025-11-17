package cn.suso.aicodetransformer.model

import cn.suso.aicodetransformer.i18n.I18n
import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.ResourceBundle

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
    var singleFileTemplate: String = defaultSimpleTemplate(),
    
    /** 汇总提示词模板 */
    @com.intellij.util.xmlb.annotations.Tag("summaryTemplate")
    var summaryTemplate: String = defaultSummaryTemplate(),
    
    /** Commit消息模板 (已废弃，保留向后兼容) */
    @Deprecated("使用 singleFileTemplate 和 summaryTemplate 替代")
    @com.intellij.util.xmlb.annotations.Tag("commitTemplate")
    var commitTemplate: String = defaultTemplate(),
    
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
         * 获取当前语言下的单文件提交模板
         */
        fun defaultSimpleTemplate(): String = I18n.t("commit.template.simple")

        /**
         * 获取当前语言下的汇总提交模板
         */
        fun defaultSummaryTemplate(): String = I18n.t("commit.template.summary")

        /**
         * 向后兼容的默认模板访问器
         */
        fun defaultTemplate(): String = defaultSimpleTemplate()

        /**
         * 向后兼容的只读属性访问器
         */
        val SIMPLE_TEMPLATE: String
            get() = defaultSimpleTemplate()

        val SUMMARY_TEMPLATE: String
            get() = defaultSummaryTemplate()

        val DEFAULT_TEMPLATE: String
            get() = defaultTemplate()

        private val SUPPORTED_LOCALES: List<Locale> = listOf(
            Locale.SIMPLIFIED_CHINESE,
            Locale.US
        )

        private fun loadTemplate(key: String, locale: Locale): String? = runCatching {
            ResourceBundle.getBundle("i18n.messages", locale).getString(key)
        }.getOrNull()

        internal fun normalizeLineEndings(value: String): String =
            value.replace("\r\n", "\n").replace("\r", "\n")

        private fun matchesTemplateDefault(value: String, key: String): Boolean {
            val normalizedInput = normalizeLineEndings(value)
            if (normalizedInput.isEmpty()) {
                return false
            }
            return SUPPORTED_LOCALES.any { locale ->
                loadTemplate(key, locale)?.let { template ->
                    val normalizedTemplate = normalizeLineEndings(template)
                    if (normalizedTemplate == normalizedInput) {
                        true
                    } else {
                        val firstLine = normalizedTemplate.substringBefore("\n")
                        firstLine.isNotEmpty() && firstLine == normalizedInput.trimEnd()
                    }
                } ?: false
            }
        }

        fun matchesSimpleTemplateDefault(value: String): Boolean =
            matchesTemplateDefault(value, "commit.template.simple")

        fun matchesSummaryTemplateDefault(value: String): Boolean =
            matchesTemplateDefault(value, "commit.template.summary")

        fun matchesGeneralTemplateDefault(value: String): Boolean =
            matchesTemplateDefault(value, "commit.template.simple")

        @Suppress("DEPRECATION")
        fun normalizeTemplates(settings: CommitSettings): CommitSettings {
            val normalized = settings.copy()
            if (matchesSimpleTemplateDefault(normalized.singleFileTemplate)) {
                normalized.singleFileTemplate = SIMPLE_TEMPLATE
            }
            if (matchesSummaryTemplateDefault(normalized.summaryTemplate)) {
                normalized.summaryTemplate = SUMMARY_TEMPLATE
            }
            if (matchesGeneralTemplateDefault(normalized.commitTemplate)) {
                normalized.commitTemplate = DEFAULT_TEMPLATE
            }
            return normalized
        }

        /**
         * 创建默认设置
         */
        fun createDefault(): CommitSettings {
            return CommitSettings()
        }
    }
}