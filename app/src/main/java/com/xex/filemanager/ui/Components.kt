package com.xex.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xex.filemanager.R
import com.xex.filemanager.fs.Fs
import com.xex.filemanager.model.Album
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.model.FileType
import com.xex.filemanager.util.countLabel
import com.xex.filemanager.util.formatDateShort
import com.xex.filemanager.util.formatSize
import java.io.File

@Composable
fun typeColor(item: FileItem): Color =
    if (item.isDir) MaterialTheme.colorScheme.primary else item.type.color

fun folderBadge(name: String): Int? = when (name.lowercase()) {
    "dcim", "camera" -> R.drawable.ic_x_badge_camera
    "download", "downloads" -> R.drawable.ic_x_badge_download
    "documents", "document" -> R.drawable.ic_x_badge_doc
    "pictures", "images" -> R.drawable.ic_x_badge_frame
    "music" -> R.drawable.ic_x_badge_music
    "movies", "videos" -> R.drawable.ic_x_badge_film
    else -> null
}

fun albumBadge(name: String, path: String): Int {
    val n = name.lowercase()
    return when {
        n.contains("camera") || path.contains("/DCIM") -> R.drawable.ic_x_badge_camera
        n.contains("screenshot") -> R.drawable.ic_x_badge_screenshot
        n.startsWith("download") -> R.drawable.ic_x_badge_download
        n.contains("share") -> R.drawable.ic_x_badge_folder
        else -> R.drawable.ic_x_badge_frame
    }
}

@Composable
fun MediaThumb(item: FileItem, size: Dp) {
    val ctx = LocalContext.current
    val req = remember(item.path, item.modified) {
        ImageRequest.Builder(ctx).data(File(item.path)).size(240).build()
    }
    Box(Modifier.size(size).background(Color(0x14000000))) {
        AsyncImage(
            model = req,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.type == FileType.VIDEO) {
            Image(
                painter = painterResource(R.drawable.ic_x_play),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(size * 0.44f)
            )
        }
    }
}

@Composable
private fun TypeGlyph(item: FileItem, size: Dp) {
    val box = Modifier.size(size)
    val pad = Modifier.size(size).padding(size / 7)
    when (item.type) {
        FileType.DOCUMENT -> Image(painterResource(R.drawable.ic_x_docs), null, box)
        FileType.IMAGE -> Image(painterResource(R.drawable.ic_x_frame), null, box)
        FileType.VIDEO -> Image(painterResource(R.drawable.ic_x_film), null, box)
        FileType.AUDIO -> Icon(painterResource(R.drawable.ic_music_note), null, pad, tint = Color(0xFF3E9C8F))
        FileType.APK -> Icon(painterResource(R.drawable.ic_android), null, pad, tint = Color(0xFFA4C639))
        FileType.ARCHIVE -> Icon(painterResource(R.drawable.ic_archive), null, pad, tint = Color(0xFF8D6E63))
        FileType.TEXT -> Icon(painterResource(R.drawable.ic_description), null, pad, tint = Color(0xFF78909C))
        else -> Icon(painterResource(R.drawable.ic_file), null, pad, tint = Color(0xFF90A4AE))
    }
}

@Composable
fun FileGlyph(item: FileItem, size: Dp, thumbs: Boolean) {
    val type = item.type
    if (item.isDir) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(
                    if (item.children == 0) R.drawable.ic_x_folder_empty else R.drawable.ic_x_folder_full
                ),
                contentDescription = null,
                modifier = Modifier.size(size)
            )
            val b = folderBadge(item.name)
            if (b != null) {
                Image(
                    painter = painterResource(b),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 1.dp)
                        .size(size * 0.55f)
                )
            }
        }
    } else if (thumbs && (type == FileType.IMAGE || type == FileType.VIDEO) && !Fs.isRemote(item.path)) {
        MediaThumb(item, size - 2.dp)
    } else {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { TypeGlyph(item, size - 6.dp) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    compact: Boolean,
    thumbs: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    val x = LocalXfm.current
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    val iconSize = if (compact) 36.dp else 48.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 40.dp else 56.dp)
                .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                FileGlyph(item, iconSize, thumbs)
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(iconSize * 0.6f)
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = if (compact) 16.sp else 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact) {
                    Row(Modifier.fillMaxWidth()) {
                        val left = if (item.isDir) {
                            if (item.children >= 0) countLabel(item.children) else ""
                        } else {
                            formatSize(item.size)
                        }
                        Text(
                            text = left,
                            fontSize = 14.sp,
                            color = x.secondary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatDateShort(item.modified),
                            fontSize = 14.sp,
                            color = x.secondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = x.separator)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCell(
    item: FileItem,
    selected: Boolean,
    thumbs: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    Column(
        modifier = Modifier
            .padding(2.dp)
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(4.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            FileGlyph(item, 64.dp, thumbs)
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
        Text(
            text = item.name,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AlbumCell(album: Album, thumbs: Boolean, onClick: () -> Unit) {
    val x = LocalXfm.current
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .padding(2.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White)
                .border(1.dp, x.tileBorder)
        ) {
            Image(
                painter = painterResource(if (album.isVideo) R.drawable.ic_x_film else R.drawable.ic_x_frame),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).fillMaxSize(0.42f)
            )
            if (thumbs) {
                val req = remember(album.cover) {
                    ImageRequest.Builder(ctx).data(File(album.cover)).size(280).build()
                }
                AsyncImage(
                    model = req,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (album.isVideo) {
                    Image(
                        painter = painterResource(R.drawable.ic_x_play),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(34.dp)
                    )
                }
            }
            Image(
                painter = painterResource(albumBadge(album.name, album.path)),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
            )
        }
        Text(
            text = album.name + " (" + album.count + ")",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )
    }
}

@Composable
fun SectionHeader(label: String) {
    val x = LocalXfm.current
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 8.dp, top = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = x.section,
                maxLines = 1
            )
        }
        HorizontalDivider(thickness = 1.dp, color = x.separator)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}

/** Строка режима «анализ папок»: полоса размера, процент от объёма и флажок. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SizeRow(
    item: FileItem,
    size: Long,
    total: Long,
    maxSize: Long,
    selected: Boolean,
    thumbs: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    val x = LocalXfm.current
    val frac = if (maxSize > 0L && size > 0L) (size.toFloat() / maxSize.toFloat()).coerceIn(0.01f, 1f) else 0f
    val pct = if (total > 0L) size.toDouble() * 100.0 / total.toDouble() else 0.0
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileGlyph(item, 48.dp, thumbs)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (frac > 0f) {
                    Row(Modifier.matchParentSize()) {
                        Row(Modifier.fillMaxWidth(frac).fillMaxHeight()) {
                            Box(Modifier.weight(0.1f).fillMaxHeight().background(Color(0xFF5AA9DE)))
                            Box(Modifier.weight(0.9f).fillMaxHeight().background(Color(0xFFA7C957)))
                        }
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = item.name,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val left = if (item.isDir) {
                        (if (item.children >= 0) countLabel(item.children) + " " else "") + "(" + formatSize(size) + ")"
                    } else {
                        formatSize(size)
                    }
                    Text(text = left, fontSize = 14.sp, color = x.secondary, maxLines = 1)
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = String.format(java.util.Locale.getDefault(), "%.2f%%", pct),
                fontSize = 14.sp,
                color = x.secondary,
                maxLines = 1
            )
            androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onLong() })
        }
        HorizontalDivider(thickness = 1.dp, color = x.separator)
    }
}
