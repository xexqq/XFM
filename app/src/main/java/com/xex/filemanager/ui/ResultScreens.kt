package com.xex.filemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.util.formatSize

@Composable
fun BigFilesScreen(vm: AppViewModel) {
    val files = vm.analysis?.bigFiles ?: emptyList()
    val selected = files.filter { it.path in vm.selection }
    val selMode = vm.selection.isNotEmpty()
    Scaffold(
        topBar = {
            XfmTopBar(
                title = {
                    Text(if (selMode) "Выбрано: " + vm.selection.size else "Большие файлы")
                },
                navigationIcon = {
                    IconButton(onClick = { if (selMode) vm.clearSelection() else vm.pop() }) {
                        Icon(if (selMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (selMode) {
                        IconButton(onClick = {
                            val gone = selected.map { it.path }.toSet()
                            vm.delete(selected) { vm.pruneAnalysis(gone) }
                        }) { Icon(Icons.Filled.Delete, "Удалить") }
                        IconButton(onClick = { vm.share(selected) }) { Icon(Icons.Filled.Share, "Поделиться") }
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (files.isEmpty()) {
                Text(
                    "Файлов больше 10 МБ нет",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(files, key = { it.path }) { f ->
                        FileRow(
                            item = f,
                            selected = f.path in vm.selection,
                            compact = false,
                            thumbs = vm.settings.thumbs,
                            onClick = { if (selMode) vm.toggleSelect(f.path) else vm.open(f, files) },
                            onLong = { vm.toggleSelect(f.path) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DupesScreen(vm: AppViewModel) {
    val groups = vm.analysis?.dupGroups ?: emptyList()
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    Scaffold(
        topBar = {
            XfmTopBar(
                title = { Text("Дубликаты файлов") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        "Дубликатов не найдено",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    OutlinedButton(onClick = {
                        picked = groups.flatMap { g -> g.drop(1).map { it.path } }.toSet()
                    }) { Text("Выбрать лишние") }
                    Button(
                        enabled = picked.isNotEmpty(),
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = {
                            val del = groups.flatten().filter { it.path in picked }
                            val gone = picked
                            vm.delete(del) {
                                vm.pruneAnalysis(gone)
                                picked = emptySet()
                            }
                        }
                    ) { Text("Удалить (" + picked.size + ")") }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    groups.forEachIndexed { gi, g ->
                        item(key = "h$gi") {
                            Text(
                                "Копий: " + g.size + " • по " + formatSize(g[0].size),
                                style = MaterialTheme.typography.labelLarge,
                                color = LocalXfm.current.section,
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
