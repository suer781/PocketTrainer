package com.pockettrainer.training

data class TrainingPreset(
    val name: String,
    val emoji: String,
    val description: String,
    val config: TrainingConfig
)

object TrainingPresets {
    val presets = listOf(
        TrainingPreset(
            name = "快速测试",
            emoji = "⚡",
            description = "最低配置，验证流程是否跑通。1 轮、小 batch、低 rank，不做验证。",
            config = TrainingConfig(
                epochs = 1, batchSize = 1, learningRate = 2e-5f,
                loraRank = 4, loraAlpha = 8f, loraDropout = 0.0f,
                warmupRatio = 0.05f, weightDecay = 0.0f, maxGradNorm = 1.0f,
                gradAccumSteps = 1, maxSeqLen = 64, valSplit = 0f,
                preprocessing = "none", schedulerType = "constant",
                earlyStopping = false, nThreads = 4, saveSteps = 0, saveTotalLimit = 1, seed = 42
            )
        ),
        TrainingPreset(
            name = "标准微调",
            emoji = "🎯",
            description = "平衡速度与效果的推荐配置。余弦调度 + 早停，适合大多数场景。",
            config = TrainingConfig(
                epochs = 3, batchSize = 2, learningRate = 2e-5f,
                loraRank = 8, loraAlpha = 16f, loraDropout = 0.05f,
                warmupRatio = 0.1f, weightDecay = 0.01f, maxGradNorm = 1.0f,
                gradAccumSteps = 4, maxSeqLen = 128, valSplit = 0.1f,
                preprocessing = "clean", schedulerType = "cosine",
                earlyStopping = true, earlyStoppingPatience = 3, earlyStoppingMinDelta = 0.001f,
                nThreads = 4, saveSteps = 500, saveTotalLimit = 3, seed = 42
            )
        ),
        TrainingPreset(
            name = "精细微调",
            emoji = "🔬",
            description = "高 rank + 低学习率，追求最佳效果。大 rank 大 alpha，带数据去重和早停。",
            config = TrainingConfig(
                epochs = 5, batchSize = 1, learningRate = 1e-5f,
                loraRank = 32, loraAlpha = 64f, loraDropout = 0.1f,
                warmupRatio = 0.15f, weightDecay = 0.02f, maxGradNorm = 1.0f,
                gradAccumSteps = 8, maxSeqLen = 256, valSplit = 0.15f,
                preprocessing = "dedup", schedulerType = "cosine",
                earlyStopping = true, earlyStoppingPatience = 5, earlyStoppingMinDelta = 0.0005f,
                nThreads = 4, saveSteps = 200, saveTotalLimit = 5, seed = 42
            )
        ),
        TrainingPreset(
            name = "风格迁移",
            emoji = "🎨",
            description = "低 rank 快速学习语气/风格，不过拟合原文内容。线性衰减，不做验证。",
            config = TrainingConfig(
                epochs = 2, batchSize = 2, learningRate = 3e-5f,
                loraRank = 4, loraAlpha = 8f, loraDropout = 0.0f,
                warmupRatio = 0.05f, weightDecay = 0.0f, maxGradNorm = 1.0f,
                gradAccumSteps = 2, maxSeqLen = 128, valSplit = 0f,
                preprocessing = "none", schedulerType = "linear",
                earlyStopping = false, nThreads = 4, saveSteps = 0, seed = 42
            )
        ),
        TrainingPreset(
            name = "角色扮演",
            emoji = "🎭",
            description = "高 alpha 强注入角色特征，适合 character card。余弦调度带早停。",
            config = TrainingConfig(
                epochs = 3, batchSize = 1, learningRate = 5e-5f,
                loraRank = 16, loraAlpha = 32f, loraDropout = 0.05f,
                warmupRatio = 0.1f, weightDecay = 0.0f, maxGradNorm = 1.0f,
                gradAccumSteps = 4, maxSeqLen = 256, valSplit = 0.1f,
                preprocessing = "clean", schedulerType = "cosine",
                earlyStopping = true, earlyStoppingPatience = 3, earlyStoppingMinDelta = 0.001f,
                nThreads = 4, saveSteps = 300, seed = 42
            )
        )
    )
}
