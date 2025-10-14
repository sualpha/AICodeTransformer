package cn.suso.aicodetransformer.ui

import cn.suso.aicodetransformer.service.*
import cn.suso.aicodetransformer.model.CacheStats
import cn.suso.aicodetransformer.model.PerformanceOptimizationConfig
import cn.suso.aicodetransformer.model.PerformanceStats
import cn.suso.aicodetransformer.model.OptimizationSuggestion
import cn.suso.aicodetransformer.service.impl.PerformanceOptimizationServiceImpl
import cn.suso.aicodetransformer.utils.PerformanceOptimizer
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.ActionEvent
import java.text.DecimalFormat
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 * 性能优化面板
 */
class PerformanceOptimizationPanel(private val project: Project) : DialogWrapper(project) {
    
    private val optimizationService: PerformanceOptimizationServiceImpl = service()
    private val performanceMonitorService: PerformanceMonitorService = service()
    private val cacheService: CacheService = service()
    private val optimizer = PerformanceOptimizer.getInstance(project)
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // UI组件
    private lateinit var performanceStatsPanel: JPanel
    private lateinit var optimizationSuggestionsPanel: JPanel
    private lateinit var cacheStatsPanel: JPanel
    private lateinit var quickOptimizeButton: JButton
    private lateinit var refreshButton: JButton
    
    // 统计标签
    private lateinit var totalRequestsLabel: JLabel
    private lateinit var successRateLabel: JLabel
    private lateinit var avgResponseTimeLabel: JLabel
    private lateinit var cacheHitRateLabel: JLabel
    private lateinit var qpsLabel: JLabel
    private lateinit var healthScoreLabel: JLabel
    
    // 缓存统计标签
    private lateinit var cacheEntriesLabel: JLabel
    private lateinit var cacheHitsLabel: JLabel
    private lateinit var cacheMissesLabel: JLabel
    private lateinit var cacheEvictionsLabel: JLabel
    
    init {
        title = "AI代码转换器 - 性能优化"
        init()
        
        // 启动定期刷新
        startPeriodicRefresh()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(800, 600)
            border = JBUI.Borders.empty(10)
        }
        
        // 创建顶部按钮面板
        val buttonPanel = createButtonPanel()
        mainPanel.add(buttonPanel, BorderLayout.NORTH)
        
        // 创建主内容面板
        val contentPanel = JBPanel<JBPanel<*>>(GridLayout(2, 2, 10, 10))
        
        // 性能统计面板
        performanceStatsPanel = createPerformanceStatsPanel()
        contentPanel.add(performanceStatsPanel)
        
        // 缓存统计面板
        cacheStatsPanel = createCacheStatsPanel()
        contentPanel.add(cacheStatsPanel)
        
        // 优化建议面板
        optimizationSuggestionsPanel = createOptimizationSuggestionsPanel()
        contentPanel.add(optimizationSuggestionsPanel)
        
        // 配置面板
        val configPanel = createConfigPanel()
        contentPanel.add(configPanel)
        
        mainPanel.add(contentPanel, BorderLayout.CENTER)
        
        // 初始化数据
        refreshData()
        
        return mainPanel
    }
    
    private fun createButtonPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT))
        
        quickOptimizeButton = JButton("一键优化").apply {
            addActionListener { performQuickOptimization() }
            background = Color(0x4CAF50)
            foreground = Color.WHITE
            isOpaque = true
            border = JBUI.Borders.empty(8, 16)
        }
        
        refreshButton = JButton("刷新数据").apply {
            addActionListener { refreshData() }
        }
        
        val warmupButton = JButton("缓存预热").apply {
            addActionListener { performCacheWarmup() }
        }
        
        panel.add(quickOptimizeButton)
        panel.add(refreshButton)
        panel.add(warmupButton)
        
        return panel
    }
    
    private fun createPerformanceStatsPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
            border = TitledBorder("性能统计")
        }
        
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(5)
            anchor = GridBagConstraints.WEST
        }
        
        // 创建统计标签
        totalRequestsLabel = JBLabel("总请求数: --")
        successRateLabel = JBLabel("成功率: --%")
        avgResponseTimeLabel = JBLabel("平均响应时间: --ms")
        cacheHitRateLabel = JBLabel("缓存命中率: --%")
        qpsLabel = JBLabel("QPS: --")
        healthScoreLabel = JBLabel("健康度: --%").apply {
            font = font.deriveFont(Font.BOLD, 14f)
        }
        
        // 添加到面板
        val labels = listOf(
            totalRequestsLabel, successRateLabel, avgResponseTimeLabel,
            cacheHitRateLabel, qpsLabel, healthScoreLabel
        )
        
        labels.forEachIndexed { index, label ->
            gbc.gridx = 0
            gbc.gridy = index
            panel.add(label, gbc)
        }
        
        return panel
    }
    
    private fun createCacheStatsPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
            border = TitledBorder("缓存统计")
        }
        
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(5)
            anchor = GridBagConstraints.WEST
        }
        
        cacheEntriesLabel = JBLabel("缓存条目: --")
        cacheHitsLabel = JBLabel("缓存命中: --")
        cacheMissesLabel = JBLabel("缓存未命中: --")
        cacheEvictionsLabel = JBLabel("缓存驱逐: --")
        
        val labels = listOf(cacheEntriesLabel, cacheHitsLabel, cacheMissesLabel, cacheEvictionsLabel)
        
        labels.forEachIndexed { index, label ->
            gbc.gridx = 0
            gbc.gridy = index
            panel.add(label, gbc)
        }
        
        return panel
    }
    
    private fun createOptimizationSuggestionsPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = TitledBorder("优化建议")
        }
        
        val suggestionsArea = JTextArea().apply {
            isEditable = false
            background = panel.background
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
        
        val scrollPane = JBScrollPane(suggestionsArea).apply {
            preferredSize = Dimension(300, 200)
        }
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 异步加载优化建议
        scope.launch {
            try {
                val suggestions = withContext(Dispatchers.IO) {
                    optimizationService.getOptimizationSuggestions()
                }
                
                val text = buildString {
                    suggestions.take(5).forEach { suggestion: OptimizationSuggestion ->
                        appendLine("• ${suggestion.title}")
                        appendLine("  影响: ${suggestion.impact}, 难度: ${suggestion.difficulty}")
                        appendLine("  ${suggestion.description}")
                        appendLine()
                    }
                }
                
                suggestionsArea.text = text.ifEmpty { "暂无优化建议" }
            } catch (e: Exception) {
                suggestionsArea.text = "加载优化建议失败: ${e.message}"
            }
        }
        
        return panel
    }
    
    private fun createConfigPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
            border = TitledBorder("配置管理")
        }
        
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(5)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }
        
        // 应用推荐配置按钮
        val applyRecommendedButton = JButton("应用推荐配置").apply {
            addActionListener { applyRecommendedConfig() }
        }
        
        // 重置配置按钮
        val resetConfigButton = JButton("重置为默认").apply {
            addActionListener { resetToDefaultConfig() }
        }
        
        // 清理缓存按钮
        val clearCacheButton = JButton("清理过期缓存").apply {
            addActionListener { clearExpiredCache() }
        }
        
        gbc.gridy = 0
        panel.add(applyRecommendedButton, gbc)
        
        gbc.gridy = 1
        panel.add(resetConfigButton, gbc)
        
        gbc.gridy = 2
        panel.add(clearCacheButton, gbc)
        
        return panel
    }
    
    private fun refreshData() {
        scope.launch {
            try {
                // 获取性能统计
                val performanceStats = withContext(Dispatchers.IO) {
                    performanceMonitorService.getPerformanceStats()
                }
                
                // 获取缓存统计
                val cacheStats = withContext(Dispatchers.IO) {
                    cacheService.getCacheStats()
                }
                
                // 获取诊断报告
                val diagnostic = withContext(Dispatchers.IO) {
                    optimizer.getDiagnosticReport()
                }
                
                // 更新UI
                updatePerformanceStats(performanceStats)
                updateCacheStats(cacheStats)
                updateHealthScore(diagnostic.overallHealth)
                
            } catch (e: Exception) {
                showError("刷新数据失败: ${e.message}")
            }
        }
    }
    
    private fun updatePerformanceStats(stats: PerformanceStats) {
        val df = DecimalFormat("#.##")
        
        totalRequestsLabel.text = "总请求数: ${stats.totalRequests}"
        successRateLabel.text = "成功率: ${df.format(stats.successRate * 100)}%"
        avgResponseTimeLabel.text = "平均响应时间: ${stats.averageResponseTime.toLong()}ms"
        cacheHitRateLabel.text = "缓存命中率: ${df.format(stats.cacheHitRate * 100)}%"
        qpsLabel.text = "QPS: ${df.format(stats.qps)}"
    }
    
    private fun updateCacheStats(stats: CacheStats) {
        cacheEntriesLabel.text = "缓存条目: ${stats.totalEntries}"
        cacheHitsLabel.text = "缓存命中: ${stats.hitCount}"
        cacheMissesLabel.text = "缓存未命中: ${stats.missCount}"
        cacheEvictionsLabel.text = "缓存驱逐: ${stats.evictionCount}"
    }
    
    private fun updateHealthScore(health: Int) {
        healthScoreLabel.text = "健康度: $health%"
        
        // 根据健康度设置颜色
        healthScoreLabel.foreground = when {
            health >= 80 -> Color(0x4CAF50) // 绿色
            health >= 60 -> Color(0xFF9800) // 橙色
            else -> Color(0xF44336) // 红色
        }
    }
    
    private fun performQuickOptimization() {
        quickOptimizeButton.isEnabled = false
        quickOptimizeButton.text = "优化中..."
        
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    optimizer.quickOptimize()
                }
                
                // 等待一段时间后刷新数据
                delay(2000)
                refreshData()
                
                showSuccess("性能优化完成！")
            } catch (e: Exception) {
                showError("优化失败: ${e.message}")
            } finally {
                quickOptimizeButton.isEnabled = true
                quickOptimizeButton.text = "一键优化"
            }
        }
    }
    
    private fun performCacheWarmup() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    optimizer.warmupCache()
                }
                showSuccess("缓存预热完成！")
            } catch (e: Exception) {
                showError("缓存预热失败: ${e.message}")
            }
        }
    }
    
    private fun applyRecommendedConfig() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    optimizationService.applyRecommendedConfig()
                }
                showSuccess("推荐配置已应用！")
                refreshData()
            } catch (e: Exception) {
                showError("应用配置失败: ${e.message}")
            }
        }
    }
    
    private fun resetToDefaultConfig() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 重置为默认配置
                    val defaultConfig = PerformanceOptimizationConfig()
                    optimizationService.updateConfig(defaultConfig)
                }
                showSuccess("配置已重置为默认值！")
                refreshData()
            } catch (e: Exception) {
                showError("重置配置失败: ${e.message}")
            }
        }
    }
    
    private fun clearExpiredCache() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    cacheService.clearExpiredCache()
                }
                showSuccess("过期缓存已清理！")
                refreshData()
            } catch (e: Exception) {
                showError("清理缓存失败: ${e.message}")
            }
        }
    }
    
    private fun startPeriodicRefresh() {
        scope.launch {
            while (true) {
                delay(30000) // 每30秒刷新一次
                try {
                    refreshData()
                } catch (e: Exception) {
                    // 忽略定期刷新的错误
                }
            }
        }
    }
    
    private fun showSuccess(message: String) {
        JOptionPane.showMessageDialog(
            contentPane,
            message,
            "成功",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
    
    private fun showError(message: String) {
        JOptionPane.showMessageDialog(
            contentPane,
            message,
            "错误",
            JOptionPane.ERROR_MESSAGE
        )
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }
    
    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}