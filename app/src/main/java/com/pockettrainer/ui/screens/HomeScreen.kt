package com.pockettrainer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun HomeScreen(navController: NavController) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("🧠 口袋训练", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("在手机上训练你的专属 AI", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text("快速开始", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    listOf("1" to ("下载模型" to "选择一个适合手机的小模型"),
                           "2" to ("准备数据" to "准备你要训练的对话数据"),
                           "3" to ("开始训练" to "一键微调，等几分钟就好"),
                           "4" to ("测试效果" to "看看训练后的模型表现如何")
                    ).forEach { (n, pair) ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(n, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(pair.first, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                Text(pair.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(onClick = { navController.navigate("models") }, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Text("模型管理", fontWeight = FontWeight.Bold)
                    }
                }
                Card(onClick = { navController.navigate("train") }, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Text("开始训练", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Card(onClick = { navController.navigate("train") }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("AI 数据清洗", fontWeight = FontWeight.Bold)
                        Text("让 AI 帮你整理训练数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}