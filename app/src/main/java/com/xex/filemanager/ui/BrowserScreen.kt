@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.xex.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xex.filemanager.AppDialog
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.Screen
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.fs.ShizukuManager
import com.xex.filemanager.fs.ShizukuState
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.model.FileTypes
import com.xex.filemanager.model.Volume
import com.xex.filemanager.util.Utils
import com.xex.filemanager.util.countLabel
import kotlinx.coroutines.delay

fun sortItems(list: List<FileItem>, by: Int, asc: Boolean, foldersFirst: Boolean): List<FileItem> {
    val cmp: Comparator<FileItem> = when (by) {
        1 -> compareBy<FileItem> { it.size }
        2 -> compareBy<FileItem> { it.modified }
        3 -> compareBy<FileItem> { it.ext }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        else -> compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    val ordered = if (asc) cmp else cmp.reversed()
    val full = compareByDescending<FileItem> { foldersFirst && it.isDir }.then(ordered)
    return list.sortedWith(full)
}

private fun crumbs(path: String, volumes: List<Volume>): List<Pair<String, String>> {
    val vol = volumes.firstOrNull { path == it.path || path.startsWith(it.path + "/") }
    val res = ArrayList<Pair<String, String>>()
    var cur = ""
    var rest = path
    if (vol != null) {
        res.add(Pair(vol.label, vol.path))
        cur = vol.path
        rest = path.removePrefix(vol.path)
    } else {
        res.add(Pair("/", "/"))
    }
    for (seg in rest.split('/').filter { it.isNotEmpty() }) {
        cur = "$cur/$seg"
        res.add(Pair(seg, cur))
    }
    return res
}

@Composable
private fun Breadcrumbs(path: String, volumes: List<Volume>, onClick: (String) -> Unit) {
    val segs = remember(path, volumes) { crumbs(path, volumes) }
    val scroll = rememberScrollState()
    LaunchedEffect(path, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segs.forEachIndexed { i, seg ->
            if (i > 0) {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp))
            }
            val last = i == segs.lastIndex
            Text(
                text = seg.first,
                style = MaterialTheme.typography.labelLarge,
                color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable { onClick(seg.second) }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun BrowserScreen(vm: AppViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current
    val top = vm.top
    val path = (top as? Screen.Browser)?.path
    val cat = (top as? Screen.Category)?.cat

    val base = vm.searchResults ?: vm.items
    val shown = remember(base, vm.query, vm.searchResults, s.showHidden, s.sortBy, s.sortAsc, s.foldersFirst) {
        var l = base
        if (!s.showHidden) l = l.filter { !it.name.startsWith(".") }
        val q = vm.query.trim()
        if (q.isNotEmpty() && vm.searchResults == null) l = l.filter { it.name.contains(q, ignoreCase = true) }
        sortItems(l, s.sortBy, s.sortAsc, s.foldersFirst)
    }
    val selected = remember(shown, vm.selection) { shown.filter { it.path in vm.selection } }
    val selMode = vm.selection.isNotEmpty()

    var menuOpen by remember { mutableStateOf(false) }
    var fabOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        if (vm.scrollPath != path) {
            vm.scrollPath = path
            vm.listState.scrollToItem(0)
            vm.gridState.scrollToItem(0)
        }
    }
    LaunchedEffect(vm.query, vm.deepSearch) {
        delay(350)
        vm.runSearch()
    }

    val title = when {
        cat != null -> cat.title
        path != null -> volumeLabelOrName(path, vm.volumes)
        else -> ""
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (selMode) {
                            Text("Выбрано: ${vm.selection.size}")
                        } else if (vm.searchOpen) {
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(Unit) { fr.requestFocus() }
                            TextField(
                                value = vm.query,
                                onValueChange = { vm.query = it },
                                placeholder = { Text("Поиск") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { vm.runSearch() }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(fr)
                            )
                        } else {
                            Column {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    countLabel(shown.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selMode) vm.clearSelection() else vm.onBack()
                        }) {
                            Icon(
                                if (selMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    },
                    actions = {
                        if (selMode) {
                            IconButton(onClick = { vm.setClipboard(selected, false) }) {
                                Icon(painterResource(R.drawable.ic_copy), "Копировать")
                            }
                            IconButton(onClick = { vm.setClipboard(selected, true) }) {
                                Icon(painterResource(R.drawable.ic_cut), "Вырезать")
                            }
                            IconButton(onClick = { vm.delete(selected) }) {
                                Icon(Icons.Filled.Delete, "Удалить")
                            }
                            IconButton(onClick = { vm.share(selected) }) {
                                Icon(Icons.Filled.Share, "Поделиться")
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    SelectionMenu(vm, selected, shown) { menuOpen = false }
                                }
                            }
                        } else if (vm.searchOpen) {
                            if (vm.query.isNotEmpty()) {
                                IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Clear, "Очистить") }
                            }
                        } else {
                            IconButton(onClick = { vm.openSearch() }) { Icon(Icons.Filled.Search, "Поиск") }
                            IconButton(onClick = { sheetOpen = true }) {
                                Icon(painterResource(R.drawable.ic_sort), "Вид и сортировка")
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Обновить") },
                                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                        onClick = { menuOpen = false; vm.reload() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Выбрать все") },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_select_all), null) },
                                        onClick = { menuOpen = false; vm.selectAll(shown) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (s.showHidden) "Скрыть скрытые файлы" else "Показать скрытые файлы") },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(if (s.showHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                                null
                                            )
                                        },
                                        onClick = { menuOpen = false; s.showHidden = !s.showHidden }
                                    )
                                    if (path != null) {
                                        DropdownMenuItem(
                                            text = { Text(if (s.isBookmarked(path)) "Убрать из закладок" else "В закладки") },
                                            leadingIcon = {
                                                Icon(
                                                    painterResource(if (s.isBookmarked(path)) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border),
                                                    null
                                                )
                                            },
                                            onClick = { menuOpen = false; s.toggleBookmark(path) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Копировать путь") },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_copy_path), null) },
                                            onClick = { menuOpen = false; Utils.copyText(ctx, path) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
                if (path != null && !selMode) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Breadcrumbs(path, vm.volumes) { vm.openFolder(it) }
                    }
                }
                if (vm.searchOpen && path != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = vm.deepSearch,
                            onClick = { vm.deepSearch = !vm.deepSearch },
                            label = { Text("Во вложенных папках") }
                        )
                        if (vm.searching) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            val cb = vm.clipboard
            if (cb != null && path != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (cb.cut) "Перемещение" else "Копирование",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                countLabel(cb.items.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { vm.cancelClipboard() }) { Text("Отмена") }
                        Button(onClick = { vm.paste() }) { Text("Вставить сюда") }
                    }
                }
            }
        },
        floatingActionButton = {
            if (path != null && !selMode && !vm.searchOpen) {
                Box {
                    FloatingActionButton(onClick = { fabOpen = true }) {
                        Icon(Icons.Filled.Add, "Создать")
                    }
                    DropdownMenu(expanded = fabOpen, onDismissRequest = { fabOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Новая папка") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_new_folder), null) },
                            onClick = {
                                fabOpen = false
                                vm.dialog = AppDialog.Input("Новая папка", "Имя папки", "", "Создать") { vm.createFolder(it) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Новый файл") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_file), null) },
                            onClick = {
                                fabOpen = false
                                vm.dialog = AppDialog.Input("Новый файл", "Имя файла", "новый.txt", "Создать") { vm.createFile(it) }
                            }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val err = vm.error
            when {
                vm.loading && vm.items.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                err != null -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(painterResource(R.drawable.ic_shield), null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(12.dp))
                        Text(err, style = MaterialTheme.typography.titleMedium)
                        if (path != null && com.xex.filemanager.fs.Fs.needsPrivilege(path)) {
                            Spacer(Modifier.size(8.dp))
                            val st = ShizukuManager.state
                            Text(
                                if (st == ShizukuState.CONNECTED) "Shizuku подключена, но доступ всё равно закрыт (нужен root-режим Shizuku)"
                                else "Эта папка защищена Android. Подключите Shizuku, чтобы открыть её.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.size(12.dp))
                            Button(onClick = { vm.push(Screen.Prefs) }) { Text("Настройки Shizuku") }
                        }
                    }
                }
                shown.isEmpty() -> {
                    Text(
                        if (vm.query.isNotBlank()) "Ничего не найдено" else "Папка пуста",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                s.viewMode == 1 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        state = vm.gridState,
                        contentPadding = PaddingValues(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(shown, key = { it.path }) { item ->
                            FileCell(
                                item = item,
                                selected = item.path in vm.selection,
                                thumbs = s.thumbs,
                                onClick = { if (selMode) vm.toggleSelect(item.path) else vm.open(item, shown) },
                                onLong = { vm.toggleSelect(item.path) }
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(state = vm.listState, modifier = Modifier.fillMaxSize()) {
                        items(shown, key = { it.path }) { item ->
                            FileRow(
                                item = item,
                                selected = item.path in vm.selection,
                                compact = s.viewMode == 2,
                                thumbs = s.thumbs,
                                onClick = { if (selMode) vm.toggleSelect(item.path) else vm.open(item, shown) },
                                onLong = { vm.toggleSelect(item.path) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            ViewSheet(vm)
        }
    }
}

private fun volumeLabelOrName(path: String, volumes: List<Volume>): String {
    val v = volumes.firstOrNull { it.path == path }
    if (v != null) return v.label
    if (path == "/") return "Корень /"
    return PathUtil.name(path)
}

@Composable
private fun SelectionMenu(vm: AppViewModel, sel: List<FileItem>, shown: List<FileItem>, close: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Выбрать все") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_select_all), null) },
        onClick = { close(); vm.selectAll(shown) }
    )
    DropdownMenuItem(
        text = { Text("Инвертировать выбор") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_checklist), null) },
        onClick = { close(); vm.invertSelection(shown) }
    )
    if (sel.size == 1) {
        val one = sel[0]
        DropdownMenuItem(
            text = { Text("Переименовать") },
            leadingIcon = { Icon(Icons.Filled.Edit, null) },
            onClick = {
                close()
                vm.dialog = AppDialog.Input("Переименовать", "Новое имя", one.name, "ОК") { n -> vm.rename(one, n) }
            }
        )
        DropdownMenuItem(
            text = { Text("Свойства") },
            leadingIcon = { Icon(Icons.Filled.Info, null) },
            onClick = { close(); vm.dialog = AppDialog.Properties(one) }
        )
        DropdownMenuItem(
            text = { Text("Открыть как…") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_open_in_new), null) },
            onClick = { close(); vm.dialog = AppDialog.OpenAs(one) }
        )
        if (!one.isDir && one.ext in FileTypes.zipLike) {
            DropdownMenuItem(
                text = { Text("Распаковать…") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_unarchive), null) },
                onClick = { close(); vm.dialog = AppDialog.ArchiveChoice(one) }
            )
        }
    } else {
        DropdownMenuItem(
            text = { Text("Переименовать группой") },
            leadingIcon = { Icon(Icons.Filled.Edit, null) },
            onClick = { close(); vm.dialog = AppDialog.MultiRename(sel) }
        )
    }
    DropdownMenuItem(
        text = { Text("Сжать в ZIP") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_archive), null) },
        onClick = {
            close()
            val def = if (sel.size == 1) sel[0].name.substringBeforeLast('.', sel[0].name) else "Архив"
            vm.dialog = AppDialog.Input("Сжать в ZIP", "Имя архива", def, "Сжать") { n -> vm.compress(sel, n) }
        }
    )
    DropdownMenuItem(
        text = { Text("Закладка") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_bookmark), null) },
        onClick = { close(); vm.toggleBookmark(sel) }
    )
    DropdownMenuItem(
        text = { Text("Скрыть / показать (точка в имени)") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_visibility_off), null) },
        onClick = { close(); vm.toggleHidden(sel) }
    )
}

@Composable
private fun ViewSheet(vm: AppViewModel) {
    val s = vm.settings
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding()) {
        Text("Вид", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("Список", "Сетка", "Компактно").forEachIndexed { i, label ->
                FilterChip(selected = s.viewMode == i, onClick = { s.viewMode = i }, label = { Text(label) })
            }
        }
        Text("Сортировка", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("Имя", "Размер", "Дата", "Тип").forEachIndexed { i, label ->
                FilterChip(selected = s.sortBy == i, onClick = { s.sortBy = i }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            FilterChip(selected = s.sortAsc, onClick = { s.sortAsc = true }, label = { Text("По возрастанию") })
            FilterChip(selected = !s.sortAsc, onClick = { s.sortAsc = false }, label = { Text("По убыванию") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            FilterChip(selected = s.foldersFirst, onClick = { s.foldersFirst = !s.foldersFirst }, label = { Text("Папки сверху") })
            FilterChip(selected = s.showHidden, onClick = { s.showHidden = !s.showHidden }, label = { Text("Скрытые") })
            FilterChip(selected = s.thumbs, onClick = { s.thumbs = !s.thumbs }, label = { Text("Миниатюры") })
        }
    }
}
