@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.xex.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
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
import com.xex.filemanager.fs.Fs
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.fs.ShizukuManager
import com.xex.filemanager.fs.ShizukuState
import com.xex.filemanager.model.Cat
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.model.FileTypes
import com.xex.filemanager.model.Volume
import com.xex.filemanager.net.Net
import com.xex.filemanager.util.Utils
import com.xex.filemanager.util.dayLabel
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

private fun buildNetCrumbs(path: String, nav: (String) -> Unit): List<Crumb> {
    val conn = Net.connOf(path)
    val rootPath = if (conn != null) Net.rootOf(conn) else path
    val res = ArrayList<Crumb>()
    val icon = if (conn != null && conn.cloud) R.drawable.ic_cloud else R.drawable.ic_desktop
    val tint = if (conn != null && conn.cloud) Color(0xFF5B9FE8) else Color(0xFF8D6E63)
    res.add(Crumb(null, icon, tint) { nav(rootPath) })
    var cur = rootPath
    for (seg in path.removePrefix(rootPath).split('/').filter { it.isNotEmpty() }) {
        cur = "$cur/$seg"
        val target = cur
        res.add(Crumb(seg, null, null) { nav(target) })
    }
    return res
}

private fun buildCrumbs(path: String, volumes: List<Volume>, nav: (String) -> Unit): List<Crumb> {
    if (Net.isNet(path)) return buildNetCrumbs(path, nav)
    val vol = volumes.firstOrNull { path == it.path || path.startsWith(it.path + "/") }
    val res = ArrayList<Crumb>()
    var cur = ""
    var rest = path
    if (vol != null) {
        val vp = vol.path
        res.add(Crumb(null, R.drawable.ic_x_drive, null) { nav(vp) })
        cur = vp
        rest = path.removePrefix(vp)
    } else {
        res.add(Crumb(null, R.drawable.ic_x_slash, null) { nav("/") })
    }
    for (seg in rest.split('/').filter { it.isNotEmpty() }) {
        cur = "$cur/$seg"
        val target = cur
        res.add(Crumb(seg, null, null) { nav(target) })
    }
    return res
}

private fun volumeLabelOrName(path: String, volumes: List<Volume>): String {
    if (Net.isConnRoot(path)) return Net.connOf(path)?.name ?: "Подключение"
    val v = volumes.firstOrNull { it.path == path }
    if (v != null) return v.label
    if (path == "/") return "Система"
    return PathUtil.name(path)
}

@Composable
fun BrowserScreen(vm: AppViewModel, onMenu: () -> Unit) {
    val s = vm.settings
    val ctx = LocalContext.current
    val x = LocalXfm.current
    val top = vm.top
    val path = (top as? Screen.Browser)?.path
    val cat = (top as? Screen.Category)?.cat
    val isAlbums = cat == Cat.IMAGES || cat == Cat.VIDEO
    val isRecent = cat == Cat.RECENT

    val base = vm.searchResults ?: vm.items
    val shown = remember(base, vm.query, vm.searchResults, s.showHidden, s.sortBy, s.sortAsc, s.foldersFirst, isRecent) {
        var l = base
        if (!s.showHidden) l = l.filter { !it.name.startsWith(".") }
        val q = vm.query.trim()
        if (q.isNotEmpty() && vm.searchResults == null) l = l.filter { it.name.contains(q, ignoreCase = true) }
        if (isRecent) l.sortedByDescending { it.modified } else sortItems(l, s.sortBy, s.sortAsc, s.foldersFirst)
    }
    val shownAlbums = remember(vm.albums, vm.query) {
        val q = vm.query.trim()
        if (q.isEmpty()) vm.albums else vm.albums.filter { it.name.contains(q, ignoreCase = true) }
    }
    val selected = remember(shown, vm.selection) { shown.filter { it.path in vm.selection } }
    val selMode = vm.selection.isNotEmpty()

    var menuOpen by remember { mutableStateOf(false) }
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

    val crumbs: List<Crumb> = if (path != null) {
        buildCrumbs(path, vm.volumes) { vm.openFolder(it) }
    } else if (cat != null) {
        val ci = catIcon(cat)
        listOf(Crumb(null, ci.res, ci.tint) {})
    } else {
        emptyList()
    }

    val trailing: (@Composable () -> Unit)? = when {
        path != null && vm.volumes.any { it.path == path } -> {
            val v = vm.volumes.first { it.path == path }
            val frac = if (v.total > 0L) (v.total - v.free).toFloat() / v.total.toFloat() else 0f
            val pct = (frac * 100f).toInt()
            val c: @Composable () -> Unit = {
                PathButton(pct.toString() + "% занято", leading = { PieIcon(frac, x.buttonText) }) {
                    vm.push(Screen.Analyzer)
                }
            }
            c
        }
        isRecent -> {
            val c: @Composable () -> Unit = {
                PathButton("Очистить") {
                    s.recentClearedAt = System.currentTimeMillis()
                    vm.reload()
                }
            }
            c
        }
        else -> null
    }

    Scaffold(
        topBar = {
            Column {
                XfmTopBar(
                    title = {
                        if (selMode) {
                            Text("Выбрано: " + vm.selection.size)
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
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedPlaceholderColor = Color(0xFFBBBBBB),
                                    unfocusedPlaceholderColor = Color(0xFFBBBBBB),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(fr)
                            )
                        } else {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selMode) vm.clearSelection()
                            else if (vm.searchOpen) vm.closeSearch()
                            else onMenu()
                        }) {
                            Icon(
                                if (selMode || vm.searchOpen) Icons.Filled.Close else Icons.Filled.Menu,
                                contentDescription = "Меню"
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
                            if (!isRecent) {
                                IconButton(onClick = { vm.openSearch() }) { Icon(Icons.Filled.Search, "Поиск") }
                                IconButton(onClick = { sheetOpen = true }) {
                                    Icon(painterResource(R.drawable.ic_tune), "Вид и сортировка")
                                }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Обновить") },
                                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                        onClick = { menuOpen = false; vm.reload() }
                                    )
                                    if (path != null) {
                                        DropdownMenuItem(
                                            text = { Text("Новая папка") },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_new_folder), null) },
                                            onClick = {
                                                menuOpen = false
                                                vm.dialog = AppDialog.Input("Новая папка", "Имя папки", "", "Создать") { vm.createFolder(it) }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Новый файл") },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_file), null) },
                                            onClick = {
                                                menuOpen = false
                                                vm.dialog = AppDialog.Input("Новый файл", "Имя файла", "новый.txt", "Создать") { vm.createFile(it) }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Выбрать все") },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_select_all), null) },
                                        onClick = { menuOpen = false; vm.selectAll(shown) }
                                    )
                                    if (!isRecent) {
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
                                    }
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
                                    DropdownMenuItem(
                                        text = { Text("Анализ памяти") },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_pie), null) },
                                        onClick = { menuOpen = false; vm.push(Screen.Analyzer) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Настройки") },
                                        onClick = { menuOpen = false; vm.push(Screen.Prefs) }
                                    )
                                }
                            }
                        }
                    }
                )
                PathBar(crumbs, onHome = { vm.goHome() }, trailing = trailing)
                if (vm.searchOpen && path != null) {
                    Surface(color = MaterialTheme.colorScheme.background) {
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
                }
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
                                com.xex.filemanager.util.countLabel(cb.items.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { vm.cancelClipboard() }) { Text("Отмена") }
                        Button(onClick = { vm.paste() }) { Text("Вставить сюда") }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val err = vm.error
            val nothingYet = if (isAlbums) vm.albums.isEmpty() && vm.items.isEmpty() else vm.items.isEmpty()
            when {
                vm.loading && nothingYet -> {
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
                        if (path != null && Fs.needsPrivilege(path)) {
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
                isAlbums -> {
                    if (shownAlbums.isEmpty()) {
                        Text(
                            if (vm.query.isNotBlank()) "Ничего не найдено" else "Ничего нет",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            state = vm.gridState,
                            contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItems(shownAlbums, key = { it.path }) { a ->
                                AlbumCell(a, s.thumbs) { vm.push(Screen.Browser(a.path, true)) }
                            }
                        }
                    }
                }
                shown.isEmpty() -> {
                    Text(
                        if (vm.query.isNotBlank()) "Ничего не найдено" else if (isRecent) "Новых файлов нет" else "Папка пуста",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                isRecent -> {
                    val groups = remember(shown) { shown.groupBy { dayLabel(it.modified) } }
                    LazyColumn(Modifier.fillMaxSize()) {
                        groups.forEach { (label, list) ->
                            item(key = "hdr" + label) { SectionHeader(label) }
                            items(list, key = { it.path }) { item ->
                                FileRow(
                                    item = item,
                                    selected = item.path in vm.selection,
                                    compact = false,
                                    thumbs = s.thumbs,
                                    onClick = { if (selMode) vm.toggleSelect(item.path) else vm.open(item, shown) },
                                    onLong = { vm.toggleSelect(item.path) }
                                )
                            }
                        }
                    }
                }
                s.viewMode == 1 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(88.dp),
                        state = vm.gridState,
                        contentPadding = PaddingValues(4.dp),
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
        if (!one.isDir && com.xex.filemanager.fs.Archives.supported(one.name)) {
            DropdownMenuItem(
                text = { Text("Открыть как архив") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_unarchive), null) },
                onClick = { close(); vm.push(Screen.Archive(one.path)) }
            )
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
        text = { Text("Сжать в архив…") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_archive), null) },
        onClick = { close(); vm.dialog = AppDialog.Compress(sel) }
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
