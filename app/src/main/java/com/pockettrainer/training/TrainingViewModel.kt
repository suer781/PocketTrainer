package com.pockettrainer.training

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ModelInfo(
    val path: String,
    val fileName: String,
    val fileSizeMb: Double
)

enum class TrainingState {
    IDLE, LOADING, READY, RUNNING, PAUSED, COMPLETED, ERROR
}

enum class DataSourceMode {
    TEXT,   // 直接输入文本
    FILE    // 导入文件
}

data class TrainingUiState(
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModel: ModelInfo? = null,
    // 数据源：多选，可以同时用文本+文件
    val directText: String = "",           // 用户直接输入的文本
    val datasetPath: String = "",          // 导入的文件路径
    val datasetName: String = "",          // 导入的文件名
    val dataSourceMode: DataSourceMode = DataSourceMode.TEXT,  // 当前输入模式
    // 系统提示词
    val systemPrompt: String = "",
    // 训练状态
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

    val canStartTraining: Boolean
        get() = trainingState == TrainingState.READY &&
                (directText.isNotEmpty() || datasetPath.isNotEmpty())

    val lossTrendDirection: Int
        get() {
            if (lossHistory.size < 5) return 0
            val recent = lossHistory.takeLast(5)
            val avg1 = recent.take(2).average()
            val avg2 = recent.takeLast(2).average()
            return when {
                avg2 - avg1 < -0.01 -> -1
                avg2 - avg1 > 0.01  -> 1
                else -> 0
            }
        }

    val lossTrendIcon: String
        get() = when (lossTrendDirection) { -1 -> "📉"; 1 -> "📈"; else -> "➡️" }
}

class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()
    private var startTimeMs = 0L

    init {
        viewModelScope.launch { scanModels() }
    }

    // ── 模型 ──

    private fun scanModels() {
        val modelsDir = File(context.getExternalFilesDir(null), "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val models = modelsDir.listFiles()
            ?.filter { it.extension == "safetensors" }
            ?.map { f ->
                ModelInfo(f.absolutePath, f.name, f.length() / (1024.0 * 1024.0))
            } ?: emptyList()

        _uiState.update { it.copy(availableModels = models) }
    }

    fun selectModel(model: ModelInfo) {
        _uiState.update { it.copy(selectedModel = model, trainingState = TrainingState.IDLE) }
    }

    /** 从外部 URI 导入模型文件（复制到 models/ 目录） */
    fun importModelFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelsDir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
                // 从 URI 推断文件名
                val fileName = getFileNameFromUri(uri) ?: "model_${System.currentTimeMillis()}.safetensors"
                val outFile = File(modelsDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw Exception("无法读取文件")

                scanModels()
                // 自动选中刚导入的
                val imported = _uiState.value.availableModels.find { it.path == outFile.absolutePath }
                imported?.let { selectModel(it) }

                _uiState.update { it.copy(outputPath = "已导入: $fileName") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "导入失败: ${e.message}", trainingState = TrainingState.ERROR) }
            }
        }
    }

    /** 加载选中的基座模型 */
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
                _uiState.update { it.copy(trainingState = TrainingState.ERROR, errorMessage = e.message) }
            }
        }
    }

    // ── 数据源 ──

    fun setDataSourceMode(mode: DataSourceMode) {
        _uiState.update { it.copy(dataSourceMode = mode) }
    }

    fun setDirectText(text: String) {
        _uiState.update { it.copy(directText = text) }
    }

    /** 从外部 URI 导入数据集文件 */
    fun importDatasetFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dataDir = File(context.getExternalFilesDir(null), "datasets").apply { mkdirs() }
                val fileName = getFileNameFromUri(uri) ?: "dataset_${System.currentTimeMillis()}.txt"
                val outFile = File(dataDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw Exception("无法读取文件")

                _uiState.update {
                    it.copy(
                        datasetPath = outFile.absolutePath,
                        datasetName = fileName
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "数据集导入失败: ${e.message}", trainingState = TrainingState.ERROR) }
            }
        }
    }

    // ── 系统提示词 ──

    fun setSystemPrompt(prompt: String) {
        _uiState.update { it.copy(systemPrompt = prompt) }
    }

    // ── 训练 ──

    fun startTraining(
        epochs: Int = 1,
        batchSize: Int = 1,
        learningRate: Float = 2e-5f,
        loraRank: Int = 8,
        loraAlpha: Float = 16f,
        nThreads: Int = 4
    ) {
        val state = _uiState.value
        // 确定数据集路径：文件模式直接用路径，文本模式先写临时文件
        val dsPath = when {
            state.datasetPath.isNotEmpty() -> state.datasetPath
            state.directText.isNotEmpty() -> {
                val dataDir = File(context.getExternalFilesDir(null), "datasets").apply { mkdirs() }
                val tmpFile = File(dataDir, "direct_input_${System.currentTimeMillis()}.txt")
                tmpFile.writeText(state.directText)
                tmpFile.absolutePath
            }
            else -> return
        }

        NativeTraining.nativeSetCallback(object : TrainingCallback {
            override fun onProgress(epoch: Int, totalEpochs: Int, step: Int, totalSteps: Int, loss: Float) {
                viewModelScope.launch(Dispatchers.Main) {
                    val elapsed = System.currentTimeMillis() - startTimeMs
                    val progress = if (totalSteps > 0) step.toFloat() / totalSteps else 0f
                    val totalProgress = if (totalEpochs > 0) (epoch - 1 + progress) / totalEpochs else 0f
                    val remaining = if (totalProgress > 0.01f)
                        (elapsed * (1 - totalProgress) / totalProgress).toLong() else 0L

                    _uiState.update { s ->
                        s.copy(
                            trainingState = TrainingState.RUNNING,
                            currentEpoch = epoch, totalEpochs = totalEpochs,
                            currentStep = step, totalSteps = totalSteps,
                            currentLoss = loss, lossHistory = s.lossHistory + loss,
                            elapsedMs = elapsed, estimatedRemainingMs = remaining,
                            progressPercent = totalProgress
                        )
                    }
                }
            }

            override fun onComplete(outputPath: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(trainingState = TrainingState.COMPLETED,
                            outputPath = outputPath,
                            elapsedMs = System.currentTimeMillis() - startTimeMs)
                    }
                }
            }

            override fun onError(message: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.update { it.copy(trainingState = TrainingState.ERROR, errorMessage = message) }
                }
            }
        })

        viewModelScope.launch(Dispatchers.IO) {
            startTimeMs = System.currentTimeMillis()
            _uiState.update { it.copy(trainingState = TrainingState.RUNNING, lossHistory = emptyList()) }

            val outputDir = File(context.getExternalFilesDir(null), "lora_output").apply { mkdirs() }

            NativeTraining.nativeStartTrainingAsync(
                datasetPath = dsPath,
                outputPath = outputDir.absolutePath,
                systemPrompt = _uiState.value.systemPrompt,
                epochs = epochs, batchSize = batchSize,
                learningRate = learningRate,
                loraRank = loraRank, loraAlpha = loraAlpha,
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
            it.copy(trainingState = TrainingState.COMPLETED,
                elapsedMs = System.currentTimeMillis() - startTimeMs)
        }
    }

    fun exportModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val exportDir = File(context.getExternalFilesDir(null), "export").apply { mkdirs() }
            val outFile = File(exportDir, "lora_weights.safetensors")
            val ok = NativeTraining.nativeExportModel(outFile.absolutePath)
            _uiState.update {
                it.copy(outputPath = if (ok) outFile.absolutePath else "导出失败",
                    errorMessage = if (!ok) "导出失败" else null)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        NativeTraining.nativeCleanup()
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
            }
        }
        return name
    }
}
