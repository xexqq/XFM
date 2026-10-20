package com.xex.filemanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.xex.filemanager.AppDialog
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.model.AppEntry
import com.xex.filemanager.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIcon(pkg: String, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size))
    }
}

@Composable
private fun AppRow(e: AppEntry, size: Long, onClick: () -> Unit) {
    val x = LocalXfm.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(e.pkg)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = e.label,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = e.version,
                        fontSize = 14.sp,
                        color = x.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = formatSize(size), fontSize = 14.sp, color = x.secondary, maxLines = 1)
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = x.separator)
    }
}

@Composable
fun AppsScreen(vm: AppViewModel) {
    var showAll by remember { mutableStateOf(false) }
    val sizes = remember(vm.analysis) { vm.analysis?.apps?.associate { it.first.pkg to it.second } ?: emptyMap() }
    val list = remember(vm.apps, showAll, sizes) {
        val l = if (showAll) vm.apps else vm.apps.filter { !it.system }
        if (sizes.isEmpty()) l else l.sortedByDescending { sizes[it.pkg] ?: it.apkSize }
    }
    Scaffold(
        topBar = {
            Column {
                XfmTopBar(
                    title = { Text("Программы") },
                    navigationIcon = {
                        IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    }
                )
                PathBar(
                    crumbs = listOf(Crumb(null, R.drawable.ic_android, Color(0xFFA4C639)) {}),
                    onHome = { vm.goHome() },
                    trailing = {
                        PathButton(if (showAll) "Все" else "Мои") { showAll = !showAll }
                    }
                )
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (vm.appsLoading && vm.apps.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (list.isEmpty()) {
                Text(
                    "Приложений не найдено",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.pkg }) { e ->
                        AppRow(e, sizes[e.pkg] ?: e.apkSize) { vm.dialog = AppDialog.AppActions(e) }
                    }
                }
            }
        }
    }
}
