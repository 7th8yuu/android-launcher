package com.example.mylauncher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Src { HOME, DOCK, DRAWER }

fun LauncherState.appMenu(app: AppEntry, rect: Rect, src: Src, page: Int) {
    val items = ArrayList<MenuItem>()

    // Lối tắt nhanh của ứng dụng (như Quick Actions của iOS) — cần là launcher mặc định
    act.shortcutsFor(app.pkg).forEach { sc ->
        val label = sc.shortLabel?.toString() ?: sc.id
        items += MenuItem(label) { act.startShortcut(sc) }
    }

    when (src) {
        Src.DRAWER -> {
            items += MenuItem("Thêm vào màn hình chính") { addToHome(app) }
            items += MenuItem("Thêm vào dock") { addToDock(app) }
        }
        Src.HOME -> {
            items += MenuItem("Chỉnh sửa Màn hình chính") { editMode = true }
            items += MenuItem("Xoá khỏi màn hình chính") { removeFromPage(page, app.key) }
            if (page > 0) items += MenuItem("Chuyển sang trang trước") { movePage(page, app.key, -1) }
            if (page in 0 until store.pages.lastIndex) {
                items += MenuItem("Chuyển sang trang sau") { movePage(page, app.key, 1) }
            }
        }
        Src.DOCK -> items += MenuItem("Xoá khỏi dock") { removeFromDock(app.key) }
    }
    items += MenuItem("Đổi biểu tượng (chọn ảnh)…") { act.pickIconFor(app) }
    if (icons.hasCustom(app.key)) {
        items += MenuItem("Khôi phục biểu tượng gốc") {
            icons.removeCustom(app.key)
            store.iconVersion++
        }
    }
    items += MenuItem("Đổi danh mục…") { menu = categoryMenu(app, rect) }
    items += MenuItem("Ẩn ứng dụng") { hide(app) }
    items += MenuItem("Thông tin ứng dụng") { act.openAppInfo(app) }
    items += MenuItem("Gỡ cài đặt", destructive = true) { act.uninstall(app) }
    menu = MenuSpec(rect, app.label, items, app)
}

private fun LauncherState.categoryMenu(app: AppEntry, rect: Rect): MenuSpec {
    val items = ArrayList<MenuItem>()
    items += MenuItem("Tự động") { setCategory(app, null) }
    Cat.values().forEach { c -> items += MenuItem(c.title) { setCategory(app, c) } }
    return MenuSpec(rect, "Danh mục", items, app)
}

fun LauncherState.homeMenu(index: Int, anchor: Rect): MenuSpec {
    val items = ArrayList<MenuItem>()
    items += MenuItem("Chỉnh sửa Màn hình chính") { editMode = true }
    items += MenuItem("Thêm widget") { showWidgets = true }
    items += MenuItem("Thêm trang mới") { addPage() }
    val page = store.pages.getOrNull(index)
    page?.widgets?.forEach { id ->
        val label = act.widgetManager.getAppWidgetInfo(id)?.loadLabel(act.packageManager) ?: "widget"
        items += MenuItem("Gỡ widget: $label", destructive = true) { removeWidget(index, id) }
    }
    if (store.pages.size > 1) {
        items += MenuItem("Xoá trang này", destructive = true) { removePage(index) }
    }
    items += MenuItem("Đổi hình nền") { act.openWallpaperPicker() }
    items += MenuItem("Cài đặt launcher") { showSettings = true }
    return MenuSpec(anchor, null, items)
}

@Composable
fun MenuLayer(s: LauncherState) {
    AnimatedContent(
        targetState = s.menu,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(140)) + scaleIn(
                animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
                initialScale = 0.78f
            )) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 0.9f))
        },
        label = "menu"
    ) { spec ->
        if (spec == null) Spacer(Modifier.fillMaxSize()) else MenuPopup(s, spec)
    }
}

@Composable
private fun MenuPopup(s: LauncherState, spec: MenuSpec) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.menu = null }
    ) {
        Box(
            Modifier.layout { measurable, c ->
                val p = measurable.measure(c.copy(minWidth = 0, minHeight = 0))
                layout(c.maxWidth, c.maxHeight) {
                    val margin = 16.dp.roundToPx()
                    val gap = 10.dp.roundToPx()
                    val x = (spec.anchor.center.x - p.width / 2f).toInt()
                        .coerceIn(margin, (c.maxWidth - p.width - margin).coerceAtLeast(margin))
                    var y = spec.anchor.bottom.toInt() + gap
                    if (y + p.height > c.maxHeight - margin) y = spec.anchor.top.toInt() - p.height - gap
                    y = y.coerceIn(margin, (c.maxHeight - p.height - margin).coerceAtLeast(margin))
                    p.place(x, y)
                }
            }
        ) { MenuCard(s, spec) }
    }
}

@Composable
private fun MenuCard(s: LauncherState, spec: MenuSpec) {
    Column(
        Modifier
            .widthIn(min = 230.dp, max = 300.dp)
            .glass(RoundedCornerShape(26.dp), 0.85f)
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        if (spec.preview != null || spec.title != null) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                spec.preview?.let {
                    AppIcon(s, it, 36.dp)
                    Spacer(Modifier.width(12.dp))
                }
                spec.title?.let {
                    Text(
                        it,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.18f)))
        }
        spec.items.forEachIndexed { i, item ->
            Text(
                item.label,
                color = if (item.destructive) Color(0xFFFF7A7A) else Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val cur = s.menu
                        item.action()
                        if (s.menu === cur) s.menu = null
                    }
                    .padding(horizontal = 18.dp, vertical = 13.dp)
            )
            if (i < spec.items.lastIndex) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.12f)))
            }
        }
    }
}
