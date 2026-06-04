package com.pockettrainer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetScreen(navController: NavController) {
    val ctx = LocalContext.current
    val dsDir = remember { File(ctx.filesDir, "datasets").also { it.mkdirs() } }
    var datasets by remember { mutableStateOf(loadDatasets(dsDir)) }
    var showImport by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            ctx.contentResolver.openInputStream(it)?.use { inp ->
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "dataset.jsonl"
                val target = File(dsDir, name)
                target.outputStream().use { out -> inp.copyTo(out) }
                datasets = loadDatasets(dsDir)
                showImport = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { TopAppBar(title = { Text("数据集") }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "返回") }
        }) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("数据集管理", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${datasets.size} 个数据集", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = { importLauncher.launch(arrayOf("application/jsonl","text/*","application/json","text/csv")) }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("导入")
                    }
                }
            }
        }
        if (datasets.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudUpload, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("还没有数据集", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("点击上方“导入”添加训练数据", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        }
        items(datasets, key = { it.name }) { ds ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                ListItem(
                    headlineContent = { Text(ds.name, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Text("${detectLabel(ds)} · ${formatSize(ds.length())} · ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ds.lastModified()))}", fontSize = 12.sp)
                    },
                    leadingContent = { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        IconButton(onClick = { ds.delete(); datasets = loadDatasets(dsDir) }) { Icon(Icons.Default.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) }
                    }
                )
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

private fun loadDatasets(dir: File): List<File> = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

private fun detectLabel(f: File): String {
    if (f.extension == "csv") return "CSV"
    val first = f.bufferedReader().use { it.readLine() } ?: return "文本"
    return try {
        val json = com.google.gson.JsonParser.parseString(first).asJsonObject
        when {
            json.has("messages") -> "对话 JSONL"
            json.has("instruction") -> "Alpaca"
            json.has("conversations") -> "ShareGPT"
            else -> "文本"
        }
    } catch (_: Exception) { "文本" }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> String.format("%.1fMB", bytes / 1048576.0)
}
