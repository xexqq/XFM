package com.xex.filemanager.net

import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import com.xex.filemanager.fs.Fs
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.util.formatDate
import com.xex.filemanager.util.formatSize
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Простой HTTP-сервер: просмотр папок, скачивание, загрузка (PUT), удаление и создание папок. */
class FileHttpServer(private val root: String, private val port: Int, private val password: String) {
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    @Volatile
    private var running = false

    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(port))
        server = ss
        running = true
        val t = Thread {
            while (running) {
                try {
                    val s = ss.accept()
                    pool.execute { handle(s) }
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        }
        t.isDaemon = true
        t.start()
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (e: Exception) {
            // игнорируем
        }
        pool.shutdownNow()
    }

    private fun readLine(ins: InputStream): String? {
        val sb = ByteArrayOutputStream()
        while (true) {
            val b = ins.read()
            if (b < 0) return if (sb.size() == 0) null else sb.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.write(b)
            if (sb.size() > 16384) break
        }
        return sb.toString("UTF-8")
    }

    private fun normalize(path: String): String {
        val out = ArrayList<String>()
        for (seg in path.split('/')) {
            if (seg.isEmpty() || seg == ".") continue
            if (seg == "..") {
                if (out.isNotEmpty()) out.removeAt(out.size - 1)
            } else {
                out.add(seg)
            }
        }
        return out.joinToString("/")
    }

    private fun authorized(h: Map<String, String>): Boolean {
        if (password.isEmpty()) return true
        val a = h["authorization"] ?: return false
        if (!a.startsWith("Basic ")) return false
        return try {
            val dec = String(Base64.decode(a.substring(6).trim(), Base64.DEFAULT), Charsets.UTF_8)
            dec.substringAfter(':') == password
        } catch (e: Exception) {
            false
        }
    }

    private fun respond(
        out: OutputStream, code: Int, text: String,
        headers: Map<String, String>, body: ByteArray?, bodyLen: Long = -1L
    ) {
        val len = if (body != null) body.size.toLong() else if (bodyLen >= 0L) bodyLen else 0L
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(code).append(' ').append(text).append("\r\n")
        for ((k, v) in headers) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("Content-Length: ").append(len).append("\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        if (body != null) out.write(body)
    }

    private fun page(title: String, text: String, code: Int, out: OutputStream) {
        val html = "<html><head><meta charset=utf-8><title>$title</title></head><body><h3>$text</h3></body></html>"
        respond(out, code, title, mapOf("Content-Type" to "text/html; charset=utf-8"), html.toByteArray(Charsets.UTF_8))
    }

    private fun handle(s: Socket) {
        try {
            s.soTimeout = 60000
            val ins = BufferedInputStream(s.getInputStream())
            val out = BufferedOutputStream(s.getOutputStream())
            val reqLine = readLine(ins) ?: return
            val parts = reqLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val headers = HashMap<String, String>()
            while (true) {
                val l = readLine(ins) ?: break
                if (l.isEmpty()) break
                val i = l.indexOf(':')
                if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            }
            if (!authorized(headers)) {
                respond(
                    out, 401, "Unauthorized",
                    mapOf("WWW-Authenticate" to "Basic realm=\"XFM\"", "Content-Type" to "text/plain"),
                    "Нужен пароль".toByteArray(Charsets.UTF_8)
                )
                out.flush()
                return
            }
            val target = parts[1]
            val q = target.indexOf('?')
            val pathPart = if (q >= 0) target.substring(0, q) else target
            val query = if (q >= 0) target.substring(q + 1) else ""
            val rel = normalize(Uri.decode(pathPart))
            val full = if (rel.isEmpty()) root else "$root/$rel"
            when (method) {
                "GET", "HEAD" -> doGet(method == "HEAD", full, rel, pathPart, query, headers, out)
                "PUT" -> doPut(full, headers, ins, out)
                "DELETE" -> {
                    val ok = Fs.backend(full).delete(full)
                    if (ok) respond(out, 204, "No Content", emptyMap(), null) else page("Error", "Не удалось удалить", 500, out)
                }
                "POST" -> {
                    val name = query.split('&').firstOrNull { it.startsWith("mkdir=") }?.substring(6)
                    if (name != null) {
                        val dir = full + "/" + normalize(Uri.decode(name))
                        Fs.backend(dir).mkdirs(dir)
                        respond(out, 201, "Created", emptyMap(), null)
                    } else {
                        page("Bad Request", "Неверный запрос", 400, out)
                    }
                }
                else -> page("Method Not Allowed", "Метод не поддерживается", 405, out)
            }
            out.flush()
        } catch (e: Exception) {
            // соединение оборвано
        } finally {
            try {
                s.close()
            } catch (e: Exception) {
                // игнорируем
            }
        }
    }

    private fun doGet(
        head: Boolean, full: String, rel: String, pathPart: String, query: String,
        headers: Map<String, String>, out: OutputStream
    ) {
        val b = Fs.backend(full)
        val st: FileItem? = if (rel.isEmpty()) FileItem(full, "", true) else b.stat(full)
        if (st == null) {
            page("Not Found", "Не найдено", 404, out)
            return
        }
        if (st.isDir) {
            if (!pathPart.endsWith("/")) {
                respond(out, 301, "Moved", mapOf("Location" to "$pathPart/"), null)
                return
            }
            val body = listing(b.list(full) ?: emptyList(), rel).toByteArray(Charsets.UTF_8)
            respond(out, 200, "OK", mapOf("Content-Type" to "text/html; charset=utf-8"), if (head) null else body, body.size.toLong())
            return
        }
        val ext = st.name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        var start = 0L
        var end = st.size - 1
        var partial = false
        val range = headers["range"]
        if (range != null && range.startsWith("bytes=")) {
            val r = range.substring(6).split('-')
            val a = r.getOrNull(0)?.toLongOrNull()
            val z = r.getOrNull(1)?.toLongOrNull()
            if (a != null) {
                start = a
                if (z != null) end = minOf(z, st.size - 1)
                partial = true
            } else if (z != null) {
                start = maxOf(0L, st.size - z)
                partial = true
            }
        }
        if (start > end && st.size > 0L) {
            respond(out, 416, "Range Not Satisfiable", emptyMap(), null)
            return
        }
        val len = if (st.size == 0L) 0L else end - start + 1
        val h = HashMap<String, String>()
        h["Content-Type"] = mime
        h["Accept-Ranges"] = "bytes"
        val disp = if (query.contains("dl=1")) "attachment" else "inline"
        h["Content-Disposition"] = disp + "; filename*=UTF-8''" + Uri.encode(st.name)
        if (partial) h["Content-Range"] = "bytes $start-$end/${st.size}"
        respond(out, if (partial) 206 else 200, if (partial) "Partial Content" else "OK", h, null, len)
        if (head || len == 0L) return
        b.input(full).use { ins ->
            var skip = start
            while (skip > 0L) {
                val n = ins.skip(skip)
                if (n <= 0L) break
                skip -= n
            }
            val buf = ByteArray(64 * 1024)
            var left = len
            while (left > 0L) {
                val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                left -= n
            }
        }
    }

    private fun doPut(full: String, headers: Map<String, String>, ins: InputStream, out: OutputStream) {
        val len = headers["content-length"]?.toLongOrNull()
        if (len == null) {
            page("Length Required", "Нужен Content-Length", 411, out)
            return
        }
        if ((headers["expect"] ?: "").contains("100")) {
            out.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray())
            out.flush()
        }
        val b = Fs.backend(full)
        val par = PathUtil.parent(full)
        if (par != null) b.mkdirs(par)
        b.output(full, false).use { os ->
            val buf = ByteArray(64 * 1024)
            var left = len
            while (left > 0L) {
                val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                os.write(buf, 0, n)
                left -= n
            }
        }
        respond(out, 201, "Created", emptyMap(), null)
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun listing(items: List<FileItem>, rel: String): String {
        val sorted = items.sortedWith(compareByDescending<FileItem> { it.isDir }.thenBy { it.name.lowercase() })
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=utf-8>")
        sb.append("<meta name=viewport content=\"width=device-width,initial-scale=1\">")
        sb.append("<title>XFM /").append(esc(rel)).append("</title><style>")
        sb.append("body{font-family:sans-serif;margin:0;background:#fafafa}")
        sb.append("header{background:#212121;color:#fff;padding:14px 18px;font-size:18px}")
        sb.append("main{padding:12px 18px}table{width:100%;border-collapse:collapse}")
        sb.append("td{padding:9px 6px;border-bottom:1px solid #ddd}a{color:#008578;text-decoration:none}")
        sb.append(".s{color:#777;font-size:13px;white-space:nowrap}button{margin:4px 6px 4px 0;padding:8px 14px}")
        sb.append("</style></head><body><header>XFM Файлы — /").append(esc(rel)).append("</header><main>")
        sb.append("<input type=file id=f multiple><button onclick=up()>Загрузить</button>")
        sb.append("<button onclick=mk()>Новая папка</button>")
        if (rel.isNotEmpty()) sb.append("<p><a href=\"../\">⬆ Вверх</a></p>")
        sb.append("<table>")
        for (f in sorted) {
            val href = Uri.encode(f.name) + (if (f.isDir) "/" else "")
            sb.append("<tr><td><a href=\"").append(href).append("\">")
            sb.append(if (f.isDir) "📁 " else "📄 ").append(esc(f.name)).append("</a></td>")
            sb.append("<td class=s>").append(if (f.isDir) "" else formatSize(f.size)).append("</td>")
            sb.append("<td class=s>").append(formatDate(f.modified)).append("</td>")
            sb.append("<td><button data-n=\"").append(esc(f.name)).append("\" onclick=\"del(this.dataset.n)\">✕</button></td></tr>")
        }
        sb.append("</table></main><script>")
        sb.append("async function up(){for(const f of document.getElementById('f').files){")
        sb.append("await fetch(location.pathname+encodeURIComponent(f.name),{method:'PUT',body:f});}location.reload();}")
        sb.append("function del(n){if(confirm('Удалить '+n+'?'))fetch(location.pathname+encodeURIComponent(n),{method:'DELETE'}).then(()=>location.reload());}")
        sb.append("function mk(){const n=prompt('Имя папки');if(n)fetch(location.pathname+'?mkdir='+encodeURIComponent(n),{method:'POST'}).then(()=>location.reload());}")
        sb.append("</script></body></html>")
        return sb.toString()
    }
}
