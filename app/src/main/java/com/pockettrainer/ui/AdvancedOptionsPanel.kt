package com.pockettrainer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pockettrainer.training.ParamMeta
import com.pockettrainer.training.TrainingConfig
import com.pockettrainer.training.TrainingParamRegistry

@Composable
fun AdvancedOptionsPanel(
    config: TrainingConfig,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: ((TrainingConfig) -> TrainingConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            // ── 折叠标题栏 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("高级参数", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LoRA r=${config.loraRank} · lr=${formatScientific(config.learningRate)} · bs=${config.batchSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── 展开内容 ──
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 按 category 分组
                    val grouped = TrainingParamRegistry.params.groupBy { it.category }

                    grouped.forEach { (category, params) ->
                        Text(
                            category,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )

                        params.forEach { meta ->
                            ParamField(meta = meta, config = config, onUpdate = onUpdate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParamField(
    meta: ParamMeta,
    config: TrainingConfig,
    onUpdate: ((TrainingConfig) -> TrainingConfig) -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }

    // 根据 key 获取当前值
    val currentValue = when (meta.key) {
        "epochs"           -> config.epochs.toString()
        "batchSize"        -> config.batchSize.toString()
        "learningRate"     -> formatScientific(config.learningRate)
        "loraRank"         -> config.loraRank.toString()
        "loraAlpha"        -> config.loraAlpha.toString()
        "loraDropout"      -> config.loraDropout.toString()
        "warmupRatio"      -> config.warmupRatio.toString()
        "weightDecay"      -> config.weightDecay.toString()
        "maxGradNorm"      -> config.maxGradNorm.toString()
        "gradAccumSteps"   -> config.gradAccumSteps.toString()
        "maxSeqLen"        -> config.maxSeqLen.toString()
        "nThreads"         -> config.nThreads.toString()
        "saveSteps"        -> config.saveSteps.toString()
        "seed"             -> config.seed.toString()
        else -> ""
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 参数名
            Text(
                meta.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            // 输入框
            OutlinedTextField(
                value = currentValue,
                onValueChange = { newVal ->
                    onUpdate { old ->
                        when (meta.key) {
                            "epochs"           -> old.copy(epochs = newVal.toIntOrNull() ?: old.epochs)
                            "batchSize"        -> old.copy(batchSize = newVal.toIntOrNull() ?: old.batchSize)
                            "learningRate"     -> old.copy(learningRate = parseScientific(newVal) ?: old.learningRate)
                            "loraRank"         -> old.copy(loraRank = newVal.toIntOrNull() ?: old.loraRank)
                            "loraAlpha"        -> old.copy(loraAlpha = newVal.toFloatOrNull() ?: old.loraAlpha)
                            "loraDropout"      -> old.copy(loraDropout = newVal.toFloatOrNull() ?: old.loraDropout)
                            "warmupRatio"      -> old.copy(warmupRatio = newVal.toFloatOrNull() ?: old.warmupRatio)
                            "weightDecay"      -> old.copy(weightDecay = newVal.toFloatOrNull() ?: old.weightDecay)
                            "maxGradNorm"      -> old.copy(maxGradNorm = newVal.toFloatOrNull() ?: old.maxGradNorm)
                            "gradAccumSteps"   -> old.copy(gradAccumSteps = newVal.toIntOrNull() ?: old.gradAccumSteps)
                            "maxSeqLen"        -> old.copy(maxSeqLen = newVal.toIntOrNull() ?: old.maxSeqLen)
                            "nThreads"         -> old.copy(nThreads = newVal.toIntOrNull() ?: old.nThreads)
                            "saveSteps"        -> old.copy(saveSteps = newVal.toIntOrNull() ?: old.saveSteps)
                            "seed"             -> old.copy(seed = newVal.toIntOrNull() ?: old.seed)
                            else -> old
                        }
                    }
                },
                modifier = Modifier.width(100.dp).height(48.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (meta.key == "learningRate" || meta.key == "loraAlpha" ||
                                     meta.key == "loraDropout" || meta.key == "warmupRatio" ||
                                     meta.key == "weightDecay" || meta.key == "maxGradNorm")
                        KeyboardType.Decimal else KeyboardType.Number
                ),
                trailingIcon = {
                    IconButton(onClick = { showTooltip = !showTooltip }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.HelpOutline, null, modifier = Modifier.size(14.dp))
                    }
                }
            )
        }

        // 建议范围
        Text(
            "建议: ${meta.range}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp)
        )

        // 说明展开
        AnimatedVisibility(visible = showTooltip) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    meta.description,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

private fun formatScientific(value: Float): String {
    return when {
        value == 0f -> "0"
        value < 0.001f || value >= 100f -> "%.1e".format(value)
        value < 1f -> "%.0e".format(value).replace("e", "e").let {
            // format as 2e-5 style
            val exp = Math.log10(value.toDouble()).toInt()
            val mantissa = value / Math.pow(10.0, exp.toDouble()).toFloat()
            "%.0fe%d".format(mantissa, exp)
        }
        else -> "%.4f".format(value)
    }
}

private fun parseScientific(s: String): Float? {
    return try {
        // 支持 2e-5, 2E-5, 0.00002 等格式
        s.trim().toFloatOrNull() ?: run {
            val clean = s.lowercase().replace("f", "")
            clean.toFloatOrNull()
        }
    } catch (_: Exception) { null }
}
