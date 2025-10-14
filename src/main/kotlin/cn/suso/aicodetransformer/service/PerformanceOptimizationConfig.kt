package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.PerformanceOptimizationConfig
import cn.suso.aicodetransformer.model.OptimizationSuggestion

/**
 * 性能优化服务接口
 */
interface PerformanceOptimizationService {
    
    /**
     * 获取当前优化配置
     */
    fun getConfig(): PerformanceOptimizationConfig
    
    /**
     * 更新优化配置
     */
    fun updateConfig(config: PerformanceOptimizationConfig)
    
    /**
     * 获取性能优化建议
     */
    fun getOptimizationSuggestions(): List<OptimizationSuggestion>
    
    /**
     * 应用推荐的优化配置
     */
    fun applyRecommendedConfig()
}