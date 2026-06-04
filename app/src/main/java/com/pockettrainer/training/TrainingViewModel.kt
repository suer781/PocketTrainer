package com.pockettrainer.training

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pockettrainer.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val step: TrainStep = TrainStep.SELECT_DATA,
        val isLoading: Boolean = false, val error: String? = null,
        val datasetPath: String = "", val datasetFormat: DatasetFormat = DatasetFormat.CHAT_JSONL,
        val datasetSampleCount: Int = 0, val isDataReady: Boolean = false,
        val modelPath: String = "", val isModelLoaded: Boolean = false, val modelInfo: String = "",
        val loraRank: Int = 8, val loraAlpha: Float = 16.0f, val loraParamCount: Int = 0,
        val epochs: Int = 3, val batchSize: Int = 4, val learningRate: Float = 2e-4f,
        val seqLen: Int = 128, val gradAccumSteps: Int = 4,
        val isTraining: Boolean = false, val isPaused: Boolean = false,
        val currentEpoch: Int = 0, val totalEpochs: Int = 0, val currentStep: Int = 0,
        val currentLoss: Float = 0f, val lossHistory: List<Float> = emptyList(),
        val statusMessage: String = "就绪", val isComplete: Boolean = false, val outputPath: String = ""
    )

    enum class TrainStep { SELECT_DATA, CONFIG_PARAMS, TRAINING, COMPLETE }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val trainer = NativeTraining()
    private val modelRepo = (application as com.pockettrainer.PocketTrainerApp).modelRepository
    private val datasetRepo = (application as com.pockettrainer.PocketTrainerApp).datasetRepository

    fun importDataset(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val file = File(path)
                val format = datasetRepo.detectFormat(file)
                datasetRepo.convertToTrainingFormat(file, format).fold(
                    onSuccess = { _uiState.value = _uiState.value.copy(isLoading = false, datasetPath = it.path, datasetFormat = format, datasetSampleCount = it.sampleCount, isDataReady = true) },
                    onFailure = { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
                )
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun loadModel(dir: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = trainer.initialize(dir)
            _uiState.value = _uiState.value.copy(isModelLoaded = ok, modelPath = dir, error = if (!ok) "模型加载失败" else null)
        }
    }

    fun startTraining() {
        val s = _uiState.value
        if (!s.isModelLoaded || !s.isDataReady) { _uiState.value = s.copy(error = "请先加载模型和数据"); return }
        viewModelScope.launch(Dispatchers.IO) {
            trainer.setupLoRA(s.loraRank, s.loraAlpha)
            trainer.prepareData(s.datasetPath, s.seqLen, s.batchSize)
            _uiState.value = _uiState.value.copy(isTraining = true, step = TrainStep.TRAINING)
            trainer.train(TrainingConfig(s.epochs, s.batchSize, s.learningRate, s.gradAccumSteps), object : NativeTraining.TrainingCallback {
                override fun onProgress(epoch: Int, total: Int, step: Int, loss: Float) {
                    val cur = _uiState.value
                    _uiState.value = cur.copy(currentEpoch = epoch+1, totalEpochs = total, currentStep = step, currentLoss = loss, lossHistory = (cur.lossHistory + loss).takeLast(500))
                }
                override fun onComplete(ok: Boolean, msg: String) { _uiState.value = _uiState.value.copy(isTraining = false, isComplete = ok, step = if (ok) TrainStep.COMPLETE else TrainStep.TRAINING) }
                override fun onError(e: String) { _uiState.value = _uiState.value.copy(isTraining = false, error = e) }
            })
        }
    }

    fun pauseTraining() { trainer.pauseTraining(); _uiState.value = _uiState.value.copy(isPaused = true) }
    fun resumeTraining() { trainer.resumeTraining(); _uiState.value = _uiState.value.copy(isPaused = false) }
    fun stopTraining() { trainer.stopTraining(); _uiState.value = _uiState.value.copy(isTraining = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    override fun onCleared() { super.onCleared(); trainer.release() }
}