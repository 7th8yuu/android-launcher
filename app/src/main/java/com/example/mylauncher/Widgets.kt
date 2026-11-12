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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import java.util.Locale

/** Widget Pin: vòng tròn tiến trình động. */
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
    Column(
        modifier
            .height(120.dp)
            .glass(RoundedCornerShape(28.dp), s.store.glass.value)
            .padding(14.dp)
    ) {
        Text(if (info.second) "Đang sạc" else "Pin", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(68.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(68.dp)) {
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                val inset = 4.dp.toPx()
                val sz = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), sz, style = stroke)
                drawArc(color, -90f, sweep, false, Offset(inset, inset), sz, style = stroke)
            }
            Text(
                if (level >= 0) "$level%" else "–",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Widget Lịch: ngày hôm nay. */
@Composable
fun CalendarCard(s: LauncherState, modifier: Modifier = Modifier) {
    val today = remember0()
    val dow = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    val month = today.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    Column(
        modifier
            .height(120.dp)
            .glass(RoundedCornerShape(28.dp), s.store.glass.value)
            .padding(14.dp)
    ) {
        Text(dow.uppercase(), color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(today.dayOfMonth.toString(), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light)
        Text(month, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

@Composable
private fun remember0(): LocalDate = androidx.compose.runtime.remember { LocalDate.now() }
