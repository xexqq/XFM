@file:OptIn(ExperimentalMaterial3Api::class)

package com.xex.filemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xex.filemanager.AppDialog
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.util.formatDate
import com.xex.filemanager.util.formatSize

@Composable
fun TrashScreen(vm: AppViewModel) {
    val entries = vm.trashEntries
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val chosen = entries.filter { it.id in selected }

    Scaffold(
        topBar = {
            XfmTopBar(
                title = {
                    Column {
                        Text("Корзина")
                        Text(
                            if (selected.isEmpty()) "${entries.size} шт. • ${formatSize(entries.sumOf { it.size })}" else "Выбрано: ${selected.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFBDBDBD)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { vm.restoreFromTrash(chosen); selected = emptySet() }) {
                            Icon(painterResource(R.drawable.ic_restore_page), "Восстановить")
                        }
                        IconButton(onClick = {
                            val list = chosen
                            vm.dialog = AppDialog.Confirm("Удалить навсегда", "Удалить выбранное без возможности восстановления?", "Удалить") {
                                vm.purgeFromTrash(list)
                            }
                            selected = emptySet()
                        }) { Icon(painterResource(R.drawable.ic_delete_sweep), "Удалить навсегда") }
                    } else if (entries.isNotEmpty()) {
                        IconButton(onClick = {
                            vm.dialog = AppDialog.Confirm("Очистить корзину", "Удалить всё содержимое корзины навсегда?", "Очистить") {
                                vm.emptyTrash()
                            }
                        }) { Icon(painterResource(R.drawable.ic_delete_sweep), "Очистить") }
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (entries.isEmpty()) {
                Text(
                    "Корзина пуста",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.id }) { e ->
                        val on = e.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (on) selected - e.id else selected + e.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = { selected = if (on) selected - e.id else selected + e.id })
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    e.origPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatDate(e.deletedAt) + " • " + formatSize(e.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
