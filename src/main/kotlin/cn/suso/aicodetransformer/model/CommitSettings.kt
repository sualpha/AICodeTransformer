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
 * Commit设置数据模型 - 包含单个模板管理
 */
@Serializable
data class CommitSettings(
    /** 是否启用自动提交 */
    val autoCommitEnabled: Boolean = false,
    
    /** 是否启用自动推送 */
    val autoPushEnabled: Boolean = false,
    
    /** Commit消息模板 */
    val commitTemplate: String = DEFAULT_TEMPLATE
) {
    companion object {
        /**
         * 内置变量列表
         */
        val BUILT_IN_VARIABLES = listOf(
            CommitVariable(
                name = "{CHANGES}",
                description = "代码变更信息",
                example = "文件: src/main/App.kt\n变更类型: MODIFIED\n差异详情: ..."
            ),
            CommitVariable(
                name = "{FILES}",
                description = "变更文件列表",
                example = "src/main/App.kt, src/test/AppTest.kt"
            ),
            CommitVariable(
                name = "{CHANGE_TYPE}",
                description = "主要变更类型",
                example = "ADDED, MODIFIED, DELETED"
            ),
            CommitVariable(
                name = "{FILE_COUNT}",
                description = "变更文件数量",
                example = "3"
            ),
            CommitVariable(
                name = "{PROJECT_NAME}",
                description = "项目名称",
                example = "AICodeTransformer"
            )
        )
        
        /**
         * 默认模板常量 - 作为 AI 提示词使用
         */
        const val DEFAULT_TEMPLATE = """请根据以下代码变更信息生成一个规范的Git提交消息。

要求：
1. 使用约定式提交格式：type(scope): description
2. type 可以是：feat, fix, docs, style, refactor, test, chore
3. scope 是可选的，表示影响的模块或组件
4. description 要简洁明了，使用中文，不超过50个字符
5. 如果变更复杂，可以添加详细的body说明
6. 直接输出commit消息，不要包含其他解释

变更信息：
{CHANGES}

变更文件：{FILES}
文件数量：{FILE_COUNT}
项目：{PROJECT_NAME}"""
        
        /**
         * 获取默认模板
         */
        fun getDefaultTemplate(): String {
            return DEFAULT_TEMPLATE
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
                commitTemplate = map["commitTemplate"] as? String ?: getDefaultTemplate()
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
            "commitTemplate" to commitTemplate
        )
    }
}