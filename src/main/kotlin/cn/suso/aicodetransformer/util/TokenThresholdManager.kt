package cn.suso.aicodetransformer.util

import cn.suso.aicodetransformer.model.ModelConfiguration
import cn.suso.aicodetransformer.model.FileChangeInfo


object TokenThresholdManager {
    
    /**
     * 响应预留比例 - 为AI响应预留的Token比例
     */
    private const val RESPONSE_RESERVE_RATIO = 0.1
    
    /**
     * 处理决策结果
     */
    data class ProcessingDecision(
        val needsBatching: Boolean,
        val maxTokens: Int,
        val availableTokens: Int,
        val totalTokens: Int,
        val templateTokens: Int,
        val reason: String,
        val batches: List<List<FileChangeInfo>>? = null
    )
    
    /**
     * 在commit生成提示词时进行唯一的阈值判断
     * 根据选择的文件和模型的maxToken来决定处理方式
     */
    fun decideProcessingStrategy(
        fileChanges: List<FileChangeInfo>,
        templateTokens: Int,
        modelConfig: ModelConfiguration,
        tokenEstimator: (FileChangeInfo) -> Int
    ): ProcessingDecision {
        val maxTokens = modelConfig.maxTokens
        val responseReserveTokens = (maxTokens * RESPONSE_RESERVE_RATIO).toInt()
        val availableTokens = maxTokens - responseReserveTokens - templateTokens
        
        // 计算所有选择文件的总Token数
        val totalTokens = fileChanges.sumOf { tokenEstimator(it) }
        
        val needsBatching = totalTokens > availableTokens
        
        val (reason, batches) = if (needsBatching) {
            // 需要分批处理
            val batchList = createBatches(fileChanges, availableTokens, tokenEstimator)
            val reason = "总Token数($totalTokens) > 可用Token数($availableTokens), 分为${batchList.size}个批次处理"
            Pair(reason, batchList)
        } else {
            // 无需分批处理
            val reason = "总Token数($totalTokens) <= 可用Token数($availableTokens), 单次处理"
            Pair(reason, null)
        }
        
        return ProcessingDecision(
            needsBatching = needsBatching,
            maxTokens = maxTokens,
            availableTokens = availableTokens,
            totalTokens = totalTokens,
            templateTokens = templateTokens,
            reason = reason,
            batches = batches
        )
    }
    
    /**
     * 创建批次
     */
    private fun createBatches(
        fileChanges: List<FileChangeInfo>,
        maxTokensPerBatch: Int,
        tokenEstimator: (FileChangeInfo) -> Int
    ): List<List<FileChangeInfo>> {
        val batches = mutableListOf<List<FileChangeInfo>>()
        val currentBatch = mutableListOf<FileChangeInfo>()
        var currentBatchTokens = 0
        
        for (fileChange in fileChanges) {
            val fileTokens = tokenEstimator(fileChange)
            
            // 如果单个文件就超过批次限制，单独成批
            if (fileTokens > maxTokensPerBatch) {
                if (currentBatch.isNotEmpty()) {
                    batches.add(currentBatch.toList())
                    currentBatch.clear()
                    currentBatchTokens = 0
                }
                batches.add(listOf(fileChange))
                continue
            }
            
            // 如果加入当前文件会超过批次限制，先保存当前批次
            if (currentBatchTokens + fileTokens > maxTokensPerBatch && currentBatch.isNotEmpty()) {
                batches.add(currentBatch.toList())
                currentBatch.clear()
                currentBatchTokens = 0
            }
            
            currentBatch.add(fileChange)
            currentBatchTokens += fileTokens
        }
        
        // 添加最后一个批次
        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch.toList())
        }
        
        return batches
    }
}