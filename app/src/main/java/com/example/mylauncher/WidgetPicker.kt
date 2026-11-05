package com.example.mylauncher

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WidgetPicker(s: LauncherState) {
    AnimatedVisibility(
        visible = s.showWidgets,
        enter = slideInVertically(animationSpec = tween(320)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(animationSpec = tween(260)) { it } + fadeOut(tween(200))
    ) {
        val ctx = LocalContext.current
        val providers = remember {
            s.act.widgetManager.installedProviders
                .filter { (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 }
                .sortedBy { it.loadLabel(ctx.packageManager).lowercase() }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    s.showWidgets = false
                }
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .glass(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), 0.8f)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(16.dp)
            ) {
                Text("Chọn widget", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(8.dp))
                LazyColumn {
                    items(providers, key = { it.provider.flattenToString() }) { info ->
                        WidgetRow(s, info)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(s: LauncherState, info: AppWidgetProviderInfo) {
    val ctx = LocalContext.current
    val label = remember(info) { info.loadLabel(ctx.packageManager).toString() }
    val icon by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.Default) {
            try { info.loadIcon(ctx, 0)?.toBitmap(96, 96)?.asImageBitmap() } catch (_: Exception) { null }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                s.showWidgets = false
                s.act.startAddWidget(info)
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp)) {
            icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
