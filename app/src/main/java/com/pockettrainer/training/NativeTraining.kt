package com.pockettrainer.training

class NativeTraining {
    companion object { init { System.loadLibrary("pocket_trainer") } }

    interface TrainingCallback {
        fun onProgress(epoch: Int, totalEpochs: Int, step: Int, loss: Float)
        fun onComplete(success: Boolean, message: String)
        fun onError(error: String)
    }

    external fun nativeInitialize(modelDirPath: String, numThreads: Int): Boolean
    external fun nativeSetupLoRA(rank: Int, alpha: Float): Boolean
    external fun nativeLoadDataset(dataPath: String, seqLen: Int, batchSize: Int): Boolean
    external fun nativeTrain(epochs: Int, gradAccumSteps: Int, learningRate: Float, maxGradNorm: Float, callback: TrainingCallback): Boolean
    external fun nativeSave(outputPath: String): Boolean
    external fun nativeStop()
    external fun nativePause()
    external fun nativeResume()
    external fun nativeRelease()
    external fun nativeGetLoRAParamCount(): Int
    external fun nativeGetModelInfo(): String
    external fun nativeGetDatasetSize(): Int
    external fun nativeIsTraining(): Boolean

    fun initialize(modelDirPath: String, numThreads: Int = 0) = nativeInitialize(modelDirPath, numThreads)
    fun setupLoRA(rank: Int = 8, alpha: Float = 16.0f) = nativeSetupLoRA(rank, alpha)
    fun prepareData(dataPath: String, seqLen: Int = 128, batchSize: Int = 4) = nativeLoadDataset(dataPath, seqLen, batchSize)
    fun train(config: TrainingConfig, callback: TrainingCallback) = nativeTrain(config.epochs, config.gradientAccumSteps, config.learningRate, config.maxGradNorm, callback)
    fun save(outputPath: String) = nativeSave(outputPath)
    fun stop() = nativeStop()
    fun pause() = nativePause()
    fun resume() = nativeResume()
    fun release() = nativeRelease()
    fun getLoRAParamCount() = nativeGetLoRAParamCount()
    fun getModelInfo() = nativeGetModelInfo()
    fun getDatasetSize() = nativeGetDatasetSize()
    fun isTraining() = nativeIsTraining()
}