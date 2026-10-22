@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.xex.filemanager.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xex.filemanager.AppDialog
import com.xex.filemanager.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xex.filemanager.data.AppSettings
import com.xex.filemanager.data.Palettes
import com.xex.filemanager.fs.Fs
import com.xex.filemanager.fs.ShizukuManager
import com.xex.filemanager.fs.ShizukuState

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            XfmTopBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // ---------- Shizuku ----------
            SectionTitle("Shizuku — доступ к системным папкам")
            ShizukuCard(vm, s)

            // ---------- Тема ----------
            SectionTitle("Тема оформления")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Режим", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    listOf("Система", "Светлая", "Тёмная").forEachIndexed { i, label ->
                        FilterChip(selected = s.darkMode == i, onClick = { s.darkMode = i }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Акцентный цвет", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Palettes.forEach { p ->
                        val sel = !s.dynamic && s.themeId == p.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(p.seed)
                                    .border(
                                        width = if (sel) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                    .clickable { s.themeId = p.id; s.dynamic = false },
                                contentAlignment = Alignment.Center
                            ) {
                                if (sel) Icon(Icons.Filled.Check, null, tint = androidx.compose.ui.graphics.Color.White)
                            }
                            Text(p.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            SwitchRow("AMOLED (чисто чёрный фон)", "Для тёмной темы на OLED-экранах", s.amoled) { s.amoled = it }
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow("Цвета из обоев (Material You)", "Использовать системную палитру Android 12+", s.dynamic) { s.dynamic = it }
            }

            // ---------- Главный экран ----------
            SectionTitle("Главный экран")
            SwitchRow("Показывать плитку «Система»", "Корень файловой системы (/). С Shizuku открывает защищённые папки", s.showSystem) { s.showSystem = it }

            // ---------- Вид ----------
            SectionTitle("Вид списка")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                listOf("Список", "Сетка", "Компактно").forEachIndexed { i, label ->
                    FilterChip(selected = s.viewMode == i, onClick = { s.viewMode = i }, label = { Text(label) })
                }
            }
            SwitchRow("Показывать скрытые файлы", "Файлы и папки, имя которых начинается с точки", s.showHidden) { s.showHidden = it }
            SwitchRow("Папки сверху", null, s.foldersFirst) { s.foldersFirst = it }
            SwitchRow("Миниатюры изображений и видео", null, s.thumbs) { s.thumbs = it }

            // ---------- Файлы ----------
            SectionTitle("Файловые операции")
            SwitchRow("Корзина", "Удалённые файлы сначала попадают в корзину", s.useTrash) { s.useTrash = it }
            SwitchRow("Подтверждать удаление", null, s.confirmDelete) { s.confirmDelete = it }

            // ---------- Редактор ----------
            SectionTitle("Текстовый редактор")
            SwitchRow("Перенос строк", null, s.editorWrap) { s.editorWrap = it }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Размер шрифта: ${s.editorSize}", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { if (s.editorSize > 8) s.editorSize = s.editorSize - 1 }) { Text("−") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { if (s.editorSize < 32) s.editorSize = s.editorSize + 1 }) { Text("+") }
            }

            SectionTitle("О приложении")
            Text(
                "XFM Файлы 1.0 — файловый менеджер с темами и поддержкой Shizuku.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ShizukuCard(vm: AppViewModel, s: AppSettings) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val st = ShizukuManager.state
    val title: String
    val text: String
    when (st) {
        ShizukuState.NOT_INSTALLED -> {
            title = "Shizuku не установлена"
            text = "Установите приложение Shizuku (shizuku.rikka.app), запустите его через беспроводную отладку или root и вернитесь сюда."
        }
        ShizukuState.NOT_RUNNING -> {
            title = "Shizuku не запущена"
            text = "Откройте Shizuku и запустите сервис (беспроводная отладка / root / adb). После этого нажмите «Обновить»."
        }
        ShizukuState.NO_PERMISSION -> {
            title = "Нужно разрешение"
            text = "Shizuku работает. Дайте приложению доступ к Shizuku."
        }
        ShizukuState.CONNECTING -> {
            title = "Подключение…"
            text = "Запускаю файловый сервис с повышенными правами."
        }
        ShizukuState.CONNECTED -> {
            val who = when (ShizukuManager.uid) {
                0 -> "root"
                2000 -> "shell (ADB)"
                else -> "uid ${ShizukuManager.uid}"
            }
            title = "Подключено ✓"
            text = "Сервис работает с правами: $who. Теперь открываются Android/data, Android/obb и системные папки."
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ShizukuManager.lastError.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(ShizukuManager.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (st == ShizukuState.NO_PERMISSION) {
                    Button(onClick = { ShizukuManager.requestPermission() }) { Text("Выдать доступ") }
                }
                if (st == ShizukuState.NOT_INSTALLED || st == ShizukuState.NOT_RUNNING) {
                    Button(onClick = { ShizukuManager.openShizukuApp(ctx) }) {
                        Text(if (st == ShizukuState.NOT_INSTALLED) "Скачать" else "Открыть Shizuku")
                    }
                }
                OutlinedButton(onClick = { ShizukuManager.clearError(); ShizukuManager.refresh() }) { Text("Обновить") }
            }
            if (st == ShizukuState.CONNECTED) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        val out = withContext(Dispatchers.IO) {
                            ShizukuManager.exec(
                                "id; ls -ld /sdcard/Android/data; ls /sdcard/Android/data | head -3; " +
                                    "ls /storage/emulated/0/Android/data | head -3"
                            )
                        }
                        vm.dialog = AppDialog.Info("Проверка доступа", out.take(1500))
                    }
                }) { Text("Проверить доступ") }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Text("Когда использовать Shizuku", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf("Никогда", "Авто", "Всегда").forEachIndexed { i, label ->
                    FilterChip(
                        selected = s.shizukuMode == i,
                        onClick = { s.shizukuMode = i; Fs.mode = i },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                "«Авто» — только для Android/data, Android/obb и системных папок. «Всегда» — все операции идут через Shizuku.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
