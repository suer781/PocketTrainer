package com.pockettrainer.training

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ModelInfo(
    val path: String,
    val fileName: String,
    val fileSizeMb: Double
)

enum class TrainingState {
    IDLE, LOADING, READY, RUNNING, PAUSED, COMPLETED, ERROR
}

data class TrainingUiState(
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModel: ModelInfo? = null,
    val trainingState: TrainingState = TrainingState.IDLE,
    val errorMessage: String? = null,
    val currentEpoch: Int = 0,
    val totalEpochs: Int = 0,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val currentLoss: Float = 0f,
    val lossHistory: List<Float> = emptyList(),
    val elapsedMs: Long = 0,
    val estimatedRemainingMs: Long = 0,
    val progressPercent: Float = 0f,
    val outputPath: String = ""
) {
    val progressText: String
        get() = when (trainingState) {
            TrainingState.IDLE      -> "就绪"
            TrainingState.LOADING   -> "加载模型中..."
            TrainingState.READY     -> "模型已加载"
            TrainingState.RUNNING   -> "Epoch $currentEpoch/$totalEpochs | Step $currentStep/$totalSteps | Loss: %.4f".format(currentLoss)
            TrainingState.PAUSED    -> "已暂停 | Step $currentStep"
            TrainingState.COMPLETED -> "训练完成！"
            TrainingState.ERROR     -> "错误: $errorMessage"
        }

    val formattedElapsed: String
        get() {
            val s = elapsedMs / 1000
            return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        }

    val formattedRemaining: String
        get() {
            if (estimatedRemainingMs <= 0) return "--:--:--"
            val s = estimatedRemainingMs / 1000
            return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        }

    val formattedFileSize: String
        get() = selectedModel?.let { "%.1f MB".format(it.fileSizeMb) } ?: "未选择"

    // 趋势方向：-1 下降, 0 持平, 1 上升
    val lossTrendDirection: Int
        get() {
            if (lossHistory.size < 5) return 0
            val recent = lossHistory.takeLast(5)
            val avg1 = recent.take(2).average()
            val avg2 = recent.takeLast(2).average()
            val diff = avg2 - avg1
            return when {
                diff < -0.01 -> -1
                diff > 0.01  -> 1
                else -> 0
            }
        }

    val lossTrendIcon: String
        get() = when (lossTrendDirection) {
            -1   -> "📉"
            1    -> "📈"
            else -> "➡️"
        }

    // LoRA 消耗估算
    val loraMemoryMb: Double
        get() {
            if (lossHistory.isEmpty()) return 0.0
            val paramCount = 35_000_000L  // 估算
            return paramCount * 2L / 1_048_576.0
        }
}

class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()
    private var startTimeMs = 0L

    init {
        viewModelScope.launch { scanModels() }
    }

    /** 扫描 Download/PocketTrainer/models/ 下的 .safetensors 文件 */
    private fun scanModels() {
        val modelsDir = File(context.getExternalFilesDir(null), "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val models = modelsDir.listFiles()
            ?.filter { it.extension == "safetensors" }
            ?.map { f ->
                ModelInfo(
                    path = f.absolutePath,
                    fileName = f.name,
                    fileSizeMb = f.length() / (1024.0 * 1024.0)
                )
            }
            ?: emptyList()

        _uiState.update { it.copy(availableModels = models) }
    }

    fun selectModel(model: ModelInfo) {
        _uiState.update { it.copy(selectedModel = model, trainingState = TrainingState.IDLE) }
    }

    fun loadModel() {
        val model = _uiState.value.selectedModel ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(trainingState = TrainingState.LOADING, errorMessage = null) }
            try {
                val cfg = NativeTraining.nativeLoadConfig(model.path)
                if (cfg == 0L) throw Exception("无法读取模型配置")
                val ptr = NativeTraining.nativeLoadModel(model.path)
                if (ptr == 0L) throw Exception("模型加载失败")
                _uiState.update { it.copy(trainingState = TrainingState.READY) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(trainingState = TrainingState.ERROR, errorMessage = e.message)
                }
            }
        }
    }

    fun startTraining(
        datasetPath: String,
        epochs: Int = 1,
        batchSize: Int = 1,
        learningRate: Float = 2e-5f,
        loraRank: Int = 8,
        loraAlpha: Float = 16f,
        nThreads: Int = 4
    ) {
        // 注册回调
        NativeTraining.nativeSetCallback(object : TrainingCallback {
            override fun onProgress(epoch: Int, totalEpochs: Int, step: Int, totalSteps: Int, loss: Float) {
                viewModelScope.launch(Dispatchers.Main) {
                    val elapsed = System.currentTimeMillis() - startTimeMs
                    val progress = if (totalSteps > 0) step.toFloat() / totalSteps else 0f
                    val totalProgress = if (totalEpochs > 0) (epoch - 1 + progress) / totalEpochs else 0f
                    val remaining = if (totalProgress > 0.01f)
                        (elapsed * (1 - totalProgress) / totalProgress).toLong() else 0L

                    _uiState.update { state ->
                        state.copy(
                            trainingState = TrainingState.RUNNING,
                            currentEpoch = epoch,
                            totalEpochs = totalEpochs,
                            currentStep = step,
                            totalSteps = totalSteps,
                            currentLoss = loss,
                            lossHistory = state.lossHistory + loss,
                            elapsedMs = elapsed,
                            estimatedRemainingMs = remaining,
                            progressPercent = totalProgress
                        )
                    }
                }
            }

            override fun onComplete(outputPath: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            trainingState = TrainingState.COMPLETED,
                            outputPath = outputPath,
                            elapsedMs = System.currentTimeMillis() - startTimeMs
                        )
                    }
                }
            }

            override fun onError(message: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(trainingState = TrainingState.ERROR, errorMessage = message)
                    }
                }
            }
        })

        viewModelScope.launch(Dispatchers.IO) {
            startTimeMs = System.currentTimeMillis()
            _uiState.update { it.copy(trainingState = TrainingState.RUNNING, lossHistory = emptyList()) }

            val outputDir = File(context.getExternalFilesDir(null), "lora_output").apply { mkdirs() }

            NativeTraining.nativeStartTrainingAsync(
                datasetPath = datasetPath,
                outputPath = outputDir.absolutePath,
                epochs = epochs,
                batchSize = batchSize,
                learningRate = learningRate,
                loraRank = loraRank,
                loraAlpha = loraAlpha,
                nThreads = nThreads
            )
        }
    }

    fun pauseTraining() {
        NativeTraining.nativePauseTraining()
        _uiState.update { it.copy(trainingState = TrainingState.PAUSED) }
    }

    fun resumeTraining() {
        NativeTraining.nativeResumeTraining()
        _uiState.update { it.copy(trainingState = TrainingState.RUNNING) }
    }

    fun stopTraining() {
        NativeTraining.nativeStopTraining()
        _uiState.update {
            it.copy(
                trainingState = TrainingState.COMPLETED,
                elapsedMs = System.currentTimeMillis() - startTimeMs
            )
        }
    }

    fun exportModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val exportDir = File(context.getExternalFilesDir(null), "export").apply { mkdirs() }
            val outFile = File(exportDir, "lora_weights.safetensors")
            val ok = NativeTraining.nativeExportModel(outFile.absolutePath)
            _uiState.update {
                it.copy(
                    outputPath = if (ok) outFile.absolutePath else "导出失败",
                    errorMessage = if (!ok) "导出失败" else null
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        NativeTraining.nativeCleanup()
    }
}
