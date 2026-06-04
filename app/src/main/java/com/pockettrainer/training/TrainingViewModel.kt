package com.pockettrainer.training

import android.content.Context

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class ModelInfo(
    val path: String,
    val fileName: String,
    val fileSizeMb: Double
)

data class ModelMetadata(
    val paramCount: String = "",      // "0.5B" / "1.8B"
    val hiddenSize: Int = 0,
    val numLayers: Int = 0,
    val numHeads: Int = 0,
    val vocabSize: Int = 0,
    val architecture: String = ""
)

data class DatasetStats(
    val sampleCount: Int = 0,
    val charCount: Int = 0,
    val estimatedTokens: Int = 0,
    val estimatedMinutes: Float = 0f
)

enum class TrainingState {
    IDLE, LOADING, READY, RUNNING, PAUSED, COMPLETED, ERROR
}

enum class DataSourceMode {
    TEXT, FILE
}

data class TrainingUiState(
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModel: ModelInfo? = null,
    val modelMetadata: ModelMetadata? = null,

    // 数据源
    val directText: String = "",
    val datasetPath: String = "",
    val datasetName: String = "",
    val dataSourceMode: DataSourceMode = DataSourceMode.TEXT,
    val datasetStats: DatasetStats? = null,

    // 系统提示词
    val systemPrompt: String = "",

    // 训练配置
    val config: TrainingConfig = TrainingConfig(),
    val activePreset: String = "",   // 当前选中的预设名
    val showAdvanced: Boolean = false,

    // 训练状态
    val trainingState: TrainingState = TrainingState.IDLE,
    val errorMessage: String? = null,
    val currentEpoch: Int = 0,
    val totalEpochs: Int = 0,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val currentLoss: Float = 0f,
    val bestLoss: Float = Float.MAX_VALUE,
    val lossHistory: List<Float> = emptyList(),
    val elapsedMs: Long = 0,
    val estimatedRemainingMs: Long = 0,
    val progressPercent: Float = 0f,
    val outputPath: String = "",
    val logExportPath: String = "",
    val error: String? = null
) {
    val progressText: String
        get() = when (trainingState) {
            TrainingState.IDLE      -> "就绪"
            TrainingState.LOADING   -> "加载模型中..."
            TrainingState.READY     -> {
                val info = modelMetadata
                if (info != null) "已加载: ${info.architecture} ${info.paramCount}"
                else "模型已加载"
            }
            TrainingState.RUNNING   -> "Epoch $currentEpoch/$totalEpochs | Step $currentStep/$totalSteps | Loss: %.4f | Best: %.4f".format(currentLoss, bestLoss)
            TrainingState.PAUSED    -> "已暂停 | Step $currentStep"
            TrainingState.COMPLETED -> "训练完成！Best Loss: %.4f".format(bestLoss)
            TrainingState.ERROR     -> "错误: $errorMessage"
        }

    val formattedElapsed: String
        get() { val s = elapsedMs / 1000; return "%d:%02d:%02d".format(s/3600, (s%3600)/60, s%60) }

    val formattedRemaining: String
        get() {
            if (estimatedRemainingMs <= 0) return "--:--:--"
            val s = estimatedRemainingMs / 1000; return "%d:%02d:%02d".format(s/3600, (s%3600)/60, s%60)
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
            val avg1 = recent.take(2).average(); val avg2 = recent.takeLast(2).average()
            return when { avg2 - avg1 < -0.01 -> -1; avg2 - avg1 > 0.01 -> 1; else -> 0 }
        }

    val lossTrendIcon: String
        get() = when (lossTrendDirection) { -1 -> "📉"; 1 -> "📈"; else -> "➡️" }
}

class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()
    private var startTimeMs = 0L

    init { viewModelScope.launch { scanModels() } }

    // ── 模型 ──

    private fun scanModels() {
        val modelsDir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
        val models = modelsDir.listFiles()
            ?.filter { it.extension == "safetensors" }
            ?.map { ModelInfo(it.absolutePath, it.name, it.length() / (1024.0 * 1024.0)) }
            ?: emptyList()
        _uiState.update { it.copy(availableModels = models) }
    }

    fun selectModel(model: ModelInfo) {
        _uiState.update { it.copy(selectedModel = model, trainingState = TrainingState.IDLE, modelMetadata = null) }
    }

    fun importModelFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelsDir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
                val fileName = getFileNameFromUri(uri) ?: "model_${System.currentTimeMillis()}.safetensors"
                val outFile = File(modelsDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw Exception("无法读取文件")
                scanModels()
                val imported = _uiState.value.availableModels.find { it.path == outFile.absolutePath }
                imported?.let { selectModel(it) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "导入失败: ${e.message}", trainingState = TrainingState.ERROR) }
            }
        }
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

                // 从 safetensors 元数据读取模型信息
                val meta = extractModelMetadata(model.path)
                _uiState.update { it.copy(trainingState = TrainingState.READY, modelMetadata = meta) }
            } catch (e: Exception) {
                _uiState.update { it.copy(trainingState = TrainingState.ERROR, errorMessage = e.message) }
            }
        }
    }

    private fun extractModelMetadata(path: String): ModelMetadata {
        return try {
            // 尝试从文件名推断
            val name = File(path).name.lowercase()
            val paramCount = when {
                "0.5b" in name || "500m" in name -> "0.5B"
                "1b" in name || "1.0b" in name   -> "1B"
                "1.5b" in name || "1_5b" in name  -> "1.5B"
                "3b" in name || "3.0b" in name    -> "3B"
                "7b" in name || "7.0b" in name    -> "7B"
                else -> {
                    // 从文件大小粗略估算
                    val sizeMb = File(path).length() / (1024.0 * 1024.0)
                    when {
                        sizeMb < 500  -> "~0.5B"
                        sizeMb < 1200 -> "~1B"
                        sizeMb < 2000 -> "~1.5B"
                        sizeMb < 5000 -> "~3B"
                        else          -> "~7B+"
                    }
                }
            }
            val arch = when {
                "qwen" in name -> "Qwen"
                "deepseek" in name -> "DeepSeek"
                "llama" in name -> "LLaMA"
                "mistral" in name -> "Mistral"
                "phi" in name -> "Phi"
                "gemma" in name -> "Gemma"
                else -> "Unknown"
            }
            ModelMetadata(paramCount = paramCount, architecture = arch)
        } catch (_: Exception) { ModelMetadata() }
    }

    // ── 数据源 ──

    fun setDataSourceMode(mode: DataSourceMode) { _uiState.update { it.copy(dataSourceMode = mode) } }

    fun setDirectText(text: String) {
        _uiState.update { it.copy(directText = text, datasetStats = computeStats(text)) }
    }

    fun importDatasetFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dataDir = File(context.getExternalFilesDir(null), "datasets").apply { mkdirs() }
                val fileName = getFileNameFromUri(uri) ?: "dataset_${System.currentTimeMillis()}.txt"
                val outFile = File(dataDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw Exception("无法读取文件")
                val text = outFile.readText()
                _uiState.update {
                    it.copy(datasetPath = outFile.absolutePath, datasetName = fileName,
                        datasetStats = computeStats(text))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "数据集导入失败: ${e.message}", trainingState = TrainingState.ERROR) }
            }
        }
    }

    private fun computeStats(text: String): DatasetStats {
        val charCount = text.length
        // 按空行分段算样本数
        val samples = text.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.size.coerceAtLeast(1)
        val estimatedTokens = (charCount / 3.5).toInt()  // 中文约 3.5 字符/token
        // 粗估训练时间：每 1000 token 约 0.5 秒（端侧单线程）
        val estimatedMinutes = estimatedTokens * 0.5f / 60f * _uiState.value.config.epochs
        return DatasetStats(samples, charCount, estimatedTokens, estimatedMinutes)
    }

    // ── 预设 ──

    fun applyPreset(preset: TrainingPreset) {
        _uiState.update { it.copy(config = preset.config, activePreset = preset.name) }
    }

    // ── 系统提示词 ──

    fun setSystemPrompt(prompt: String) { _uiState.update { it.copy(systemPrompt = prompt) } }

    // ── 训练配置 ──

    fun toggleAdvanced() { _uiState.update { it.copy(showAdvanced = !it.showAdvanced) } }

    fun updateConfig(updater: (TrainingConfig) -> TrainingConfig) {
        _uiState.update { it.copy(config = updater(it.config), activePreset = "") }  // 手动改时清除预设高亮
    }

    // ── 训练 ──

    fun startTraining() {
        val state = _uiState.value
        val cfg = state.config
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
                    val remaining = if (totalProgress > 0.01f) (elapsed * (1 - totalProgress) / totalProgress).toLong() else 0L

                    _uiState.update { s ->
                        val newBest = minOf(s.bestLoss, loss)
                        s.copy(
                            trainingState = TrainingState.RUNNING,
                            currentEpoch = epoch, totalEpochs = totalEpochs,
                            currentStep = step, totalSteps = totalSteps,
                            currentLoss = loss, bestLoss = newBest,
                            lossHistory = s.lossHistory + loss,
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
            _uiState.update { it.copy(trainingState = TrainingState.RUNNING, lossHistory = emptyList(), bestLoss = Float.MAX_VALUE) }
            val outputDir = File(context.getExternalFilesDir(null), "lora_output").apply { mkdirs() }
            // 将字符串选项转为 JNI int 枚举
            val prepInt = when (cfg.preprocessing) { "clean" -> 1; "dedup" -> 2; else -> 0 }
            val schedInt = when (cfg.schedulerType) { "linear" -> 0; "cosine" -> 1; "constant" -> 2; "constant_with_warmup" -> 3; else -> 1 }

            NativeTraining.nativeStartTrainingAsync(
                datasetPath = dsPath,
                outputPath = outputDir.absolutePath,
                systemPrompt = state.systemPrompt,
                epochs = cfg.epochs, batchSize = cfg.batchSize,
                learningRate = cfg.learningRate,
                loraRank = cfg.loraRank, loraAlpha = cfg.loraAlpha, loraDropout = cfg.loraDropout,
                warmupRatio = cfg.warmupRatio, weightDecay = cfg.weightDecay,
                maxGradNorm = cfg.maxGradNorm, gradAccumSteps = cfg.gradAccumSteps,
                maxSeqLen = cfg.maxSeqLen, valSplit = cfg.valSplit,
                preprocessing = prepInt, schedulerType = schedInt,
                earlyStopping = if (cfg.earlyStopping) 1 else 0,
                earlyStoppingPatience = cfg.earlyStoppingPatience,
                earlyStoppingMinDelta = cfg.earlyStoppingMinDelta,
                resumeFromCheckpoint = cfg.resumeFromCheckpoint,
                saveTotalLimit = cfg.saveTotalLimit,
                nThreads = cfg.nThreads, saveSteps = cfg.saveSteps, seed = cfg.seed
            )
        }
    }

    fun pauseTraining() { NativeTraining.nativePauseTraining(); _uiState.update { it.copy(trainingState = TrainingState.PAUSED) } }
    fun resumeTraining() { NativeTraining.nativeResumeTraining(); _uiState.update { it.copy(trainingState = TrainingState.RUNNING) } }
    fun stopTraining() {
        NativeTraining.nativeStopTraining()
        _uiState.update { it.copy(trainingState = TrainingState.COMPLETED, elapsedMs = System.currentTimeMillis() - startTimeMs) }
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

    /** 供 TrainScreen 调用的导出入口 */
    fun exportLora(ctx: android.content.Context) = exportModel()

    // ── 日志导出 ──

    fun exportTrainingLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val logDir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "training_log_$timestamp.json")

            val json = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("model", state.selectedModel?.fileName ?: "unknown")
                put("modelMetadata", JSONObject().apply {
                    put("architecture", state.modelMetadata?.architecture ?: "")
                    put("paramCount", state.modelMetadata?.paramCount ?: "")
                })
                put("dataset", JSONObject().apply {
                    put("name", state.datasetName)
                    put("mode", state.dataSourceMode.name)
                    put("samples", state.datasetStats?.sampleCount ?: 0)
                    put("chars", state.datasetStats?.charCount ?: 0)
                    put("estimatedTokens", state.datasetStats?.estimatedTokens ?: 0)
                })
                put("systemPrompt", state.systemPrompt)
                put("config", JSONObject().apply {
                    put("epochs", state.config.epochs)
                    put("batchSize", state.config.batchSize)
                    put("learningRate", state.config.learningRate)
                    put("loraRank", state.config.loraRank)
                    put("loraAlpha", state.config.loraAlpha)
                    put("loraDropout", state.config.loraDropout)
                    put("warmupRatio", state.config.warmupRatio)
                    put("weightDecay", state.config.weightDecay)
                    put("maxGradNorm", state.config.maxGradNorm)
                    put("gradAccumSteps", state.config.gradAccumSteps)
                    put("maxSeqLen", state.config.maxSeqLen)
                    put("seed", state.config.seed)
                })
                put("results", JSONObject().apply {
                    put("bestLoss", state.bestLoss)
                    put("finalLoss", state.lossHistory.lastOrNull() ?: 0f)
                    put("totalSteps", state.currentStep)
                    put("elapsedMs", state.elapsedMs)
                })
                put("lossHistory", state.lossHistory)
            }

            logFile.writeText(json.toString(2))
            _uiState.update { it.copy(logExportPath = logFile.absolutePath) }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    override fun onCleared() { super.onCleared(); NativeTraining.nativeCleanup() }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        return name
    }
}
