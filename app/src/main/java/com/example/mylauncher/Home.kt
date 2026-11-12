@file:OptIn(ExperimentalFoundationApi::class)

package com.example.mylauncher

import android.app.WallpaperManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

fun shapeOf(i: Int): Shape = when (i) {
    1 -> RoundedCornerShape(percent = 23)   // squircle kiểu iOS
    2 -> CircleShape
    else -> RectangleShape
}

/** Trạng thái kéo thả sắp xếp icon (chế độ chỉnh sửa). */
class DragCtx {
    var key by mutableStateOf<String?>(null)
    var pos by mutableStateOf(Offset.Zero)
    val rects = HashMap<String, Rect>()
}

@Composable
fun HomeLayer(s: LauncherState) {
    val store = s.store
    val pages = store.pages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val dens = LocalDensity.current
    val view = LocalView.current
    val drag = remember { DragCtx() }

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
    LaunchedEffect(pagerState, pages.size) {
        val wm = WallpaperManager.getInstance(view.context)
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect { p ->
            val n = (pages.size - 1).coerceAtLeast(1)
            try { wm.setWallpaperOffsets(view.windowToken, (p / n).coerceIn(0f, 1f), 0.5f) } catch (_: Exception) {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .graphicsLayer {
                    val o = maxOf(s.drawer, s.spot)
                    val sc = launchScale * (1f - 0.08f * o)
                    scaleX = sc
                    scaleY = sc
                    alpha = launchAlpha * (1f - o)
                }
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = drag.key == null,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
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
                ) {
                    HomePage(s, page, drag) { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction }
                }
            }
            PageDots(pagerState, pages.size)
            SearchPill(s)
            Dock(s)
        }
        EditBar(s, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun EditBar(s: LauncherState, modifier: Modifier) {
    Box(modifier) {
        AnimatedVisibility(
            visible = s.editMode,
            enter = slideInVertically(tween<IntOffset>(300)) { -it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween<IntOffset>(220)) { -it } + fadeOut(tween(150))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassPill("＋ Widget") { s.showWidgets = true }
                GlassPill("Xong") { s.editMode = false }
            }
        }
    }
}

@Composable
fun HomePage(s: LauncherState, index: Int, drag: DragCtx, pageFrac: () -> Float) {
    val store = s.store
    val page = store.pages.getOrNull(index) ?: return
    val cols = store.columns.value
    val haptic = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }

    // Rung lắc khi chỉnh sửa (chỉ chạy khi đang ở chế độ chỉnh sửa)
    val jig = remember { Animatable(0f) }
    LaunchedEffect(s.editMode) {
        if (s.editMode) {
            jig.snapTo(-1f)
            while (true) {
                jig.animateTo(1f, tween(110, easing = LinearEasing))
                jig.animateTo(-1f, tween(110, easing = LinearEasing))
            }
        } else {
            jig.snapTo(0f)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(index) {
                detectTapGestures(
                    onTap = { if (s.editMode) s.editMode = false },
                    onDoubleTap = { if (!s.editMode && store.doubleTapLock.value) s.act.lockScreen() },
                    onLongPress = { off ->
                        if (!s.editMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val p = origin + off
                            s.menu = s.homeMenu(index, Rect(p, p))
                        }
                    }
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .clipToBounds()
                .graphicsLayer {
                    translationX = Tilt.x * 6.dp.toPx()
                    translationY = -Tilt.y * 4.dp.toPx()
                }
        ) {
            if (index == 0) {
                ClockHeader(s)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BatteryCard(s, Modifier.weight(1f))
                    CalendarCard(s, Modifier.weight(1f))
                }
            }
            page.widgets.forEach { id -> WidgetSlot(s, id) }
            val items = page.apps.mapNotNull { s.byKey[it] }
            items.chunked(cols).forEachIndexed { r, row ->
                val dragging = row.any { it.key == drag.key }
                Row(Modifier.fillMaxWidth().zIndex(if (dragging) 5f else 0f)) {
                    row.forEachIndexed { c, app ->
                        AppCell(
                            s, app, Modifier.weight(1f),
                            src = Src.HOME, page = index,
                            index = r * cols + c + 1,
                            drag = drag,
                            jiggle = { jig.value },
                            parallax = { pageFrac() * (c - (cols - 1) / 2f) * 12f }
                        )
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (items.isEmpty() && page.widgets.isEmpty() && index != 0) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "Trang trống.\nGiữ lâu để thêm widget hoặc thêm ứng dụng từ thư viện.",
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
            .padding(vertical = 6.dp)
            .glass(RoundedCornerShape(32.dp), s.store.glass.value)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Row {
            time.forEach { ch ->
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(tween<IntOffset>(350)) { it } + fadeIn(tween(350))) togetherWith
                            (slideOutVertically(tween<IntOffset>(350)) { -it } + fadeOut(tween(250)))
                    },
                    label = "digit"
                ) { c ->
                    Text(c.toString(), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
                }
            }
        }
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
fun AppIcon(s: LauncherState, app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
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
private fun CountBadge(n: Int, modifier: Modifier) {
    val a = remember(n) { Animatable(0.3f) }
    LaunchedEffect(n) { a.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
    Box(
        modifier
            .graphicsLayer { scaleX = a.value; scaleY = a.value }
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(Color(0xFFFF3B30), CircleShape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(if (n > 99) "99+" else n.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MinusBadge(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(24.dp)
            .background(Color(0xFF8E8E93), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(12.dp, 2.dp).background(Color.White))
    }
}

@Composable
fun AppCell(
    s: LauncherState,
    app: AppEntry,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    src: Src,
    page: Int = -1,
    index: Int = 0,
    drag: DragCtx? = null,
    jiggle: () -> Float = { 0f },
    parallax: () -> Float = { 0f }
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    val editing = s.editMode && src == Src.HOME
    val badge = BadgeService.counts[app.pkg] ?: 0
    val isDragged = drag?.key == app.key
    val animateIn = src == Src.HOME
    val appear = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animateIn) {
            delay((index % 24) * 18L)
            appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    DisposableEffectRemove(drag, app.key)

    val base = modifier
        .onGloballyPositioned {
            bounds = it.boundsInWindow()
            drag?.rects?.put(app.key, bounds)
        }
        .zIndex(if (isDragged) 2f else 0f)
        .graphicsLayer {
            val a = appear.value
            alpha = a.coerceIn(0f, 1f)
            val sc = (0.6f + 0.4f * a) * (if (isDragged) 1.12f else 1f)
            scaleX = sc
            scaleY = sc
            translationX = parallax()
            if (editing && !isDragged) rotationZ = jiggle() * (if (index % 2 == 0) 2f else -2f)
            if (isDragged && drag != null) {
                translationX += drag.pos.x - bounds.center.x
                translationY = drag.pos.y - bounds.center.y
            }
        }

    val interact: Modifier = if (editing && drag != null) {
        Modifier.pointerInput(app.key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { off ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    drag.key = app.key
                    drag.pos = bounds.topLeft + off
                },
                onDrag = { change, delta ->
                    change.consume()
                    drag.pos += delta
                    s.dragReorder(page, drag)
                },
                onDragEnd = { drag.key = null },
                onDragCancel = { drag.key = null }
            )
        }
    } else {
        Modifier.bounceClick(
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                s.appMenu(app, bounds, src, page)
            }
        ) { s.launch(app, bounds) }
    }

    Column(
        base.then(interact).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AppIcon(s, app, s.store.iconSize.value.dp)
            if (badge > 0 && !editing) {
                CountBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
            }
            if (editing) {
                MinusBadge(Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-6).dp)) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    s.removeFromPage(page, app.key)
                }
            }
        }
        if (showLabel && s.store.showLabels.value) {
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                color = Color.White,
                fontSize = 11.sp,
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
private fun DisposableEffectRemove(drag: DragCtx?, key: String) {
    androidx.compose.runtime.DisposableEffect(key) {
        onDispose { drag?.rects?.remove(key) }
    }
}

/** Thanh "Tìm kiếm" nổi phía trên dock, giống iOS. Chạm để mở Spotlight (biến hình từ chính thanh này). */
@Composable
fun SearchPill(s: LauncherState) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .onGloballyPositioned { s.pillBounds = it.boundsInWindow() }
                .glass(CircleShape, s.store.glass.value)
                .bounceClick { s.openSpotlight() }
                .padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tìm kiếm", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        }
    }
}

@Composable
fun Dock(s: LauncherState) {
    val dock = s.store.dock.mapNotNull { s.byKey[it] }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .heightIn(min = 90.dp)
            .glass(RoundedCornerShape(36.dp), s.store.glass.value),
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
