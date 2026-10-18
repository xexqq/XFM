package com.xex.filemanager

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xex.filemanager.data.AppSettings
import com.xex.filemanager.data.TrashEntry
import com.xex.filemanager.data.TrashStore
import com.xex.filemanager.fs.FileOps
import com.xex.filemanager.fs.Fs
import com.xex.filemanager.fs.PathUtil
import com.xex.filemanager.fs.Progress
import com.xex.filemanager.model.Cat
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.model.FileType
import com.xex.filemanager.model.FileTypes
import com.xex.filemanager.model.Volume
import com.xex.filemanager.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data class Browser(val path: String) : Screen
    data class Category(val cat: Cat) : Screen
    data object Trash : Screen
    data object Analyzer : Screen
    data object Prefs : Screen
    data object Terminal : Screen
    data class Images(val paths: List<String>, val index: Int) : Screen
    data class Editor(val path: String) : Screen
}

sealed interface AppDialog {
    class Input(
        val title: String,
        val hint: String,
        val initial: String,
        val confirm: String,
        val onOk: (String) -> Unit
    ) : AppDialog

    class Confirm(val title: String, val text: String, val confirm: String, val onOk: () -> Unit) : AppDialog
    class Properties(val item: FileItem) : AppDialog
    class MultiRename(val items: List<FileItem>) : AppDialog
    class ArchiveChoice(val item: FileItem) : AppDialog
    class OpenAs(val item: FileItem) : AppDialog
}

class Clipboard(val items: List<FileItem>, val cut: Boolean)

fun buildName(item: FileItem, index: Int, template: String, start: Int): String {
    val dot = if (item.isDir) -1 else item.name.lastIndexOf('.')
    val base = if (dot > 0) item.name.substring(0, dot) else item.name
    val ext = if (dot > 0) item.name.substring(dot) else ""
    val num = (start + index).toString().padStart(2, '0')
    val r = template.replace("{name}", base).replace("{n}", num)
    return if (r.contains("{ext}")) r.replace("{ext}", ext) else r + ext
}

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    val settings = AppSettings(app)
    val trashStore = TrashStore(app)

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val top: Screen
        get() = stack.last()

    var items by mutableStateOf<List<FileItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selection by mutableStateOf<Set<String>>(emptySet())
    var query by mutableStateOf("")
    var searchOpen by mutableStateOf(false)
    var deepSearch by mutableStateOf(false)
    var searchResults by mutableStateOf<List<FileItem>?>(null)
    var searching by mutableStateOf(false)
    var clipboard by mutableStateOf<Clipboard?>(null)
    var task by mutableStateOf<Progress?>(null)
    var dialog by mutableStateOf<AppDialog?>(null)
    var volumes by mutableStateOf<List<Volume>>(emptyList())
    var trashEntries by mutableStateOf<List<TrashEntry>>(emptyList())

    val listState = LazyListState()
    val gridState = LazyGridState()
    var scrollPath: String? = null

    private var loadJob: Job? = null
    private var itemsKey: Any? = null
    private var searchJob: Job? = null

    init {
        refreshVolumes()
    }

    // ---------- Сообщения ----------

    fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Навигация ----------

    val canHandleBack: Boolean
        get() = selection.isNotEmpty() || searchOpen || stack.size > 1

    fun push(s: Screen) {
        stack.add(s)
        onScreenChanged()
    }

    fun replaceTop(s: Screen) {
        stack[stack.lastIndex] = s
        onScreenChanged()
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        onScreenChanged()
        return true
    }

    fun goHome() {
        stack.clear()
        stack.add(Screen.Home)
        onScreenChanged()
    }

    fun openFromRoot(s: Screen) {
        stack.clear()
        stack.add(Screen.Home)
        stack.add(s)
        onScreenChanged()
    }

    fun openFolder(path: String) {
        if (top is Screen.Browser) replaceTop(Screen.Browser(path)) else push(Screen.Browser(path))
    }

    fun goUp() {
        val t = top
        if (t !is Screen.Browser) {
            pop()
            return
        }
        val isRoot = volumes.any { it.path == t.path }
        val par = if (isRoot) null else PathUtil.parent(t.path)
        if (par == null) pop() else replaceTop(Screen.Browser(par))
    }

    fun onBack(): Boolean {
        if (selection.isNotEmpty()) {
            selection = emptySet()
            return true
        }
        if (searchOpen) {
            closeSearch()
            return true
        }
        if (top is Screen.Browser) {
            goUp()
            return true
        }
        return pop()
    }

    private fun onScreenChanged() {
        selection = emptySet()
        query = ""
        searchOpen = false
        searchResults = null
        searching = false
        searchJob?.cancel()
        if (top is Screen.Home) refreshVolumes()
        reload()
    }

    // ---------- Загрузка ----------

    fun refreshVolumes() {
        viewModelScope.launch {
            volumes = withContext(Dispatchers.IO) { FileOps.volumes(app) }
        }
    }

    fun refreshTrash() {
        viewModelScope.launch {
            trashEntries = withContext(Dispatchers.IO) {
                trashStore.list().sortedByDescending { it.deletedAt }
            }
        }
    }

    private fun loadDir(path: String): Pair<List<FileItem>, String?> {
        return try {
            val l = Fs.backend(path).list(path)
            if (l == null || (l.isEmpty() && !Fs.isRemote(path) && Fs.isAppDataDir(path))) {
                Pair(emptyList(), "Нет доступа к этой папке")
            } else {
                Pair(l, null)
            }
        } catch (e: Exception) {
            Pair(emptyList(), e.message ?: "Ошибка чтения папки")
        }
    }

    fun reload() {
        loadJob?.cancel()
        when (val s = top) {
            is Screen.Browser -> {
                if (itemsKey != s) {
                    items = emptyList()
                    itemsKey = s
                }
                loading = true
                error = null
                loadJob = viewModelScope.launch {
                    val res = withContext(Dispatchers.IO) { loadDir(s.path) }
                    items = res.first
                    error = res.second
                    loading = false
                }
            }
            is Screen.Category -> {
                if (itemsKey != s) {
                    items = emptyList()
                    itemsKey = s
                }
                loading = true
                error = null
                loadJob = viewModelScope.launch {
                    val res = withContext(Dispatchers.IO) { FileOps.queryCategory(app, s.cat) }
                    items = res
                    loading = false
                }
            }
            is Screen.Trash -> refreshTrash()
            is Screen.Home -> refreshVolumes()
            else -> {}
        }
    }

    // ---------- Выделение ----------

    fun toggleSelect(path: String) {
        selection = if (path in selection) selection - path else selection + path
    }

    fun selectAll(list: List<FileItem>) {
        selection = list.map { it.path }.toSet()
    }

    fun invertSelection(list: List<FileItem>) {
        selection = list.map { it.path }.filter { it !in selection }.toSet()
    }

    fun clearSelection() {
        selection = emptySet()
    }

    // ---------- Поиск ----------

    fun openSearch() {
        searchOpen = true
    }

    fun closeSearch() {
        searchOpen = false
        query = ""
        searchResults = null
        searching = false
        searchJob?.cancel()
    }

    fun runSearch() {
        searchJob?.cancel()
        val s = top
        val q = query.trim()
        if (!deepSearch || q.isEmpty() || s !is Screen.Browser) {
            searchResults = null
            searching = false
            return
        }
        searching = true
        searchJob = viewModelScope.launch {
            val r = FileOps.search(s.path, q)
            searchResults = r
            searching = false
        }
    }

    // ---------- Задачи ----------

    fun runTask(title: String, after: () -> Unit = {}, block: (Progress) -> Unit) {
        val p = Progress(title)
        selection = emptySet()
        task = p
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block(p)
            } catch (e: Exception) {
                p.error = e.message ?: e.toString()
            }
            withContext(Dispatchers.Main) {
                task = null
                val err = p.error
                if (err != null) {
                    val hint = if (err.contains("denied", true) || err.contains("EACCES") || err.contains("Read-only", true)) {
                        "\nДля защищённых папок включите Shizuku в настройках"
                    } else ""
                    toast("Ошибка: $err$hint")
                } else if (p.cancelled) {
                    toast("Отменено")
                } else {
                    toast("Готово")
                }
                reload()
                after()
            }
        }
    }

    fun cancelTask() {
        task?.cancelled = true
    }

    // ---------- Операции ----------

    fun setClipboard(list: List<FileItem>, cut: Boolean) {
        if (list.isEmpty()) return
        clipboard = Clipboard(list, cut)
        selection = emptySet()
        toast(if (cut) "Вырезано: ${list.size}" else "Скопировано: ${list.size}")
    }

    fun cancelClipboard() {
        clipboard = null
    }

    fun paste() {
        val cb = clipboard ?: return
        val dst = (top as? Screen.Browser)?.path ?: return
        clipboard = null
        runTask(if (cb.cut) "Перемещение" else "Копирование") { p ->
            p.itemsTotal = cb.items.size
            if (!cb.cut) p.total = cb.items.sumOf { FileOps.sizeOf(it) }
            val buf = ByteArray(256 * 1024)
            var failed = 0
            var lastErr = ""
            for (item in cb.items) {
                if (p.cancelled) break
                p.current = item.name
                try {
                    if (cb.cut) {
                        if (PathUtil.parent(item.path) != dst) FileOps.move(item, dst, p, buf)
                    } else {
                        FileOps.copy(item, dst, p, buf)
                    }
                } catch (e: Exception) {
                    failed++
                    lastErr = e.message ?: ""
                }
                p.itemsDone++
            }
            if (failed > 0) p.error = "не удалось обработать $failed ($lastErr)"
        }
    }

    fun delete(list: List<FileItem>, after: () -> Unit = {}) {
        if (list.isEmpty()) return
        val useTrash = settings.useTrash
        val text = if (useTrash) "Переместить в корзину: ${list.size}?" else "Удалить навсегда: ${list.size}? Это нельзя отменить."
        val action = {
            if (useTrash) moveToTrash(list, after) else deleteForever(list, after)
        }
        if (settings.confirmDelete) {
            dialog = AppDialog.Confirm("Удаление", text, "Удалить") { action() }
        } else {
            action()
        }
    }

    fun deleteForever(list: List<FileItem>, after: () -> Unit = {}) {
        runTask("Удаление", after) { p ->
            p.itemsTotal = list.size
            var failed = 0
            for (item in list) {
                if (p.cancelled) break
                p.current = item.name
                try {
                    if (!FileOps.deletePermanent(item)) failed++
                } catch (e: Exception) {
                    failed++
                }
                p.itemsDone++
            }
            if (failed > 0) p.error = "не удалось удалить: $failed"
        }
    }

    fun moveToTrash(list: List<FileItem>, after: () -> Unit = {}) {
        runTask("Перемещение в корзину", after) { p ->
            p.itemsTotal = list.size
            val buf = ByteArray(256 * 1024)
            var failed = 0
            for (item in list) {
                if (p.cancelled) break
                p.current = item.name
                try {
                    trashStore.moveIn(item, p, buf)
                } catch (e: Exception) {
                    failed++
                }
                p.itemsDone++
            }
            if (failed > 0) p.error = "не удалось удалить: $failed"
        }
    }

    fun restoreFromTrash(list: List<TrashEntry>) {
        runTask("Восстановление") { p ->
            p.itemsTotal = list.size
            val buf = ByteArray(256 * 1024)
            var failed = 0
            for (e in list) {
                if (p.cancelled) break
                p.current = e.name
                try {
                    val target = PathUtil.parent(e.origPath) ?: Utils.primaryRoot()
                    Fs.backend(target).mkdirs(target)
                    FileOps.move(trashStore.storedItem(e), target, p, buf)
                    trashStore.remove(e.id)
                } catch (ex: Exception) {
                    failed++
                }
                p.itemsDone++
            }
            if (failed > 0) p.error = "не удалось восстановить: $failed"
        }
    }

    fun purgeFromTrash(list: List<TrashEntry>) {
        runTask("Удаление из корзины") { p ->
            p.itemsTotal = list.size
            for (e in list) {
                trashStore.remove(e.id)
                p.itemsDone++
            }
        }
    }

    fun emptyTrash() {
        runTask("Очистка корзины") { _ -> trashStore.clear() }
    }

    fun createFolder(name: String) {
        val dir = (top as? Screen.Browser)?.path ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    FileOps.createFolder(dir, name)
                } catch (e: Exception) {
                    false
                }
            }
            toast(if (ok) "Папка создана" else "Не удалось создать папку")
            reload()
        }
    }

    fun createFile(name: String) {
        val dir = (top as? Screen.Browser)?.path ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    FileOps.createFile(dir, name)
                } catch (e: Exception) {
                    false
                }
            }
            toast(if (ok) "Файл создан" else "Не удалось создать файл (возможно, он уже существует)")
            reload()
        }
    }

    fun rename(item: FileItem, newName: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    FileOps.rename(item, newName)
                } catch (e: Exception) {
                    false
                }
            }
            toast(if (ok) "Переименовано" else "Не удалось переименовать (имя занято или нет доступа)")
            reload()
        }
    }

    fun multiRename(list: List<FileItem>, template: String, start: Int) {
        viewModelScope.launch {
            val failed = withContext(Dispatchers.IO) {
                var f = 0
                list.forEachIndexed { i, item ->
                    try {
                        if (!FileOps.rename(item, buildName(item, i, template, start))) f++
                    } catch (e: Exception) {
                        f++
                    }
                }
                f
            }
            toast(if (failed == 0) "Переименовано: ${list.size}" else "Не удалось: $failed")
            reload()
        }
    }

    fun toggleHidden(list: List<FileItem>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (item in list) {
                    try {
                        val n = if (item.name.startsWith(".")) item.name.trimStart('.') else "." + item.name
                        if (n.isNotEmpty()) FileOps.rename(item, n)
                    } catch (e: Exception) {
                        // пропускаем
                    }
                }
            }
            reload()
        }
    }

    fun compress(list: List<FileItem>, name: String) {
        if (list.isEmpty()) return
        val dir = (top as? Screen.Browser)?.path ?: PathUtil.parent(list[0].path) ?: return
        val fn = if (name.endsWith(".zip", true)) name else "$name.zip"
        runTask("Архивация") { p ->
            p.total = list.sumOf { FileOps.sizeOf(it) }
            val b = Fs.backend(dir)
            val dest = PathUtil.join(dir, PathUtil.uniqueName(b, dir, fn, false))
            FileOps.zip(list, dest, p, ByteArray(256 * 1024))
        }
    }

    fun extract(item: FileItem, intoFolder: Boolean) {
        val parent = PathUtil.parent(item.path) ?: return
        val base = item.name.substringBeforeLast('.', item.name)
        runTask("Распаковка") { p ->
            val b = Fs.backend(parent)
            val dest = if (intoFolder) PathUtil.join(parent, PathUtil.uniqueName(b, parent, base, true)) else parent
            p.current = item.name
            FileOps.unzip(item.path, dest, p, ByteArray(256 * 1024))
        }
    }

    fun toggleBookmark(list: List<FileItem>) {
        for (f in list) settings.toggleBookmark(f.path)
        selection = emptySet()
    }

    // ---------- Открытие ----------

    fun open(item: FileItem, siblings: List<FileItem>) {
        if (item.isDir) {
            openFolder(item.path)
            return
        }
        settings.addRecent(item.path)
        when (item.type) {
            FileType.IMAGE -> {
                val imgs = siblings.filter { it.type == FileType.IMAGE && !Fs.isRemote(it.path) }.map { it.path }
                if (item.path in imgs) push(Screen.Images(imgs, imgs.indexOf(item.path))) else openExternal(item)
            }
            FileType.TEXT -> if (item.size <= 1_000_000L) push(Screen.Editor(item.path)) else openExternal(item)
            FileType.ARCHIVE -> if (item.ext in FileTypes.zipLike) {
                dialog = AppDialog.ArchiveChoice(item)
            } else {
                openExternal(item)
            }
            FileType.OTHER -> if (FileTypes.mime(item) == "*/*") dialog = AppDialog.OpenAs(item) else openExternal(item)
            else -> openExternal(item)
        }
    }

    fun openExternal(item: FileItem, mime: String? = null) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    Utils.localCopy(app, item)
                } catch (e: Exception) {
                    null
                }
            }
            if (file == null) {
                toast("Не удалось подготовить файл")
            } else {
                Utils.view(app, file, mime ?: FileTypes.mime(item))
            }
        }
    }

    fun share(list: List<FileItem>) {
        val files = list.filter { !it.isDir }
        if (files.isEmpty()) {
            toast("Отправлять можно только файлы, не папки")
            return
        }
        viewModelScope.launch {
            val locals = withContext(Dispatchers.IO) {
                files.mapNotNull {
                    try {
                        Utils.localCopy(app, it)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            Utils.share(app, locals)
        }
    }
}
