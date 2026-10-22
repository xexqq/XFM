package com.xex.filemanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xex.filemanager.R
import com.xex.filemanager.model.Cat

/** Верхняя панель как в оригинале: чёрная строка статуса + тёмный тулбар 56dp. */
@Composable
fun XfmTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val x = LocalXfm.current
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(x.statusBar)
                .windowInsetsTopHeight(WindowInsets.statusBars)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(x.toolbar),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides x.onToolbar) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { navigationIcon() }
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    ProvideTextStyle(
                        TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = x.onToolbar)
                    ) { title() }
                }
                actions()
            }
        }
    }
}

class Crumb(val label: String?, val icon: Int?, val tint: Color?, val onClick: () -> Unit)

@Composable
fun PathBar(
    crumbs: List<Crumb>,
    onHome: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    homeRes: Int = R.drawable.ic_x_home
) {
    val x = LocalXfm.current
    val scroll = rememberScrollState()
    LaunchedEffect(crumbs.size, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxWidth().background(x.pathBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scroll)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(homeRes),
                    contentDescription = "Главная",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onHome)
                )
                crumbs.forEachIndexed { i, c ->
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = x.pathText,
                        modifier = Modifier.size(24.dp)
                    )
                    val last = i == crumbs.lastIndex
                    if (c.icon != null) {
                        val m = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = c.onClick)
                        if (c.tint != null) {
                            Icon(painterResource(c.icon), null, m.padding(4.dp), tint = c.tint)
                        } else {
                            Image(painterResource(c.icon), null, m)
                        }
                    } else {
                        Text(
                            text = c.label ?: "",
                            fontSize = 18.sp,
                            color = if (last) x.pathTextSel else x.pathText,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = c.onClick)
                                .padding(horizontal = 6.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(8.dp))
            }
        }
        HorizontalDivider(color = x.pathBorder)
    }
}

@Composable
fun PathButton(text: String, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val x = LocalXfm.current
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .border(1.dp, x.buttonText, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text.uppercase(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = x.buttonText,
            maxLines = 1
        )
    }
}

@Composable
fun PieIcon(fraction: Float, color: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        drawCircle(color = color.copy(alpha = 0.3f))
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * fraction.coerceIn(0f, 1f),
            useCenter = true
        )
    }
}

class CatIcon(val res: Int, val tint: Color?)

fun catIcon(cat: Cat): CatIcon = when (cat) {
    Cat.IMAGES -> CatIcon(R.drawable.ic_x_frame, null)
    Cat.VIDEO -> CatIcon(R.drawable.ic_x_film, null)
    Cat.DOCS -> CatIcon(R.drawable.ic_x_docs, null)
    Cat.AUDIO -> CatIcon(R.drawable.ic_music_note, Color(0xFF3E9C8F))
    Cat.ARCHIVES -> CatIcon(R.drawable.ic_archive, Color(0xFF8D6E63))
    Cat.APK -> CatIcon(R.drawable.ic_android, Color(0xFFA4C639))
    Cat.RECENT -> CatIcon(R.drawable.ic_schedule, Color(0xFF5F7B94))
}

@Composable
fun CatGlyph(cat: Cat, size: Dp) {
    val ci = catIcon(cat)
    if (ci.tint != null) {
        Icon(painterResource(ci.res), null, Modifier.size(size), tint = ci.tint)
    } else {
        Image(painterResource(ci.res), null, Modifier.size(size))
    }
}
