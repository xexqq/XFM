package com.xex.filemanager.fs

import android.os.ParcelFileDescriptor
import com.xex.filemanager.IFileService
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Запускается Shizuku в отдельном процессе с правами shell (adb) или root.
 * Все исключения перехватываются, иначе процесс сервиса упадёт.
 */
class PrivilegedFileService : IFileService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    private fun line(f: File): String {
        val dir = f.isDirectory
        val count = if (dir) (f.list()?.size ?: -1) else -1
        val size = if (dir) 0L else f.length()
        val name = f.name.replace('\t', ' ').replace('\n', ' ')
        return name + "\t" + (if (dir) "d" else "f") + "\t" + size + "\t" + f.lastModified() + "\t" + count
    }

    override fun list(path: String): String {
        return try {
            val files = File(path).listFiles() ?: return "!"
            val sb = StringBuilder()
            for (f in files) {
                sb.append(line(f)).append('\n')
            }
            sb.toString()
        } catch (e: Throwable) {
            "!"
        }
    }

    override fun stat(path: String): String {
        return try {
            val f = File(path)
            if (f.exists()) line(f) else ""
        } catch (e: Throwable) {
            ""
        }
    }

    override fun openRead(path: String): ParcelFileDescriptor? {
        return try {
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Throwable) {
            null
        }
    }

    override fun openWrite(path: String, append: Boolean): ParcelFileDescriptor? {
        return try {
            val mode = ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_CREATE or
                (if (append) ParcelFileDescriptor.MODE_APPEND else ParcelFileDescriptor.MODE_TRUNCATE)
            ParcelFileDescriptor.open(File(path), mode)
        } catch (e: Throwable) {
            null
        }
    }

    override fun mkdirs(path: String): Boolean {
        return try {
            val f = File(path)
            f.isDirectory || f.mkdirs()
        } catch (e: Throwable) {
            false
        }
    }

    override fun delete(path: String): Boolean {
        return try {
            File(path).deleteRecursively()
        } catch (e: Throwable) {
            false
        }
    }

    override fun rename(from: String, to: String): Boolean {
        return try {
            File(from).renameTo(File(to))
        } catch (e: Throwable) {
            false
        }
    }

    override fun exists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Throwable) {
            false
        }
    }

    override fun exec(command: String): String {
        return try {
            val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val killer = Thread {
                try {
                    Thread.sleep(30000)
                    p.destroy()
                } catch (e: InterruptedException) {
                    // команда завершилась раньше
                }
            }
            killer.isDaemon = true
            killer.start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor(5, TimeUnit.SECONDS)
            killer.interrupt()
            val code = try {
                p.exitValue()
            } catch (e: Throwable) {
                -1
            }
            out.take(200000) + "\n[код выхода: " + code + "]"
        } catch (e: Throwable) {
            "Ошибка: " + e.message
        }
    }

    override fun myUid(): Int = android.os.Process.myUid()
}
