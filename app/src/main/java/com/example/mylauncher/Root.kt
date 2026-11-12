package com.example.mylauncher

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

@Composable
fun LauncherRoot(s: LauncherState) {
    val scope = rememberCoroutineScope()
    s.uiScope = scope
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    LauncherTheme(s.store) {
        Box(Modifier.fillMaxSize().homeSwipe(s, screenH)) {
            HomeLayer(s)
            DrawerHost(s, screenH)
            SpotlightLayer(s)
            WidgetPicker(s)
            SettingsLayer(s)
            MenuLayer(s)
            GhostLayer(s)
        }
    }

    // Làm mờ hình nền phía sau khi mở thư viện / tìm kiếm / cài đặt (Android 12+)
    if (Build.VERSION.SDK_INT >= 31) {
        val overlay by animateFloatAsState(
            targetValue = if (s.showSettings || s.showWidgets) 1f else 0f,
            animationSpec = tween(300),
            label = "overlayBlur"
        )
        LaunchedEffect(Unit) {
            snapshotFlow {
                if (s.store.blur.value) (maxOf(s.drawer, s.spot, overlay) * 80f).toInt() else 0
            }.distinctUntilChanged().collect { r ->
                try { s.act.window.setBackgroundBlurRadius(r) } catch (_: Throwable) {}
            }
        }
    }

    BackHandler { s.onBack() }
}

/**
 * Vuốt lên: kéo thư viện ứng dụng lên theo ngón tay.
 * Vuốt xuống: kéo Spotlight xuống theo ngón tay (ô tìm kiếm biến hình từ thanh "Tìm kiếm").
 */
fun Modifier.homeSwipe(s: LauncherState, screenH: Float): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (s.drawer > 0f || s.spot > 0f || s.showSettings || s.showWidgets || s.menu != null || s.editMode) {
            return@awaitEachGesture
        }
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        val slop = viewConfiguration.touchSlop
        var dx = 0f
        var dy = 0f
        var mode = 0 // 0: chưa quyết định, 1: ngăn kéo, 2: Spotlight
        var settled = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) {
                if (mode != 0) {
                    val vy = tracker.calculateVelocity().y
                    if (mode == 1) s.settleDrawer(vy) else s.settleSpot(vy)
                    settled = true
                }
                break
            }
            tracker.addPosition(ch.uptimeMillis, ch.position)
            val d = ch.positionChange()
            if (mode == 0) {
                dx += d.x
                dy += d.y
                if (-dy > slop && -dy > abs(dx) * 1.2f) {
                    mode = 1
                    ch.consume()
                    s.dragDrawer(-dy, screenH)
                } else if (dy > slop && dy > abs(dx) * 1.2f && s.store.swipeDownSearch.value) {
                    mode = 2
                    ch.consume()
                    s.beginSpotDrag()
                    s.dragSpot(dy, screenH)
                } else if (abs(dx) > slop * 1.5f) {
                    break
                }
            } else {
                ch.consume()
                if (mode == 1) s.dragDrawer(-d.y, screenH) else s.dragSpot(d.y, screenH)
            }
        }
        if (mode != 0 && !settled) {
            if (mode == 1) s.settleDrawer(0f) else s.settleSpot(0f)
        }
    }
}
