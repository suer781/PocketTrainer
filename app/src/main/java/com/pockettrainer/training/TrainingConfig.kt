package com.pockettrainer.training

/**
 * 训练超参数配置
 */
data class TrainingConfig(
    // ── 基础参数 ──
    val epochs: Int = 1,
    val batchSize: Int = 1,
    val learningRate: Float = 2e-5f,

    // ── LoRA 参数 ──
    val loraRank: Int = 8,
    val loraAlpha: Float = 16f,
    val loraDropout: Float = 0.05f,

    // ── 优化器参数 ──
    val warmupRatio: Float = 0.1f,
    val weightDecay: Float = 0.01f,
    val maxGradNorm: Float = 1.0f,
    val gradAccumSteps: Int = 4,

    // ── 数据参数 ──
    val maxSeqLen: Int = 128,
    val valSplit: Float = 0.1f,
    val preprocessing: String = "none",

    // ── 学习率调度 ──
    val schedulerType: String = "cosine",

    // ── 早停 ──
    val earlyStopping: Boolean = false,
    val earlyStoppingPatience: Int = 3,
    val earlyStoppingMinDelta: Float = 0.001f,

    // ── 检查点 ──
    val resumeFromCheckpoint: String = "",
    val saveTotalLimit: Int = 3,

    // ── 系统参数 ──
    val nThreads: Int = 4,
    val saveSteps: Int = 500,
    val seed: Int = 42
)

// ══════════════════════════════════════════════
// 参数元数据：类型安全，UI 根据 type 渲染不同控件
// ══════════════════════════════════════════════

enum class ParamType { INT, FLOAT, BOOL, DROPDOWN, TEXT }

data class ParamMeta(
    val key: String,
    val label: String,
    val description: String,
    val hint: String,
    val range: String,
    val category: String,
    val type: ParamType = ParamType.INT,
    val options: List<String> = emptyList()  // DROPDOWN 类型的选项
)

object TrainingParamRegistry {
    val params = listOf(

        // ── 基础参数 ──
        ParamMeta("epochs", "训练轮数 (Epochs)",
            "完整遍历一次数据集为 1 个 Epoch。轮数越多学习越充分，但过多会过拟合。小数据集建议 1-3 轮，大数据集 1 轮即可。",
            "1", "1 ~ 10", "基础参数", ParamType.INT),

        ParamMeta("batchSize", "批大小 (Batch Size)",
            "每次前向传播使用的样本数。增大可提高训练稳定性，但会线性增加显存/内存占用。端侧设备建议 1-2。",
            "1", "1 ~ 8", "基础参数", ParamType.INT),

        ParamMeta("learningRate", "学习率 (Learning Rate)",
            "控制每次梯度更新的步长。过大会震荡发散，过小收敛极慢。LoRA 微调推荐 1e-5 ~ 5e-5。",
            "2e-5", "1e-6 ~ 1e-4", "基础参数", ParamType.FLOAT),

        // ── LoRA 参数 ──
        ParamMeta("loraRank", "LoRA 秩 (Rank)",
            "低秩分解的维度。秩越高可训练参数越多、表达能力越强，但计算量和内存也越大。简单任务 4-8，复杂任务 16-64。",
            "8", "4 ~ 64", "LoRA 参数", ParamType.INT),

        ParamMeta("loraAlpha", "LoRA 缩放因子 (Alpha)",
            "缩放 LoRA 更新幅度的系数，实际缩放 = alpha / rank。通常设为 rank 的 1-2 倍。值越大 LoRA 对原模型影响越强。",
            "16", "rank × 1~2", "LoRA 参数", ParamType.FLOAT),

        ParamMeta("loraDropout", "LoRA Dropout",
            "LoRA 层的随机失活概率，用于防止过拟合。数据量小时可适当增大（0.1-0.3），数据量大可设 0。",
            "0.05", "0 ~ 0.3", "LoRA 参数", ParamType.FLOAT),

        // ── 优化器 ──
        ParamMeta("warmupRatio", "预热比例 (Warmup Ratio)",
            "训练初期学习率从 0 线性升至设定值的步数占比。预热可避免初始梯度过大导致训练不稳定。建议 5%-10%。",
            "0.1", "0 ~ 0.3", "优化器", ParamType.FLOAT),

        ParamMeta("weightDecay", "权重衰减 (Weight Decay)",
            "L2 正则化系数，对大权重施加惩罚以防止过拟合。推荐 0.01，设为 0 则关闭正则化。",
            "0.01", "0 ~ 0.1", "优化器", ParamType.FLOAT),

        ParamMeta("maxGradNorm", "梯度裁剪 (Max Grad Norm)",
            "梯度的最大 L2 范数，超过则等比缩放。防止梯度爆炸，对训练稳定性至关重要。1.0 是常用默认值。",
            "1.0", "0.5 ~ 5.0", "优化器", ParamType.FLOAT),

        ParamMeta("gradAccumSteps", "梯度累积步数",
            "在不增加内存的前提下模拟更大 batch size。实际 batch = batchSize × gradAccumSteps。端侧内存紧张时增大此值。",
            "4", "1 ~ 32", "优化器", ParamType.INT),

        // ── 数据 ──
        ParamMeta("maxSeqLen", "最大序列长度",
            "每个训练样本的最大 token 数。越长可捕获更多上下文，但内存消耗平方级增长。端侧建议 64-256。",
            "128", "32 ~ 512", "数据", ParamType.INT),

        ParamMeta("valSplit", "验证集比例",
            "从训练数据中切出多少比例作为验证集，用于监控过拟合和触发早停。设 0 则不切分。",
            "0.1", "0 ~ 0.3", "数据", ParamType.FLOAT),

        ParamMeta("preprocessing", "数据预处理",
            "none: 不处理原文；clean: 去除空白/控制字符/多余换行；dedup: 去重+清理。大数据集去重在端侧会慢。",
            "none", "三种模式", "数据", ParamType.DROPDOWN,
            options = listOf("none", "clean", "dedup")),

        // ── 学习率调度 ──
        ParamMeta("schedulerType", "学习率调度器",
            "控制学习率在整个训练过程中的变化方式。\n• linear: 线性衰减到 0，最稳定\n• cosine: 余弦退火，通常效果最好\n• constant: 全程不变，简单粗暴\n• constant_with_warmup: 预热后恒定",
            "cosine", "四种模式", "学习率调度", ParamType.DROPDOWN,
            options = listOf("linear", "cosine", "constant", "constant_with_warmup")),

        // ── 早停 ──
        ParamMeta("earlyStopping", "早停 (Early Stopping)",
            "开启后，当验证集 loss 连续 N 轮不再下降时自动停止训练，避免浪费时间和过拟合。",
            "关闭", "开/关", "早停", ParamType.BOOL),

        ParamMeta("earlyStoppingPatience", "早停耐心值 (Patience)",
            "连续多少个评估周期 loss 不下降就触发早停。越大越容忍波动，但训练时间更长。",
            "3", "2 ~ 10", "早停", ParamType.INT),

        ParamMeta("earlyStoppingMinDelta", "早停最小降幅",
            "loss 下降幅度低于此值视为「没有改善」。设太大会过早停，设太小等于没开。",
            "0.001", "0.0001 ~ 0.01", "早停", ParamType.FLOAT),

        // ── 检查点 ──
        ParamMeta("resumeFromCheckpoint", "续训检查点路径",
            "填入上次训练保存的检查点路径，从断点继续训练。留空则从头开始。",
            "留空=从头", "路径", "检查点", ParamType.TEXT),

        ParamMeta("saveTotalLimit", "检查点保留数",
            "最多保留几个检查点文件，超出自动删除最旧的。省存储空间，设 0 表示不限。",
            "3", "0 ~ 10", "检查点", ParamType.INT),

        // ── 系统 ──
        ParamMeta("nThreads", "CPU 线程数",
            "用于训练计算的线程数。建议设为设备大核数（通常 4），超过核心数不会更快反而可能更慢。",
            "4", "1 ~ 8", "系统", ParamType.INT),

        ParamMeta("saveSteps", "保存间隔 (Steps)",
            "每隔多少步保存一次检查点。设为 0 则只在训练结束时保存。频繁保存会占用存储空间。",
            "500", "0 ~ 2000", "系统", ParamType.INT),

        ParamMeta("seed", "随机种子 (Seed)",
            "固定随机数种子以确保实验可复现。相同种子 + 相同参数 = 相同结果。",
            "42", "任意整数", "系统", ParamType.INT)
    )
}
