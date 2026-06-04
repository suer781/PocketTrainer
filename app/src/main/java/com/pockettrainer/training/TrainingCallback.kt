package com.pockettrainer.training

/**
 * 训练回调接口 — 由 C++ JNI 调用
 */
interface TrainingCallback {
    fun onProgress(epoch: Int, totalEpochs: Int, step: Int, totalSteps: Int, loss: Float)
    fun onComplete(outputPath: String)
    fun onError(message: String)
}
