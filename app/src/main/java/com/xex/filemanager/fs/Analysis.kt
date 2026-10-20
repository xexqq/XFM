package com.xex.filemanager.fs

import android.content.Context
import com.xex.filemanager.model.AppEntry
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.model.FileType
import com.xex.filemanager.model.Volume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Индексы: 0 фото, 1 аудио, 2 видео, 3 документы, 4 сжатые, 5 другое. */
data class AnalysisResult(
    val volume: Volume,
    val catSizes: LongArray,
    val apps: List<Pair<AppEntry, Long>>,
    val appsTotal: Long,
    val bigFiles: List<FileItem>,
    val bigTotal: Long,
    val caches: List<Pair<AppEntry, Long>>,
    val cacheTotal: Long,
    val dupGroups: List<List<FileItem>>,
    val dupTotal: Long,
    val exact: Boolean
)

object Analyzer {
    const val BIG = 10L * 1024 * 1024

    private fun catIndex(f: FileItem): Int = when (f.type) {
        FileType.IMAGE -> 0
        FileType.AUDIO -> 1
        FileType.VIDEO -> 2
        FileType.DOCUMENT, FileType.TEXT -> 3
        FileType.ARCHIVE -> 4
        else -> 5
    }

    suspend fun run(
        ctx: Context,
        volume: Volume,
        status: (phase: String, path: String, count: Int) -> Unit
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val sizes = LongArray(6)
        val big = ArrayList<FileItem>()
        val bySize = HashMap<Long, MutableList<FileItem>>()
        var count = 0
        val stack = ArrayDeque<String>()
        stack.add(volume.path)
        while (stack.isNotEmpty() && isActive) {
            val dir = stack.removeLast()
            val list = try {
                Fs.backend(dir).list(dir)
            } catch (e: Exception) {
                null
            } ?: continue
            for (c in list) {
                if (c.isDir) {
                    stack.add(c.path)
                    continue
                }
                count++
                if (count % 40 == 0) status("Сканирование файлов", c.path, count)
                sizes[catIndex(c)] += c.size
                if (c.size >= BIG) big.add(c)
                if (c.size >= 4096L) bySize.getOrPut(c.size) { ArrayList() }.add(c)
            }
        }
        status("Сканирование файлов", "", count)
        big.sortByDescending { it.size }

        val groups = ArrayList<List<FileItem>>()
        var checked = 0
        for ((size, files) in bySize) {
            if (!isActive) break
            if (files.size < 2 || size > 400L * 1024 * 1024) continue
            val byHash = HashMap<String, MutableList<FileItem>>()
            for (f in files) {
                if (!isActive) break
                checked++
                status("Поиск дубликатов", f.path, checked)
                val h = try {
                    FileOps.hash(f.path, "MD5")
                } catch (e: Exception) {
                    continue
                }
                byHash.getOrPut(h) { ArrayList() }.add(f)
            }
            for (g in byHash.values) if (g.size > 1) groups.add(g)
        }
        groups.sortByDescending { it[0].size * (it.size - 1) }

        val apps = AppsRepo.list(ctx)
        val usage = AppsRepo.hasUsageAccess(ctx)
        val sized = ArrayList<Pair<AppEntry, Long>>()
        val caches = ArrayList<Pair<AppEntry, Long>>()
        for ((i, a) in apps.withIndex()) {
            if (!isActive) break
            status("Подсчёт программ", a.label, i)
            val r = AppsRepo.sizeOf(ctx, a, usage)
            if (r.first > 0L) sized.add(Pair(a, r.first))
            if (r.second > 0L) caches.add(Pair(a, r.second))
        }
        sized.sortByDescending { it.second }
        caches.sortByDescending { it.second }

        AnalysisResult(
            volume = volume,
            catSizes = sizes,
            apps = sized,
            appsTotal = sized.sumOf { it.second },
            bigFiles = big,
            bigTotal = big.sumOf { it.size },
            caches = caches,
            cacheTotal = caches.sumOf { it.second },
            dupGroups = groups,
            dupTotal = groups.sumOf { it[0].size * (it.size - 1) },
            exact = usage
        )
    }
}
