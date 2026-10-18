package com.xex.filemanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xex.filemanager.AppDialog
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.Screen
import com.xex.filemanager.model.Cat
import com.xex.filemanager.util.Utils
import com.xex.filemanager.util.formatSize

@Composable
private fun HomeTile(
    name: String,
    info: String?,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val x = LocalXfm.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            text = name,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = info ?: " ",
            fontSize = 12.sp,
            color = x.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BoxIcon(content: @Composable () -> Unit) {
    val x = LocalXfm.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(shape)
            .background(x.tileBg)
            .border(1.dp, x.tileBorder, shape),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun HomeScreen(vm: AppViewModel, onMenu: () -> Unit) {
    val s = vm.settings
    val root = Utils.primaryRoot()
    var menuOpen by remember { mutableStateOf(false) }

    fun statInfo(p: Pair<Int, Long>?): String? =
        if (p == null) null else formatSize(p.second) + " (" + p.first + ")"

    Scaffold(
        topBar = {
            XfmTopBar(
                title = { Text("XFM Файлы") },
                navigationIcon = {
                    IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Меню") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Корзина") },
                                onClick = { menuOpen = false; vm.push(Screen.Trash) }
                            )
                            DropdownMenuItem(
                                text = { Text("Анализ памяти") },
                                onClick = { menuOpen = false; vm.push(Screen.Analyzer) }
                            )
                            DropdownMenuItem(
                                text = { Text("Терминал (Shizuku)") },
                                onClick = { menuOpen = false; vm.push(Screen.Terminal) }
                            )
                            DropdownMenuItem(
                                text = { Text("Обновить") },
                                onClick = { menuOpen = false; vm.refreshHome() }
                            )
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                onClick = { menuOpen = false; vm.push(Screen.Prefs) }
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxSize().padding(pad)
        ) {
            vm.volumes.forEach { v ->
                item(key = "vol" + v.path) {
                    val used = (v.total - v.free).coerceAtLeast(0L)
                    HomeTile(
                        name = v.label,
                        info = formatSize(used) + " / " + formatSize(v.total),
                        icon = {
                            Image(painterResource(R.drawable.ic_x_drive), null, Modifier.size(64.dp))
                        },
                        onClick = { vm.push(Screen.Browser(v.path)) }
                    )
                }
            }
            if (s.showSystem) {
                item(key = "system") {
                    HomeTile(
                        name = "Система",
                        info = null,
                        icon = {
                            Image(painterResource(R.drawable.ic_x_slash), null, Modifier.size(64.dp))
                        },
                        onClick = { vm.push(Screen.Browser("/")) }
                    )
                }
            }
            item(key = "downloads") {
                HomeTile(
                    name = "Загрузки",
                    info = statInfo(vm.downloadStat),
                    icon = {
                        Box(Modifier.size(64.dp)) {
                            Image(painterResource(R.drawable.ic_x_folder_full), null, Modifier.size(64.dp))
                            Image(
                                painter = painterResource(R.drawable.ic_x_badge_download),
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.BottomStart).size(36.dp)
                            )
                        }
                    },
                    onClick = { vm.push(Screen.Browser("$root/Download")) }
                )
            }
            for (c in listOf(Cat.IMAGES, Cat.AUDIO, Cat.VIDEO, Cat.DOCS)) {
                item(key = "cat" + c.name) {
                    HomeTile(
                        name = c.title,
                        info = statInfo(vm.catStats[c]),
                        icon = { BoxIcon { CatGlyph(c, 38.dp) } },
                        onClick = { vm.push(Screen.Category(c)) }
                    )
                }
            }
            item(key = "apps") {
                HomeTile(
                    name = "Программы",
                    info = null,
                    icon = {
                        BoxIcon {
                            Icon(
                                painterResource(R.drawable.ic_android),
                                null,
                                Modifier.size(38.dp),
                                tint = Color(0xFFA4C639)
                            )
                        }
                    },
                    onClick = { vm.push(Screen.Apps) }
                )
            }
            item(key = "recent") {
                HomeTile(
                    name = Cat.RECENT.title,
                    info = statInfo(vm.catStats[Cat.RECENT]),
                    icon = { BoxIcon { CatGlyph(Cat.RECENT, 38.dp) } },
                    onClick = { vm.push(Screen.Category(Cat.RECENT)) }
                )
            }
            item(key = "cloud") {
                HomeTile(
                    name = "Облако",
                    info = null,
                    icon = {
                        BoxIcon {
                            Icon(painterResource(R.drawable.ic_cloud), null, Modifier.size(40.dp), tint = Color(0xFF5B9FE8))
                        }
                    },
                    onClick = {
                        vm.dialog = AppDialog.Info(
                            "Облако",
                            "Подключение облачных хранилищ пока не реализовано. Эта плитка добавлена для совпадения внешнего вида."
                        )
                    }
                )
            }
            item(key = "remote") {
                HomeTile(
                    name = "Удалённые службы",
                    info = null,
                    icon = {
                        BoxIcon {
                            Icon(painterResource(R.drawable.ic_desktop), null, Modifier.size(38.dp), tint = Color(0xFF8D6E63))
                        }
                    },
                    onClick = {
                        vm.dialog = AppDialog.Info(
                            "Удалённые службы",
                            "Подключение по FTP, SFTP и SMB пока не реализовано. Эта плитка добавлена для совпадения внешнего вида."
                        )
                    }
                )
            }
            item(key = "network") {
                HomeTile(
                    name = "Доступ из сети",
                    info = null,
                    icon = {
                        BoxIcon {
                            Icon(painterResource(R.drawable.ic_devices), null, Modifier.size(40.dp), tint = Color(0xFF3E6B63))
                        }
                    },
                    onClick = {
                        vm.dialog = AppDialog.Info(
                            "Доступ из сети",
                            "Доступ к файлам по Wi-Fi пока не реализован. Эта плитка добавлена для совпадения внешнего вида."
                        )
                    }
                )
            }
        }
    }
}
