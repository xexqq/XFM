package com.xex.filemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.util.Utils

@Composable
fun DecompileScreen(vm: AppViewModel, path: String) {
    var query by remember { mutableStateOf("") }
    val res = vm.decRes
    val list = remember(res, query) {
        val l = res?.classes ?: emptyList()
        if (query.isBlank()) l else l.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    Scaffold(
        topBar = {
            Column {
                XfmTopBar(
                    title = {
                        Text(PathUtil.name(path), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    }
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск класса") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val err = vm.decError
            if (vm.decLoading) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Читаю классы…", modifier = Modifier.padding(top = 12.dp))
                }
            } else if (err != null) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            } else if (res != null) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        (if (res.mode == "dex") "Dalvik → smali" else "Java → CFR") + " • классов: " + res.classes.size,
                        fontSize = 13.sp,
                        color = LocalXfm.current.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it }) { n ->
                            Column(Modifier.fillMaxWidth().clickable { vm.decompileClass(n) }) {
                                Text(
                                    n,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                                HorizontalDivider(color = LocalXfm.current.separator)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CodeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val lines = remember(vm.codeText) { vm.codeText.split('\n') }
    val hs = rememberScrollState()
    Scaffold(
        topBar = {
            XfmTopBar(
                title = { Text(vm.codeTitle.substringAfterLast('.'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    IconButton(onClick = { Utils.copyText(ctx, vm.codeText) }) {
                        Icon(painterResource(R.drawable.ic_copy), "Копировать")
                    }
                    IconButton(onClick = { vm.saveCode() }) {
                        Icon(painterResource(R.drawable.ic_save), "Сохранить")
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).horizontalScroll(hs)) {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(lines) { i, line ->
                    Row {
                        Text(
                            (i + 1).toString().padStart(5) + "  ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = LocalXfm.current.secondary
                        )
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            softWrap = false,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
