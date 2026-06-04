package com.pockettrainer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.pockettrainer.training.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(viewModel: TrainingViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showStartDialog by remember { mutableStateOf(false) }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importModelFromUri(it) } }

    val datasetPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importDatasetFromUri(it) } }

    // ── 训练确认弹窗 ──
    if (showStartDialog) {
        AlertDialog(
            onDismissRequest = { showStartDialog = false },
            icon = { Icon(Icons.Default.Psychology, null) },
            title = { Text("确认开始微调") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("即将对模型进行微调训练，模型权重将被修改。")
                    if (uiState.systemPrompt.isNotEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Text("系统提示词将被训练进模型权重", modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                    // 数据集概览
                    uiState.datasetStats?.let { stats ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("数据集: ${stats.sampleCount} 样本 · ${"%,d".format(stats.estimatedTokens)} tokens",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("预计训练: ~${"%.0f".format(stats.estimatedMinutes)} 分钟",
                                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Text("数据集较大时训练时间可能较长，请耐心等待。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("训练过程中请勿关闭应用。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { Button(onClick = { showStartDialog = false; viewModel.startTraining() }) { Text("开始训练") } },
            dismissButton = { TextButton(onClick = { showStartDialog = false }) { Text("取消") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ══════════════════════════════════════
        // ① 导入基座模型
        // ══════════════════════════════════════
        item { SectionHeader(1, "导入基座模型", "Qwen、DeepSeek 等 .safetensors 格式，参数量不限") }

        if (uiState.availableModels.isNotEmpty()) {
            items(uiState.availableModels) { model ->
                ModelCard(model, model == uiState.selectedModel) { viewModel.selectModel(model) }
            }
        }

        item {
            Button(onClick = { modelPickerLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("导入模型文件")
            }
        }

        item {
            Button(
                onClick = { viewModel.loadModel() },
                enabled = uiState.selectedModel != null && uiState.trainingState in listOf(TrainingState.IDLE, TrainingState.ERROR),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp))
                Text(when (uiState.trainingState) {
                    TrainingState.LOADING -> "加载中..."; TrainingState.READY -> "重新加载"; else -> "加载模型"
                })
            }
        }

        // ── 模型信息卡 ──
        if (uiState.modelMetadata != null) {
            item { ModelInfoCard(uiState.modelMetadata!!, uiState.formattedFileSize) }
        }

        // ══════════════════════════════════════
        // ② 训练数据
        // ══════════════════════════════════════
        item { SectionHeader(2, "训练数据", "直接输入文本或导入文件") }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.dataSourceMode == DataSourceMode.TEXT,
                    onClick = { viewModel.setDataSourceMode(DataSourceMode.TEXT) },
                    label = { Text("直接输入") },
                    leadingIcon = { if (uiState.dataSourceMode == DataSourceMode.TEXT) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = uiState.dataSourceMode == DataSourceMode.FILE,
                    onClick = { viewModel.setDataSourceMode(DataSourceMode.FILE) },
                    label = { Text("导入文件") },
                    leadingIcon = { if (uiState.dataSourceMode == DataSourceMode.FILE) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        if (uiState.dataSourceMode == DataSourceMode.TEXT) {
            item {
                OutlinedTextField(
                    value = uiState.directText,
                    onValueChange = { viewModel.setDirectText(it) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    placeholder = { Text("粘贴或输入训练文本...\n\n每段用空行分隔，每段作为一个训练样本") },
                    supportingText = {
                        Text(if (uiState.directText.isEmpty()) "输入要训练进模型的文本内容"
                             else "${uiState.directText.length} 字符", style = MaterialTheme.typography.bodySmall)
                    }
                )
            }
        }

        if (uiState.dataSourceMode == DataSourceMode.FILE) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { datasetPickerLauncher.launch(arrayOf("*/*")) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.datasetPath.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer
                                         else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (uiState.datasetPath.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.FolderOpen, null,
                            tint = if (uiState.datasetPath.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (uiState.datasetName.isNotEmpty()) uiState.datasetName
                                 else "点击选择文件（.txt/.jsonl/.json/.csv 等）", fontWeight = FontWeight.Medium)
                            if (uiState.datasetPath.isNotEmpty()) {
                                Text(uiState.datasetPath, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // ── 数据集统计 ──
        if (uiState.datasetStats != null) {
            item { DatasetStatsCard(uiState.datasetStats!!) }
        }

        // ══════════════════════════════════════
        // ③ 系统提示词（可选）
        // ══════════════════════════════════════
        item { SectionHeader(3, "系统提示词（可选）", "训练进模型权重，之后无需再传") }

        item {
            OutlinedTextField(
                value = uiState.systemPrompt,
                onValueChange = { viewModel.setSystemPrompt(it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text("例如：你是一个专业的法律助手，擅长...") },
                label = { Text("系统提示词") },
                supportingText = {
                    Text(if (uiState.systemPrompt.isEmpty()) "留空则不注入"
                         else "${uiState.systemPrompt.length} 字符，将注入每个训练样本前", style = MaterialTheme.typography.bodySmall)
                }
            )
        }

        // ══════════════════════════════════════
        // ④ 训练预设
        // ══════════════════════════════════════
        item { SectionHeader(4, "训练预设", "一键套用推荐配置，或自定义高级参数") }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrainingPresets.presets.forEach { preset ->
                    val isActive = uiState.activePreset == preset.name
                    AssistChip(
                        onClick = { viewModel.applyPreset(preset) },
                        label = { Text("${preset.emoji} ${preset.name}") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                             else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }

        // ── 预设说明 ──
        if (uiState.activePreset.isNotEmpty()) {
            item {
                val preset = TrainingPresets.presets.find { it.name == uiState.activePreset }
                preset?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                        Text(it.description, modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ══════════════════════════════════════
        // ⑤ 高级参数
        // ══════════════════════════════════════
        item {
            AdvancedOptionsPanel(
                config = uiState.config,
                isExpanded = uiState.showAdvanced,
                onToggle = { viewModel.toggleAdvanced() },
                onUpdate = { updater -> viewModel.updateConfig(updater) }
            )
        }

        // ══════════════════════════════════════
        // ⑥ 训练
        // ══════════════════════════════════════
        if (uiState.trainingState in listOf(TrainingState.READY, TrainingState.RUNNING, TrainingState.PAUSED, TrainingState.COMPLETED)) {
            item { SectionHeader(6, "开始训练", null) }

            item {
                TrainingControlPanel(
                    uiState = uiState,
                    onStart = { showStartDialog = true },
                    onPause = { viewModel.pauseTraining() },
                    onResume = { viewModel.resumeTraining() },
                    onStop = { viewModel.stopTraining() },
                    onExport = { viewModel.exportModel() },
                    onExportLog = { viewModel.exportTrainingLog() }
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
                LossCurveView(lossHistory = uiState.lossHistory, currentLoss = uiState.currentLoss, modifier = Modifier.fillMaxWidth())
            }
        }

        // ── 免责声明 ──
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("训练结果由用户自行承担。请确保拥有模型和数据的合法使用权。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("数据集是否清洗由用户自行决定。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

// ── 子组件 ──

@Composable
private fun SectionHeader(step: Int, title: String, subtitle: String?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$step", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp))
        }
    }
}

@Composable
fun ModelCard(model: ModelInfo, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
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
fun ModelInfoCard(meta: ModelMetadata, fileSize: String) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoChip("架构", meta.architecture)
            InfoChip("参数", meta.paramCount)
            InfoChip("大小", fileSize)
        }
    }
}

@Composable
fun DatasetStatsCard(stats: DatasetStats) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoChip("样本", "${stats.sampleCount}")
            InfoChip("字符", "%,d".format(stats.charCount))
            InfoChip("~Tokens", "%,d".format(stats.estimatedTokens))
            InfoChip("预计", "~${"%.0f".format(stats.estimatedMinutes)} 分钟")
        }
    }
}

@Composable
fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TrainingControlPanel(
    uiState: TrainingUiState,
    onStart: () -> Unit, onPause: () -> Unit, onResume: () -> Unit,
    onStop: () -> Unit, onExport: () -> Unit, onExportLog: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (uiState.trainingState) {
                    TrainingState.READY, TrainingState.COMPLETED -> {
                        Button(onClick = onStart, enabled = uiState.canStartTraining, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp))
                            Text(if (!uiState.canStartTraining) "请先输入训练数据" else "开始训练")
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text("导出 LoRA")
                    }
                    OutlinedButton(onClick = onExportLog, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Description, null); Spacer(Modifier.width(4.dp)); Text("导出日志")
                    }
                }
            }

            // 日志导出路径
            if (uiState.logExportPath.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("日志已保存: ${uiState.logExportPath}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
