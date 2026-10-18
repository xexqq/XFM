package com.xex.filemanager.fs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xex.filemanager.IFileService
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, CONNECTING, CONNECTED }

object ShizukuManager {
    private const val REQUEST_CODE = 4242
    const val PACKAGE = "moe.shizuku.privileged.api"

    var state by mutableStateOf(ShizukuState.NOT_INSTALLED)
        private set
    var uid by mutableStateOf(-1)
        private set
    var lastError by mutableStateOf("")
        private set

    private var appCtx: Context? = null
    private var service: IFileService? = null
    private var bound = false

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }

    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        bound = false
        Fs.remote = null
        refresh()
    }

    private val permResult = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            refresh()
        } else {
            lastError = "Разрешение Shizuku не выдано"
            refresh()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                val s = IFileService.Stub.asInterface(binder)
                service = s
                Fs.remote = RemoteBackend(s)
                uid = try {
                    s.myUid()
                } catch (e: Throwable) {
                    -1
                }
                state = ShizukuState.CONNECTED
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            Fs.remote = null
            refresh()
        }
    }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permResult)
        } catch (e: Throwable) {
            lastError = e.message ?: "Shizuku недоступна"
        }
        refresh()
    }

    fun isInstalled(): Boolean {
        val c = appCtx ?: return false
        return try {
            c.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun refresh() {
        try {
            if (!Shizuku.pingBinder()) {
                state = if (isInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                state = ShizukuState.NO_PERMISSION
                return
            }
            if (service != null) {
                state = ShizukuState.CONNECTED
                return
            }
            state = ShizukuState.CONNECTING
            bind()
        } catch (e: Throwable) {
            lastError = e.message ?: "Ошибка Shizuku"
            state = if (isInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        }
    }

    private fun bind() {
        val c = appCtx ?: return
        if (bound) return
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(c.packageName, PrivilegedFileService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("fs")
                .debuggable(false)
                .version(1)
            Shizuku.bindUserService(args, connection)
            bound = true
        } catch (e: Throwable) {
            lastError = e.message ?: "Не удалось запустить сервис"
        }
    }

    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                refresh()
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                lastError = "Выдайте разрешение вручную в приложении Shizuku"
                return
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) {
            lastError = e.message ?: "Ошибка запроса разрешения"
        }
    }

    fun openShizukuApp(ctx: Context) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(PACKAGE)
        try {
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            } else {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(web)
            }
        } catch (e: Throwable) {
            lastError = e.message ?: "Не удалось открыть Shizuku"
        }
    }

    fun exec(cmd: String): String {
        val s = service ?: return "Shizuku не подключена"
        return try {
            s.exec(cmd)
        } catch (e: Throwable) {
            "Ошибка: " + e.message
        }
    }

    fun clearError() {
        lastError = ""
    }
}
