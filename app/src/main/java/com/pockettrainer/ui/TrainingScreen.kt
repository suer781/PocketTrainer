package com.pockettrainer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
fun TrainingScreen(viewModel: TrainingViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // 文件选择器
    val modelPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importModelFromUri(it) } }

    val datasetPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importDatasetFromUri(it) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ══════════════════════════════════════
        // 第一步：导入基座模型
        // ══════════════════════════════════════
        item {
            SectionHeader(
                step = 1,
                title = "导入基座模型",
                subtitle = "选择要微调的 .safetensors 模型"
            )
        }

        // 已有的模型列表
        if (uiState.availableModels.isNotEmpty()) {
            items(uiState.availableModels) { model ->
                ModelCard(
                    model = model,
                    isSelected = model == uiState.selectedModel,
                    onClick = { viewModel.selectModel(model) }
                )
            }
        }

        // 导入按钮
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导入模型文件")
                }
            }
        }

        // 加载按钮
        item {
            Button(
                onClick = { viewModel.loadModel() },
                enabled = uiState.selectedModel != null &&
                        uiState.trainingState in listOf(TrainingState.IDLE, TrainingState.ERROR),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, null)
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

        // ══════════════════════════════════════
        // 第二步：选择数据集
        // ══════════════════════════════════════
        item {
            SectionHeader(
                step = 2,
                title = "选择数据集",
                subtitle = "支持 .txt / .jsonl / .json / .csv"
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    datasetPickerLauncher.launch(arrayOf("*/*"))
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.datasetPath.isNotEmpty())
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (uiState.datasetPath.isNotEmpty()) Icons.Default.CheckCircle
                        else Icons.Default.FolderOpen,
                        null,
                        tint = if (uiState.datasetPath.isNotEmpty()) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (uiState.datasetName.isNotEmpty()) uiState.datasetName
                            else "点击选择数据集文件",
                            fontWeight = FontWeight.Medium
                        )
                        if (uiState.datasetPath.isNotEmpty()) {
                            Text(
                                uiState.datasetPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════
        // 第三步：训练
        // ══════════════════════════════════════
        if (uiState.trainingState in listOf(
                TrainingState.READY, TrainingState.RUNNING,
                TrainingState.PAUSED, TrainingState.COMPLETED
            )
        ) {
            item {
                SectionHeader(step = 3, title = "开始训练", subtitle = null)
            }

            item {
                TrainingControlPanel(
                    uiState = uiState,
                    onStart = { viewModel.startTraining() },
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(uiState.progressText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)

                        if (uiState.trainingState == TrainingState.RUNNING) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { uiState.progressPercent }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("已用: ${uiState.formattedElapsed}", style = MaterialTheme.typography.bodySmall)
                                Text("剩余: ${uiState.formattedRemaining}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                LossCurveView(
                    lossHistory = uiState.lossHistory,
                    currentLoss = uiState.currentLoss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── 免责声明 ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "用户自行提供的模型和数据集，训练结果由用户自行承担。请确保拥有模型和数据的合法使用权。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(step: Int, title: String, subtitle: String?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$step", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp))
        }
    }
}

@Composable
fun ModelCard(model: ModelInfo, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, "selected", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(model.fileName, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("%.1f MB".format(model.fileSizeMb), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TrainingControlPanel(
    uiState: com.pockettrainer.training.TrainingUiState,
    onStart: () -> Unit, onPause: () -> Unit, onResume: () -> Unit,
    onStop: () -> Unit, onExport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (uiState.trainingState) {
                    TrainingState.READY, TrainingState.COMPLETED -> {
                        Button(
                            onClick = onStart,
                            enabled = uiState.canStartTraining,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (uiState.datasetPath.isEmpty()) "请先选择数据集" else "开始训练")
                        }
                    }
                    TrainingState.RUNNING -> {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, null); Spacer(Modifier.width(4.dp)); Text("暂停")
                        }
                        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("停止")
                        }
                    }
                    TrainingState.PAUSED -> {
                        Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("继续")
                        }
                        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("停止")
                        }
                    }
                    else -> {}
                }
            }

            if (uiState.trainingState == TrainingState.COMPLETED) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text("导出 LoRA 权重")
                }
            }
        }
    }
}
