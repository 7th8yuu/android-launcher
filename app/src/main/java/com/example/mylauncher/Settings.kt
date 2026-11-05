@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.mylauncher

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun SettingsLayer(s: LauncherState) {
    AnimatedVisibility(
        visible = s.showSettings,
        enter = slideInHorizontally(tween<IntOffset>(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
        exit = slideOutHorizontally(tween<IntOffset>(260)) { it } + fadeOut(tween(200))
    ) { SettingsScreen(s) }
}

@Composable
private fun SettingsScreen(s: LauncherState) {
    val st = s.store
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { s.showSettings = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Cài đặt launcher", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Section("Cử chỉ") {
                    SwitchRow(
                        "Chạm 2 lần để tắt màn hình",
                        "Chạm hai lần vào chỗ trống trên màn hình chính",
                        st.doubleTapLock.value
                    ) { st.doubleTapLock.value = it }
                    if (LockService.connected) {
                        Text("Dịch vụ trợ năng: đang bật ✓", color = Color(0xFF8CE99A), fontSize = 14.sp)
                    } else {
                        Text(
                            "Cần bật dịch vụ trợ năng “My Launcher – Khoá màn hình” để cử chỉ này hoạt động. " +
                                "Dịch vụ chỉ dùng để khoá màn hình, không đọc nội dung.",
                            color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp
                        )
                        Button(onClick = {
                            s.act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("Mở cài đặt trợ năng") }
                        Text(
                            "Nếu mục này bị làm mờ (Android 13+, app cài ngoài Play Store): Cài đặt → Ứng dụng → " +
                                "My Launcher → ⋮ → “Cho phép cài đặt bị hạn chế”, rồi thử lại.",
                            color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Section("Giao diện") {
                    SliderRow(
                        "Độ đậm kính (Liquid Glass)", st.glass.value, 0f..1f,
                        "${(st.glass.value * 100).roundToInt()}%"
                    ) { st.glass.value = it }
                    if (Build.VERSION.SDK_INT >= 31) {
                        SwitchRow(
                            "Làm mờ hình nền",
                            "Mờ nền phía sau khi mở thư viện ứng dụng và cài đặt",
                            st.blur.value
                        ) { st.blur.value = it }
                        SwitchRow(
                            "Màu động Material You",
                            "Lấy màu theo hình nền",
                            st.dynamicColor.value
                        ) { st.dynamicColor.value = it }
                    }
                    SliderRow(
                        "Kích thước biểu tượng", st.iconSize.value.toFloat(), 44f..72f,
                        "${st.iconSize.value}dp"
                    ) { st.iconSize.value = it.roundToInt() }
                    ChipRow("Số cột", listOf("4", "5"), st.columns.value - 4) { st.columns.value = it + 4 }
                    ChipRow("Hình dạng biểu tượng", listOf("Gốc", "Bo góc", "Tròn"), st.iconShape.value) {
                        st.iconShape.value = it
                    }
                    SwitchRow("Hiện tên ứng dụng", null, st.showLabels.value) { st.showLabels.value = it }
                }
            }

            item {
                Section("Bộ biểu tượng") {
                    val packs = remember { s.icons.listPacks() }
                    PackRow("Mặc định của hệ thống", st.iconPack.value.isEmpty()) {
                        st.iconPack.value = ""
                        s.icons.invalidate()
                        st.iconVersion++
                    }
                    packs.forEach { p ->
                        PackRow(p.label, st.iconPack.value == p.pkg) {
                            st.iconPack.value = p.pkg
                            s.icons.invalidate()
                            st.iconVersion++
                        }
                    }
                    if (packs.isEmpty()) {
                        Text(
                            "Chưa phát hiện bộ biểu tượng nào. Cài bộ biểu tượng hỗ trợ Nova/ADW/Go từ Play Store " +
                                "rồi quay lại đây. Bạn cũng có thể giữ lâu một ứng dụng → “Đổi biểu tượng” để chọn ảnh riêng.",
                            color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp
                        )
                    }
                    OutlinedButton(onClick = {
                        s.icons.clearAllCustom()
                        st.iconVersion++
                        s.toast("Đã xoá mọi biểu tượng tuỳ chỉnh")
                    }) { Text("Xoá mọi biểu tượng tự chọn") }
                }
            }

            item {
                Section("Trang chủ") {
                    Text("Số trang: ${st.pages.size}", color = Color.White, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { s.addPage() }) { Text("Thêm trang") }
                        OutlinedButton(onClick = {
                            val last = st.pages.lastIndex
                            val p = st.pages[last]
                            if (st.pages.size > 1 && p.apps.isEmpty() && p.widgets.isEmpty()) s.removePage(last)
                            else s.toast("Chỉ xoá được trang cuối khi trang đó trống")
                        }) { Text("Xoá trang cuối") }
                    }
                }
            }

            item {
                Section("Ứng dụng đã ẩn") {
                    val hidden = s.apps.filter { it.key in st.hidden }
                    if (hidden.isEmpty()) {
                        Text("Không có ứng dụng nào bị ẩn", color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp)
                    }
                    hidden.forEach { a ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(s, a, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { s.unhide(a.key) }) { Text("Hiện") }
                        }
                    }
                }
            }

            item {
                Section("Hệ thống") {
                    Button(onClick = { s.act.openDefaultLauncherSettings() }) {
                        Text("Đặt làm launcher mặc định")
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp), 0.5f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = Color(0xFFB8C7FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row {
            Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ChipRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o ->
                FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o) })
            }
        }
    }
}

@Composable
private fun PackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}
