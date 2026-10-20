package com.xex.filemanager.fs

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.xex.filemanager.util.Utils
import com.xex.filemanager.model.AppEntry
import java.io.File

object AppsRepo {
    fun list(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val out = ArrayList<AppEntry>()
        val infos = try {
            pm.getInstalledPackages(0)
        } catch (e: Exception) {
            emptyList()
        }
        for (p in infos) {
            val ai = p.applicationInfo ?: continue
            val src = ai.sourceDir ?: ""
            val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = try {
                ai.loadLabel(pm).toString()
            } catch (e: Exception) {
                p.packageName
            }
            out.add(AppEntry(label, p.packageName, p.versionName ?: "", src, File(src).length(), system))
        }
        return out.sortedBy { it.label.lowercase() }
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        return try {
            val ao = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            } else {
                @Suppress("DEPRECATION")
                ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /** Возвращает (общий размер, размер кэша). */
    fun sizeOf(ctx: Context, a: AppEntry, usage: Boolean): Pair<Long, Long> {
        if (usage) {
            try {
                val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val st = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, a.pkg, Process.myUserHandle())
                return Pair(st.appBytes + st.dataBytes, st.cacheBytes)
            } catch (e: Exception) {
                // переходим к подсчёту по папкам
            }
        }
        val root = Utils.primaryRoot()
        var total = a.apkSize
        var cache = 0L
        try {
            total += FileOps.folderStat(root + "/Android/obb/" + a.pkg).second
            val d = root + "/Android/data/" + a.pkg
            total += FileOps.folderStat(d).second
            cache = FileOps.folderStat("$d/cache").second
        } catch (e: Exception) {
            // нет доступа
        }
        return Pair(total, cache)
    }
}
