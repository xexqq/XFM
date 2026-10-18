package com.xex.filemanager.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatSize(b: Long): String {
    if (b < 0) return "—"
    if (b < 1024) return "$b Б"
    val units = arrayOf("КБ", "МБ", "ГБ", "ТБ")
    var v = b.toDouble()
    var i = -1
    do {
        v /= 1024.0
        i++
    } while (v >= 1024.0 && i < units.size - 1)
    val fmt = if (v >= 100) "%.0f %s" else "%.1f %s"
    return String.format(Locale.getDefault(), fmt, v, units[i])
}

private val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

fun formatDate(ms: Long): String {
    if (ms <= 0L) return ""
    return synchronized(dateFmt) { dateFmt.format(Date(ms)) }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return if (m10 == 1 && m100 != 11) one
    else if (m10 in 2..4 && m100 !in 12..14) few
    else many
}

fun countLabel(n: Int): String = "$n ${plural(n, "объект", "объекта", "объектов")}"
