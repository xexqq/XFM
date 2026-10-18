package com.xex.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xex.filemanager.fs.Fs
import com.xex.filemanager.model.FileItem
import com.xex.filemanager.model.FileType
import com.xex.filemanager.util.countLabel
import com.xex.filemanager.util.formatDate
import com.xex.filemanager.util.formatSize
import java.io.File

@Composable
fun typeColor(item: FileItem): Color =
    if (item.isDir) MaterialTheme.colorScheme.primary else item.type.color

@Composable
fun FileIcon(item: FileItem, size: Dp) {
    val c = typeColor(item)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(c.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(item.type.icon),
            contentDescription = null,
            tint = c,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
fun FileThumb(item: FileItem, size: Dp, thumbs: Boolean) {
    val showImage = thumbs && !item.isDir &&
        (item.type == FileType.IMAGE || item.type == FileType.VIDEO) &&
        !Fs.isRemote(item.path)
    if (showImage) {
        val ctx = LocalContext.current
        val req = remember(item.path, item.modified) {
            ImageRequest.Builder(ctx).data(File(item.path)).size(200).build()
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(item.type.color.copy(alpha = 0.16f))
        ) {
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        }
    } else {
        FileIcon(item, size)
    }
}

fun subtitle(item: FileItem): String {
    val date = formatDate(item.modified)
    return if (item.isDir) {
        val c = if (item.children >= 0) countLabel(item.children) else ""
        listOf(date, c).filter { it.isNotEmpty() }.joinToString(" • ")
    } else {
        listOf(date, formatSize(item.size)).filter { it.isNotEmpty() }.joinToString(" • ")
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
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent
    val iconSize = if (compact) 34.dp else 46.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 14.dp, vertical = if (compact) 4.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            FileThumb(item, iconSize, thumbs)
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(RoundedCornerShape(iconSize / 4))
                        .background(MaterialTheme.colorScheme.primary),
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
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(
                    text = subtitle(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(8.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            FileThumb(item, 68.dp, thumbs)
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
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

@Composable
fun Tile(
    title: String,
    iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (locked) 0.08f else 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (locked) color.copy(alpha = 0.5f) else color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}
