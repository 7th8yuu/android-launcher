package com.example.mylauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DrawerHost(s: LauncherState, screenH: Float) {
    if (s.drawer > 0f) DrawerContent(s, screenH)
}

private fun drawerNested(s: LauncherState, screenH: Float) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (s.openCat == null && source == NestedScrollSource.Drag && available.y < 0f && s.drawer < 1f) {
            s.dragDrawer(-available.y, screenH)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (s.openCat == null && source == NestedScrollSource.Drag && available.y > 0f) {
            s.dragDrawer(-available.y, screenH)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (s.openCat == null && s.drawer > 0f && s.drawer < 1f) {
            s.settleDrawer(available.y)
            return available
        }
        return Velocity.Zero
    }
}

/** Thư viện ứng dụng kiểu iOS: thẻ danh mục + ô tìm kiếm; chạm vào ô tìm kiếm để ra danh sách A–Z. */
@Composable
private fun DrawerContent(s: LauncherState, screenH: Float) {
    val store = s.store
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    // Back khi đang tìm kiếm: bỏ focus (về lại các thẻ danh mục) trước khi đóng thư viện
    BackHandler(enabled = s.libraryFocus) {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val nested = remember(screenH) { drawerNested(s, screenH) }

    val visible = remember(s.apps, store.hidden) { s.apps.filter { it.key !in store.hidden } }
    val q = s.query.trim()
    val results = remember(visible, q) {
        if (q.isEmpty()) visible else visible.filter { it.label.contains(q, ignoreCase = true) }
    }
    val grouped = remember(visible, store.catOverride) { visible.groupBy { s.catOf(it) } }
    val showList = s.libraryFocus || q.isNotEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.38f * s.drawer.coerceIn(0f, 1f))) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - s.drawer) * 0.5f * size.height
                    alpha = s.drawer.coerceIn(0f, 1f)
                }
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            DrawerSearch(s) { results.firstOrNull()?.let { s.launch(it, null) } }
            Spacer(Modifier.size(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(nested)) {
                if (showList) {
                    AllAppsGrid(s, results, q.isNotEmpty())
                } else {
                    LibraryGrid(s, grouped)
                }
                AnimatedContent(
                    targetState = s.openCat,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                            initialScale = 0.82f
                        )) togetherWith (fadeOut(tween(140)) + scaleOut(targetScale = 0.9f))
                    },
                    label = "categorySheet"
                ) { cat ->
                    if (cat != null) {
                        CategorySheet(s, cat, grouped[cat].orEmpty())
                    } else {
                        Spacer(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSearch(s: LauncherState, onSearch: () -> Unit) {
    BasicTextField(
        value = s.query,
        onValueChange = { s.query = it },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .onFocusChanged { s.libraryFocus = it.isFocused }
            .glass(RoundedCornerShape(26.dp), s.store.glass.value + 0.15f),
        decorationBox = { inner ->
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (s.query.isEmpty()) {
                        Text("Thư viện ứng dụng", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                    }
                    inner()
                }
                if (s.query.isNotEmpty()) {
                    IconButton(onClick = { s.query = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Xoá", tint = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    )
}

@Composable
private fun LibraryGrid(s: LauncherState, grouped: Map<Cat, List<AppEntry>>) {
    val cats = Cat.values().filter { !grouped[it].isNullOrEmpty() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(cats, key = { it.name }) { cat -> CategoryCard(s, cat, grouped[cat].orEmpty()) }
    }
}

@Composable
private fun SlotIcon(s: LauncherState, app: AppEntry?, size: Dp) {
    if (app != null) AppIcon(s, app, size) else Spacer(Modifier.size(size))
}

/** Ô thứ 4 của thẻ: nếu còn nhiều ứng dụng thì hiện cụm 2x2 icon nhỏ, như App Library của iOS. */
@Composable
private fun FourthSlot(s: LauncherState, apps: List<AppEntry>, size: Dp) {
    if (apps.size > 4) {
        val rest = apps.drop(3).take(4)
        val mini = size * 0.44f
        Column(Modifier.size(size), verticalArrangement = Arrangement.SpaceBetween) {
            rest.chunked(2).forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    r.forEach { a -> AppIcon(s, a, mini) }
                    repeat(2 - r.size) { Spacer(Modifier.size(mini)) }
                }
            }
        }
    } else {
        SlotIcon(s, apps.getOrNull(3), size)
    }
}

@Composable
private fun CategoryCard(s: LauncherState, cat: Cat, apps: List<AppEntry>) {
    val iconSize = 50.dp
    Column(
        Modifier.fillMaxWidth().bounceClick { s.openCat = cat },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), s.store.glass.value)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SlotIcon(s, apps.getOrNull(0), iconSize)
                    SlotIcon(s, apps.getOrNull(1), iconSize)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SlotIcon(s, apps.getOrNull(2), iconSize)
                    FourthSlot(s, apps, iconSize)
                }
            }
        }
        Spacer(Modifier.size(7.dp))
        Text(cat.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AllAppsGrid(s: LauncherState, list: List<AppEntry>, searching: Boolean) {
    val entries = remember(list, searching) {
        buildList<Any> {
            if (searching) {
                addAll(list)
            } else {
                var last = ' '
                list.forEach { a ->
                    val ch = a.label.firstOrNull()?.uppercaseChar()
                    val c = if (ch != null && ch.isLetter()) ch else '#'
                    if (c != last) { add(c); last = c }
                    add(a)
                }
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(s.store.columns.value),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            entries,
            key = { e -> if (e is AppEntry) e.key else "h$e" },
            span = { e -> if (e is AppEntry) GridItemSpan(1) else GridItemSpan(maxLineSpan) }
        ) { e ->
            if (e is AppEntry) {
                AppCell(s, e, Modifier.fillMaxWidth(), src = Src.DRAWER)
            } else {
                Text(
                    e.toString(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CategorySheet(s: LauncherState, cat: Cat, apps: List<AppEntry>) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.openCat = null },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .glass(RoundedCornerShape(32.dp), s.store.glass.value + 0.3f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cat.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { s.openCat = null }) { Text("Đóng") }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(s.store.columns.value),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(apps, key = { it.key }) { a -> AppCell(s, a, Modifier.fillMaxWidth(), src = Src.DRAWER) }
            }
        }
    }
}
