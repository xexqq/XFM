package com.xex.filemanager.fs

import com.github.junrar.Archive
import com.xex.filemanager.model.FileItem
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Date
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ArchiveEntry(val path: String, val isDir: Boolean, val size: Long, val modified: Long)

object Archives {
    /** Тип архива по имени файла или null, если формат не поддерживается. */
    fun kind(name: String): String? {
        val n = name.lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> "targz"
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> "tarbz2"
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> "tarxz"
            n.endsWith(".tar") -> "tar"
            n.endsWith(".7z") -> "7z"
            n.endsWith(".rar") -> "rar"
            n.endsWith(".gz") -> "gz"
            n.endsWith(".bz2") -> "bz2"
            n.endsWith(".xz") -> "xz"
            n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".cbz") ||
                n.endsWith(".epub") || n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm") ||
                n.endsWith(".docx") || n.endsWith(".xlsx") || n.endsWith(".pptx") || n.endsWith(".odt") ||
                n.endsWith(".ods") || n.endsWith(".odp") || n.endsWith(".aar") -> "zip"
            else -> null
        }
    }

    fun supported(name: String): Boolean = kind(name) != null

    fun baseName(name: String): String {
        val n = name.lowercase()
        for (suf in listOf(".tar.gz", ".tar.bz2", ".tar.xz")) {
            if (n.endsWith(suf)) return name.substring(0, name.length - suf.length)
        }
        return name.substringBeforeLast('.', name)
    }

    private fun clean(p: String): String = p.replace('\\', '/').trim('/')

    private fun openZip(file: File): ZipFile {
        return try {
            ZipFile(file, Charsets.UTF_8)
        } catch (e: Exception) {
            ZipFile(file, Charset.forName("IBM866"))
        }
    }

    /**
     * Один проход по архиву. [visit] получает запись и функцию, открывающую её поток;
     * вернуть false — остановить обход.
     */
    fun forEach(file: File, kind: String, tmp: File, visit: (ArchiveEntry, () -> InputStream) -> Boolean) {
        when (kind) {
            "zip" -> openZip(file).use { z ->
                for (e in z.entries().asSequence()) {
                    val ent = ArchiveEntry(clean(e.name), e.isDirectory, if (e.size >= 0) e.size else 0L, e.time)
                    if (ent.path.isEmpty()) continue
                    if (!visit(ent) { z.getInputStream(e) }) break
                }
            }
            "tar", "targz", "tarbz2", "tarxz" -> {
                val raw = BufferedInputStream(FileInputStream(file))
                val ins: InputStream = when (kind) {
                    "targz" -> GZIPInputStream(raw)
                    "tarbz2" -> BZip2CompressorInputStream(raw)
                    "tarxz" -> XZCompressorInputStream(raw)
                    else -> raw
                }
                TarArchiveInputStream(ins).use { t ->
                    var e: TarArchiveEntry? = t.nextTarEntry
                    while (true) {
                        val cur: TarArchiveEntry = e ?: break
                        val ent = ArchiveEntry(clean(cur.name), cur.isDirectory, cur.size, cur.modTime?.time ?: 0L)
                        if (ent.path.isNotEmpty()) {
                            if (!visit(ent) { t }) break
                        }
                        e = t.nextTarEntry
                    }
                }
            }
            "7z" -> SevenZFile(file).use { sz ->
                var e: SevenZArchiveEntry? = sz.nextEntry
                while (true) {
                    val cur: SevenZArchiveEntry = e ?: break
                    val mod = if (cur.hasLastModifiedDate) cur.lastModifiedDate.time else 0L
                    val ent = ArchiveEntry(clean(cur.name), cur.isDirectory, cur.size, mod)
                    if (ent.path.isNotEmpty()) {
                        if (!visit(ent) { sz.getInputStream(cur) }) break
                    }
                    e = sz.nextEntry
                }
            }
            "rar" -> Archive(file).use { a ->
                var h = a.nextFileHeader()
                while (true) {
                    val cur = h ?: break
                    val ent = ArchiveEntry(clean(cur.fileName), cur.isDirectory, cur.fullUnpackSize, cur.mTime?.time ?: 0L)
                    if (ent.path.isNotEmpty()) {
                        val go = visit(ent) {
                            val t = File.createTempFile("rar", ".tmp", tmp)
                            FileOutputStream(t).use { o -> a.extractFile(cur, o) }
                            object : FilterInputStream(FileInputStream(t)) {
                                override fun close() {
                                    super.close()
                                    t.delete()
                                }
                            }
                        }
                        if (!go) break
                    }
                    h = a.nextFileHeader()
                }
            }
            "gz", "bz2", "xz" -> {
                val raw = BufferedInputStream(FileInputStream(file))
                val name = file.name.substringBeforeLast('.', file.name)
                val ent = ArchiveEntry(name, false, 0L, file.lastModified())
                try {
                    visit(ent) {
                        when (kind) {
                            "gz" -> GZIPInputStream(raw)
                            "bz2" -> BZip2CompressorInputStream(raw)
                            else -> XZCompressorInputStream(raw)
                        }
                    }
                } finally {
                    raw.close()
                }
            }
            else -> throw IOException("Формат не поддерживается")
        }
    }

    fun list(file: File, kind: String, tmp: File): List<ArchiveEntry> {
        val out = ArrayList<ArchiveEntry>()
        forEach(file, kind, tmp) { e, _ ->
            out.add(e)
            true
        }
        return out
    }

    /** Распаковка в [destDir]. [only] — пути выбранных записей внутри архива (null = всё). */
    fun extract(
        file: File, kind: String, tmp: File, destDir: String,
        only: List<String>?, p: Progress, buf: ByteArray
    ) {
        val ob = Fs.backend(destDir)
        if (!ob.mkdirs(destDir)) throw IOException("Не удалось создать папку $destDir")
        forEach(file, kind, tmp) { e, supplier ->
            if (p.cancelled) return@forEach false
            var rel = e.path
            if (only != null) {
                val root = only.firstOrNull { e.path == it || e.path.startsWith("$it/") }
                if (root == null) return@forEach true
                val pre = root.substringBeforeLast('/', "")
                rel = if (pre.isEmpty()) e.path else e.path.substring(pre.length + 1)
            }
            val bad = rel.startsWith("/") || rel.split('/').any { it == ".." }
            if (bad || rel.isEmpty()) return@forEach true
            val target = PathUtil.join(destDir, rel)
            if (e.isDir) {
                ob.mkdirs(target)
            } else {
                val par = PathUtil.parent(target)
                if (par != null) ob.mkdirs(par)
                p.current = rel
                supplier().use { ins ->
                    ob.output(target, false).use { outs ->
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
            true
        }
    }

    // ---------- Создание TAR.GZ и 7Z ----------

    fun createTarGz(items: List<FileItem>, dest: String, p: Progress, buf: ByteArray) {
        val ob = Fs.backend(dest)
        val os = TarArchiveOutputStream(GZIPOutputStream(BufferedOutputStream(ob.output(dest, false))))
        os.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
        os.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
        os.use { t ->
            for (item in items) {
                if (p.cancelled) break
                addToTar(t, item, item.name, dest, p, buf, 0)
            }
        }
    }

    private fun addToTar(
        t: TarArchiveOutputStream, item: FileItem, name: String, dest: String,
        p: Progress, buf: ByteArray, depth: Int
    ) {
        if (item.path == dest || depth > 40 || p.cancelled) return
        val b = Fs.backend(item.path)
        if (item.isDir) {
            val te = TarArchiveEntry("$name/")
            te.modTime = Date(item.modified)
            t.putArchiveEntry(te)
            t.closeArchiveEntry()
            for (c in b.list(item.path) ?: emptyList()) addToTar(t, c, name + "/" + c.name, dest, p, buf, depth + 1)
        } else {
            p.current = item.name
            val te = TarArchiveEntry(name)
            te.size = item.size
            te.modTime = Date(item.modified)
            t.putArchiveEntry(te)
            b.input(item.path).use { ins ->
                var left = item.size
                while (left > 0 && !p.cancelled) {
                    val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) break
                    t.write(buf, 0, n)
                    left -= n
                    p.done += n
                }
            }
            t.closeArchiveEntry()
        }
    }

    fun createSevenZ(items: List<FileItem>, dest: String, tmpDir: File, p: Progress, buf: ByteArray) {
        val tmp = File.createTempFile("xfm", ".7z", tmpDir)
        try {
            SevenZOutputFile(tmp).use { out ->
                for (item in items) {
                    if (p.cancelled) break
                    addToSevenZ(out, item, item.name, p, buf, 0)
                }
            }
            if (!p.cancelled) {
                Fs.backend(dest).output(dest, false).use { o -> FileInputStream(tmp).use { it.copyTo(o) } }
            }
        } finally {
            tmp.delete()
        }
    }

    private fun addToSevenZ(out: SevenZOutputFile, item: FileItem, name: String, p: Progress, buf: ByteArray, depth: Int) {
        if (depth > 40 || p.cancelled) return
        val b = Fs.backend(item.path)
        val e = SevenZArchiveEntry()
        e.name = name
        e.lastModifiedDate = Date(item.modified)
        if (item.isDir) {
            e.isDirectory = true
            out.putArchiveEntry(e)
            out.closeArchiveEntry()
            for (c in b.list(item.path) ?: emptyList()) addToSevenZ(out, c, name + "/" + c.name, p, buf, depth + 1)
        } else {
            p.current = item.name
            e.isDirectory = false
            e.size = item.size
            out.putArchiveEntry(e)
            b.input(item.path).use { ins ->
                while (!p.cancelled) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    p.done += n
                }
            }
            out.closeArchiveEntry()
        }
    }
}

/** Папки и файлы архива внутри каталога [dir] (пустая строка — корень). */
fun archiveChildren(entries: List<ArchiveEntry>, dir: String): List<FileItem> {
    val prefix = if (dir.isEmpty()) "" else "$dir/"
    val map = LinkedHashMap<String, FileItem>()
    for (e in entries) {
        if (!e.path.startsWith(prefix) || e.path == dir) continue
        val rest = e.path.substring(prefix.length)
        if (rest.isEmpty()) continue
        val slash = rest.indexOf('/')
        if (slash < 0) {
            map[prefix + rest] = FileItem(prefix + rest, rest, e.isDir, e.size, e.modified)
        } else {
            val seg = rest.substring(0, slash)
            val key = prefix + seg
            if (!map.containsKey(key)) map[key] = FileItem(key, seg, true, 0L, e.modified)
        }
    }
    return map.values.toList()
}
