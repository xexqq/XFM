package com.xex.filemanager.net

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.compose.runtime.mutableStateListOf
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.xex.filemanager.fs.FsBackend
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.model.FileItem
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Conn(
    val id: String,
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val path: String,
    val cloud: Boolean
)

object ConnStore {
    val conns = mutableStateListOf<Conn>()
    private var ctx: Context? = null

    fun init(c: Context) {
        ctx = c.applicationContext
        conns.clear()
        val raw = c.getSharedPreferences("conns", Context.MODE_PRIVATE).getString("list", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                conns.add(
                    Conn(
                        o.getString("id"), o.getString("name"), o.getString("type"), o.getString("host"),
                        o.getInt("port"), o.getString("user"), o.getString("pass"), o.getString("path"),
                        o.getBoolean("cloud")
                    )
                )
            }
        } catch (e: Exception) {
            // повреждённый список — начинаем с пустого
        }
    }

    private fun save() {
        val c = ctx ?: return
        val arr = JSONArray()
        for (k in conns) {
            val o = JSONObject()
            o.put("id", k.id)
            o.put("name", k.name)
            o.put("type", k.type)
            o.put("host", k.host)
            o.put("port", k.port)
            o.put("user", k.user)
            o.put("pass", k.pass)
            o.put("path", k.path)
            o.put("cloud", k.cloud)
            arr.put(o)
        }
        c.getSharedPreferences("conns", Context.MODE_PRIVATE).edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Conn) {
        conns.add(c)
        save()
    }

    fun remove(id: String) {
        conns.removeAll { it.id == id }
        save()
    }

    fun get(id: String): Conn? = conns.firstOrNull { it.id == id }
}

object Net {
    const val PREFIX = "/~net/"
    lateinit var ctx: Context

    fun isNet(path: String): Boolean = path.startsWith(PREFIX)

    fun rootOf(c: Conn): String = PREFIX + c.id

    fun isConnRoot(path: String): Boolean = isNet(path) && !path.removePrefix(PREFIX).trimEnd('/').contains('/')

    fun connOf(path: String): Conn? = ConnStore.get(path.removePrefix(PREFIX).substringBefore('/'))

    fun remotePath(path: String): String {
        val rest = path.removePrefix(PREFIX)
        val i = rest.indexOf('/')
        val rp = if (i < 0) "" else rest.substring(i)
        return if (rp.isEmpty()) "/" else rp
    }
}

/** Файловый бэкенд для FTP, SFTP и WebDAV. Пути вида /~net/<id>/каталог/файл. */
object NetFs : FsBackend {
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun target(path: String): Pair<Conn, String> {
        val c = Net.connOf(path) ?: throw IOException("Подключение не найдено")
        val rp = Net.remotePath(path)
        val base = c.path.trimEnd('/')
        val full = if (c.type == "webdav" || base.isEmpty()) rp else base + (if (rp == "/") "" else rp)
        return Pair(c, if (full.isEmpty()) "/" else full)
    }

    // ---------- Общий интерфейс ----------

    override fun list(path: String): List<FileItem>? {
        val (c, rp) = target(path)
        return when (c.type) {
            "ftp" -> ftpList(c, rp, path)
            "sftp" -> sftpList(c, rp, path)
            else -> davList(c, rp, path)
        }
    }

    override fun stat(path: String): FileItem? {
        if (Net.isConnRoot(path)) return FileItem(path, Net.connOf(path)?.name ?: "", true)
        val parent = PathUtil.parent(path) ?: return null
        return try {
            list(parent)?.firstOrNull { it.path == path }
        } catch (e: Exception) {
            null
        }
    }

    override fun exists(path: String): Boolean = stat(path) != null

    override fun input(path: String): InputStream {
        val (c, rp) = target(path)
        return when (c.type) {
            "ftp" -> ftpInput(c, rp)
            "sftp" -> sftpInput(c, rp)
            else -> davInput(c, rp)
        }
    }

    override fun output(path: String, append: Boolean): OutputStream {
        val (c, rp) = target(path)
        return when (c.type) {
            "ftp" -> ftpOutput(c, rp, append)
            "sftp" -> sftpOutput(c, rp, append)
            else -> davOutput(c, rp)
        }
    }

    override fun mkdirs(path: String): Boolean {
        val (c, rp) = target(path)
        return when (c.type) {
            "ftp" -> ftpMkdirs(c, rp)
            "sftp" -> sftpMkdirs(c, rp)
            else -> davMkdirs(c, rp)
        }
    }

    override fun delete(path: String): Boolean {
        val (c, rp) = target(path)
        return when (c.type) {
            "ftp" -> ftpDeleteTop(c, rp)
            "sftp" -> sftpDeleteTop(c, rp)
            else -> davDelete(c, rp)
        }
    }

    override fun rename(from: String, to: String): Boolean {
        val (c, a) = target(from)
        val (_, b) = target(to)
        return when (c.type) {
            "ftp" -> ftpRename(c, a, b)
            "sftp" -> sftpRename(c, a, b)
            else -> davRename(c, a, b)
        }
    }

    // ---------- FTP ----------

    private fun ftpConnect(c: Conn): FTPClient {
        val f = FTPClient()
        f.controlEncoding = "UTF-8"
        f.connectTimeout = 15000
        f.connect(c.host, c.port)
        val login = f.login(if (c.user.isEmpty()) "anonymous" else c.user, c.pass)
        if (!login) {
            ftpQuit(f)
            throw IOException("FTP: неверный логин или пароль")
        }
        f.enterLocalPassiveMode()
        f.setFileType(FTP.BINARY_FILE_TYPE)
        return f
    }

    private fun ftpQuit(f: FTPClient) {
        try {
            if (f.isConnected) {
                try {
                    f.logout()
                } catch (e: Exception) {
                    // не страшно
                }
                f.disconnect()
            }
        } catch (e: Exception) {
            // не страшно
        }
    }

    private fun ftpList(c: Conn, rp: String, base: String): List<FileItem> {
        val f = ftpConnect(c)
        try {
            val out = ArrayList<FileItem>()
            for (x in f.listFiles(rp)) {
                if (x.name == "." || x.name == "..") continue
                val ts = x.timestamp
                out.add(
                    FileItem(
                        PathUtil.join(base, x.name), x.name, x.isDirectory,
                        if (x.isDirectory) 0L else x.size, if (ts != null) ts.timeInMillis else 0L
                    )
                )
            }
            return out
        } finally {
            ftpQuit(f)
        }
    }

    private fun ftpInput(c: Conn, rp: String): InputStream {
        val f = ftpConnect(c)
        val s = f.retrieveFileStream(rp)
        if (s == null) {
            ftpQuit(f)
            throw IOException("FTP: не удалось открыть файл")
        }
        return object : FilterInputStream(s) {
            override fun close() {
                try {
                    super.close()
                    f.completePendingCommand()
                } catch (e: Exception) {
                    // игнорируем
                } finally {
                    ftpQuit(f)
                }
            }
        }
    }

    private fun ftpOutput(c: Conn, rp: String, append: Boolean): OutputStream {
        val f = ftpConnect(c)
        val s = if (append) f.appendFileStream(rp) else f.storeFileStream(rp)
        if (s == null) {
            ftpQuit(f)
            throw IOException("FTP: не удалось записать файл")
        }
        return object : FilterOutputStream(s) {
            override fun write(b: ByteArray, off: Int, len: Int) {
                out.write(b, off, len)
            }

            override fun close() {
                try {
                    super.close()
                    f.completePendingCommand()
                } catch (e: Exception) {
                    // игнорируем
                } finally {
                    ftpQuit(f)
                }
            }
        }
    }

    private fun ftpMkdirs(c: Conn, rp: String): Boolean {
        val f = ftpConnect(c)
        try {
            var cur = ""
            for (seg in rp.split('/').filter { it.isNotEmpty() }) {
                cur += "/$seg"
                f.makeDirectory(cur)
            }
            return true
        } finally {
            ftpQuit(f)
        }
    }

    private fun ftpDeleteRec(f: FTPClient, p: String): Boolean {
        if (f.changeWorkingDirectory(p)) {
            f.changeWorkingDirectory("/")
            for (x in f.listFiles(p)) {
                if (x.name == "." || x.name == "..") continue
                ftpDeleteRec(f, p.trimEnd('/') + "/" + x.name)
            }
            return f.removeDirectory(p)
        }
        return f.deleteFile(p)
    }

    private fun ftpDeleteTop(c: Conn, rp: String): Boolean {
        val f = ftpConnect(c)
        try {
            return ftpDeleteRec(f, rp)
        } finally {
            ftpQuit(f)
        }
    }

    private fun ftpRename(c: Conn, a: String, b: String): Boolean {
        val f = ftpConnect(c)
        try {
            return f.rename(a, b)
        } finally {
            ftpQuit(f)
        }
    }

    // ---------- SFTP ----------

    private class Sftp(val session: Session, val ch: ChannelSftp) {
        fun close() {
            try {
                ch.disconnect()
            } catch (e: Exception) {
                // игнорируем
            }
            try {
                session.disconnect()
            } catch (e: Exception) {
                // игнорируем
            }
        }
    }

    private fun sftpOpen(c: Conn): Sftp {
        val s = JSch().getSession(c.user, c.host, c.port)
        s.setPassword(c.pass)
        s.setConfig("StrictHostKeyChecking", "no")
        s.connect(15000)
        val ch = s.openChannel("sftp") as ChannelSftp
        ch.connect(15000)
        return Sftp(s, ch)
    }

    private fun sftpList(c: Conn, rp: String, base: String): List<FileItem> {
        val s = sftpOpen(c)
        try {
            val out = ArrayList<FileItem>()
            for (o in s.ch.ls(rp)) {
                val e = o as ChannelSftp.LsEntry
                val n = e.filename
                if (n == "." || n == "..") continue
                val a = e.attrs
                out.add(FileItem(PathUtil.join(base, n), n, a.isDir, if (a.isDir) 0L else a.size, a.mTime * 1000L))
            }
            return out
        } finally {
            s.close()
        }
    }

    private fun sftpInput(c: Conn, rp: String): InputStream {
        val s = sftpOpen(c)
        val ins = try {
            s.ch.get(rp)
        } catch (e: Exception) {
            s.close()
            throw e
        }
        return object : FilterInputStream(ins) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    s.close()
                }
            }
        }
    }

    private fun sftpOutput(c: Conn, rp: String, append: Boolean): OutputStream {
        val s = sftpOpen(c)
        val os = try {
            if (append) s.ch.put(rp, ChannelSftp.APPEND) else s.ch.put(rp)
        } catch (e: Exception) {
            s.close()
            throw e
        }
        return object : FilterOutputStream(os) {
            override fun write(b: ByteArray, off: Int, len: Int) {
                out.write(b, off, len)
            }

            override fun close() {
                try {
                    super.close()
                } finally {
                    s.close()
                }
            }
        }
    }

    private fun sftpMkdirs(c: Conn, rp: String): Boolean {
        val s = sftpOpen(c)
        try {
            var cur = ""
            for (seg in rp.split('/').filter { it.isNotEmpty() }) {
                cur += "/$seg"
                try {
                    s.ch.mkdir(cur)
                } catch (e: Exception) {
                    // уже существует
                }
            }
            return true
        } finally {
            s.close()
        }
    }

    private fun sftpDeleteRec(ch: ChannelSftp, p: String) {
        val st = ch.stat(p)
        if (st.isDir) {
            for (o in ch.ls(p)) {
                val e = o as ChannelSftp.LsEntry
                if (e.filename == "." || e.filename == "..") continue
                sftpDeleteRec(ch, p.trimEnd('/') + "/" + e.filename)
            }
            ch.rmdir(p)
        } else {
            ch.rm(p)
        }
    }

    private fun sftpDeleteTop(c: Conn, rp: String): Boolean {
        val s = sftpOpen(c)
        try {
            sftpDeleteRec(s.ch, rp)
            return true
        } finally {
            s.close()
        }
    }

    private fun sftpRename(c: Conn, a: String, b: String): Boolean {
        val s = sftpOpen(c)
        try {
            s.ch.rename(a, b)
            return true
        } finally {
            s.close()
        }
    }

    // ---------- WebDAV ----------

    private fun davUrl(c: Conn, rp: String): String {
        val base = c.host.trimEnd('/')
        val enc = rp.split('/').joinToString("/") { Uri.encode(it) }
        return base + (if (enc.startsWith("/")) enc else "/$enc")
    }

    private fun davRequest(
        c: Conn, rp: String, method: String,
        body: RequestBody? = null, headers: Map<String, String> = emptyMap()
    ): Response {
        val b = Request.Builder()
            .url(davUrl(c, rp))
            .header("Authorization", Credentials.basic(c.user, c.pass, Charsets.UTF_8))
        for ((k, v) in headers) b.header(k, v)
        b.method(method, body)
        return http.newCall(b.build()).execute()
    }

    private fun parseHttpDate(t: String): Long {
        return try {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(t)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun davList(c: Conn, rp: String, base: String): List<FileItem> {
        val xml = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop>" +
            "<d:getcontentlength/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>"
        val r = davRequest(c, rp, "PROPFIND", xml.toRequestBody("application/xml".toMediaType()), mapOf("Depth" to "1"))
        r.use {
            if (r.code != 207 && !r.isSuccessful) throw IOException("WebDAV: HTTP " + r.code)
            val text = r.body?.string() ?: ""
            return parseDav(text, davUrl(c, rp), base)
        }
    }

    private fun parseDav(xml: String, selfUrl: String, base: String): List<FileItem> {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val out = ArrayList<FileItem>()
        val selfPath = Uri.decode(try {
            URI(selfUrl).path ?: ""
        } catch (e: Exception) {
            ""
        }).trimEnd('/')
        var href = ""
        var len = 0L
        var mod = 0L
        var coll = false
        var inResp = false
        var tag = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                tag = p.name.substringAfter(':').lowercase()
                if (tag == "response") {
                    inResp = true
                    href = ""
                    len = 0L
                    mod = 0L
                    coll = false
                } else if (tag == "collection" && inResp) {
                    coll = true
                }
            } else if (ev == XmlPullParser.TEXT) {
                val t = p.text.trim()
                if (inResp && t.isNotEmpty()) {
                    if (tag == "href") href = t
                    else if (tag == "getcontentlength") len = t.toLongOrNull() ?: 0L
                    else if (tag == "getlastmodified") mod = parseHttpDate(t)
                }
            } else if (ev == XmlPullParser.END_TAG) {
                val n = p.name.substringAfter(':').lowercase()
                if (n == "response") {
                    inResp = false
                    val hp = try {
                        URI(href).path ?: href
                    } catch (e: Exception) {
                        href
                    }
                    val dec = Uri.decode(hp).trimEnd('/')
                    val name = dec.substringAfterLast('/')
                    if (dec != selfPath && name.isNotEmpty()) {
                        out.add(FileItem(PathUtil.join(base, name), name, coll, if (coll) 0L else len, mod))
                    }
                }
                tag = ""
            }
            ev = p.next()
        }
        return out
    }

    private fun davInput(c: Conn, rp: String): InputStream {
        val r = davRequest(c, rp, "GET")
        if (!r.isSuccessful) {
            r.close()
            throw IOException("WebDAV: HTTP " + r.code)
        }
        val body = r.body ?: throw IOException("WebDAV: пустой ответ")
        return body.byteStream()
    }

    private fun davOutput(c: Conn, rp: String): OutputStream {
        val tmp = File.createTempFile("dav", ".tmp", Net.ctx.cacheDir)
        return object : FileOutputStream(tmp) {
            private var done = false

            override fun close() {
                super.close()
                if (done) return
                done = true
                try {
                    val r = davRequest(c, rp, "PUT", tmp.asRequestBody("application/octet-stream".toMediaType()))
                    r.use {
                        if (!r.isSuccessful) throw IOException("WebDAV: загрузка не удалась (HTTP " + r.code + ")")
                    }
                } finally {
                    tmp.delete()
                }
            }
        }
    }

    private fun davMkdirs(c: Conn, rp: String): Boolean {
        var cur = ""
        for (seg in rp.split('/').filter { it.isNotEmpty() }) {
            cur += "/$seg"
            try {
                davRequest(c, cur, "MKCOL").close()
            } catch (e: Exception) {
                // уже существует
            }
        }
        return true
    }

    private fun davDelete(c: Conn, rp: String): Boolean {
        val r = davRequest(c, rp, "DELETE")
        r.use { return r.isSuccessful || r.code == 404 }
    }

    private fun davRename(c: Conn, a: String, b: String): Boolean {
        val r = davRequest(c, a, "MOVE", null, mapOf("Destination" to davUrl(c, b), "Overwrite" to "F"))
        r.use { return r.isSuccessful }
    }
}
