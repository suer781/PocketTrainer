package com.pockettrainer.ui.screens

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
import androidx.navigation.NavController
import com.pockettrainer.data.ModelRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(navController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val repo = remember { ModelRepository(context) }
    val scope = rememberCoroutineScope()
    var importedModels by remember { mutableStateOf(repo.getDownloadedModels()) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    // Import dialog state
    var showImportDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableFloatStateOf(0f) }
    var importError by remember { mutableStateOf<String?>(null) }

    // SAF launcher
    val safLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                importError = null
                val result = repo.importFromUri(uri)
                isImporting = false
                if (result.isSuccess) importedModels = repo.getDownloadedModels()
                else importError = result.exceptionOrNull()?.message
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("模型管理") }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "返回") }
            })
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("推荐模型") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("已下载") })
            }
            when (selectedTab) {
                0 -> RecommendedTab()
                1 -> DownloadedTab(
                    models = importedModels,
                    dateFormat = dateFormat,
                    isImporting = isImporting,
                    importProgress = importProgress,
                    importError = importError,
                    onImportClick = { showImportDialog = true },
                    onModelClick = { path -> navController.previousBackStackEntry?.savedStateHandle?.set("modelPath", path); navController.popBackStack() }
                )
            }
        }
    }

    // Import method chooser
    if (showImportDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showImportDialog = false }) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("导入模型", style = MaterialTheme.typography.headlineSmall)
                    Text("选择模型文件来源", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showImportDialog = false
                            safLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhoneAndroid, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("从本地文件导入")
                    }
                    Text("支持手机存储和已连接的云盘（Google Drive、OneDrive 等）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = {
                            showImportDialog = false
                            showUrlDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("从链接导入")
                    }
                    Text("支持 Hugging Face、网盘直链、NAS 地址等",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // URL import dialog
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) showUrlDialog = false },
            title = { Text("从链接导入") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("粘贴模型文件的下载链接", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("URL") },
                        placeholder = { Text("https://huggingface.co/...") },
                        singleLine = true,
                        enabled = !isImporting,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isImporting) {
                        LinearProgressIndicator(progress = { importProgress }, modifier = Modifier.fillMaxWidth())
                        Text("${(importProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    importError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            scope.launch {
                                isImporting = true
                                importError = null
                                importProgress = 0f
                                val result = repo.importFromUrl(urlInput.trim()) { importProgress = it }
                                isImporting = false
                                if (result.isSuccess) {
                                    importedModels = repo.getDownloadedModels()
                                    showUrlDialog = false
                                    urlInput = ""
                                } else {
                                    importError = result.exceptionOrNull()?.message
                                }
                            }
                        }
                    },
                    enabled = urlInput.isNotBlank() && !isImporting
                ) { Text("下载") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }, enabled = !isImporting) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RecommendedTab() {
    val models = listOf(
        Triple("Qwen2 0.5B", "Qwen/Qwen2-0.5B-Instruct-GGUF", "350MB"),
        Triple("Qwen2 1.5B", "Qwen/Qwen2-1.5B-Instruct-GGUF", "1.0GB"),
        Triple("Gemma 2B", "google/gemma-2-2b-it-GGUF", "1.5GB"),
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("选择一个适合你手机的模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("手机内存 < 6GB 选 0.5B，> 8GB 选 1.5B", Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
        items(models) { (name, repoId, size) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(repoId, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("📦 $size", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DownloadedTab(
    models: List<File>,
    dateFormat: SimpleDateFormat,
    isImporting: Boolean,
    importProgress: Float,
    importError: String?,
    onImportClick: () -> Unit,
    onModelClick: (String) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        if (models.isEmpty() && !isImporting) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("还没有导入任何模型", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("点击下方按钮导入模型文件", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(models) { model ->
                    Card(
                        onClick = { onModelClick(model.absolutePath) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(model.name, fontWeight = FontWeight.Bold)
                                Text("%.1f MB · %s".format(model.length() / (1024.0 * 1024.0), dateFormat.format(Date(model.lastModified()))),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
        }
        // FAB
        FloatingActionButton(
            onClick = onImportClick,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "导入模型")
        }
        // Progress overlay
        if (isImporting) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                if (importProgress > 0f) Text("${(importProgress * 100).toInt()}%") else Text("导入中...")
            }
        }
        // Error snackbar
        importError?.let {
            Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                Text("导入失败: $it")
            }
        }
    }
}
