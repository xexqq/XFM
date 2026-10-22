package com.xex.filemanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.Screen
import com.xex.filemanager.fs.AnalysisResult
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.util.formatSize

@Composable
private fun ReportCard(
    title: String,
    accent: String?,
    button: String?,
    buttonColor: Color? = null,
    onButton: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val x = LocalXfm.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        shape = RoundedCornerShape(3.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                if (accent != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(accent, fontSize = 20.sp, color = x.section, maxLines = 1)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
            if (button != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = x.separator)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onButton)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = button.uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = buttonColor ?: MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CatLine(name: String, size: Long, icon: @Composable () -> Unit) {
    val x = LocalXfm.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Text(name, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
        Text(formatSize(size), fontSize = 15.sp, color = x.secondary, maxLines = 1)
    }
}

@Composable
private fun ResLine(
    title: String,
    subtitle: String?,
    size: Long,
    icon: @Composable () -> Unit
) {
    val x = LocalXfm.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = x.secondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(formatSize(size), fontSize = 18.sp, color = x.section, maxLines = 1)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontSize = 16.sp,
        color = LocalXfm.current.secondary,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
    )
}

@Composable
private fun Report(vm: AppViewModel, a: AnalysisResult) {
    val x = LocalXfm.current
    val v = a.volume
    val used = (v.total - v.free).coerceAtLeast(0L)
    val frac = if (v.total > 0L) used.toFloat() / v.total.toFloat() else 0f
    val root = v.path
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        ReportCard(v.label, formatSize(v.free) + " свободно", "Ещё", null, { vm.push(Screen.Browser(root, false, true)) }) {
            Text(
                text = (frac * 100f).toInt().toString() + "%",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(x.separator)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .background(x.section)
                )
            }
            Spacer(Modifier.height(14.dp))
            val s = a.catSizes
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    CatLine("Фото", s[0]) { Image(painterResource(R.drawable.ic_x_frame), null, Modifier.size(24.dp)) }
                    CatLine("Видео", s[2]) { Image(painterResource(R.drawable.ic_x_film), null, Modifier.size(24.dp)) }
                    CatLine("Сжатые файлы", s[4]) {
                        Icon(painterResource(R.drawable.ic_archive), null, Modifier.size(22.dp), tint = Color(0xFF8D6E63))
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    CatLine("Аудио", s[1]) {
                        Icon(painterResource(R.drawable.ic_music_note), null, Modifier.size(22.dp), tint = Color(0xFF3E9C8F))
                    }
                    CatLine("Документы", s[3]) { Image(painterResource(R.drawable.ic_x_docs), null, Modifier.size(24.dp)) }
                    CatLine("Другое", s[5]) {
                        Icon(painterResource(R.drawable.ic_more_horiz), null, Modifier.size(22.dp), tint = x.secondary)
                    }
                }
            }
        }

        ReportCard("Программы", formatSize(a.appsTotal), "Ещё", null, { vm.push(Screen.Apps) }) {
            if (a.apps.isEmpty()) Hint("Нет данных о размерах")
            for (e in a.apps.take(2)) {
                ResLine(e.first.label, null, e.second) { AppIcon(e.first.pkg, 36.dp) }
            }
        }

        if (!a.exact) {
            ReportCard("Точные размеры", null, "Выдать доступ", x.section, { vm.openUsageAccess() }) {
                Hint("Без доступа к статистике использования размеры программ и кэша считаются приблизительно (по папкам). Включи доступ для XFM в открывшемся списке.")
            }
        }

        ReportCard("Большие файлы", formatSize(a.bigTotal), "Ещё", null, { vm.push(Screen.BigFiles) }) {
            Hint("Файлы больше, чем 10 МБ")
            for (f in a.bigFiles.take(2)) {
                val dir = (PathUtil.parent(f.path) ?: "").removePrefix(root).ifEmpty { "/" }
                ResLine(f.name, dir, f.size) { FileGlyph(f, 36.dp, false) }
            }
        }

        ReportCard("Кэш-файлы", formatSize(a.cacheTotal), "Очистить", x.section, { vm.clearCaches() }) {
            Hint("Кэш-файлы — это временные файлы, которые хранятся программами")
            for (e in a.caches.take(2)) {
                ResLine(e.first.label, null, e.second) { AppIcon(e.first.pkg, 36.dp) }
            }
        }

        ReportCard("Дубликаты файлов", formatSize(a.dupTotal), "Ещё", null, { vm.push(Screen.Dupes) }) {
            if (a.dupGroups.isEmpty()) Hint("Дубликатов не найдено")
            for (g in a.dupGroups.take(2)) {
                val f = g[0]
                val dir = (PathUtil.parent(f.path) ?: "").removePrefix(root).ifEmpty { "/" }
                ResLine(f.name, dir, f.size) { FileGlyph(f, 36.dp, false) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun AnalyzerScreen(vm: AppViewModel, onMenu: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            XfmTopBar(
                title = { Text("Анализ места") },
                navigationIcon = {
                    IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Меню") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Пересканировать") },
                                onClick = { menu = false; vm.startAnalysis(true) }
                            )
                            DropdownMenuItem(
                                text = { Text("Доступ к статистике") },
                                onClick = { menu = false; vm.openUsageAccess() }
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val a = vm.analysis
            if (vm.analyzing) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(18.dp))
                    Text(vm.analysisPhase, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text("Просмотрено: " + vm.analysisCount, fontSize = 16.sp, color = LocalXfm.current.secondary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        vm.analysisPath,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = LocalXfm.current.secondary
                    )
                    Spacer(Modifier.height(18.dp))
                    OutlinedButton(onClick = { vm.cancelAnalysis() }) { Text("Отмена") }
                }
            } else if (a == null) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Анализ не выполнен", fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.startAnalysis(true) }) { Text("Запустить анализ") }
                }
            } else {
                Report(vm, a)
            }
        }
    }
}
