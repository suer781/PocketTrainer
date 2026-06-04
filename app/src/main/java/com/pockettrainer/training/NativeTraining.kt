package com.pockettrainer.training

/**
 * JNI 桥接层 — 函数名严格匹配 training_jni.cpp
 */
object NativeTraining {
    init {
        System.loadLibrary("pocket_trainer")
    }

    /** 注册回调 */
    external fun nativeSetCallback(callback: TrainingCallback?)

    /** 从 safetensors 推断模型配置 */
    external fun nativeLoadConfig(path: String): Long

    /** 加载模型权重 + 注入 LoRA */
    external fun nativeLoadModel(path: String): Long

    /** 异步训练（后台线程，通过回调通知进度） */
    external fun nativeStartTrainingAsync(
        datasetPath: String,
        outputPath: String,
        systemPrompt: String,
        epochs: Int,
        batchSize: Int,
        learningRate: Float,
        loraRank: Int,
        loraAlpha: Float,
        loraDropout: Float,
        warmupRatio: Float,
        weightDecay: Float,
        maxGradNorm: Float,
        gradAccumSteps: Int,
        maxSeqLen: Int,
        nThreads: Int,
        saveSteps: Int,
        seed: Int
    )

    external fun nativePauseTraining()
    external fun nativeResumeTraining()
    external fun nativeStopTraining()
    external fun nativeExportModel(outputPath: String): Boolean
    external fun nativeImportLora(loraPath: String): Boolean
    external fun nativeMergeAndSave(mergedPath: String): Boolean
    external fun nativeCleanup()
}
