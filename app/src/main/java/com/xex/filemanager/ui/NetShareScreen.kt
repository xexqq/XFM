package com.xex.filemanager.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.net.ShareService
import com.xex.filemanager.net.ShareState
import com.xex.filemanager.util.Utils

@Composable
fun NetShareScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    var portText by remember { mutableStateOf(s.sharePort.toString()) }
    var pass by remember { mutableStateOf(s.sharePass) }

    fun startServer() {
        val port = (portText.toIntOrNull() ?: 8080).coerceIn(1024, 65535)
        portText = port.toString()
        s.sharePort = port
        s.sharePass = pass
        val i = Intent(ctx, ShareService::class.java)
        i.putExtra("port", port)
        i.putExtra("pass", pass)
        ContextCompat.startForegroundService(ctx, i)
    }

    fun stopServer() {
        val i = Intent(ctx, ShareService::class.java)
        i.action = ShareService.ACTION_STOP
        ctx.startService(i)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startServer()
    }

    Scaffold(
        topBar = {
            XfmTopBar(
                title = { Text("Доступ из сети") },
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
                .padding(16.dp)
        ) {
            Text(
                if (ShareState.running) "Сервер включён" else "Сервер выключен",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Открой адрес в браузере на компьютере или другом телефоне в той же Wi-Fi сети. " +
                    "Там можно смотреть папки, скачивать и загружать файлы, удалять и создавать папки.",
                fontSize = 14.sp,
                color = LocalXfm.current.secondary
            )
            Spacer(Modifier.height(14.dp))
            if (ShareState.running) {
                SelectionContainer {
                    Text(ShareState.address, fontSize = 24.sp, color = LocalXfm.current.section)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = { Utils.copyText(ctx, ShareState.address) }) { Text("Скопировать адрес") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { stopServer() }) { Text("Остановить") }
                }
            } else {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Порт") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Пароль (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    val need = Build.VERSION.SDK_INT >= 33 &&
                        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (need) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else startServer()
                }) { Text("Запустить сервер") }
            }
            if (ShareState.error.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(ShareState.error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Сервер открывает основную память. Без пароля доступ получит любой в твоей сети — задай пароль, если сеть общая. " +
                    "Имя пользователя в окне браузера может быть любым.",
                fontSize = 13.sp,
                color = LocalXfm.current.secondary
            )
        }
    }
}
