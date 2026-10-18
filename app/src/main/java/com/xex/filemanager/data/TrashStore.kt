package com.xex.filemanager.data

import android.content.Context
import com.xex.filemanager.fs.FileOps
import com.xex.filemanager.fs.Progress
import com.xex.filemanager.model.FileItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashEntry(
    val id: String,
    val name: String,
    val origPath: String,
    val deletedAt: Long,
    val isDir: Boolean,
    val size: Long
)

class TrashStore(ctx: Context) {
    val root: File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "trash").also { it.mkdirs() }
    private val index = File(root, "index.json")

    @Synchronized
    fun list(): List<TrashEntry> {
        if (!index.exists()) return emptyList()
        return try {
            val arr = JSONArray(index.readText())
            val out = ArrayList<TrashEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    TrashEntry(
                        o.getString("id"),
                        o.getString("name"),
                        o.getString("orig"),
                        o.getLong("at"),
                        o.getBoolean("dir"),
                        o.getLong("size")
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun save(list: List<TrashEntry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("name", e.name)
            o.put("orig", e.origPath)
            o.put("at", e.deletedAt)
            o.put("dir", e.isDir)
            o.put("size", e.size)
            arr.put(o)
        }
        index.writeText(arr.toString())
    }

    fun moveIn(item: FileItem, p: Progress, buf: ByteArray) {
        val id = System.currentTimeMillis().toString(36) + (0..999).random().toString()
        val dir = File(root, id)
        dir.mkdirs()
        val size = if (item.isDir) FileOps.sizeOf(item) else item.size
        FileOps.move(item, dir.path, p, buf)
        synchronized(this) {
            val l = list().toMutableList()
            l.add(TrashEntry(id, item.name, item.path, System.currentTimeMillis(), item.isDir, size))
            save(l)
        }
    }

    fun storedItem(e: TrashEntry): FileItem {
        val f = File(root, e.id + "/" + e.name)
        return FileItem(f.path, e.name, e.isDir, e.size, e.deletedAt)
    }

    fun remove(id: String) {
        File(root, id).deleteRecursively()
        synchronized(this) {
            save(list().filter { it.id != id })
        }
    }

    fun clear() {
        synchronized(this) {
            for (e in list()) File(root, e.id).deleteRecursively()
            save(emptyList())
        }
    }
}
