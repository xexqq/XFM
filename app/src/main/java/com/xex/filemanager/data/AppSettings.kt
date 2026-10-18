package com.xex.filemanager.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

data class Palette(val id: String, val name: String, val seed: Color)

val Palettes = listOf(
    Palette("teal", "Бирюза", Color(0xFF00A99A)),
    Palette("blue", "Океан", Color(0xFF2F8BFF)),
    Palette("violet", "Аметист", Color(0xFF8E5CF5)),
    Palette("pink", "Роза", Color(0xFFE8508F)),
    Palette("orange", "Закат", Color(0xFFF2851F)),
    Palette("green", "Лес", Color(0xFF4CAF50)),
    Palette("red", "Рубин", Color(0xFFE5483D)),
    Palette("gray", "Графит", Color(0xFF9AA5B1))
)

class AppSettings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun intPref(key: String, def: Int) = object : ReadWriteProperty<Any?, Int> {
        private val st = mutableStateOf(sp.getInt(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = st.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            st.value = value
            sp.edit().putInt(key, value).apply()
        }
    }

    private fun boolPref(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        private val st = mutableStateOf(sp.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = st.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            st.value = value
            sp.edit().putBoolean(key, value).apply()
        }
    }

    private fun strPref(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        private val st = mutableStateOf(sp.getString(key, def) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = st.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            st.value = value
            sp.edit().putString(key, value).apply()
        }
    }

    // Тема
    var themeId by strPref("theme", "teal")
    var darkMode by intPref("dark", 2) // 0 система, 1 светлая, 2 тёмная
    var amoled by boolPref("amoled", false)
    var dynamic by boolPref("dynamic", false)

    // Вид
    var viewMode by intPref("view", 0) // 0 список, 1 сетка, 2 компактно
    var sortBy by intPref("sortBy", 0) // 0 имя, 1 размер, 2 дата, 3 тип
    var sortAsc by boolPref("sortAsc", true)
    var foldersFirst by boolPref("foldersFirst", true)
    var showHidden by boolPref("hidden", false)
    var thumbs by boolPref("thumbs", true)

    // Файлы
    var useTrash by boolPref("trash", true)
    var confirmDelete by boolPref("confirm", true)

    // Shizuku: 0 выкл, 1 авто, 2 всегда
    var shizukuMode by intPref("shizukuMode", 1)

    // Редактор
    var editorWrap by boolPref("editorWrap", true)
    var editorSize by intPref("editorSize", 14)

    private var bookmarksRaw by strPref("bookmarks", "")
    private var recentsRaw by strPref("recents", "")

    val bookmarks: List<String>
        get() = bookmarksRaw.split('\n').filter { it.isNotEmpty() }

    val recents: List<String>
        get() = recentsRaw.split('\n').filter { it.isNotEmpty() }

    fun isBookmarked(path: String): Boolean = path in bookmarks

    fun toggleBookmark(path: String) {
        val l = bookmarks.toMutableList()
        if (!l.remove(path)) l.add(path)
        bookmarksRaw = l.joinToString("\n")
    }

    fun addRecent(path: String) {
        val l = recents.toMutableList()
        l.remove(path)
        l.add(0, path)
        recentsRaw = l.take(30).joinToString("\n")
    }

    fun clearRecents() {
        recentsRaw = ""
    }
}
