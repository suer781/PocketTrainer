package com.pockettrainer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pockettrainer.training.TrainingViewModel
import com.pockettrainer.training.TrainStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainScreen(navController: NavController, viewModel: TrainingViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            ctx.contentResolver.openInputStream(it)?.use { inp ->
                val tmp = java.io.File(ctx.cacheDir, "import_dataset.jsonl")
                tmp.outputStream().use { out -> inp.copyTo(out) }
                viewModel.importDataset(tmp.absolutePath)
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { TopAppBar(title = { Text("训练") }, navigationIcon = {
            if (state.step != TrainStep.SELECT_DATA) IconButton(onClick = { viewModel.resetToStep(TrainStep.SELECT_DATA) }) {
                Icon(Icons.Default.ArrowBack, "返回")
            }
        }) }

        item {
            StepIndicator(state.step)
            Spacer(Modifier.height(16.dp))
        }

        item {
            when (state.step) {
                TrainStep.SELECT_DATA -> SelectDataStep(state, onPick = { filePicker.launch(arrayOf("application/jsonl","text/*","application/json","text/csv")) }, onBack = { navController.popBackStack() })
                TrainStep.CONFIG_PARAMS -> ConfigParamsStep(state, viewModel)
                TrainStep.TRAINING -> TrainingProgressStep(state, viewModel)
                TrainStep.COMPLETE -> TrainingCompleteStep(state)
            }
        }

        state.error?.let { msg ->
            item { ErrorCard(msg) { viewModel.clearError() } }
        }
    }
}

@Composable
private fun StepIndicator(current: TrainStep) {
    val steps = listOf("数据" to TrainStep.SELECT_DATA, "参数" to TrainStep.CONFIG_PARAMS, "训练" to TrainStep.TRAINING, "完成" to TrainStep.COMPLETE)
    val idx = steps.indexOfFirst { it.second == current }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, (label, step) ->
            val done = i < idx
            val active = step == current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = MaterialTheme.shapes.small, color = when { done -> MaterialTheme.colorScheme.primary; active -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant }, modifier = Modifier.size(32.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (done) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        else Text("${i+1}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 12.sp, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (i < steps.lastIndex) Surface(Modifier.weight(1f).height(2.dp).padding(horizontal = 4.dp), color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {}
        }
    }
}

@Composable
private fun SelectDataStep(state: com.pockettrainer.training.UiState, onPick: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("选择训练数据", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text("支持的格式：", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        val fmts = listOf("JSONL 对话" to "messages" to "role + content", "Alpaca" to "instruction/input/output", "ShareGPT" to "conversations" to "from + value", "纯文本" to "每行一句", "CSV" to "question,answer")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fmts.forEach { (name, desc) ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DataObject, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column { Text(name, fontWeight = FontWeight.Medium); Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick, Modifier.fillMaxWidth().height(48.dp), enabled = !state.isLoading) {
            if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("选择数据文件") }
        }
        TextButton(onClick = onBack) { Text("返回首页") }
    }
}

@Composable
private fun ConfigParamsStep(state: com.pockettrainer.training.UiState, viewModel: TrainingViewModel) {
    var loraRank by remember { mutableStateOf(state.loraRank.toFloat()) }
    var loraAlpha by remember { mutableStateOf(state.loraAlpha) }
    var epochs by remember { mutableStateOf(state.epochs.toFloat()) }
    var batchSize by remember { mutableStateOf(state.batchSize.toFloat()) }
    var lr by remember { mutableStateOf(state.learningRate) }
    var seqLen by remember { mutableIntStateOf(state.seqLen) }
    var gradAccum by remember { mutableStateOf(state.gradAccumSteps.toFloat()) }

    Column(Modifier.padding(16.dp)) {
        Text("训练参数", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text("数据: ${state.datasetSampleCount} 条 · ${state.datasetFormat}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("模型: ${state.modelPath.substringAfterLast('/').ifEmpty { "未选择" }}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ParamSliderCard(Modifier.weight(1f), "LoRA Rank", loraRank, 1f..64f, 6, "${loraRank.toInt()}") { loraRank = it }
            ParamSliderCard(Modifier.weight(1f), "LoRA Alpha", loraAlpha, 1f..128f, 12, String.format("%.0f", loraAlpha)) { loraAlpha = it }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ParamSliderCard(Modifier.weight(1f), "Epochs", epochs, 1f..20f, 19, "${epochs.toInt()}") { epochs = it }
            ParamSliderCard(Modifier.weight(1f), "Batch Size", batchSize, 1f..32f, 31, "${batchSize.toInt()}") { batchSize = it }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ParamSliderCard(Modifier.weight(1f), "Learning Rate", lr, 1e-5f..1e-3f, 9, String.format("%.1e", lr)) { lr = it }
            ParamSliderCard(Modifier.weight(1f), "Seq Length", seqLen.toFloat(), 32f..2048f, 7, "${seqLen}") { seqLen = it.toInt() }
        }
        Spacer(Modifier.height(8.dp))
        ParamSliderCard(Modifier.fillMaxWidth(), "梯度累积步数", gradAccum, 1f..32f, 31, "${gradAccum.toInt()}") { gradAccum = it }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.updateTrainingParams(loraRank.toInt(), loraAlpha, epochs.toInt(), batchSize.toInt(), lr, seqLen, gradAccum.toInt()); viewModel.startTraining() }, Modifier.fillMaxWidth().height(52.dp), enabled = state.isModelLoaded && state.isDataReady && !state.isLoading) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("开始训练", fontWeight = FontWeight.Bold)
        }
        if (!state.isModelLoaded || !state.isDataReady) Text("请先在「模型」页下载模型，并导入数据集", Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ParamSliderCard(modifier: Modifier, label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, display: String, onChange: (Float) -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Slider(value, onChange, valueRange = range, steps = steps, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TrainingProgressStep(state: com.pockettrainer.training.UiState, viewModel: TrainingViewModel) {
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressIndicator(state)
        Spacer(Modifier.height(16.dp))
        Text(state.statusMessage, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (state.isPaused) viewModel.resumeTraining() else viewModel.pauseTraining() }, Modifier.weight(1f).height(48.dp)) {
                Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null); Spacer(Modifier.width(6.dp)); Text(if (state.isPaused) "继续" else "暂停")
            }
            Button(onClick = { viewModel.stopTraining() }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("停止")
            }
        }
        if (state.lossHistory.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Loss 曲线", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            LossChart(state.lossHistory, Modifier.fillMaxWidth().height(200.dp))
        }
    }
}

@Composable
private fun ProgressIndicator(state: com.pockettrainer.training.UiState) {
    val progress = if (state.totalEpochs > 0) state.currentEpoch.toFloat() / state.totalEpochs else 0f
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress }, Modifier.fillMaxSize(), strokeWidth = 8.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.currentEpoch}/${state.totalEpochs}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Epoch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricCol("Step", "${state.currentStep}")
                MetricCol("Loss", if (state.currentLoss > 0f) String.format("%.4f", state.currentLoss) else "-")
                MetricCol("Epoch", "${state.currentEpoch}/${state.totalEpochs}")
            }
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LossChart(history: List<Float>, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (history.isEmpty()) return@Canvas
        val max = history.max(); val min = history.min()
        val range = (max - min).coerceAtLeast(0.001f)
        val padL = 48f; val padR = 16f; val padT = 16f; val padB = 32f
        val w = size.width - padL - padR; val h = size.height - padT - padB
        drawContext.canvas.nativeCanvas.apply {
            drawText("%.4f".format(max), 4f, padT + 14f, android.graphics.Paint().apply { textSize = 24f; color = android.graphics.Color.GRAY })
            drawText("%.4f".format(min), 4f, size.height - padB + 24f, android.graphics.Paint().apply { textSize = 24f; color = android.graphics.Color.GRAY })
        }
        for (i in 0..4) {
            val y = padT + h * i / 4f
            drawLine(outline, Offset(padL, y), Offset(padL + w, y), strokeWidth = 1f)
        }
        val path = Path()
        history.forEachIndexed { i, v ->
            val x = padL + w * i / (history.size - 1).coerceAtLeast(1)
            val y = padT + h * (1f - (v - min) / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primary, style = Stroke(width = 3f))
        history.forEachIndexed { i, v ->
            val x = padL + w * i / (history.size - 1).coerceAtLeast(1)
            val y = padT + h * (1f - (v - min) / range)
            drawCircle(primary, 5f, Offset(x, y))
        }
    }
}

@Composable
private fun TrainingCompleteStep(state: com.pockettrainer.training.UiState) {
    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(80.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(24.dp))
        Text("训练完成！", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("最终 Loss: ${if (state.lossHistory.isNotEmpty()) String.format("%.4f", state.lossHistory.last()) else "-"}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            ListItem(headlineContent = { Text("输出路径") }, supportingContent = { Text(state.outputPath.ifEmpty { "未指定" }, fontSize = 13.sp) }, leadingContent = { Icon(Icons.Default.Folder, null) })
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { /* TODO: 导出模型 */ }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("导出模型") }
    }
}

@Composable
private fun ErrorCard(msg: String, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Text(msg, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}
