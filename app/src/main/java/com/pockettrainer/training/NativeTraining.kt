package com.pockettrainer.training

/**
 * JNI 桥接层 — 函数名严格匹配 training_jni.cpp
 */
object NativeTraining {
    init { System.loadLibrary("pocket_trainer") }

    external fun nativeSetCallback(callback: TrainingCallback?)
    external fun nativeLoadConfig(path: String): Long
    external fun nativeLoadModel(path: String): Long
    external fun nativeInitTokenizer(vocabPath: String, mergesPath: String): Boolean

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
        valSplit: Float,
        preprocessing: Int,           // 0=none, 1=clean, 2=dedup
        schedulerType: Int,           // 0=linear, 1=cosine, 2=constant, 3=constant_with_warmup
        earlyStopping: Int,           // 0=off, 1=on
        earlyStoppingPatience: Int,
        earlyStoppingMinDelta: Float,
        resumeFromCheckpoint: String,
        saveTotalLimit: Int,
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
