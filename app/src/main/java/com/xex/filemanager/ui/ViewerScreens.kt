@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.xex.filemanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.Screen
import com.xex.filemanager.fs.Fs
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImageViewerScreen(vm: AppViewModel, s: Screen.Images) {
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = s.index, pageCount = { s.paths.size })
    var bars by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(s.paths[page]) { bars = !bars }
        }
        if (bars) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
                }
                Text(
                    text = PathUtil.name(s.paths[pager.currentPage]),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("${pager.currentPage + 1}/${s.paths.size}", color = Color.White, modifier = Modifier.padding(end = 4.dp))
                IconButton(onClick = { Utils.share(ctx, listOf(File(s.paths[pager.currentPage]))) }) {
                    Icon(Icons.Filled.Share, "Поделиться", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(path: String, onTap: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val ctx = LocalContext.current
    val req = remember(path) { ImageRequest.Builder(ctx).data(File(path)).build() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (pressed >= 2 || scale > 1f) {
                            if (zoom != 1f || pan != Offset.Zero) {
                                val ns = (scale * zoom).coerceIn(1f, 6f)
                                scale = ns
                                offset = if (ns <= 1f) Offset.Zero else offset + pan
                                event.changes.forEach { c -> if (c.positionChanged()) c.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = req,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Composable
fun EditorScreen(vm: AppViewModel, path: String) {
    val s = vm.settings
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<TextFieldValue?>(null) }
    var original by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        val r = withContext(Dispatchers.IO) {
            try {
                Fs.backend(path).input(path).use { ins -> String(ins.readBytes(), Charsets.UTF_8) }
            } catch (e: Exception) {
                null
            }
        }
        if (r == null) {
            failed = true
        } else {
            original = r
            text = TextFieldValue(r)
        }
    }

    val t = text
    val dirty = t != null && t.text != original

    fun save(then: () -> Unit) {
        val cur = text ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    Fs.backend(path).output(path, false).use { outs -> outs.write(cur.text.toByteArray(Charsets.UTF_8)) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) {
                original = cur.text
                vm.toast("Сохранено")
                then()
            } else {
                vm.toast("Не удалось сохранить файл")
            }
        }
    }

    BackHandler {
        if (dirty) confirmExit = true else vm.pop()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        PathUtil.name(path) + if (dirty) " •" else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmExit = true else vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { s.editorWrap = !s.editorWrap }) {
                        Text(if (s.editorWrap) "Перенос: вкл" else "Перенос: выкл")
                    }
                    IconButton(onClick = { save {} }, enabled = dirty) {
                        Icon(painterResource(R.drawable.ic_save), "Сохранить")
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).imePadding()) {
            if (failed) {
                Text("Не удалось прочитать файл", modifier = Modifier.align(Alignment.Center))
            } else if (t == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val vs = rememberScrollState()
                val hs = rememberScrollState()
                BasicTextField(
                    value = t,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = s.editorSize.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    softWrap = s.editorWrap,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vs)
                        .then(if (s.editorWrap) Modifier else Modifier.horizontalScroll(hs))
                        .padding(12.dp)
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Сохранить изменения?") },
            text = { Text("В файле есть несохранённые правки.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; save { vm.pop() } }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false }) { Text("Отмена") }
                    TextButton(onClick = { confirmExit = false; vm.pop() }) { Text("Не сохранять") }
                }
            }
        )
    }
}
