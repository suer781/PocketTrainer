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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pockettrainer.data.ModelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(navController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("模型管理") })
        TabRow(selectedTabIndex = selectedTab) {
            listOf("推荐模型", "已下载").forEachIndexed { i, t -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) }) }
        }
        when (selectedTab) { 0 -> RecommendedTab(); 1 -> DownloadedTab() }
    }
}

@Composable
fun RecommendedTab() {
    val models = listOf(
        ModelInfo("Qwen2 0.5B", "Qwen/Qwen2-0.5B-Instruct-GGUF", "350MB", "最小，速度最快", "⭐⭐⭐"),
        ModelInfo("Qwen2 1.5B", "Qwen/Qwen2-1.5B-Instruct-GGUF", "1.0GB", "效果更好，推荐", "⭐⭐⭐⭐"),
        ModelInfo("Gemma 2B", "google/gemma-2-2b-it-GGUF", "1.5GB", "Google 出品", "⭐⭐⭐⭐"),
    )
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("选择一个适合你手机的模型", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("手机内存 < 6GB 选 0.5B，> 8GB 选 1.5B", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(models) { model ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(model.name, fontWeight = FontWeight.Bold)
                            Text(model.size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(model.rating)
                    }
                    Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { /* reserved for future model browser */ }, Modifier.fillMaxWidth(), enabled = false) {
                        Text("即将推出")
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedTab() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("还没有导入任何模型", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}