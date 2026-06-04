package com.pockettrainer.training

class NativeTraining {
    companion object { init { System.loadLibrary("pocket_trainer") } }

    interface TrainingCallback {
        fun onProgress(epoch: Int, totalEpochs: Int, step: Int, loss: Float)
        fun onComplete(success: Boolean, message: String)
        fun onError(error: String)
    }

    external fun nativeInitialize(modelPath: String, nThreads: Int): Boolean
    external fun nativeSetupLoRA(rank: Int, alpha: Float): Boolean
    external fun nativeLoadDataset(dataPath: String, seqLen: Int, batchSize: Int): Boolean
    external fun nativeTrain(epochs: Int, gradAccumSteps: Int, learningRate: Float, maxGradNorm: Float): Boolean
    external fun nativeSave(outputPath: String): Boolean
    external fun nativeStop()
    external fun nativePause()
    external fun nativeResume()
    external fun nativeRelease()

    fun initialize(modelPath: String, nThreads: Int = 4) = nativeInitialize(modelPath, nThreads)
    fun setupLoRA(rank: Int = 8, alpha: Float = 16f) = nativeSetupLoRA(rank, alpha)
    fun prepareData(dataPath: String, seqLen: Int = 128, batchSize: Int = 4) = nativeLoadDataset(dataPath, seqLen, batchSize)
    fun release() = nativeRelease()
}

data class TrainingConfig(
    val epochs: Int = 3,
    val batchSize: Int = 4,
    val learningRate: Float = 2e-4f,
    val gradientAccumSteps: Int = 4,
    val maxGradNorm: Float = 1.0f,
    val loraRank: Int = 8,
    val loraAlpha: Float = 16.0f,
    val seqLen: Int = 128
)