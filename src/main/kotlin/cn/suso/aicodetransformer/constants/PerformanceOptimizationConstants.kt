package cn.suso.aicodetransformer.constants

/**
 * 优化类型
 */
enum class OptimizationType {
    HTTP_CONNECTION,
    CACHE_STRATEGY,
    REQUEST_BATCHING,
    ASYNC_PROCESSING,
    MEMORY_OPTIMIZATION,
    THREAD_POOL_TUNING;

    companion object {
        fun fromPerformanceConstant(type: PerformanceConstants.OptimizationType): OptimizationType {
            return when (type) {
                PerformanceConstants.OptimizationType.HTTP_CONNECTION -> HTTP_CONNECTION
                PerformanceConstants.OptimizationType.CACHE_STRATEGY -> CACHE_STRATEGY
                PerformanceConstants.OptimizationType.REQUEST_BATCHING -> REQUEST_BATCHING
                PerformanceConstants.OptimizationType.ASYNC_PROCESSING -> ASYNC_PROCESSING
                PerformanceConstants.OptimizationType.MEMORY_OPTIMIZATION -> MEMORY_OPTIMIZATION
                PerformanceConstants.OptimizationType.THREAD_POOL_TUNING -> THREAD_POOL_TUNING
            }
        }
    }
}

/**
 * 影响级别
 */
enum class ImpactLevel {
    LOW,     // 5-15% 性能提升
    MEDIUM,  // 15-30% 性能提升
    HIGH;    // 30%+ 性能提升

    companion object {
        fun fromPerformanceConstant(level: PerformanceConstants.ImpactLevel): ImpactLevel {
            return when (level) {
                PerformanceConstants.ImpactLevel.LOW -> LOW
                PerformanceConstants.ImpactLevel.MEDIUM -> MEDIUM
                PerformanceConstants.ImpactLevel.HIGH -> HIGH
                PerformanceConstants.ImpactLevel.CRITICAL -> HIGH // 将CRITICAL映射到HIGH
            }
        }
    }
}

/**
 * 难度级别
 */
enum class DifficultyLevel {
    EASY,    // 配置调整
    MEDIUM,  // 代码修改
    HARD;    // 架构调整

    companion object {
        fun fromPerformanceConstant(level: PerformanceConstants.DifficultyLevel): DifficultyLevel {
            return when (level) {
                PerformanceConstants.DifficultyLevel.EASY -> EASY
                PerformanceConstants.DifficultyLevel.MEDIUM -> MEDIUM
                PerformanceConstants.DifficultyLevel.HARD -> HARD
                PerformanceConstants.DifficultyLevel.EXPERT -> HARD // 将EXPERT映射到HARD
            }
        }
    }
}