package com.xex.filemanager.model

import android.webkit.MimeTypeMap
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.xex.filemanager.R

enum class FileType(val label: String, @DrawableRes val icon: Int, val color: Color) {
    FOLDER("Папка", R.drawable.ic_folder, Color(0xFF26A69A)),
    IMAGE("Изображение", R.drawable.ic_image, Color(0xFF943FE5)),
    VIDEO("Видео", R.drawable.ic_movie, Color(0xFFD92525)),
    AUDIO("Аудио", R.drawable.ic_audio, Color(0xFF00B2AC)),
    DOCUMENT("Документ", R.drawable.ic_description, Color(0xFF2987D9)),
    TEXT("Текст", R.drawable.ic_description, Color(0xFF78909C)),
    ARCHIVE("Архив", R.drawable.ic_archive, Color(0xFF8AA19C)),
    APK("Приложение", R.drawable.ic_android, Color(0xFF3DDC84)),
    OTHER("Файл", R.drawable.ic_file, Color(0xFF90A4AE))
}

enum class Cat(val title: String, @DrawableRes val icon: Int, val color: Color) {
    IMAGES("Фото", R.drawable.ic_image, Color(0xFF943FE5)),
    VIDEO("Видео", R.drawable.ic_movie, Color(0xFFD92525)),
    AUDIO("Аудио", R.drawable.ic_audio, Color(0xFF00B2AC)),
    DOCS("Документы", R.drawable.ic_description, Color(0xFF2987D9)),
    ARCHIVES("Архивы", R.drawable.ic_archive, Color(0xFF8AA19C)),
    APK("APK-файлы", R.drawable.ic_android, Color(0xFF3DDC84)),
    RECENT("Новые файлы", R.drawable.ic_history, Color(0xFFF2851F))
}

data class FileItem(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long = 0L,
    val modified: Long = 0L,
    val children: Int = -1,
    val readable: Boolean = true
) {
    val ext: String
        get() = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
    val type: FileType
        get() = FileTypes.of(this)
}

data class Album(
    val path: String,
    val name: String,
    val count: Int,
    val cover: String,
    val isVideo: Boolean
)

data class AppEntry(
    val label: String,
    val pkg: String,
    val version: String,
    val apkPath: String,
    val apkSize: Long,
    val system: Boolean,
    val manageSpace: String? = null
)

data class Volume(
    val path: String,
    val label: String,
    val total: Long,
    val free: Long,
    val removable: Boolean
)

object FileTypes {
    val image = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    val video = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "mpg", "mpeg")
    val audio = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "opus", "wma", "amr", "mid", "midi")
    val document = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub", "fb2", "djvu")
    val text = setOf(
        "txt", "md", "log", "json", "xml", "csv", "tsv", "ini", "cfg", "conf", "properties", "prop",
        "yml", "yaml", "html", "htm", "css", "js", "kt", "kts", "java", "py", "sh", "c", "cpp", "h",
        "gradle", "toml", "sql", "bat", "lua", "php", "rb", "go", "rs", "smali", "srt", "list", "env"
    )
    val archive = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar", "cbz", "tgz", "tbz2", "txz",
        "iso", "cab", "arj", "lzh", "wim", "dmg", "cpio", "deb", "rpm"
    )
    val apk = setOf("apk", "apks", "xapk", "apkm")
    val zipLike = setOf("zip", "jar", "cbz")

    fun of(item: FileItem): FileType {
        if (item.isDir) return FileType.FOLDER
        val e = item.ext
        return when {
            e in image -> FileType.IMAGE
            e in video -> FileType.VIDEO
            e in audio -> FileType.AUDIO
            e in document -> FileType.DOCUMENT
            e in text -> FileType.TEXT
            e in archive -> FileType.ARCHIVE
            e in apk -> FileType.APK
            else -> FileType.OTHER
        }
    }

    fun mime(item: FileItem): String {
        if (item.ext in apk) return "application/vnd.android.package-archive"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.ext) ?: "*/*"
    }
}
