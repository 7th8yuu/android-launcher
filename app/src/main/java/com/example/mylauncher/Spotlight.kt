@file:OptIn(ExperimentalFoundationApi::class)

package com.example.mylauncher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun SpotlightLayer(s: LauncherState) {
    if (s.spot > 0f) SpotlightContent(s)
}

@Composable
private fun SpotlightContent(s: LauncherState) {
    val dens = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val topInset = WindowInsets.statusBars.getTop(dens)

    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    LaunchedEffect(Unit) {
        snapshotFlow { s.spot }.first { it >= 0.98f }
        try { focus.requestFocus(); keyboard?.show() } catch (_: Exception) {}
    }
    LaunchedEffect(s.spotQuery) {
        delay(90)
        s.runSearch()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.5f * s.spot.coerceIn(0f, 1f))) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                s.closeSpotlight()
            }
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val p = s.spot.coerceIn(0f, 1f)
        val pill = if (s.pillBounds.width > 0f) s.pillBounds
        else Rect(w / 2f - 90f, h - 340f, w / 2f + 90f, h - 280f)

        val margin = with(dens) { 16.dp.toPx() }
        val fieldH = with(dens) { 54.dp.toPx() }
        val endTop = topInset + with(dens) { 10.dp.toPx() }
        val l = lerp(pill.left, margin, p)
        val t = lerp(pill.top, endTop, p)
        val r = lerp(pill.right, w - margin, p)
        val b = lerp(pill.bottom, endTop + fieldH, p)

        // Ô tìm kiếm "biến hình" từ thanh Tìm kiếm trên màn hình chính
        Box(
            Modifier
                .layout { m, c ->
                    val pw = (r - l).toInt().coerceAtLeast(1)
                    val ph = (b - t).toInt().coerceAtLeast(1)
                    val pl = m.measure(Constraints.fixed(pw, ph))
                    layout(c.maxWidth, c.maxHeight) { pl.place(l.toInt(), t.toInt()) }
                }
                .glass(CircleShape, 0.75f)
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) { SpotField(s, focus, p) }

        // Kết quả hiện dần phía dưới
        val topPad = with(dens) { (endTop + fieldH + 12.dp.toPx()).toDp() }
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = topPad)
                .graphicsLayer {
                    alpha = ((p - 0.45f) * 1.9f).coerceIn(0f, 1f)
                    translationY = (1f - p) * 36.dp.toPx()
                }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(horizontal = 16.dp)
        ) { SpotResults(s) }
    }
}

@Composable
private fun SpotField(s: LauncherState, focus: FocusRequester, p: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (s.spotQuery.isEmpty()) {
                Text("Tìm kiếm", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, maxLines = 1)
            }
            BasicTextField(
                value = s.spotQuery,
                onValueChange = { s.onSpotQuery(it) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val first = s.hits.filterIsInstance<Hit.AppHit>().firstOrNull()
                    if (first != null) s.launch(first.app, null)
                    else if (s.spotQuery.isNotBlank()) s.act.openUrl("https://www.google.com/search?q=" + Uri.encode(s.spotQuery))
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
        if (s.spotQuery.isNotEmpty()) {
            IconButton(onClick = { s.onSpotQuery("") }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Xoá", tint = Color.White.copy(alpha = 0.8f))
            }
        }
        if (p > 0.7f) {
            Spacer(Modifier.width(10.dp))
            Text(
                "Hủy",
                color = Color(0xFF64B5FF),
                fontSize = 15.sp,
                modifier = Modifier
                    .graphicsLayer { alpha = ((p - 0.7f) * 3.3f).coerceIn(0f, 1f) }
                    .clickable { s.closeSpotlight() }
            )
        }
    }
}

private sealed class SRow(val id: String) {
    class Header(val text: String) : SRow("h:$text")
    object Suggest : SRow("suggest")
    object Chips : SRow("chips")
    object Hint : SRow("hint")
    object AiCard : SRow("aicard")
    class CalcRow(val h: Hit.Calc) : SRow("calc")
    class AppRow(val h: Hit.AppHit) : SRow("app:${h.app.key}")
    class SettingRow(val h: Hit.SettingHit) : SRow("set:${h.entry.key}")
    class ContactRow(val h: Hit.ContactHit) : SRow("con:${h.uri}")
    class WebRow(val q: String) : SRow("web")
    class AskAi(val q: String) : SRow("askai")
}

@Composable
private fun SpotResults(s: LauncherState) {
    val q = s.spotQuery.trim()
    val hasKey = s.store.aiKey.value.isNotBlank()
    val suggestions = s.suggestions()
    val rows = remember(q, s.hits, s.aiText, s.aiBusy, s.aiError, hasKey, suggestions) {
        val out = ArrayList<SRow>()
        if (q.isEmpty()) {
            if (suggestions.isNotEmpty()) { out.add(SRow.Header("Gợi ý")); out.add(SRow.Suggest) }
            if (hasKey) { out.add(SRow.Header("Thử hỏi AI")); out.add(SRow.Chips) } else out.add(SRow.Hint)
        } else {
            if (s.aiBusy || s.aiText.isNotEmpty() || s.aiError != null) out.add(SRow.AiCard)
            s.hits.filterIsInstance<Hit.Calc>().forEach { out.add(SRow.CalcRow(it)) }
            val apps = s.hits.filterIsInstance<Hit.AppHit>()
            if (apps.isNotEmpty()) { out.add(SRow.Header("Ứng dụng")); apps.forEach { out.add(SRow.AppRow(it)) } }
            val sets = s.hits.filterIsInstance<Hit.SettingHit>()
            if (sets.isNotEmpty()) { out.add(SRow.Header("Cài đặt")); sets.forEach { out.add(SRow.SettingRow(it)) } }
            val cons = s.hits.filterIsInstance<Hit.ContactHit>()
            if (cons.isNotEmpty()) { out.add(SRow.Header("Danh bạ")); cons.forEach { out.add(SRow.ContactRow(it)) } }
            out.add(SRow.Header("Khác"))
            out.add(SRow.WebRow(q))
            out.add(SRow.AskAi(q))
        }
        out
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
            Appear(i) { RowView(s, r) }
        }
    }
}

@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(10) * 40L)
        a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = a.value
            translationY = (1f - a.value) * 24.dp.toPx()
            val sc = 0.96f + 0.04f * a.value
            scaleX = sc
            scaleY = sc
        }
    ) { content() }
}

@Composable
private fun GlassRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), 0.55f)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun RowView(s: LauncherState, r: SRow) {
    val ctx = LocalContext.current
    when (r) {
        is SRow.Header -> Text(
            r.text.uppercase(),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp)
        )
        SRow.Suggest -> Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), 0.55f).padding(vertical = 6.dp, horizontal = 6.dp)
        ) {
            val sug = s.suggestions()
            sug.forEach { a -> AppCell(s, a, Modifier.weight(1f), src = Src.DRAWER) }
            repeat(4 - sug.size) { Spacer(Modifier.weight(1f)) }
        }
        SRow.Chips -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Mở YouTube", "Đặt báo thức 7 giờ sáng", "Hẹn giờ 10 phút", "Gợi ý một bài hát để tập trung").forEach { t ->
                GlassRow(onClick = { s.onSpotQuery(t); s.askAi(t) }) {
                    Text("✦  $t", color = Color.White, fontSize = 15.sp)
                }
            }
        }
        SRow.Hint -> Text(
            "Nhập để tìm ứng dụng, danh bạ, cài đặt hoặc tính nhanh (vd: 12*(3+4)).\nThêm khoá API trong Cài đặt → AI để hỏi AI ngay tại đây.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        SRow.AiCard -> AiCard(s)
        is SRow.CalcRow -> GlassRow(onClick = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("kết quả", r.h.result))
            s.toast("Đã sao chép ${r.h.result}")
        }) {
            Column(Modifier.weight(1f)) {
                Text(r.h.expr, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp)
                Text("= ${r.h.result}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            }
            Text("Sao chép", color = Color(0xFF64B5FF), fontSize = 13.sp)
        }
        is SRow.AppRow -> GlassRow(onClick = { s.launch(r.h.app, null) }) {
            AppIcon(s, r.h.app, 40.dp)
            Spacer(Modifier.width(14.dp))
            Text(r.h.app.label, color = Color.White, fontSize = 16.sp)
        }
        is SRow.SettingRow -> GlassRow(onClick = {
            s.closeSpotlight()
            s.act.safeStart(Intent(r.h.entry.action), "Không mở được cài đặt")
        }) {
            Column {
                Text(r.h.entry.title, color = Color.White, fontSize = 16.sp)
                Text("Cài đặt", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        is SRow.ContactRow -> GlassRow(onClick = {
            s.closeSpotlight()
            s.act.safeStart(Intent(Intent.ACTION_VIEW, r.h.uri), "Không mở được danh bạ")
        }) {
            Column {
                Text(r.h.name, color = Color.White, fontSize = 16.sp)
                Text(r.h.number, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        is SRow.WebRow -> GlassRow(onClick = {
            s.closeSpotlight()
            s.act.openUrl("https://www.google.com/search?q=" + Uri.encode(r.q))
        }) { Text("Tìm “${r.q}” trên web", color = Color.White, fontSize = 16.sp) }
        is SRow.AskAi -> GlassRow(onClick = { s.askAi(r.q) }) {
            Text("✦  Hỏi AI: “${r.q}”", color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
private fun AiCard(s: LauncherState) {
    var shown by remember(s.aiText) { mutableIntStateOf(0) }
    LaunchedEffect(s.aiText) {
        shown = 0
        while (shown < s.aiText.length) {
            shown = (shown + 3).coerceAtMost(s.aiText.length)
            delay(18)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), 0.7f)
            .background(Color(0xFF7C4DFF).copy(alpha = 0.12f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("✦ AI", color = Color(0xFFB8A2FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        when {
            s.aiBusy -> ThinkingDots()
            s.aiError != null -> Text(s.aiError ?: "", color = Color(0xFFFF8A80), fontSize = 14.sp)
            else -> {
                if (s.aiText.isNotEmpty()) Text(s.aiText.take(shown), color = Color.White, fontSize = 16.sp)
                s.aiActions.forEach { a -> GlassPill(a.describe()) { s.runAiAction(a) } }
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val a by t.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(9.dp)
                    .graphicsLayer { alpha = if (i == 0) a else if (i == 1) 1.2f - a else a * 0.8f + 0.1f }
                    .background(Color.White, CircleShape)
            )
        }
    }
}
