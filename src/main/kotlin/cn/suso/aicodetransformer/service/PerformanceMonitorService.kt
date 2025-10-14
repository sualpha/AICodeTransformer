package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.ExecutionResult
import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.PerformanceStats
import cn.suso.aicodetransformer.model.RealTimeMetrics
import cn.suso.aicodetransformer.model.MonitorConfig

/**
 * 性能监控服务接口
 * 用于监控和统计API调用性能
 */
interface PerformanceMonitorService {
    
    /**
     * 记录API调用开始
     * @param requestId 请求ID
     * @param config 模型配置
     * @param prompt 提示内容
     * @return 性能跟踪器
     */
    fun startTracking(requestId: String, config: ModelConfiguration, prompt: String): PerformanceTracker
    
    /**
     * 记录API调用完成
     * @param tracker 性能跟踪器
     * @param result 执行结果
     */
    fun endTracking(tracker: PerformanceTracker, result: ExecutionResult)
    
    /**
     * 获取性能统计信息
     * @param modelConfigId 模型配置ID，为null时返回全部统计
     * @return 性能统计
     */
    fun getPerformanceStats(modelConfigId: String? = null): PerformanceStats
    
    /**
     * 获取实时性能指标
     * @return 实时性能指标
     */
    fun getRealTimeMetrics(): RealTimeMetrics
    
    /**
     * 清除性能数据
     * @param olderThanMs 清除指定时间之前的数据（毫秒）
     */
    fun clearOldData(olderThanMs: Long = 86400000) // 默认清除1天前的数据
    
    /**
     * 设置性能监控配置
     * @param config 监控配置
     */
    fun setMonitorConfig(config: MonitorConfig)
}

/**
 * 性能跟踪器
 */
interface PerformanceTracker {
    val requestId: String
    val startTime: Long
    
    /**
     * 记录缓存命中
     */
    fun recordCacheHit()
    
    /**
     * 记录限流
     */
    fun recordRateLimit()
    
    /**
     * 记录网络延迟
     * @param latencyMs 网络延迟（毫秒）
     */
    fun recordNetworkLatency(latencyMs: Long)
    
    /**
     * 添加自定义指标
     * @param key 指标名称
     * @param value 指标值
     */
    fun addMetric(key: String, value: Any)
}