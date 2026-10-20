package com.xex.filemanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xex.filemanager.AppDialog
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.Screen
import com.xex.filemanager.net.Conn
import com.xex.filemanager.net.ConnStore
import com.xex.filemanager.net.Net

@Composable
fun ConnsScreen(vm: AppViewModel, cloud: Boolean) {
    val x = LocalXfm.current
    val list = ConnStore.conns.filter { it.cloud == cloud }
    Scaffold(
        topBar = {
            XfmTopBar(
                title = { Text(if (cloud) "Облако" else "Удалённые службы") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    IconButton(onClick = { vm.dialog = AppDialog.ConnEdit(cloud) }) { Icon(Icons.Filled.Add, "Добавить") }
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (list.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(28.dp)) {
                    Text(
                        if (cloud) "Облачных хранилищ пока нет" else "Подключений пока нет",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (cloud) {
                            "Нажми «+» и выбери сервис: Яндекс.Диск, Mail.ru Облако, Nextcloud, ownCloud, Box или любой WebDAV. " +
                                "Google Диск, Dropbox и OneDrive требуют входа через их приложения и здесь недоступны."
                        } else {
                            "Нажми «+», чтобы добавить сервер FTP, SFTP или WebDAV. После подключения его папки открываются как обычные: можно копировать, переименовывать, удалять."
                        },
                        fontSize = 14.sp,
                        color = x.secondary
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.push(Screen.Browser(Net.rootOf(c))) }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(if (c.cloud) R.drawable.ic_cloud else R.drawable.ic_desktop),
                                    null,
                                    Modifier.size(34.dp),
                                    tint = if (c.cloud) Color(0xFF5B9FE8) else Color(0xFF8D6E63)
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        c.type.uppercase() + " • " + c.host,
                                        fontSize = 14.sp,
                                        color = x.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = {
                                    vm.dialog = AppDialog.Confirm("Удалить подключение", c.name, "Удалить") {
                                        ConnStore.remove(c.id)
                                    }
                                }) { Icon(Icons.Filled.Delete, "Удалить") }
                            }
                            HorizontalDivider(color = x.separator)
                        }
                    }
                }
            }
        }
    }
}

private class Preset(val title: String, val url: String, val hint: String)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ConnDialog(d: AppDialog.ConnEdit, close: () -> Unit) {
    val presets = remember {
        listOf(
            Preset("Яндекс.Диск", "https://webdav.yandex.ru", "Логин Яндекса и пароль приложения (создаётся в настройках Яндекс ID)"),
            Preset("Mail.ru Облако", "https://webdav.cloud.mail.ru", "Почта и пароль для внешних приложений"),
            Preset("Nextcloud / ownCloud", "https://", "Адрес вида https://сервер/remote.php/dav/files/ЛОГИН/"),
            Preset("Box", "https://dav.box.com/dav", "Нужен включённый доступ по WebDAV"),
            Preset("Другой WebDAV", "https://", "")
        )
    }
    var type by remember { mutableStateOf(if (d.cloud) "webdav" else "ftp") }
    var preset by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(if (d.cloud) presets[0].url else "") }
    var port by remember { mutableStateOf("21") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/") }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (d.cloud) "Облачное хранилище" else "Новое подключение") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (d.cloud) {
                    FlowRow {
                        presets.forEachIndexed { i, p ->
                            FilterChip(
                                selected = preset == i,
                                onClick = {
                                    preset = i
                                    host = p.url
                                    if (name.isBlank() || presets.any { it.title == name }) name = p.title
                                },
                                label = { Text(p.title) },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                    if (presets[preset].hint.isNotEmpty()) {
                        Text(presets[preset].hint, fontSize = 13.sp, color = LocalXfm.current.secondary)
                    }
                } else {
                    FlowRow {
                        listOf("ftp" to "FTP", "sftp" to "SFTP", "webdav" to "WebDAV").forEach { t ->
                            FilterChip(
                                selected = type == t.first,
                                onClick = {
                                    type = t.first
                                    port = when (t.first) {
                                        "ftp" -> "21"
                                        "sftp" -> "22"
                                        else -> "443"
                                    }
                                },
                                label = { Text(t.second) },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text(if (type == "webdav") "Адрес (URL)" else "Сервер") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (type != "webdav") {
                    OutlinedTextField(
                        value = port, onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Порт") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = path, onValueChange = { path = it },
                        label = { Text("Начальная папка") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Логин") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Пароль") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.isNotBlank(),
                onClick = {
                    val h = host.trim().removePrefix("ftp://").removePrefix("sftp://").trimEnd('/').let {
                        if (type == "webdav") host.trim() else it
                    }
                    val c = Conn(
                        id = System.currentTimeMillis().toString(36),
                        name = if (name.isBlank()) h else name.trim(),
                        type = type,
                        host = h,
                        port = port.toIntOrNull() ?: 21,
                        user = user.trim(),
                        pass = pass,
                        path = if (type == "webdav") "/" else path.ifBlank { "/" },
                        cloud = d.cloud
                    )
                    ConnStore.add(c)
                    close()
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } }
    )
}
