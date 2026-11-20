package com.example.mylauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp

/** Thư mục mở ra: bảng kính phóng từ icon thư mục ra giữa màn hình (như iOS). */
@Composable
fun FolderLayer(s: LauncherState) {
    val token = s.openFolder ?: return
    val folder = s.folderOf(token)
    if (folder == null) {
        LaunchedEffect(token) { s.openFolder = null }
        return
    }
    val dens = LocalDensity.current
    val p = s.folderP.value.coerceIn(0f, 1f)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.45f * p)) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.closeFolder() }
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cols = s.store.columns.value
        val rows = ((folder.apps.size + cols - 1) / cols).coerceAtLeast(1)
        val cellH = with(dens) { (s.store.iconSize.value + 38).dp.toPx() }
        val headerH = with(dens) { 64.dp.toPx() }
        val panelW = minOf(w - with(dens) { 36.dp.toPx() }, with(dens) { 380.dp.toPx() })
        val panelH = minOf(headerH + rows * cellH + with(dens) { 28.dp.toPx() }, h * 0.72f)
        val tl = (w - panelW) / 2f
        val tt = (h - panelH) * 0.36f
        val from = s.folderFrom
        val l = lerp(from.left, tl, p)
        val t = lerp(from.top, tt, p)
        val pw = lerp(from.width, panelW, p)
        val ph = lerp(from.height, panelH, p)
        val corner = lerp(20f, 34f, p)

        Box(
            Modifier
                .layout { m, c ->
                    val pl = m.measure(Constraints.fixed(pw.toInt().coerceAtLeast(1), ph.toInt().coerceAtLeast(1)))
                    layout(c.maxWidth, c.maxHeight) { pl.place(l.toInt(), t.toInt()) }
                }
                .glass(RoundedCornerShape(corner.dp), 0.85f)
                .background(Color.Black.copy(alpha = 0.30f * p))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .graphicsLayer { alpha = ((p - 0.35f) * 1.6f).coerceIn(0f, 1f) }
            ) {
                BasicTextField(
                    value = folder.name,
                    onValueChange = { s.renameFolder(token, it.take(24)) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
                LazyVerticalGrid(columns = GridCells.Fixed(cols), modifier = Modifier.fillMaxSize()) {
                    items(folder.apps.mapNotNull { s.byKey[it] }, key = { it.key }) { a ->
                        AppCell(s, a, Modifier.fillMaxWidth(), src = Src.FOLDER)
                    }
                }
            }
        }
    }
}
