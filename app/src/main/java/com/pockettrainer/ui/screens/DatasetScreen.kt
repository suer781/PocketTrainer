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
import com.pockettrainer.data.DatasetRepository
import com.pockettrainer.data.DatasetInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetScreen(navController: NavController) {
    val ctx = LocalContext.current
    val repo = remember { DatasetRepository(ctx) }
    val scope = rememberCoroutineScope()
    var datasets by remember { mutableStateOf(repo.getDatasets()) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf<DatasetInfo?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -&gt;
        uri?.let {
            scope.launch {
                isProcessing = true
                errorMessage = null
                val result = repo.importDataset(it)
                isProcessing = false
                if (result.isSuccess) {
                    datasets = repo.getDatasets()
                } else {
                    errorMessage = result.exceptionOrNull()?.message
                }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TopAppBar(title = { Text("数据集") }, actions = {
                IconButton(onClick = { importLauncher.launch(arrayOf("application/jsonl", "text/*", "application/json", "text/csv")) }) {
                    Icon(Icons.Default.Add, "导入")
                }
            })
        }

        if (isProcessing) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("正在处理数据集...")
                    }
                }
            }
        }

        errorMessage?.let { err -&gt;
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("数据集管理", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${datasets.size} 个数据集 · 支持 JSONL, CSV, TXT 格式", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (datasets.isEmpty() &amp;&amp; !isProcessing) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CloudUpload,
                            null,
                            Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("还没有数据集", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("点击右上角“+”添加训练数据", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        }

        items(datasets, key = { it.path }) { ds -&gt;
            Card(
                onClick = { showInfoDialog = ds },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                ListItem(
                    headlineContent = { Text(ds.name, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Text(
                            "${ds.format} · ${ds.sampleCount} 样本 · ${formatSize(ds.size)}",
                            fontSize = 12.sp
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch {
                                repo.deleteDataset(ds)
                                datasets = repo.getDatasets()
                            }
                        }) {
                            Icon(Icons.Default.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    showInfoDialog?.let { ds -&gt;
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            title = { Text(ds.name) },
            text = {
                Column {
                    Text("格式: ${ds.format}")
                    Text("样本数: ${ds.sampleCount}")
                    Text("大小: ${formatSize(ds.size)}")
                    Text("路径: ${ds.path}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = null }) {
                    Text("关闭")
                }
            }
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes &lt; 1024 -&gt; "${bytes}B"
    bytes &lt; 1024 * 1024 -&gt; "${bytes / 1024}KB"
    else -&gt; String.format("%.1fMB", bytes / 1048576.0)
}
