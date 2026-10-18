@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.xex.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.Screen
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.fs.ShizukuManager
import com.xex.filemanager.fs.ShizukuState
import com.xex.filemanager.model.Cat
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.model.Volume
import com.xex.filemanager.util.Utils
import com.xex.filemanager.util.formatSize
import java.io.File

@Composable
fun HomeScreen(vm: AppViewModel, onMenu: () -> Unit) {
    val s = vm.settings
    val root = Utils.primaryRoot()
    val connected = ShizukuManager.state == ShizukuState.CONNECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("XFM Файлы") },
                navigationIcon = {
                    IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Меню") }
                },
                actions = {
                    IconButton(onClick = { vm.push(Screen.Category(Cat.RECENT)) }) {
                        Icon(painterResource(R.drawable.ic_history), "Недавние")
                    }
                    IconButton(onClick = { vm.push(Screen.Prefs) }) { Icon(Icons.Filled.Settings, "Настройки") }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item { SectionTitle("Хранилище") }
            items(vm.volumes.size) { i ->
                StorageCard(vm.volumes[i]) { vm.push(Screen.Browser(vm.volumes[i].path)) }
            }

            item { SectionTitle("Категории") }
            item {
                val cats = Cat.values().toList()
                Column(Modifier.padding(horizontal = 8.dp)) {
                    cats.chunked(4).forEach { rowCats ->
                        Row(Modifier.fillMaxWidth()) {
                            rowCats.forEach { c ->
                                Tile(
                                    title = c.title,
                                    iconRes = c.icon,
                                    color = c.color,
                                    modifier = Modifier.weight(1f)
                                ) { vm.push(Screen.Category(c)) }
                            }
                            repeat(4 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Tile(
                            title = "Загрузки",
                            iconRes = R.drawable.ic_download,
                            color = Color(0xFF42A5F5),
                            modifier = Modifier.weight(1f)
                        ) { vm.push(Screen.Browser("$root/Download")) }
                        Tile(
                            title = "Камера",
                            iconRes = R.drawable.ic_image,
                            color = Color(0xFFEC407A),
                            modifier = Modifier.weight(1f)
                        ) { vm.push(Screen.Browser("$root/DCIM")) }
                        Tile(
                            title = "Документы",
                            iconRes = R.drawable.ic_folder,
                            color = Color(0xFFFFB300),
                            modifier = Modifier.weight(1f)
                        ) { vm.push(Screen.Browser("$root/Documents")) }
                        Tile(
                            title = "Музыка",
                            iconRes = R.drawable.ic_audio,
                            color = Color(0xFF26C6DA),
                            modifier = Modifier.weight(1f)
                        ) { vm.push(Screen.Browser("$root/Music")) }
                    }
                }
            }

            item { SectionTitle("Инструменты") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Tile("Анализ памяти", R.drawable.ic_pie, Color(0xFFAB47BC), Modifier.weight(1f)) { vm.push(Screen.Analyzer) }
                    Tile("Корзина", R.drawable.ic_delete_sweep, Color(0xFF8D6E63), Modifier.weight(1f)) { vm.push(Screen.Trash) }
                    Tile("Терминал", R.drawable.ic_terminal, Color(0xFF66BB6A), Modifier.weight(1f), locked = !connected) {
                        vm.push(if (connected) Screen.Terminal else Screen.Prefs)
                    }
                    Tile("Настройки", R.drawable.ic_palette, Color(0xFFFF7043), Modifier.weight(1f)) { vm.push(Screen.Prefs) }
                }
            }

            item { SectionTitle(if (connected) "Системные папки (Shizuku)" else "Системные папки (нужна Shizuku)") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    SysTile("Android/data", "$root/Android/data", connected, vm, Modifier.weight(1f))
                    SysTile("Android/obb", "$root/Android/obb", connected, vm, Modifier.weight(1f))
                    SysTile("data/local", "/data/local/tmp", connected, vm, Modifier.weight(1f))
                    SysTile("Корень /", "/", connected, vm, Modifier.weight(1f))
                }
            }

            val bms = s.bookmarks
            if (bms.isNotEmpty()) {
                item { SectionTitle("Закладки") }
                items(bms.size) { i ->
                    val p = bms[i]
                    PathRow(p, R.drawable.ic_bookmark) {
                        if (File(p).isFile) vm.push(Screen.Browser(PathUtil.parent(p) ?: root))
                        else vm.push(Screen.Browser(p))
                    }
                }
            }

            val rec = s.recents.take(6)
            if (rec.isNotEmpty()) {
                item { SectionTitle("Недавно открытые") }
                items(rec.size) { i ->
                    val p = rec[i]
                    PathRow(p, R.drawable.ic_history) {
                        val f = File(p)
                        if (f.exists()) {
                            val item = FileItem(p, f.name, f.isDirectory, f.length(), f.lastModified())
                            vm.open(item, listOf(item))
                        } else {
                            vm.toast("Файл больше не существует")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SysTile(title: String, path: String, connected: Boolean, vm: AppViewModel, modifier: Modifier) {
    Tile(title, R.drawable.ic_lock_open, Color(0xFFEF5350), modifier, locked = !connected) {
        if (connected) vm.push(Screen.Browser(path)) else vm.push(Screen.Prefs)
    }
}

@Composable
private fun PathRow(path: String, icon: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(PathUtil.name(path).ifEmpty { path }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StorageCard(v: Volume, onClick: () -> Unit) {
    val used = (v.total - v.free).coerceAtLeast(0L)
    val frac = if (v.total > 0) used.toFloat() / v.total.toFloat() else 0f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (v.removable) R.drawable.ic_sd else R.drawable.ic_phone),
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(v.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (frac > 0.9f) Color(0xFFE5483D) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Занято ${formatSize(used)} из ${formatSize(v.total)} • свободно ${formatSize(v.free)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
