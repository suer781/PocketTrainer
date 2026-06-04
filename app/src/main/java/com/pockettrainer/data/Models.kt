package com.pockettrainer.data

data class ModelInfo(val name: String, val repoId: String, val size: String, val description: String, val rating: String)

data class DatasetInfo(val name: String, val path: String, val format: DatasetFormat, val sampleCount: Int, val sizeBytes: Long)

enum class DatasetFormat { CHAT_JSONL, ALPACA_JSONL, SHAREGPT_JSONL, PLAIN_TEXT, CSV }

data class TrainConfig(
    val modelPath: String = "", val datasetPath: String = "",
    val datasetFormat: DatasetFormat = DatasetFormat.CHAT_JSONL,
    val loraRank: Int = 8, val loraAlpha: Float = 16.0f,
    val learningRate: Float = 2e-4f, val epochs: Int = 3,
    val batchSize: Int = 4, val seqLen: Int = 128,
    val gradAccumSteps: Int = 4, val maxGradNorm: Float = 1.0f, val nThreads: Int = 4
)