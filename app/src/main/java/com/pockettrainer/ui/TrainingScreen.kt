package com.pockettrainer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettrainer.training.ModelInfo
import com.pockettrainer.training.TrainingState
import com.pockettrainer.training.TrainingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    viewModel: TrainingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 模型选择 ──
        item {
            Text(
                "选择模型",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.availableModels.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "在 Download/PocketTrainer/models/ 下放置 .safetensors 模型文件",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(uiState.availableModels) { model ->
                ModelCard(
                    model = model,
                    isSelected = model == uiState.selectedModel,
                    onClick = { viewModel.selectModel(model) }
                )
            }
        }

        // ── 加载模型按钮 ──
        item {
            Button(
                onClick = { viewModel.loadModel() },
                enabled = uiState.selectedModel != null &&
                        uiState.trainingState in listOf(TrainingState.IDLE, TrainingState.ERROR),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (uiState.trainingState) {
                        TrainingState.LOADING -> "加载中..."
                        TrainingState.READY   -> "重新加载"
                        else                  -> "加载模型"
                    }
                )
            }
        }

        // ── 训练控制 ──
        if (uiState.trainingState in listOf(
                TrainingState.READY, TrainingState.RUNNING,
                TrainingState.PAUSED, TrainingState.COMPLETED
            )
        ) {
            item {
                TrainingControlPanel(
                    uiState = uiState,
                    onStart = {
                        viewModel.startTraining(
                            datasetPath = "/data/local/tmp/wikitext-2-raw",
                            epochs = 1
                        )
                    },
                    onPause = { viewModel.pauseTraining() },
                    onResume = { viewModel.resumeTraining() },
                    onStop = { viewModel.stopTraining() },
                    onExport = { viewModel.exportModel() }
                )
            }
        }

        // ── 状态 + 损失曲线 ──
        if (uiState.lossHistory.isNotEmpty() || uiState.trainingState != TrainingState.IDLE) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            uiState.progressText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )

                        if (uiState.trainingState == TrainingState.RUNNING) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { uiState.progressPercent },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("已用: ${uiState.formattedElapsed}", style = MaterialTheme.typography.bodySmall)
                                Text("剩余: ${uiState.formattedRemaining}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // ── 损失曲线 ──
            item {
                LossCurveView(
                    lossHistory = uiState.lossHistory,
                    currentLoss = uiState.currentLoss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── 输出路径 ──
        if (uiState.outputPath.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        "输出: ${uiState.outputPath}",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: ModelInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, "selected", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(model.fileName, fontWeight = FontWeight.Medium)
                Text("%.1f MB".format(model.fileSizeMb), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TrainingControlPanel(
    uiState: com.pockettrainer.training.TrainingUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("训练控制", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (uiState.trainingState) {
                    TrainingState.READY, TrainingState.COMPLETED -> {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(4.dp))
                            Text("开始训练")
                        }
                    }
                    TrainingState.RUNNING -> {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, null)
                            Spacer(Modifier.width(4.dp))
                            Text("暂停")
                        }
                        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Stop, null)
                            Spacer(Modifier.width(4.dp))
                            Text("停止")
                        }
                    }
                    TrainingState.PAUSED -> {
                        Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(4.dp))
                            Text("继续")
                        }
                        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Stop, null)
                            Spacer(Modifier.width(4.dp))
                            Text("停止")
                        }
                    }
                    else -> {}
                }
            }

            // 导出按钮
            if (uiState.trainingState == TrainingState.COMPLETED) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(4.dp))
                    Text("导出 LoRA 权重")
                }
            }
        }
    }
}
