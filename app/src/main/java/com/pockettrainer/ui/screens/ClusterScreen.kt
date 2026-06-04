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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

private data class ClusterPeer(val id: String, val name: String, val ip: String, val status: String, val role: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterScreen(navController: NavController) {
    var clusterActive by remember { mutableStateOf(false) }
    var port by remember { mutableStateOf("8400") }
    var bindAddr by remember { mutableStateOf("0.0.0.0") }
    val peers = remember { mutableStateListOf<ClusterPeer>() }
    val selfId = remember { java.util.UUID.randomUUID().toString().take(8) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { TopAppBar(title = { Text("分布式集群") }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "返回") }
        }) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = if (clusterActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (clusterActive) Icons.Default.Hub else Icons.Default.CloudOff, null, Modifier.size(40.dp), tint = if (clusterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (clusterActive) "集群运行中" else "集群未启动", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(if (clusterActive) "监听 $bindAddr:$port · ${peers.size} 台设备" else "启动后其他设备可加入", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(clusterActive, { clusterActive = it })
                }
            }
        }
        if (clusterActive) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("已连接设备", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { peers.add(ClusterPeer(java.util.UUID.randomUUID().toString().take(8), "设备-${peers.size + 1}", "192.168.1.${100 + peers.size}", "在线", if (peers.isEmpty()) "主节点" else "工作节点")) }, Modifier.height(36.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("模拟加入", fontSize = 13.sp)
                    }
                }
            }
            if (peers.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.WifiFind, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(12.dp))
                            Text("等待设备加入...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("其他设备输入 ${bindAddr}:$port 即可连接", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            items(peers, key = { it.id }) { peer ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(peer.name, fontWeight = FontWeight.Medium)
                            if (peer.role == "主节点") { Spacer(Modifier.width(8.dp)); SuggestionChip(onClick = {}, label = { Text("主", fontSize = 11.sp) }, Modifier.height(24.dp)) }
                        } },
                        supportingContent = { Text("${peer.ip} · ${peer.role}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = { Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(peer.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { peers.remove(peer) }) { Icon(Icons.Default.RemoveCircleOutline, "移除", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    )
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 16.dp)) }
        item {
            Text("网络配置", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        item {
            OutlinedTextField(value = port, onValueChange = { port = it.filter { c -> c.isDigit() } }, label = { Text("端口") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.SettingsEthernet, null) })
        }
        item {
            OutlinedTextField(value = bindAddr, onValueChange = { bindAddr = it }, label = { Text("绑定地址") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Wifi, null) })
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(16.dp)) {
                    Text("使用说明", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    val tips = listOf("1. 打开集群开关，本机将作为主节点启动", "2. 其他设备在训练页输入本机 IP 和端口加入", "3. 主节点负责模型分片和梯度聚合", "4. 工作节点负责本地前向/反向计算", "5. 所有设备需在同一局域网内")
                    tips.forEach { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
