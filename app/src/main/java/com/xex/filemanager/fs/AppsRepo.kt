package com.xex.filemanager.fs

import android.content.Context
import android.content.pm.ApplicationInfo
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
}
