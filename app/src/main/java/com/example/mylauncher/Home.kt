@file:OptIn(ExperimentalFoundationApi::class)

package com.example.mylauncher

import android.app.WallpaperManager
import android.text.format.DateFormat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

fun shapeOf(i: Int): Shape = when (i) {
    1 -> RoundedCornerShape(percent = 28)
    2 -> CircleShape
    else -> RectangleShape
}

@Composable
fun HomeLayer(s: LauncherState) {
    val store = s.store
    val pages = store.pages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val dens = LocalDensity.current
    val view = LocalView.current

    // Animation mở / đóng ứng dụng: màn hình chính phóng to + mờ dần khi mở app, thu về khi quay lại
    val launchScale by animateFloatAsState(
        targetValue = if (s.launching) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "launchScale"
    )
    val launchAlpha by animateFloatAsState(
        targetValue = if (s.launching) 0f else 1f,
        animationSpec = tween(260),
        label = "launchAlpha"
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { s.currentPage = it }
    }
    LaunchedEffect(s.homeSignal) {
        if (s.homeSignal > 0 && pagerState.currentPage != 0) pagerState.animateScrollToPage(0)
    }
    LaunchedEffect(s.pageRequest) {
        val r = s.pageRequest
        if (r >= 0) {
            if (r < pagerState.pageCount) pagerState.animateScrollToPage(r)
            s.pageRequest = -1
        }
    }
    // Hình nền cuộn nhẹ theo trang (parallax)
    LaunchedEffect(pagerState, pages.size) {
        val wm = WallpaperManager.getInstance(view.context)
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect { p ->
            val n = (pages.size - 1).coerceAtLeast(1)
            try { wm.setWallpaperOffsets(view.windowToken, (p / n).coerceIn(0f, 1f), 0.5f) } catch (_: Exception) {}
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .graphicsLayer {
                val d = s.drawer
                val sc = launchScale * (1f - 0.08f * d)
                scaleX = sc
                scaleY = sc
                alpha = launchAlpha * (1f - d)
            }
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val frac = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val f = frac.absoluteValue.coerceIn(0f, 1f)
                    alpha = 1f - 0.7f * f
                    val sc = 1f - 0.12f * f
                    scaleX = sc
                    scaleY = sc
                    rotationY = frac * 22f
                    cameraDistance = 14f * dens.density
                }
            ) { HomePage(s, page) }
        }
        PageDots(pagerState, pages.size)
        Dock(s)
    }
}

@Composable
fun HomePage(s: LauncherState, index: Int) {
    val store = s.store
    val page = store.pages.getOrNull(index) ?: return
    val cols = store.columns.value
    val haptic = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(index) {
                detectTapGestures(
                    onDoubleTap = { if (store.doubleTapLock.value) s.act.lockScreen() },
                    onLongPress = { off ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val p = origin + off
                        s.menu = s.homeMenu(index, Rect(p, p))
                    }
                )
            }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).clipToBounds()) {
            if (index == 0) ClockHeader(s)
            page.widgets.forEach { id -> WidgetSlot(s, id) }
            val items = page.apps.mapNotNull { s.byKey[it] }
            items.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        AppCell(s, app, Modifier.weight(1f), src = Src.HOME, page = index)
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (items.isEmpty() && page.widgets.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "Giữ lâu để thêm widget, trang mới hoặc mở cài đặt.\nVuốt lên để mở thư viện ứng dụng.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            }
        }
    }
}

@Composable
fun ClockHeader(s: LauncherState) {
    val ctx = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .glass(RoundedCornerShape(32.dp), s.store.glass.value)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Text(time, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
        Text(date, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
    }
}

@Composable
fun WidgetSlot(s: LauncherState, id: Int) {
    val info = remember(id) { s.act.widgetManager.getAppWidgetInfo(id) } ?: return
    val density = LocalDensity.current
    val h = with(density) { info.minHeight.toDp() }.coerceIn(72.dp, 380.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(h)
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        AndroidView(
            factory = { ctx -> s.act.widgetHost.createView(ctx, id, info) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun AppIcon(s: LauncherState, app: AppEntry, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val ver = s.store.iconVersion
    val bmp by produceState<ImageBitmap?>(s.icons.peek(app), app.key, ver) {
        value = withContext(Dispatchers.Default) { s.icons.get(app) }
    }
    val shape = shapeOf(s.store.iconShape.value)
    Box(modifier.size(size).clip(shape)) {
        bmp?.let {
            Image(bitmap = it, contentDescription = app.label, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun AppCell(
    s: LauncherState,
    app: AppEntry,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    src: Src,
    page: Int = -1
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    Column(
        modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .bounceClick(
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    s.appMenu(app, bounds, src, page)
                }
            ) { s.launch(app, bounds) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(s, app, s.store.iconSize.value.dp)
        if (showLabel && s.store.showLabels.value) {
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 6f)),
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
fun Dock(s: LauncherState) {
    val dock = s.store.dock.mapNotNull { s.byKey[it] }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = 84.dp)
            .glass(RoundedCornerShape(32.dp), s.store.glass.value),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dock.forEach { app -> AppCell(s, app, showLabel = false, src = Src.DOCK) }
        }
    }
}

@Composable
fun PageDots(state: PagerState, count: Int) {
    if (count <= 1) {
        Spacer(Modifier.height(10.dp))
    } else {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(count) { i ->
                val sel = state.currentPage == i
                val w by animateDpAsState(
                    targetValue = if (sel) 18.dp else 6.dp,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy),
                    label = "dot"
                )
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(w)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (sel) 0.95f else 0.45f))
                )
            }
        }
    }
}
