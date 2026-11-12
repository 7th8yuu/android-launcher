package com.example.mylauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hiệu ứng mở ứng dụng kiểu iOS: icon phóng từ vị trí bấm ra toàn màn hình,
 * khi quay lại thì thu về đúng chỗ icon.
 */
@Composable
fun GhostLayer(s: LauncherState) {
    val app = s.ghostApp ?: return
    val dens = LocalDensity.current
    val bmp by produceState<ImageBitmap?>(s.icons.peek(app), app.key) {
        value = withContext(Dispatchers.Default) { s.icons.get(app) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val p = s.ghostP.value
        val f = s.ghostFrom
        val l = lerp(f.left, 0f, p)
        val t = lerp(f.top, 0f, p)
        val bw = lerp(f.width, w, p)
        val bh = lerp(f.height, h, p)
        val corner = ((1f - p) * 24f).toInt().coerceIn(0, 50)
        val iconPx = lerp(f.width, with(dens) { 108.dp.toPx() }, p)
        val iconAlpha = (1f - (p - 0.55f).coerceAtLeast(0f) * 2.4f).coerceIn(0f, 1f)

        Box(
            Modifier.layout { m, c ->
                val pl = m.measure(Constraints.fixed(bw.toInt().coerceAtLeast(1), bh.toInt().coerceAtLeast(1)))
                layout(c.maxWidth, c.maxHeight) { pl.place(l.toInt(), t.toInt()) }
            }
                .clip(RoundedCornerShape(percent = corner))
                .background(Color(0xFF0E0E16).copy(alpha = (p * 1.4f).coerceIn(0f, 1f))),
            contentAlignment = Alignment.Center
        ) {
            bmp?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier
                        .size(with(dens) { iconPx.toDp() })
                        .graphicsLayer { alpha = iconAlpha }
                )
            }
        }
    }
}
