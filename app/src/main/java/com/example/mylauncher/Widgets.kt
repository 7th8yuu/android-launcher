package com.example.mylauncher

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val WidgetShape = RoundedCornerShape(24.dp)

/** Widget đồng hồ kim kiểu iOS: kim giờ/phút trắng, kim giây cam. */
@Composable
fun AnalogClockCard(s: LauncherState, modifier: Modifier = Modifier) {
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }
    Box(modifier.glass(WidgetShape, s.store.glass.value).padding(14.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            for (i in 0 until 60) {
                val big = i % 5 == 0
                val rad = Math.toRadians(i * 6.0 - 90.0)
                val len = if (big) r * 0.11f else r * 0.045f
                val a = Offset(c.x + cos(rad).toFloat() * (r - len), c.y + sin(rad).toFloat() * (r - len))
                val b = Offset(c.x + cos(rad).toFloat() * r, c.y + sin(rad).toFloat() * r)
                drawLine(
                    Color.White.copy(alpha = if (big) 0.9f else 0.35f), a, b,
                    strokeWidth = if (big) 2.4.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round
                )
            }
            val sec = now.second + now.nano / 1_000_000_000f
            val min = now.minute + sec / 60f
            val hr = (now.hour % 12) + min / 60f
            fun hand(deg: Float, len: Float, w: Float, col: Color) {
                val rad = Math.toRadians((deg - 90f).toDouble())
                drawLine(col, c, Offset(c.x + cos(rad).toFloat() * len, c.y + sin(rad).toFloat() * len), strokeWidth = w, cap = StrokeCap.Round)
            }
            hand(hr * 30f, r * 0.5f, 5.dp.toPx(), Color.White)
            hand(min * 6f, r * 0.75f, 3.5.dp.toPx(), Color.White)
            hand(sec * 6f, r * 0.84f, 1.6.dp.toPx(), Color(0xFFFF9500))
            drawCircle(Color(0xFFFF9500), 4.5.dp.toPx(), c)
            drawCircle(Color(0xFF202020), 1.6.dp.toPx(), c)
        }
    }
}

/** Widget Lịch: ngày hôm nay, kiểu iOS. */
@Composable
fun CalendarCard(s: LauncherState, modifier: Modifier = Modifier) {
    val today = remember { LocalDate.now() }
    val dow = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    val month = today.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    Column(
        modifier
            .glass(WidgetShape, s.store.glass.value)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(dow.uppercase(), color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(today.dayOfMonth.toString(), color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.weight(1f))
        Text(month, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
    }
}

/** Widget Pin dạng ngang với vòng tròn tiến trình động. */
@Composable
fun BatteryCard(s: LauncherState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val info by produceState(Pair(-1, false)) {
        while (true) {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val sc = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            value = Pair(if (l >= 0 && sc > 0) l * 100 / sc else -1, charging)
            delay(30_000)
        }
    }
    val level = info.first
    val sweep by animateFloatAsState(
        targetValue = level.coerceAtLeast(0) / 100f * 360f,
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "battery"
    )
    val color = when {
        info.second -> Color(0xFF34C759)
        level in 0..20 -> Color(0xFFFF453A)
        else -> Color.White
    }
    Row(
        modifier
            .height(84.dp)
            .glass(WidgetShape, s.store.glass.value)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(54.dp)) {
                val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                val inset = 3.dp.toPx()
                val sz = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), sz, style = stroke)
                drawArc(color, -90f, sweep, false, Offset(inset, inset), sz, style = stroke)
            }
            Text(if (level >= 0) "$level" else "–", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text("Pin", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (info.second) "Đang sạc" else if (level >= 0) "$level% còn lại" else "Không rõ",
                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp
            )
        }
    }
}
