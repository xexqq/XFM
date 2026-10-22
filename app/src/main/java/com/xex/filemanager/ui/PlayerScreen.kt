@file:OptIn(UnstableApi::class)

package com.xex.filemanager.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.xex.filemanager.AppViewModel
import com.xex.filemanager.R
import com.xex.filemanager.Screen
import com.xex.filemanager.fs.PathUtil
import java.io.File

@Composable
fun PlayerScreen(vm: AppViewModel, s: Screen.Player) {
    val ctx = LocalContext.current
    val exo = remember { ExoPlayer.Builder(ctx).build() }
    var title by remember { mutableStateOf(PathUtil.name(s.paths[s.index])) }

    DisposableEffect(Unit) {
        onDispose { exo.release() }
    }
    LaunchedEffect(s.paths, s.index) {
        exo.setMediaItems(s.paths.map { MediaItem.fromUri(Uri.fromFile(File(it))) })
        exo.seekTo(s.index, 0L)
        exo.prepare()
        exo.playWhenReady = true
    }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val i = exo.currentMediaItemIndex
                if (i in s.paths.indices) title = PathUtil.name(s.paths[i])
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!s.video) {
            Icon(
                painterResource(R.drawable.ic_music_note),
                null,
                Modifier.size(120.dp).align(Alignment.Center),
                tint = Color(0xFF3E9C8F)
            )
        }
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = exo
                    useController = true
                    controllerShowTimeoutMs = if (s.video) 3000 else 0
                    controllerHideOnTouch = s.video
                    keepScreenOn = s.video
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x66000000))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.pop() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
            }
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}
