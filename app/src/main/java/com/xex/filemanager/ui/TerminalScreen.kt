@file:OptIn(ExperimentalMaterial3Api::class)

package com.xex.filemanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.fs.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TerminalScreen(vm: AppViewModel) {
    var cmd by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("Команды выполняются с правами Shizuku (shell или root).\nПример: ls -la /data/local/tmp\n") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    LaunchedEffect(log) { scroll.scrollTo(scroll.maxValue) }

    fun execute() {
        val c = cmd.trim()
        if (c.isEmpty() || busy) return
        cmd = ""
        busy = true
        log += "\n$ $c\n"
        scope.launch {
            val out = withContext(Dispatchers.IO) { ShizukuManager.exec(c) }
            log += out + "\n"
            busy = false
        }
    }

    Scaffold(
        topBar = {
            XfmTopBar(
                title = { Text("Терминал (Shizuku)") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    text = log,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .verticalScroll(scroll)
                        .padding(10.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                OutlinedTextField(
                    value = cmd,
                    onValueChange = { cmd = it },
                    placeholder = { Text("команда") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { execute() }),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { execute() }, enabled = !busy) { Icon(Icons.Filled.PlayArrow, "Выполнить") }
            }
        }
    }
}
