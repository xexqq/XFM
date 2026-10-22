package com.xex.filemanager.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.fs.Converter
import com.xex.filemanager.fs.Fs
import com.xex.filemanager.fs.PathUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private data class CropRect(val l: Float, val t: Float, val r: Float, val b: Float)

private val filterNames = listOf("Нет", "Ч/Б", "Сепия", "Негатив", "Тёплый", "Холодный", "Винтаж", "Контраст")

private fun filterMatrix(i: Int): ColorMatrix {
    val m = ColorMatrix()
    when (i) {
        1 -> m.setSaturation(0f)
        2 -> m.set(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        3 -> m.set(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
        4 -> m.set(floatArrayOf(1.1f, 0f, 0f, 0f, 10f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0.9f, 0f, -10f, 0f, 0f, 0f, 1f, 0f))
        5 -> m.set(floatArrayOf(0.9f, 0f, 0f, 0f, -10f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.1f, 0f, 10f, 0f, 0f, 0f, 1f, 0f))
        6 -> m.set(floatArrayOf(0.6f, 0.3f, 0.1f, 0f, 20f, 0.2f, 0.6f, 0.1f, 0f, 10f, 0.1f, 0.2f, 0.5f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        7 -> m.set(floatArrayOf(1.3f, 0f, 0f, 0f, -38f, 0f, 1.3f, 0f, 0f, -38f, 0f, 0f, 1.3f, 0f, -38f, 0f, 0f, 0f, 1f, 0f))
        else -> {}
    }
    return m
}

private fun fullMatrix(filter: Int, bright: Float, contrast: Float, sat: Float): ColorMatrix {
    val m = ColorMatrix()
    m.postConcat(filterMatrix(filter))
    val s = ColorMatrix()
    s.setSaturation(sat)
    m.postConcat(s)
    val t = (1f - contrast) * 127.5f + bright
    m.postConcat(
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f
            )
        )
    )
    return m
}

private fun bake(src: Bitmap, m: ColorMatrix): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val p = Paint()
    p.colorFilter = ColorMatrixColorFilter(m)
    AndroidCanvas(out).drawBitmap(src, 0f, 0f, p)
    return out
}

@Composable
private fun CropOverlay(
    iw: Int, ih: Int, bw: Float, bh: Float,
    rect: CropRect, aspectRatio: Float?, onChange: (CropRect) -> Unit
) {
    val scale = minOf(bw / iw, bh / ih)
    val dw = iw * scale
    val dh = ih * scale
    val left = (bw - dw) / 2f
    val top = (bh - dh) / 2f
    var handle by remember { mutableStateOf(0) }
    val current by rememberUpdatedState(rect)
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(iw, ih, bw, bh, aspectRatio) {
                detectDragGestures(
                    onDragStart = { o ->
                        val r = current
                        val x1 = left + r.l * dw
                        val y1 = top + r.t * dh
                        val x2 = left + r.r * dw
                        val y2 = top + r.b * dh
                        val tol = 64f
                        fun near(ax: Float, ay: Float): Boolean = abs(o.x - ax) < tol && abs(o.y - ay) < tol
                        handle = when {
                            near(x1, y1) -> 2
                            near(x2, y1) -> 3
                            near(x1, y2) -> 4
                            near(x2, y2) -> 5
                            o.x in x1..x2 && o.y in y1..y2 -> 1
                            else -> 0
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val r = current
                        val dx = drag.x / dw
                        val dy = drag.y / dh
                        var l = r.l
                        var t = r.t
                        var rr = r.r
                        var b = r.b
                        when (handle) {
                            1 -> {
                                val w = rr - l
                                val h = b - t
                                l = (l + dx).coerceIn(0f, 1f - w)
                                t = (t + dy).coerceIn(0f, 1f - h)
                                rr = l + w
                                b = t + h
                            }
                            2 -> { l += dx; t += dy }
                            3 -> { rr += dx; t += dy }
                            4 -> { l += dx; b += dy }
                            5 -> { rr += dx; b += dy }
                        }
                        if (handle >= 2) {
                            l = l.coerceIn(0f, rr - 0.05f)
                            rr = rr.coerceIn(l + 0.05f, 1f)
                            t = t.coerceIn(0f, b - 0.05f)
                            b = b.coerceIn(t + 0.05f, 1f)
                            if (aspectRatio != null) {
                                val hFrac = (rr - l) * iw / aspectRatio / ih
                                if (handle == 2 || handle == 3) t = (b - hFrac).coerceAtLeast(0f)
                                else b = (t + hFrac).coerceAtMost(1f)
                            }
                        }
                        onChange(CropRect(l, t, rr, b))
                    }
                )
            }
    ) {
        val x1 = left + rect.l * dw
        val y1 = top + rect.t * dh
        val x2 = left + rect.r * dw
        val y2 = top + rect.b * dh
        val shade = Color(0x99000000)
        drawRect(shade, Offset(0f, 0f), Size(bw, y1))
        drawRect(shade, Offset(0f, y2), Size(bw, bh - y2))
        drawRect(shade, Offset(0f, y1), Size(x1, y2 - y1))
        drawRect(shade, Offset(x2, y1), Size(bw - x2, y2 - y1))
        drawRect(Color.White, Offset(x1, y1), Size(x2 - x1, y2 - y1), style = Stroke(3f))
        val gx = (x2 - x1) / 3f
        val gy = (y2 - y1) / 3f
        for (i in 1..2) {
            drawLine(Color(0x88FFFFFF), Offset(x1 + gx * i, y1), Offset(x1 + gx * i, y2), 1.5f)
            drawLine(Color(0x88FFFFFF), Offset(x1, y1 + gy * i), Offset(x2, y1 + gy * i), 1.5f)
        }
        for (p in listOf(Offset(x1, y1), Offset(x2, y1), Offset(x1, y2), Offset(x2, y2))) drawCircle(Color.White, 13f, p)
    }
}

@Composable
fun PhotoEditorScreen(vm: AppViewModel, path: String) {
    val scope = rememberCoroutineScope()
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var undo by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var tool by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(0) }
    var bright by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1f) }
    var sat by remember { mutableStateOf(1f) }
    var crop by remember { mutableStateOf(CropRect(0.1f, 0.1f, 0.9f, 0.9f)) }
    var aspectIdx by remember { mutableStateOf(0) }
    var rw by remember { mutableStateOf("") }
    var rh by remember { mutableStateOf("") }
    var saveOpen by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val aspects = listOf<Pair<String, Float?>>(
        "Свободно" to null, "1:1" to 1f, "4:3" to 4f / 3f, "3:4" to 0.75f, "16:9" to 16f / 9f, "9:16" to 9f / 16f
    )

    LaunchedEffect(path) {
        val r = withContext(Dispatchers.IO) {
            try {
                Converter.loadBitmap(path, 2560)
            } catch (e: Exception) {
                null
            }
        }
        if (r == null) {
            failed = true
        } else {
            bmp = r
            rw = r.width.toString()
            rh = r.height.toString()
        }
    }

    fun commit(nb: Bitmap) {
        val cur = bmp
        if (cur != null) undo = (undo + cur).takeLast(6)
        bmp = nb
        rw = nb.width.toString()
        rh = nb.height.toString()
    }

    fun transform(m: Matrix) {
        val b = bmp ?: return
        commit(Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true))
    }

    fun bakeColors() {
        val b = bmp ?: return
        commit(bake(b, fullMatrix(filter, bright, contrast, sat)))
        filter = 0
        bright = 0f
        contrast = 1f
        sat = 1f
    }

    fun setAspect(i: Int) {
        aspectIdx = i
        val b = bmp ?: return
        val a = aspects[i].second ?: return
        val ia = b.width.toFloat() / b.height.toFloat()
        val w: Float
        val h: Float
        if (a >= ia) {
            w = 0.9f
            h = 0.9f * ia / a
        } else {
            h = 0.9f
            w = 0.9f * a / ia
        }
        crop = CropRect((1f - w) / 2f, (1f - h) / 2f, (1f + w) / 2f, (1f + h) / 2f)
    }

    val matrix = fullMatrix(filter, bright, contrast, sat)
    val colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(matrix.array))

    Scaffold(
        topBar = {
            XfmTopBar(
                title = { Text("Редактор фото") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    if (undo.isNotEmpty()) {
                        TextButton(onClick = {
                            val last = undo.last()
                            undo = undo.dropLast(1)
                            bmp = last
                            rw = last.width.toString()
                            rh = last.height.toString()
                        }) { Text("Отмена", color = Color.White) }
                    }
                    IconButton(onClick = { if (bmp != null) saveOpen = true }) {
                        Icon(painterResource(R.drawable.ic_save), "Сохранить")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            val b = bmp
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF101010))
            ) {
                if (failed) {
                    Text("Не удалось открыть изображение", color = Color.White, modifier = Modifier.align(Alignment.Center))
                } else if (b == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (tool == 1) {
                        CropOverlay(
                            b.width, b.height, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(),
                            crop, aspects[aspectIdx].second
                        ) { crop = it }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp)) {
                when (tool) {
                    1 -> {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            aspects.forEachIndexed { i, a ->
                                FilterChip(
                                    selected = aspectIdx == i,
                                    onClick = { setAspect(i) },
                                    label = { Text(a.first) },
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                        Button(onClick = {
                            val src = bmp
                            if (src != null) {
                                val x = (crop.l * src.width).toInt().coerceIn(0, src.width - 1)
                                val y = (crop.t * src.height).toInt().coerceIn(0, src.height - 1)
                                val w = ((crop.r - crop.l) * src.width).toInt().coerceIn(1, src.width - x)
                                val h = ((crop.b - crop.t) * src.height).toInt().coerceIn(1, src.height - y)
                                commit(Bitmap.createBitmap(src, x, y, w, h))
                                crop = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
                                tool = 0
                            }
                        }) { Text("Обрезать") }
                    }
                    2 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = rw,
                                onValueChange = { v ->
                                    rw = v.filter { c -> c.isDigit() }.take(5)
                                    val src = bmp
                                    val nw = rw.toIntOrNull()
                                    if (src != null && nw != null) rh = (nw.toLong() * src.height / src.width).toString()
                                },
                                label = { Text("Ширина") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = rh,
                                onValueChange = { v ->
                                    rh = v.filter { c -> c.isDigit() }.take(5)
                                    val src = bmp
                                    val nh = rh.toIntOrNull()
                                    if (src != null && nh != null) rw = (nh.toLong() * src.width / src.height).toString()
                                },
                                label = { Text("Высота") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            listOf(25, 50, 75, 150, 200).forEach { pc ->
                                OutlinedButton(
                                    onClick = {
                                        val src = bmp
                                        if (src != null) {
                                            rw = (src.width * pc / 100).coerceAtLeast(1).toString()
                                            rh = (src.height * pc / 100).coerceAtLeast(1).toString()
                                        }
                                    },
                                    modifier = Modifier.padding(end = 6.dp)
                                ) { Text("$pc%") }
                            }
                            Button(onClick = {
                                val src = bmp
                                val nw = rw.toIntOrNull()
                                val nh = rh.toIntOrNull()
                                if (src != null && nw != null && nh != null && nw in 1..8000 && nh in 1..8000) {
                                    commit(Bitmap.createScaledBitmap(src, nw, nh, true))
                                    tool = 0
                                } else {
                                    vm.toast("Размер должен быть от 1 до 8000")
                                }
                            }) { Text("Применить") }
                        }
                    }
                    3 -> {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            filterNames.forEachIndexed { i, n ->
                                FilterChip(
                                    selected = filter == i,
                                    onClick = { filter = i },
                                    label = { Text(n) },
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                    }
                    4 -> {
                        Text("Яркость", fontSize = 13.sp)
                        Slider(value = bright, onValueChange = { bright = it }, valueRange = -100f..100f)
                        Text("Контраст", fontSize = 13.sp)
                        Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.5f)
                        Text("Насыщенность", fontSize = 13.sp)
                        Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..2f)
                    }
                    else -> {}
                }
                if ((tool == 3 || tool == 4) && (filter != 0 || bright != 0f || contrast != 1f || sat != 1f)) {
                    Button(onClick = { bakeColors() }) { Text("Применить к фото") }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                TextButton(onClick = { tool = 0; transform(Matrix().apply { postRotate(-90f) }) }) { Text("⟲ Влево") }
                TextButton(onClick = { tool = 0; transform(Matrix().apply { postRotate(90f) }) }) { Text("⟳ Вправо") }
                TextButton(onClick = { tool = 0; transform(Matrix().apply { postScale(-1f, 1f) }) }) { Text("Зеркало ↔") }
                TextButton(onClick = { tool = 0; transform(Matrix().apply { postScale(1f, -1f) }) }) { Text("Зеркало ↕") }
                TextButton(onClick = { tool = if (tool == 1) 0 else 1 }) { Text("Обрезка") }
                TextButton(onClick = { tool = if (tool == 2) 0 else 2 }) { Text("Размер") }
                TextButton(onClick = { tool = if (tool == 3) 0 else 3 }) { Text("Фильтры") }
                TextButton(onClick = { tool = if (tool == 4) 0 else 4 }) { Text("Настройки") }
            }
        }
    }

    if (saveOpen) {
        var fmt by remember { mutableStateOf("jpg") }
        var quality by remember { mutableStateOf(92f) }
        AlertDialog(
            onDismissRequest = { if (!busy) saveOpen = false },
            title = { Text("Сохранить") },
            text = {
                Column {
                    Row {
                        listOf("jpg", "png", "webp").forEach { f ->
                            FilterChip(
                                selected = fmt == f,
                                onClick = { fmt = f },
                                label = { Text(f.uppercase()) },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                    if (fmt != "png") {
                        Text("Качество: " + quality.toInt() + "%", fontSize = 13.sp)
                        Slider(value = quality, onValueChange = { quality = it }, valueRange = 40f..100f)
                    }
                    if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val src = bmp
                    if (src != null) {
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                try {
                                    val fin = bake(src, fullMatrix(filter, bright, contrast, sat))
                                    val dir = PathUtil.parent(path) ?: "/"
                                    val base = PathUtil.name(path).substringBeforeLast('.', PathUtil.name(path))
                                    val b2 = Fs.backend(dir)
                                    val dest = PathUtil.join(dir, PathUtil.uniqueName(b2, dir, base + "_edit." + fmt, false))
                                    Converter.saveBitmap(fin, dest, fmt, quality.toInt())
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            busy = false
                            saveOpen = false
                            vm.toast(if (ok) "Сохранено рядом с оригиналом (_edit)" else "Не удалось сохранить")
                        }
                    }
                }) { Text("Сохранить копию") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { saveOpen = false }) { Text("Отмена") } }
        )
    }
}
