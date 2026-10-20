package com.xex.filemanager.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xex.filemanager.MainActivity
import com.xex.filemanager.R
import com.xex.filemanager.util.Utils
import java.net.Inet4Address
import java.net.NetworkInterface

object ShareState {
    var running by mutableStateOf(false)
    var address by mutableStateOf("")
    var error by mutableStateOf("")
}

fun localIp(): String {
    try {
        val ifs = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
        for (nif in ifs.asSequence()) {
            if (!nif.isUp || nif.isLoopback) continue
            for (a in nif.inetAddresses.asSequence()) {
                if (a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress) return a.hostAddress ?: continue
            }
        }
    } catch (e: Exception) {
        // нет сети
    }
    return "127.0.0.1"
}

class ShareService : Service() {
    private var server: FileHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        val port = intent?.getIntExtra("port", 8080) ?: 8080
        val pass = intent?.getStringExtra("pass") ?: ""
        val addr = "http://" + localIp() + ":" + port
        startFg(addr)
        Thread {
            try {
                server?.stop()
                val s = FileHttpServer(Utils.primaryRoot(), port, pass)
                s.start()
                server = s
                ShareState.address = addr
                ShareState.error = ""
                ShareState.running = true
            } catch (e: Exception) {
                ShareState.error = e.message ?: "Не удалось запустить сервер"
                ShareState.running = false
                shutdown()
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun startFg(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Доступ из сети", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_folder)
            .setContentTitle("XFM: доступ из сети включён")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun shutdown() {
        server?.stop()
        server = null
        ShareState.running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        server?.stop()
        ShareState.running = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.xex.filemanager.STOP_SHARE"
        private const val CHANNEL = "share"
        private const val NOTIF_ID = 77
    }
}
