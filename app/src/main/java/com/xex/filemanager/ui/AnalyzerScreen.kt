@file:OptIn(ExperimentalMaterial3Api::class)

package com.xex.filemanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.Screen
import com.xex.filemanager.fs.FileOps
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AnalyzerScreen(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    val vols = vm.volumes
    var rootIdx by remember { mutableStateOf(0) }
    val root = vols.getOrNull(rootIdx)?.path ?: "/storage/emulated/0"

    var job by remember { mutableStateOf<Job?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(0) }

    // Папки
    var folderPath by remember { mutableStateOf<String?>(null) }
    var folderRows by remember { mutableStateOf<List<Pair<FileItem, Long>>>(emptyList()) }
    // Крупные файлы
    var bigFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    // Дубликаты
    var groups by remember { mutableStateOf<List<List<FileItem>>>(emptyList()) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun cancel() {
        job?.cancel()
        scanning = false
    }

    fun scanFolders(path: String) {
        job?.cancel()
        folderPath = path
        folderRows = emptyList()
        scanning = true
        job = scope.launch {
            val r = withContext(Dispatchers.IO) { FileOps.childSizes(path, this) }
            folderRows = r
            scanning = false
        }
    }

    fun scanBig() {
        job?.cancel()
        bigFiles = emptyList()
        scanned = 0
        scanning = true
        job = scope.launch {
            val r = withContext(Dispatchers.IO) { FileOps.largest(root, this, 100) { scanned = it } }
            bigFiles = r
            scanning = false
        }
    }

    fun scanDupes() {
        job?.cancel()
        groups = emptyList()
        picked = emptySet()
        scanned = 0
        scanning = true
        job = scope.launch {
            val r = withContext(Dispatchers.IO) { FileOps.duplicates(root, this) { scanned = it } }
            groups = r
            scanning = false
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Анализ памяти") },
                    navigationIcon = {
                        IconButton(onClick = { cancel(); vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    }
                )
                TabRow(selectedTabIndex = tab) {
                    listOf("Папки", "Крупные", "Дубликаты").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { cancel(); tab = i }, text = { Text(label) })
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (vols.size > 1) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vols.forEachIndexed { i, v ->
                        FilterChip(
                            selected = rootIdx == i,
                            onClick = { cancel(); rootIdx = i; folderPath = null; folderRows = emptyList(); bigFiles = emptyList(); groups = emptyList() },
                            label = { Text(v.label) }
                        )
                    }
                }
            }
            when (tab) {
                0 -> {
                    val cur = folderPath
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            cur ?: "Нажмите «Сканировать»",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (cur != null && cur != root) {
                            OutlinedButton(onClick = { scanFolders(PathUtil.parent(cur) ?: root) }) { Text("Вверх") }
                            Spacer(Modifier.width(6.dp))
                        }
                        Button(onClick = { scanFolders(cur ?: root) }) { Text("Сканировать") }
                    }
                    if (scanning) Busy("Считаю размеры…")
                    val maxSize = (folderRows.firstOrNull()?.second ?: 1L).coerceAtLeast(1L)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(folderRows, key = { it.first.path }) { row ->
                            val item = row.first
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (item.isDir) scanFolders(item.path) else vm.open(item, listOf(item))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatSize(row.second), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(5.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth((row.second.toFloat() / maxSize.toFloat()).coerceIn(0.01f, 1f))
                                            .fillMaxHeight()
                                            .background(typeColor(item))
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Топ-100 самых больших файлов", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Button(onClick = { scanBig() }) { Text("Сканировать") }
                    }
                    if (scanning) Busy("Просмотрено файлов: $scanned")
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(bigFiles, key = { it.path }) { item ->
                            FileRow(
                                item = item,
                                selected = false,
                                compact = false,
                                thumbs = vm.settings.thumbs,
                                onClick = { vm.open(item, bigFiles) },
                                onLong = {
                                    vm.delete(listOf(item)) { bigFiles = bigFiles.filter { f -> f.path != item.path } }
                                }
                            )
                        }
                    }
                }
                else -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Одинаковые файлы (по размеру и MD5)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Button(onClick = { scanDupes() }) { Text("Искать") }
                    }
                    if (scanning) Busy("Просмотрено файлов: $scanned")
                    if (groups.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = {
                                picked = groups.flatMap { g -> g.drop(1).map { it.path } }.toSet()
                            }) { Text("Выбрать лишние") }
                            Button(
                                enabled = picked.isNotEmpty(),
                                onClick = {
                                    val del = groups.flatten().filter { it.path in picked }
                                    vm.delete(del) {
                                        groups = groups.map { g -> g.filter { it.path !in picked } }.filter { it.size > 1 }
                                        picked = emptySet()
                                    }
                                }
                            ) { Text("Удалить (${picked.size})") }
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        groups.forEachIndexed { gi, g ->
                            item(key = "h$gi") {
                                Text(
                                    "Копий: ${g.size} • по ${formatSize(g[0].size)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
                                )
                            }
                            items(g, key = { it.path }) { f ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { picked = if (f.path in picked) picked - f.path else picked + f.path }
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = f.path in picked,
                                        onCheckedChange = { picked = if (f.path in picked) picked - f.path else picked + f.path }
                                    )
                                    Text(
                                        f.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Busy(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
