package com.xex.filemanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xex.filemanager.AppDialog
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.Screen
import com.xex.filemanager.buildName
import com.xex.filemanager.fs.FileOps
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.util.formatDate
import com.xex.filemanager.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DialogHost(vm: AppViewModel) {
    val d = vm.dialog
    val close = { vm.dialog = null }
    when (d) {
        null -> {}
        is AppDialog.Input -> InputDialog(d, close)
        is AppDialog.Confirm -> AlertDialog(
            onDismissRequest = close,
            title = { Text(d.title) },
            text = { Text(d.text) },
            confirmButton = {
                TextButton(onClick = { close(); d.onOk() }) { Text(d.confirm) }
            },
            dismissButton = { TextButton(onClick = close) { Text("Отмена") } }
        )
        is AppDialog.Properties -> PropertiesDialog(d.item, close)
        is AppDialog.MultiRename -> MultiRenameDialog(vm, d, close)
        is AppDialog.ArchiveChoice -> AlertDialog(
            onDismissRequest = close,
            title = { Text(d.item.name) },
            text = { Text("Что сделать с архивом?") },
            confirmButton = {
                Column {
                    TextButton(onClick = { close(); vm.extract(d.item, true) }) { Text("Распаковать в папку") }
                    TextButton(onClick = { close(); vm.extract(d.item, false) }) { Text("Распаковать сюда") }
                    TextButton(onClick = { close(); vm.openExternal(d.item) }) { Text("Открыть другим приложением") }
                }
            },
            dismissButton = { TextButton(onClick = close) { Text("Отмена") } }
        )
        is AppDialog.OpenAs -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Открыть как…") },
            text = { Text(d.item.name) },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        close()
                        if (d.item.size <= 1_000_000L) vm.push(Screen.Editor(d.item.path)) else vm.openExternal(d.item, "text/plain")
                    }) { Text("Текст (редактор)") }
                    TextButton(onClick = { close(); vm.openExternal(d.item, "image/*") }) { Text("Изображение") }
                    TextButton(onClick = { close(); vm.openExternal(d.item, "audio/*") }) { Text("Аудио") }
                    TextButton(onClick = { close(); vm.openExternal(d.item, "video/*") }) { Text("Видео") }
                    TextButton(onClick = { close(); vm.openExternal(d.item, "*/*") }) { Text("Любой тип") }
                }
            },
            dismissButton = { TextButton(onClick = close) { Text("Отмена") } }
        )
    }
}

@Composable
private fun InputDialog(d: AppDialog.Input, close: () -> Unit) {
    var text by remember { mutableStateOf(d.initial) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(d.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(d.hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && !text.contains('/'),
                onClick = { close(); d.onOk(text.trim()) }
            ) { Text(d.confirm) }
        },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } }
    )
}

@Composable
private fun MultiRenameDialog(vm: AppViewModel, d: AppDialog.MultiRename, close: () -> Unit) {
    var template by remember { mutableStateOf("{name}_{n}") }
    var start by remember { mutableStateOf("1") }
    val startNum = start.toIntOrNull() ?: 1
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Групповое переименование") },
        text = {
            Column {
                Text(
                    "Шаблон: {name} — имя, {n} — номер, {ext} — расширение (если не указать, расширение сохранится).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("Шаблон") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Начать с номера") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Пример:", style = MaterialTheme.typography.labelMedium)
                d.items.take(3).forEachIndexed { i, item ->
                    Text(
                        item.name + "  →  " + buildName(item, i, template, startNum),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = template.isNotBlank() && !template.contains('/'),
                onClick = { close(); vm.multiRename(d.items, template, startNum) }
            ) { Text("Переименовать") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } }
    )
}

@Composable
private fun PropertiesDialog(item: FileItem, close: () -> Unit) {
    var size by remember { mutableStateOf<Long?>(if (item.isDir) null else item.size) }
    var md5 by remember { mutableStateOf<String?>(null) }
    var sha by remember { mutableStateOf<String?>(null) }
    var hashing by remember { mutableStateOf(false) }

    LaunchedEffect(item.path) {
        if (item.isDir) {
            size = withContext(Dispatchers.IO) { FileOps.sizeOf(item) }
        }
    }
    LaunchedEffect(hashing) {
        if (hashing) {
            md5 = withContext(Dispatchers.IO) {
                try {
                    FileOps.hash(item.path, "MD5")
                } catch (e: Exception) {
                    "ошибка"
                }
            }
            sha = withContext(Dispatchers.IO) {
                try {
                    FileOps.hash(item.path, "SHA-256")
                } catch (e: Exception) {
                    "ошибка"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("Свойства") },
        text = {
            SelectionContainer {
                Column {
                    Prop("Имя", item.name)
                    Prop("Путь", item.path)
                    Prop("Тип", item.type.label)
                    Prop("Размер", size?.let { formatSize(it) + " ($it Б)" } ?: "считаю…")
                    if (item.isDir && item.children >= 0) Prop("Содержимое", item.children.toString() + " шт.")
                    Prop("Изменён", formatDate(item.modified))
                    val f = File(item.path)
                    if (f.exists()) {
                        Prop("Права", (if (f.canRead()) "чтение " else "") + (if (f.canWrite()) "запись " else "") + (if (f.canExecute()) "выполнение" else ""))
                    }
                    if (!item.isDir) {
                        if (!hashing) {
                            TextButton(onClick = { hashing = true }) { Text("Вычислить MD5 и SHA-256") }
                        } else {
                            Prop("MD5", md5 ?: "считаю…")
                            Prop("SHA-256", sha ?: "считаю…")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Закрыть") } }
    )
}

@Composable
private fun Prop(label: String, value: String) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (label == "MD5" || label == "SHA-256") FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
fun ProgressHost(vm: AppViewModel) {
    val p = vm.task ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(p.title) },
        text = {
            Column {
                Text(p.current.ifEmpty { "…" }, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (p.total > 0L || p.itemsTotal > 0) {
                    LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    if (p.total > 0L) Text(formatSize(p.done) + " / " + formatSize(p.total), style = MaterialTheme.typography.bodySmall)
                    if (p.itemsTotal > 0) {
                        Spacer(Modifier.width(12.dp))
                        Text("${p.itemsDone} / ${p.itemsTotal}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.cancelTask() }) { Text("Отмена") } }
    )
}
