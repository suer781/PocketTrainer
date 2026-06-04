package com.pockettrainer.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("pocket_trainer_settings", Context.MODE_PRIVATE)

    var nThreads by remember { mutableFloatStateOf(prefs.getFloat("nThreads", 4f)) }
    var useGPU by remember { mutableStateOf(prefs.getBoolean("useGPU", false)) }
    var useBLAS by remember { mutableStateOf(prefs.getBoolean("useBLAS", true)) }

    // Persist on change
    LaunchedEffect(nThreads) { prefs.edit().putFloat("nThreads", nThreads).apply() }
    LaunchedEffect(useGPU) { prefs.edit().putBoolean("useGPU", useGPU).apply() }
    LaunchedEffect(useBLAS) { prefs.edit().putBoolean("useBLAS", useBLAS).apply() }

    LazyColumn(Modifier.fillMaxSize()) {
        item { TopAppBar(title = { Text("设置") }) }
        item { Text("性能", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item {
            ListItem(
                headlineContent = { Text("CPU 线程数") },
                supportingContent = { Text("当前：${nThreads.toInt()} 线程") },
                leadingContent = { Icon(Icons.Default.Memory, null) },
                trailingContent = { Slider(nThreads, { nThreads = it }, valueRange = 1f..8f, steps = 6, modifier = Modifier.width(160.dp)) }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("GPU 加速 (Vulkan)") },
                supportingContent = { Text("实验性功能") },
                leadingContent = { Icon(Icons.Default.Speed, null) },
                trailingContent = { Switch(useGPU, { useGPU = it }) }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("BLAS 加速") },
                supportingContent = { Text("OpenBLAS 矩阵运算加速") },
                leadingContent = { Icon(Icons.Default.FlashOn, null) },
                trailingContent = { Switch(useBLAS, { useBLAS = it }) }
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { Text("关于", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item { ListItem(headlineContent = { Text("口袋训练 v0.1.0") }, supportingContent = { Text("基于 MobileFineTuner · GPL v3") }, leadingContent = { Icon(Icons.Default.Info, null) }) }
    }
}