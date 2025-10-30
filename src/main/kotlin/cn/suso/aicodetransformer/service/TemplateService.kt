package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.*

/**
 * 模板服务接口
 * 用于管理AI代码转换模板
 */
interface TemplateService {
    
    /**
     * 根据ID获取模板
     * @param id 模板ID
     * @return 模板，如果不存在则返回null
     */
    fun getTemplateById(id: String): Template?
    
    /**
     * 增加模板使用次数
     * @param id 模板ID
     */
    fun incrementUsageCount(id: String)
    
    /**
     * 渲染模板
     * @param templateId 模板ID
     * @param variableValues 变量值映射
     * @return 渲染结果
     */
    fun renderTemplate(templateId: String, variableValues: Map<String, String>): TemplateRenderResult
    
    // ========== Commit模板专门方法 ==========
    
    /**
     * 获取所有commit模板
     * @return commit模板列表
     */
    fun getCommitTemplates(): List<Template>
    
    /**
     * 获取默认commit模板
     * @return 默认commit模板，如果不存在则返回null
     */
    fun getDefaultCommitTemplate(): Template?
    
    /**
     * 设置默认commit模板
     * @param templateId 模板ID
     * @return 是否设置成功
     */
    fun setDefaultCommitTemplate(templateId: String): Boolean
    
    /**
     * 创建commit模板
     * @param name 模板名称
     * @param content 模板内容
     * @param description 模板描述
     * @return 创建的模板
     */
    fun createCommitTemplate(name: String, content: String, description: String = ""): Template
    
    /**
     * 获取commit模板变量
     * @return 可用的变量列表及其描述
     */
    fun getCommitTemplateVariables(): Map<String, String>
}