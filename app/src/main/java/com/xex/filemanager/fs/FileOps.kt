package com.xex.filemanager.fs

import android.content.ContentResolver
import android.content.Context
import android.os.Environment
import android.app.usage.StorageStatsManager
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xex.filemanager.model.Album
import com.xex.filemanager.model.Cat
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.model.FileTypes
import com.xex.filemanager.model.Volume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.PriorityQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Состояние длительной операции (копирование, удаление, архивация…). */
class Progress(val title: String) {
    var current by mutableStateOf("")
    var done by mutableStateOf(0L)
    var total by mutableStateOf(0L)
    var itemsDone by mutableStateOf(0)
    var itemsTotal by mutableStateOf(0)
    var error by mutableStateOf<String?>(null)

    @Volatile
    var cancelled = false

    val fraction: Float
        get() = when {
            total > 0L -> (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            itemsTotal > 0 -> (itemsDone.toFloat() / itemsTotal.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
}

object FileOps {

    fun sizeOf(item: FileItem, depth: Int = 0, scope: CoroutineScope? = null): Long {
        if (!item.isDir) return item.size
        if (depth > 40) return 0L
        val list = try {
            Fs.backend(item.path).list(item.path)
        } catch (e: Exception) {
            null
        } ?: return 0L
        var total = 0L
        for (c in list) {
            if (scope != null && !scope.isActive) return total
            total += if (c.isDir) sizeOf(c, depth + 1, scope) else c.size
        }
        return total
    }

    fun copy(src: FileItem, dstDir: String, p: Progress, buf: ByteArray, depth: Int = 0): String {
        if (depth > 40) throw IOException("Слишком глубокая вложенность")
        val sb = Fs.backend(src.path)
        val db = Fs.backend(dstDir)
        val newName = PathUtil.uniqueName(db, dstDir, src.name, src.isDir)
        val dst = PathUtil.join(dstDir, newName)
        if (src.isDir) {
            if (dst == src.path || dst.startsWith(src.path + "/")) {
                throw IOException("Нельзя копировать папку саму в себя")
            }
            if (!db.mkdirs(dst)) throw IOException("Не удалось создать папку $dst")
            val children = sb.list(src.path) ?: emptyList()
            for (c in children) {
                if (p.cancelled) break
                copy(c, dst, p, buf, depth + 1)
            }
        } else {
            p.current = src.name
            sb.input(src.path).use { ins ->
                db.output(dst, false).use { outs ->
                    while (true) {
                        if (p.cancelled) break
                        val n = ins.read(buf)
                        if (n < 0) break
                        outs.write(buf, 0, n)
                        p.done += n
                    }
                }
            }
        }
        return dst
    }

    fun move(src: FileItem, dstDir: String, p: Progress, buf: ByteArray) {
        val sb = Fs.backend(src.path)
        val db = Fs.backend(dstDir)
        if (sb === db) {
            val newName = PathUtil.uniqueName(db, dstDir, src.name, src.isDir)
            val dst = PathUtil.join(dstDir, newName)
            if (src.isDir && (dst == src.path || dst.startsWith(src.path + "/"))) {
                throw IOException("Нельзя переместить папку саму в себя")
            }
            if (sb.rename(src.path, dst)) {
                p.done += src.size
                return
            }
        }
        copy(src, dstDir, p, buf)
        if (!p.cancelled) {
            if (!sb.delete(src.path)) throw IOException("Скопировано, но не удалось удалить оригинал: ${src.name}")
        }
    }

    fun deletePermanent(item: FileItem): Boolean = Fs.backend(item.path).delete(item.path)

    fun createFolder(parent: String, name: String): Boolean =
        Fs.backend(parent).mkdirs(PathUtil.join(parent, name))

    fun createFile(parent: String, name: String): Boolean {
        val b = Fs.backend(parent)
        val path = PathUtil.join(parent, name)
        if (b.exists(path)) return false
        b.output(path, false).close()
        return true
    }

    fun rename(item: FileItem, newName: String): Boolean {
        val parent = PathUtil.parent(item.path) ?: return false
        val target = PathUtil.join(parent, newName)
        val b = Fs.backend(item.path)
        if (b.exists(target)) return false
        return b.rename(item.path, target)
    }

    // ---------- Архивы ----------

    fun zip(items: List<FileItem>, destZip: String, p: Progress, buf: ByteArray) {
        val ob = Fs.backend(destZip)
        ZipOutputStream(ob.output(destZip, false).buffered()).use { zos ->
            for (item in items) {
                if (p.cancelled) break
                addToZip(zos, item, item.name, destZip, p, buf, 0)
            }
        }
    }

    private fun addToZip(
        zos: ZipOutputStream, item: FileItem, entry: String, dest: String,
        p: Progress, buf: ByteArray, depth: Int
    ) {
        if (item.path == dest || depth > 40 || p.cancelled) return
        val b = Fs.backend(item.path)
        if (item.isDir) {
            zos.putNextEntry(ZipEntry("$entry/"))
            zos.closeEntry()
            for (c in b.list(item.path) ?: emptyList()) {
                addToZip(zos, c, entry + "/" + c.name, dest, p, buf, depth + 1)
            }
        } else {
            p.current = item.name
            val e = ZipEntry(entry)
            if (item.modified > 0L) e.time = item.modified
            zos.putNextEntry(e)
            b.input(item.path).use { ins ->
                while (true) {
                    if (p.cancelled) break
                    val n = ins.read(buf)
                    if (n < 0) break
                    zos.write(buf, 0, n)
                    p.done += n
                }
            }
            zos.closeEntry()
        }
    }

    fun unzip(zipPath: String, destDir: String, p: Progress, buf: ByteArray) {
        val ib = Fs.backend(zipPath)
        val ob = Fs.backend(destDir)
        if (!ob.mkdirs(destDir)) throw IOException("Не удалось создать папку $destDir")
        ZipInputStream(ib.input(zipPath).buffered()).use { zis ->
            var e: ZipEntry? = zis.nextEntry
            while (e != null) {
                if (p.cancelled) break
                val name = e.name.replace('\\', '/')
                val bad = name.startsWith("/") || name.split('/').any { it == ".." }
                if (!bad) {
                    val target = PathUtil.join(destDir, name.trimEnd('/'))
                    if (e.isDirectory) {
                        ob.mkdirs(target)
                    } else {
                        val par = PathUtil.parent(target)
                        if (par != null) ob.mkdirs(par)
                        p.current = name
                        ob.output(target, false).use { outs ->
                            while (true) {
                                if (p.cancelled) break
                                val n = zis.read(buf)
                                if (n < 0) break
                                outs.write(buf, 0, n)
                                p.done += n
                            }
                        }
                    }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
    }

    // ---------- Хэши ----------

    fun hash(path: String, algo: String): String {
        val md = MessageDigest.getInstance(algo)
        Fs.backend(path).input(path).use { ins ->
            val buf = ByteArray(65536)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---------- Поиск и анализ ----------

    suspend fun search(root: String, query: String, limit: Int = 3000): List<FileItem> =
        withContext(Dispatchers.IO) {
            val scope = this
            val res = ArrayList<FileItem>()
            val q = query.lowercase()
            val stack = ArrayDeque<String>()
            stack.add(root)
            var visited = 0
            while (stack.isNotEmpty() && scope.isActive && res.size < limit && visited < 500000) {
                val dir = stack.removeLast()
                val list = try {
                    Fs.backend(dir).list(dir)
                } catch (e: Exception) {
                    null
                } ?: continue
                for (c in list) {
                    visited++
                    if (c.name.lowercase().contains(q)) res.add(c)
                    if (c.isDir) stack.add(c.path)
                }
            }
            res
        }

    fun walkFiles(root: String, scope: CoroutineScope, onFile: (FileItem) -> Unit) {
        val stack = ArrayDeque<String>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && scope.isActive && visited < 2000000) {
            val dir = stack.removeLast()
            val list = try {
                Fs.backend(dir).list(dir)
            } catch (e: Exception) {
                null
            } ?: continue
            for (c in list) {
                visited++
                if (c.isDir) stack.add(c.path) else onFile(c)
            }
        }
    }

    fun childSizes(path: String, scope: CoroutineScope): List<Pair<FileItem, Long>> {
        val list = try {
            Fs.backend(path).list(path)
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val out = ArrayList<Pair<FileItem, Long>>()
        for (c in list) {
            if (!scope.isActive) break
            out.add(Pair(c, sizeOf(c, 0, scope)))
        }
        out.sortByDescending { it.second }
        return out
    }

    fun largest(root: String, scope: CoroutineScope, n: Int, onCount: (Int) -> Unit): List<FileItem> {
        val heap = PriorityQueue<FileItem>(n + 1) { a, b -> a.size.compareTo(b.size) }
        var count = 0
        walkFiles(root, scope) { f ->
            count++
            if (count % 300 == 0) onCount(count)
            if (heap.size < n) {
                heap.add(f)
            } else if (f.size > (heap.peek()?.size ?: 0L)) {
                heap.poll()
                heap.add(f)
            }
        }
        onCount(count)
        return heap.toList().sortedByDescending { it.size }
    }

    fun duplicates(root: String, scope: CoroutineScope, onCount: (Int) -> Unit): List<List<FileItem>> {
        val bySize = HashMap<Long, MutableList<FileItem>>()
        var count = 0
        walkFiles(root, scope) { f ->
            count++
            if (count % 300 == 0) onCount(count)
            if (f.size >= 1024L) bySize.getOrPut(f.size) { ArrayList() }.add(f)
        }
        onCount(count)
        val groups = ArrayList<List<FileItem>>()
        for ((size, files) in bySize) {
            if (!scope.isActive) break
            if (files.size < 2 || size > 300L * 1024 * 1024) continue
            val byHash = HashMap<String, MutableList<FileItem>>()
            for (f in files) {
                if (!scope.isActive) break
                val h = try {
                    hash(f.path, "MD5")
                } catch (e: Exception) {
                    continue
                }
                byHash.getOrPut(h) { ArrayList() }.add(f)
            }
            for (g in byHash.values) if (g.size > 1) groups.add(g)
        }
        groups.sortByDescending { it[0].size * (it.size - 1) }
        return groups
    }

    // ---------- Тома и категории ----------

    fun volumes(ctx: Context): List<Volume> {
        val out = ArrayList<Volume>()
        val primary = Environment.getExternalStorageDirectory().absolutePath
        var sTotal = 0L
        var sFree = 0L
        try {
            val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            sTotal = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
            sFree = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
        } catch (e: Exception) {
            // используем StatFs
        }
        fun add(path: String, label: String, removable: Boolean) {
            try {
                val st = StatFs(path)
                var total = st.blockCountLong * st.blockSizeLong
                var free = st.availableBlocksLong * st.blockSizeLong
                if (path == primary && sTotal > 0L) {
                    total = sTotal
                    free = sFree
                }
                out.add(Volume(path, label, total, free, removable))
            } catch (e: Exception) {
                out.add(Volume(path, label, 0L, 0L, removable))
            }
        }
        add(primary, "Основная память", false)
        for (f in ctx.getExternalFilesDirs(null)) {
            if (f == null) continue
            val s = f.absolutePath
            val idx = s.indexOf("/Android/data")
            if (idx > 0) {
                val root = s.substring(0, idx)
                if (root != primary && out.none { it.path == root }) add(root, "SD-карта / USB", true)
            }
        }
        return out
    }

    private fun categorySelection(cat: Cat, sinceSec: Long): Pair<String, Array<String>?> = when (cat) {
        Cat.IMAGES -> Pair("media_type=1", null)
        Cat.VIDEO -> Pair("media_type=3", null)
        Cat.AUDIO -> Pair("media_type=2", null)
        Cat.DOCS -> Pair(
            FileTypes.document.joinToString(" OR ") { "_data LIKE ?" },
            FileTypes.document.map { "%.$it" }.toTypedArray()
        )
        Cat.ARCHIVES -> Pair(
            FileTypes.archive.joinToString(" OR ") { "_data LIKE ?" },
            FileTypes.archive.map { "%.$it" }.toTypedArray()
        )
        Cat.APK -> Pair(
            FileTypes.apk.joinToString(" OR ") { "_data LIKE ?" },
            FileTypes.apk.map { "%.$it" }.toTypedArray()
        )
        Cat.RECENT -> Pair("_data IS NOT NULL AND _size>0 AND date_modified>?", arrayOf(sinceSec.toString()))
    }

    private fun sinceSeconds(clearedAtMs: Long): Long {
        val weekAgo = System.currentTimeMillis() / 1000L - 7L * 86400L
        return maxOf(weekAgo, clearedAtMs / 1000L)
    }

    /** Файлы категории из MediaStore, новые сверху. */
    fun queryCategory(ctx: Context, cat: Cat, clearedAtMs: Long = 0L, limit: Int = 30000): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val sel = categorySelection(cat, sinceSeconds(clearedAtMs))
        val out = ArrayList<FileItem>()
        try {
            ctx.contentResolver.query(uri, proj, sel.first, sel.second, "date_modified DESC")?.use { c ->
                val iData = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val iName = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (c.moveToNext() && out.size < limit) {
                    val path = c.getString(iData) ?: continue
                    if (path.contains("/.")) continue
                    if (cat == Cat.RECENT && path.contains("/Android/")) continue
                    val f = File(path)
                    if (!f.isFile) continue
                    val name = c.getString(iName) ?: f.name
                    out.add(FileItem(path, name, false, c.getLong(iSize), c.getLong(iDate) * 1000L))
                }
            }
        } catch (e: Exception) {
            // MediaStore недоступен — возвращаем что есть
        }
        return out
    }

    /** Количество и общий размер файлов категории. */
    fun statCategory(ctx: Context, cat: Cat, clearedAtMs: Long = 0L): Pair<Int, Long> {
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.SIZE)
        val sel = categorySelection(cat, sinceSeconds(clearedAtMs))
        var count = 0
        var total = 0L
        try {
            ctx.contentResolver.query(uri, proj, sel.first, sel.second, null)?.use { c ->
                val iData = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: continue
                    if (path.contains("/.")) continue
                    if (cat == Cat.RECENT && path.contains("/Android/")) continue
                    count++
                    total += c.getLong(iSize)
                }
            }
        } catch (e: Exception) {
            // нет доступа
        }
        return Pair(count, total)
    }

    /** Количество файлов и размер папки (рекурсивно). */
    fun folderStat(path: String): Pair<Int, Long> {
        var count = 0
        var total = 0L
        val stack = ArrayDeque<String>()
        stack.add(path)
        var visited = 0
        while (stack.isNotEmpty() && visited < 300000) {
            val dir = stack.removeLast()
            val list = try {
                Fs.backend(dir).list(dir)
            } catch (e: Exception) {
                null
            } ?: continue
            for (c in list) {
                visited++
                if (c.isDir) {
                    stack.add(c.path)
                } else {
                    count++
                    total += c.size
                }
            }
        }
        return Pair(count, total)
    }

    fun buildAlbums(list: List<FileItem>, isVideo: Boolean): List<Album> {
        val groups = LinkedHashMap<String, MutableList<FileItem>>()
        for (f in list) {
            val parent = PathUtil.parent(f.path) ?: continue
            groups.getOrPut(parent) { ArrayList() }.add(f)
        }
        return groups.map { (dir, files) ->
            Album(dir, PathUtil.name(dir), files.size, files[0].path, isVideo)
        }
    }
}
