package com.xex.filemanager.fs

import android.os.ParcelFileDescriptor
import com.xex.filemanager.IFileService
import com.xex.filemanager.model.FileItem
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

interface FsBackend {
    fun list(path: String): List<FileItem>?
    fun stat(path: String): FileItem?
    fun input(path: String): InputStream
    fun output(path: String, append: Boolean = false): OutputStream
    fun mkdirs(path: String): Boolean
    fun delete(path: String): Boolean
    fun rename(from: String, to: String): Boolean
    fun exists(path: String): Boolean
}

class LocalBackend : FsBackend {
    private fun toItem(f: File, withCount: Boolean): FileItem {
        val dir = f.isDirectory
        val count = if (dir && withCount) (f.list()?.size ?: -1) else -1
        return FileItem(
            path = f.path,
            name = f.name,
            isDir = dir,
            size = if (dir) 0L else f.length(),
            modified = f.lastModified(),
            children = count,
            readable = f.canRead()
        )
    }

    override fun list(path: String): List<FileItem>? {
        val files = File(path).listFiles() ?: return null
        val withCount = files.count { it.isDirectory } <= 300
        return files.map { toItem(it, withCount) }
    }

    override fun stat(path: String): FileItem? {
        val f = File(path)
        return if (f.exists()) toItem(f, true) else null
    }

    override fun input(path: String): InputStream = FileInputStream(path)

    override fun output(path: String, append: Boolean): OutputStream = FileOutputStream(path, append)

    override fun mkdirs(path: String): Boolean {
        val f = File(path)
        return f.isDirectory || f.mkdirs()
    }

    override fun delete(path: String): Boolean = File(path).deleteRecursively()

    override fun rename(from: String, to: String): Boolean = File(from).renameTo(File(to))

    override fun exists(path: String): Boolean = File(path).exists()
}

class RemoteBackend(private val svc: IFileService) : FsBackend {
    private fun parse(line: String, parent: String): FileItem? {
        val p = line.split('\t')
        if (p.size < 5) return null
        val name = p[0]
        return FileItem(
            path = PathUtil.join(parent, name),
            name = name,
            isDir = p[1] == "d",
            size = p[2].toLongOrNull() ?: 0L,
            modified = p[3].toLongOrNull() ?: 0L,
            children = p[4].toIntOrNull() ?: -1
        )
    }

    override fun list(path: String): List<FileItem>? {
        val raw = svc.list(path) ?: return null
        if (raw == "!") return null
        val out = ArrayList<FileItem>()
        for (line in raw.split('\n')) {
            if (line.isEmpty()) continue
            val item = parse(line, path)
            if (item != null) out.add(item)
        }
        return out
    }

    override fun stat(path: String): FileItem? {
        val raw = svc.stat(path) ?: return null
        if (raw.isEmpty()) return null
        return parse(raw, PathUtil.parent(path) ?: "/")
    }

    override fun input(path: String): InputStream {
        val pfd: ParcelFileDescriptor = svc.openRead(path) ?: throw FileNotFoundException(path)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd)
    }

    override fun output(path: String, append: Boolean): OutputStream {
        val pfd: ParcelFileDescriptor = svc.openWrite(path, append) ?: throw FileNotFoundException(path)
        return ParcelFileDescriptor.AutoCloseOutputStream(pfd)
    }

    override fun mkdirs(path: String): Boolean = svc.mkdirs(path)
    override fun delete(path: String): Boolean = svc.delete(path)
    override fun rename(from: String, to: String): Boolean = svc.rename(from, to)
    override fun exists(path: String): Boolean = svc.exists(path)
}

object PathUtil {
    fun parent(path: String): String? {
        val p = if (path.length > 1) path.trimEnd('/') else path
        if (p.isEmpty() || p == "/") return null
        val i = p.lastIndexOf('/')
        return if (i <= 0) "/" else p.substring(0, i)
    }

    fun name(path: String): String = path.trimEnd('/').substringAfterLast('/')

    fun join(dir: String, name: String): String =
        if (dir.endsWith("/")) dir + name else "$dir/$name"

    fun uniqueName(b: FsBackend, dir: String, name: String, isDir: Boolean): String {
        if (!b.exists(join(dir, name))) return name
        val dot = if (isDir) -1 else name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val cand = "$base ($i)$ext"
            if (!b.exists(join(dir, cand))) return cand
            i++
        }
    }
}

/** Маршрутизатор: выбирает обычный доступ или привилегированный (Shizuku). */
object Fs {
    val local: FsBackend = LocalBackend()

    @Volatile
    var remote: FsBackend? = null

    /** 0 = выкл, 1 = авто (только защищённые пути), 2 = всегда */
    @Volatile
    var mode: Int = 1

    private val androidDirs = Regex("^/(storage/[^/]+|sdcard)/Android/(data|obb)(/.*)?$")

    /** Папки Android/data и Android/obb чужих приложений. */
    fun isAppDataDir(path: String): Boolean =
        androidDirs.matches(path) && !path.contains("/Android/data/com.xex.filemanager")

    fun needsPrivilege(path: String): Boolean {
        if (androidDirs.matches(path)) return isAppDataDir(path)
        if (path == "/") return false
        return !(path.startsWith("/storage") || path.startsWith("/sdcard"))
    }

    fun backend(path: String): FsBackend {
        val r = remote
        if (r != null && mode != 0 && (mode == 2 || needsPrivilege(path))) return r
        return local
    }

    fun isRemote(path: String): Boolean {
        val r = remote
        return r != null && backend(path) === r
    }
}
