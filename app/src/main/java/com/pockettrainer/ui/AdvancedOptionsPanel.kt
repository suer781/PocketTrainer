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
import com.pockettrainer.training.ParamType
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
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("高级参数", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LoRA r=${config.loraRank} · lr=${formatLR(config.learningRate)} · ${config.schedulerType}",
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
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val grouped = TrainingParamRegistry.params.groupBy { it.category }

                    grouped.forEach { (category, params) ->
                        Text(
                            category,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

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

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 参数名 + 说明按钮
            Column(modifier = Modifier.weight(1f)) {
                Text(meta.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text("建议: ${meta.range}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }

            // 根据类型渲染不同控件
            when (meta.type) {
                ParamType.BOOL -> {
                    val checked = when (meta.key) {
                        "earlyStopping" -> config.earlyStopping
                        else -> false
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = { v ->
                            onUpdate { old ->
                                when (meta.key) {
                                    "earlyStopping" -> old.copy(earlyStopping = v)
                                    else -> old
                                }
                            }
                        }
                    )
                }

                ParamType.DROPDOWN -> {
                    val current = when (meta.key) {
                        "schedulerType" -> config.schedulerType
                        "preprocessing" -> config.preprocessing
                        else -> ""
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedCard(
                            modifier = Modifier.clickable { expanded = true }.width(160.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(current, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            meta.options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        expanded = false
                                        onUpdate { old ->
                                            when (meta.key) {
                                                "schedulerType" -> old.copy(schedulerType = option)
                                                "preprocessing" -> old.copy(preprocessing = option)
                                                else -> old
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                ParamType.TEXT -> {
                    val value = when (meta.key) {
                        "resumeFromCheckpoint" -> config.resumeFromCheckpoint
                        else -> ""
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { v ->
                            onUpdate { old ->
                                when (meta.key) {
                                    "resumeFromCheckpoint" -> old.copy(resumeFromCheckpoint = v)
                                    else -> old
                                }
                            }
                        },
                        modifier = Modifier.width(180.dp).height(44.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        placeholder = { Text(meta.hint, style = MaterialTheme.typography.labelSmall) }
                    )
                }

                else -> {
                    // INT / FLOAT — 数字输入框
                    val value = getValueForKey(meta.key, config)
                    OutlinedTextField(
                        value = value,
                        onValueChange = { newVal ->
                            onUpdate { old -> setValueForKey(meta.key, newVal, old) }
                        },
                        modifier = Modifier.width(100.dp).height(44.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (meta.type == ParamType.FLOAT) KeyboardType.Decimal
                                           else KeyboardType.Number
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showTooltip = !showTooltip }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.HelpOutline, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )
                }
            }
        }

        // 说明气泡
        AnimatedVisibility(visible = showTooltip) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(meta.description, modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    }
}

// ── 工具函数 ──

private fun getValueForKey(key: String, config: TrainingConfig): String = when (key) {
    "epochs"           -> config.epochs.toString()
    "batchSize"        -> config.batchSize.toString()
    "learningRate"     -> formatLR(config.learningRate)
    "loraRank"         -> config.loraRank.toString()
    "loraAlpha"        -> config.loraAlpha.toString()
    "loraDropout"      -> config.loraDropout.toString()
    "warmupRatio"      -> config.warmupRatio.toString()
    "weightDecay"      -> config.weightDecay.toString()
    "maxGradNorm"      -> config.maxGradNorm.toString()
    "gradAccumSteps"   -> config.gradAccumSteps.toString()
    "maxSeqLen"        -> config.maxSeqLen.toString()
    "valSplit"         -> config.valSplit.toString()
    "earlyStoppingPatience"  -> config.earlyStoppingPatience.toString()
    "earlyStoppingMinDelta"  -> config.earlyStoppingMinDelta.toString()
    "saveTotalLimit"   -> config.saveTotalLimit.toString()
    "nThreads"         -> config.nThreads.toString()
    "saveSteps"        -> config.saveSteps.toString()
    "seed"             -> config.seed.toString()
    else -> ""
}

private fun setValueForKey(key: String, raw: String, old: TrainingConfig): TrainingConfig = when (key) {
    "epochs"           -> old.copy(epochs = raw.toIntOrNull() ?: old.epochs)
    "batchSize"        -> old.copy(batchSize = raw.toIntOrNull() ?: old.batchSize)
    "learningRate"     -> old.copy(learningRate = parseLR(raw) ?: old.learningRate)
    "loraRank"         -> old.copy(loraRank = raw.toIntOrNull() ?: old.loraRank)
    "loraAlpha"        -> old.copy(loraAlpha = raw.toFloatOrNull() ?: old.loraAlpha)
    "loraDropout"      -> old.copy(loraDropout = raw.toFloatOrNull() ?: old.loraDropout)
    "warmupRatio"      -> old.copy(warmupRatio = raw.toFloatOrNull() ?: old.warmupRatio)
    "weightDecay"      -> old.copy(weightDecay = raw.toFloatOrNull() ?: old.weightDecay)
    "maxGradNorm"      -> old.copy(maxGradNorm = raw.toFloatOrNull() ?: old.maxGradNorm)
    "gradAccumSteps"   -> old.copy(gradAccumSteps = raw.toIntOrNull() ?: old.gradAccumSteps)
    "maxSeqLen"        -> old.copy(maxSeqLen = raw.toIntOrNull() ?: old.maxSeqLen)
    "valSplit"         -> old.copy(valSplit = raw.toFloatOrNull() ?: old.valSplit)
    "earlyStoppingPatience"  -> old.copy(earlyStoppingPatience = raw.toIntOrNull() ?: old.earlyStoppingPatience)
    "earlyStoppingMinDelta"  -> old.copy(earlyStoppingMinDelta = raw.toFloatOrNull() ?: old.earlyStoppingMinDelta)
    "saveTotalLimit"   -> old.copy(saveTotalLimit = raw.toIntOrNull() ?: old.saveTotalLimit)
    "nThreads"         -> old.copy(nThreads = raw.toIntOrNull() ?: old.nThreads)
    "saveSteps"        -> old.copy(saveSteps = raw.toIntOrNull() ?: old.saveSteps)
    "seed"             -> old.copy(seed = raw.toIntOrNull() ?: old.seed)
    else -> old
}

private fun formatLR(value: Float): String = when {
    value == 0f -> "0"
    value < 0.001f -> "%.0e".format(value).let {
        val exp = kotlin.math.log10(value.toDouble()).toInt()
        val mantissa = value / Math.pow(10.0, exp.toDouble()).toFloat()
        "%.0fe%d".format(mantissa, exp)
    }
    else -> "%.6g".format(value)
}

private fun parseLR(s: String): Float? = s.trim().toFloatOrNull()
    ?: s.trim().lowercase().toFloatOrNull()
