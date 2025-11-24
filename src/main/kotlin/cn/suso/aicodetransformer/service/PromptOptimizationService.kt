package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.model.PromptOptimizationRequest
import cn.suso.aicodetransformer.model.PromptOptimizationResult

/**
 * 向 AI 请求生成或优化 Prompt 模板的服务。
 */
interface PromptOptimizationService {
    /**
     * 根据用户输入与内置变量信息，生成高质量的模板内容。
     */
    fun optimizePrompt(request: PromptOptimizationRequest): PromptOptimizationResult
}
