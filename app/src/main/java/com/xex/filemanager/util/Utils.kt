package com.xex.filemanager.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.xex.filemanager.fs.Fs
import com.xex.filemanager.model.FileItem
import java.io.File

object Utils {
    fun uri(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    fun primaryRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    /** Возвращает локальный файл; если файл в защищённой области (Shizuku) — копирует в кэш. */
    fun localCopy(ctx: Context, item: FileItem): File {
        if (!Fs.isRemote(item.path)) return File(item.path)
        val dir = File(ctx.cacheDir, "shared")
        dir.mkdirs()
        val target = File(dir, item.name)
        Fs.backend(item.path).input(item.path).use { ins ->
            target.outputStream().use { outs -> ins.copyTo(outs) }
        }
        return target
    }

    fun view(ctx: Context, file: File, mime: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri(ctx, file), mime)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            toast(ctx, "Нет приложения для открытия этого файла")
        } catch (e: Exception) {
            toast(ctx, "Не удалось открыть: " + (e.message ?: ""))
        }
    }

    fun share(ctx: Context, files: List<File>) {
        if (files.isEmpty()) return
        try {
            val i: Intent
            if (files.size == 1) {
                i = Intent(Intent.ACTION_SEND)
                i.type = "*/*"
                i.putExtra(Intent.EXTRA_STREAM, uri(ctx, files[0]))
            } else {
                i = Intent(Intent.ACTION_SEND_MULTIPLE)
                i.type = "*/*"
                i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uri(ctx, it) }))
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(i, "Поделиться")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        } catch (e: Exception) {
            toast(ctx, "Не удалось поделиться: " + (e.message ?: ""))
        }
    }

    fun copyText(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("path", text))
        toast(ctx, "Скопировано")
    }

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    fun hasAllFilesAccess(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ctx.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun openAllFilesSettings(ctx: Context) {
        try {
            val i = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + ctx.packageName)
            )
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            val i = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }
}
