package com.pockettrainer.training

/**
 * JNI 桥接层 — 函数名严格匹配 training_jni.cpp
 */
object NativeTraining {
    init {
        System.loadLibrary("pocket_trainer")
    }

    /** 注册回调（C++ 通过它回传进度/完成/错误） */
    external fun nativeSetCallback(callback: TrainingCallback?)

    /** 从 safetensors 推断模型配置 */
    external fun nativeLoadConfig(path: String): Long

    /** 加载模型权重 + 注入 LoRA，返回模型指针 */
    external fun nativeLoadModel(path: String): Long

    /** 同步训练（阻塞当前线程） */
    external fun nativeStartTraining(
        datasetPath: String,
        outputPath: String,
        epochs: Int,
        batchSize: Int,
        learningRate: Float,
        loraRank: Int,
        loraAlpha: Float,
        nThreads: Int
    ): Boolean

    /** 异步训练（后台线程，通过回调通知进度） */
    external fun nativeStartTrainingAsync(
        datasetPath: String,
        outputPath: String,
        epochs: Int,
        batchSize: Int,
        learningRate: Float,
        loraRank: Int,
        loraAlpha: Float,
        nThreads: Int
    )

    external fun nativePauseTraining()
    external fun nativeResumeTraining()
    external fun nativeStopTraining()
    external fun nativeExportModel(outputPath: String): Boolean

    /** 导入 LoRA 权重文件到当前模型 */
    external fun nativeImportLora(loraPath: String): Boolean

    /** 合并 LoRA 到基座模型并保存完整模型 */
    external fun nativeMergeAndSave(mergedPath: String): Boolean

    external fun nativeCleanup()
}
