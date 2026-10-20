package com.xex.filemanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.Screen
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.fs.archiveChildren
import com.xex.filemanager.model.FileItem

@Composable
fun ArchiveScreen(vm: AppViewModel, s: Screen.Archive) {
    val selMode = vm.selection.isNotEmpty()
    val shown = remember(vm.arcEntries, s.dir) {
        archiveChildren(vm.arcEntries, s.dir)
            .sortedWith(compareByDescending<FileItem> { it.isDir }.thenBy { it.name.lowercase() })
    }
    val selected = remember(shown, vm.selection) { shown.filter { it.path in vm.selection } }

    val crumbs = ArrayList<Crumb>()
    crumbs.add(Crumb(null, R.drawable.ic_archive, Color(0xFF8D6E63)) { vm.replaceTop(Screen.Archive(s.path, "")) })
    var cur = ""
    for (seg in s.dir.split('/').filter { it.isNotEmpty() }) {
        cur = if (cur.isEmpty()) seg else "$cur/$seg"
        val target = cur
        crumbs.add(Crumb(seg, null, null) { vm.replaceTop(Screen.Archive(s.path, target)) })
    }

    fun back() {
        if (selMode) {
            vm.clearSelection()
        } else if (s.dir.isNotEmpty()) {
            vm.replaceTop(Screen.Archive(s.path, s.dir.substringBeforeLast('/', "")))
        } else {
            vm.pop()
        }
    }

    Scaffold(
        topBar = {
            Column {
                XfmTopBar(
                    title = {
                        Text(
                            if (selMode) "Выбрано: " + vm.selection.size else PathUtil.name(s.path),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { back() }) {
                            Icon(
                                if (selMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (selMode) vm.extractArchive(s.path, selected) else vm.extractArchive(s.path, null)
                            vm.clearSelection()
                        }) {
                            Icon(painterResource(R.drawable.ic_unarchive), "Распаковать")
                        }
                    }
                )
                PathBar(crumbs, onHome = { vm.goHome() })
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val err = vm.arcError
            if (vm.arcLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (err != null) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            } else if (shown.isEmpty()) {
                Text(
                    "Пусто",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.path }) { item ->
                        FileRow(
                            item = item,
                            selected = item.path in vm.selection,
                            compact = false,
                            thumbs = false,
                            onClick = {
                                if (selMode) {
                                    vm.toggleSelect(item.path)
                                } else if (item.isDir) {
                                    vm.replaceTop(Screen.Archive(s.path, item.path))
                                } else {
                                    vm.openArchiveEntry(s.path, item)
                                }
                            },
                            onLong = { vm.toggleSelect(item.path) }
                        )
                    }
                }
            }
        }
    }
}
