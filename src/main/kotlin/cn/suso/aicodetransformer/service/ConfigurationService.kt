package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.LoggingConfigState
import cn.suso.aicodetransformer.model.GlobalSettings
import cn.suso.aicodetransformer.model.CommitSettings

/**
 * 配置管理服务接口
 * 负责管理AI模型的配置信息
 */
interface ConfigurationService {
    
    /**
     * 获取所有模型配置
     * @return 模型配置列表
     */
    fun getModelConfigurations(): List<ModelConfiguration>
    
    /**
     * 根据ID获取模型配置
     * @param id 配置ID
     * @return 模型配置，如果不存在返回null
     */
    fun getModelConfiguration(id: String): ModelConfiguration?
    
    /**
     * 保存模型配置
     * @param config 模型配置
     */
    fun saveModelConfiguration(config: ModelConfiguration)
    
    /**
     * 删除模型配置
     * @param id 配置ID
     * @return 是否删除成功
     */
    fun deleteModelConfiguration(id: String): Boolean
    
    /**
     * 获取启用的模型配置
     * @return 启用的模型配置列表
     */
    fun getEnabledModelConfigurations(): List<ModelConfiguration>
    
    /**
     * 获取默认模型配置
     * @return 默认模型配置，如果没有返回null
     */
    fun getDefaultModelConfiguration(): ModelConfiguration?
    
    /**
     * 设置默认模型配置
     * @param id 配置ID
     */
    fun setDefaultModelConfiguration(id: String)
    
    /**
     * 验证模型配置是否有效
     * @param config 模型配置
     * @return 验证结果，成功返回null，失败返回错误信息
     */
    fun validateModelConfiguration(config: ModelConfiguration): String?
    
    /**
     * 获取API密钥
     * @param configId 配置ID
     * @return API密钥，如果不存在返回null
     */
    fun getApiKey(configId: String): String?
    
    /**
     * 保存API密钥
     * @param configId 配置ID
     * @param apiKey API密钥
     */
    fun saveApiKey(configId: String, apiKey: String)
    
    /**
     * 删除API密钥
     * @param configId 配置ID
     */
    fun deleteApiKey(configId: String)
    
    /**
     * 导出配置（不包含API密钥）
     * @return JSON格式的配置字符串
     */
    fun exportConfigurations(): String
    
    /**
     * 导入配置
     * @param configJson JSON格式的配置字符串
     * @return 导入成功的配置数量
     */
    fun importConfigurations(configJson: String): Int
    
    /**
     * 重置为默认配置
     */
    fun resetToDefaults()
    
    /**
     * 添加配置变更监听器
     * @param listener 监听器
     */
    fun addConfigurationChangeListener(listener: ConfigurationChangeListener)
    
    /**
     * 移除配置变更监听器
     * @param listener 监听器
     */
    fun removeConfigurationChangeListener(listener: ConfigurationChangeListener)
    
    /**
     * 获取日志配置
     * @return 日志配置
     */
    fun getLoggingConfig(): LoggingConfigState
    
    /**
     * 保存日志配置
     * @param config 日志配置
     */
    fun saveLoggingConfig(config: LoggingConfigState)
    
    /**
     * 获取全局设置
     * @return 全局设置
     */
    fun getGlobalSettings(): GlobalSettings
    
    /**
     * 更新全局设置
     * @param settings 全局设置
     */
    fun updateGlobalSettings(settings: GlobalSettings)
    
    /**
     * 获取Commit设置
     * @return Commit设置
     */
    fun getCommitSettings(): CommitSettings
    
    /**
     * 保存Commit设置
     * @param settings Commit设置
     */
    fun saveCommitSettings(settings: CommitSettings)
}

/**
 * 配置变更监听器接口
 */
interface ConfigurationChangeListener {
    /**
     * 配置添加时调用
     * @param config 新增的配置
     */
    fun onConfigurationAdded(config: ModelConfiguration) {}
    
    /**
     * 配置更新时调用
     * @param oldConfig 旧配置
     * @param newConfig 新配置
     */
    fun onConfigurationUpdated(oldConfig: ModelConfiguration, newConfig: ModelConfiguration) {}
    
    /**
     * 配置删除时调用
     * @param config 被删除的配置
     */
    fun onConfigurationDeleted(config: ModelConfiguration) {}
    
    /**
     * 默认配置变更时调用
     * @param config 新的默认配置
     */
    fun onDefaultConfigurationChanged(config: ModelConfiguration?) {}
}