package com.gamebox.helper

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.chrisbanes.haze.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

val Cy = Color(0xFF7DD3FC)
val Bg = Color(0xFF0A1118)

class Bat(val lv: Int, val t: Float, val mv: Int, val ma: Int, val health: Int, val plug: Int)

fun readBat(ctx: Context): Bat {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return Bat(
        i?.getIntExtra("level", 0) ?: 0, (i?.getIntExtra("temperature", 0) ?: 0) / 10f, i?.getIntExtra("voltage", 0) ?: 0,
        Math.abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) / 1000,
        i?.getIntExtra("health", 0) ?: 0, i?.getIntExtra("plugged", 0) ?: 0
    )
}

fun f1(x: Double): String = String.format(Locale.US, "%.1f", x)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { App(getSharedPreferences("gs", Context.MODE_PRIVATE)) }
    }
}

// Kính lỏng: blur nền phía sau + tint tối + viền sáng chéo
@Composable
fun GCard(hz: HazeState, mod: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val s = RoundedCornerShape(24.dp)
    Column(
        mod.clip(s)
            .hazeEffect(hz) { blurRadius = 24.dp; tints = listOf(HazeTint(Color(0xFF0C1620).copy(.55f))) }
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.35f), Color.White.copy(.05f), Color.White.copy(.15f))), s)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun Seg(items: List<String>, sel: Int, mod: Modifier = Modifier, on: (Int) -> Unit) {
    Row(mod.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.06f)).padding(4.dp)) {
        items.forEachIndexed { i, s ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (i == sel) Cy.copy(.25f) else Color.Transparent)
                    .clickable { on(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text(s, color = if (i == sel) Cy else Color.White.copy(.85f), fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
fun Stat(l: String, v: String) {
    Column(Modifier.padding(end = 24.dp)) {
        Text(l, fontSize = 10.sp, letterSpacing = 1.5.sp, color = Color.White.copy(.6f))
        Text(v, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Lbl(t: String) = Text(t, fontSize = 11.sp, letterSpacing = 2.sp, color = Cy.copy(.85f))

@Composable
fun Spark(v: List<Int>, max: Int, mod: Modifier) = Canvas(mod) {
    repeat(3) { drawLine(Color.White.copy(.08f), Offset(0f, size.height * (it + 1) / 4), Offset(size.width, size.height * (it + 1) / 4)) }
    if (v.size < 2) return@Canvas
    val p = Path()
    v.forEachIndexed { i, y ->
        val px = size.width * i / 59f; val py = size.height - size.height * y.coerceIn(0, max) / max.toFloat()
        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
    }
    drawPath(p, Cy, style = Stroke(width = 3.dp.toPx()))
}

@Composable
fun Rail(page: Int, set: (Int) -> Unit, power: () -> Unit) {
    Column(Modifier.fillMaxHeight().width(84.dp).background(Color.Black.copy(.35f)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Cy, Color(0xFF3B82F6)))), contentAlignment = Alignment.Center) {
            Text("GB", fontWeight = FontWeight.Black, color = Color(0xFF06121C))
        }
        Spacer(Modifier.height(16.dp))
        listOf("🎮", "⏱", "🔋", "⚙").forEachIndexed { i, e ->
            Box(
                Modifier.padding(vertical = 6.dp).size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (page == i) Cy.copy(.2f) else Color.Transparent).clickable { set(i) },
                contentAlignment = Alignment.Center
            ) { Text(e, fontSize = 22.sp) }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(.1f)).clickable { power() }, contentAlignment = Alignment.Center) { Text("✕", fontSize = 18.sp) }
    }
}

@Composable
fun App(sp: SharedPreferences) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val hz = remember { HazeState() }
    val refresh = LocalView.current.display?.refreshRate?.toInt() ?: 60
    var page by remember { mutableIntStateOf(0) }
    var m by remember { mutableIntStateOf(0) }
    val games = remember {
        val all = pm.getInstalledApplications(0).filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != ctx.packageName }
        val g = all.filter { it.category == ApplicationInfo.CATEGORY_GAME || (it.flags and ApplicationInfo.FLAG_IS_GAME) != 0 }
        g.ifEmpty { all }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
    }
    var sel by remember { mutableStateOf(sp.getString("sel", null) ?: games.firstOrNull()?.packageName ?: "") }
    val cfg = remember { mutableStateMapOf<String, Int>() }
    fun g(k: String, d: Int) = cfg["${k}_$sel"] ?: sp.getInt("${k}_$sel", d)
    fun s(k: String, v: Int) { cfg["${k}_$sel"] = v; sp.edit().putInt("${k}_$sel", v).apply() }
    fun label(p: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)
    var dnd by remember { mutableStateOf(sp.getBoolean("dnd", true)) }
    var cln by remember { mutableStateOf(sp.getBoolean("cln", true)) }
    var ov by remember { mutableStateOf(sp.getBoolean("ov", true)) }
    var fixed by remember { mutableStateOf(false) }
    var saver by remember { mutableStateOf(false) }
    var be by remember { mutableStateOf(Rt.backend) }
    var st by remember { mutableStateOf<St?>(null) }
    var ram by remember { mutableIntStateOf(0) }
    var used by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(1L) }
    var bat by remember { mutableStateOf<Bat?>(null) }
    var ping by remember { mutableIntStateOf(-1) }
    var clock by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var procs by remember { mutableStateOf(listOf<Proc>()) }
    var menu by remember { mutableStateOf(false) }
    val hist = remember { List(4) { mutableStateListOf<Int>() } }
    val temps = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) { be = withContext(Dispatchers.IO) { val b = Rt.init(); Rt.grant(ctx.packageName); b } }
    LaunchedEffect(Unit) {
        val fmt = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            withContext(Dispatchers.IO) {
                val x = Rt.stats(); st = x
                val (t, a) = Rt.mem(); total = t; used = t - a; ram = (100 - a * 100 / t).toInt()
                val b = readBat(ctx); bat = b
                clock = fmt.format(java.util.Date())
                listOf(x.cpuP, x.gpuP, ram, x.fps).forEachIndexed { i, v -> hist[i].add(v); if (hist[i].size > 60) hist[i].removeAt(0) }
                temps.add(b.t); if (temps.size > 600) temps.removeAt(0)
            }
            delay(1000)
        }
    }
    LaunchedEffect(Unit) { while (true) { ping = withContext(Dispatchers.IO) { Rt.ping() }; delay(10000) } }
    LaunchedEffect(page) { while (page == 1) { procs = withContext(Dispatchers.IO) { Rt.heavy() }; delay(8000) } }

    fun play() {
        val p = sel; if (p.isEmpty()) return
        Rt.pkg = p
        val gm = g("gm", 0); val res = g("res", 100); val fps = listOf(30, 45, 60, 90, 120, 0)[g("fps", 5)]
        val hzv = listOf(0, 60, 90, 120, 144)[g("hz", 0)]; val sat = g("sat", 100) / 100f
        thread { Rt.applyGame(p, gm, res, fps, hzv, sat); Rt.mode(gm); if (fixed) Rt.fixed(true); if (dnd) Rt.dnd(true); if (cln) Rt.clean() }
        if (ov) ctx.startForegroundService(Intent(ctx, Overlay::class.java))
        pm.getLaunchIntentForPackage(p)?.let { ctx.startActivity(it) }
    }
    fun power() {
        val p = Rt.pkg.ifEmpty { sel }; Rt.pkg = ""
        ctx.stopService(Intent(ctx, Overlay::class.java))
        thread { Rt.restore(p); Rt.mode(0); Rt.fixed(false); Rt.dnd(false) }
    }
    val selName = games.firstOrNull { it.packageName == sel }?.let { pm.getApplicationLabel(it).toString() } ?: "Chơi"
    val b = bat

    MaterialTheme(colorScheme = darkColorScheme(primary = Cy, background = Bg, surface = Bg, onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0A1118), Color(0xFF17140F))))) {
                Box(Modifier.fillMaxSize().hazeSource(hz)) {
                    Box(Modifier.align(Alignment.TopEnd).offset(60.dp, (-40).dp).size(300.dp).background(Color(0xFF8A5A1E).copy(.45f), CircleShape).blur(80.dp))
                    Box(Modifier.align(Alignment.BottomStart).offset((-40).dp, 60.dp).size(280.dp).background(Cy.copy(.25f), CircleShape).blur(80.dp))
                }
                Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Rail(page, { page = it }, { power() })
                    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("GAME BOX", fontSize = 11.sp, letterSpacing = 3.sp, color = Cy)
                                Text(listOf("Chọn game", "Hiệu năng", "Pin", "Đồ họa")[page], fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            }
                            if (page == 3) {
                                Box {
                                    Text("$selName ▾", Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.08f)).clickable { menu = true }.padding(horizontal = 14.dp, vertical = 9.dp))
                                    DropdownMenu(menu, { menu = false }) {
                                        games.forEach { a -> DropdownMenuItem({ Text(pm.getApplicationLabel(a).toString()) }, { sel = a.packageName; sp.edit().putString("sel", sel).apply(); menu = false }) }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton({
                                    listOf("hz", "sat", "res", "fps", "gm").forEach { sp.edit().remove("${it}_$sel").apply(); cfg.remove("${it}_$sel") }
                                }) { Text("Đặt lại mặc định") }
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                when (be) { "root" -> "Root"; "shizuku" -> "Shizuku"; else -> "Chưa có quyền" },
                                Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.08f)).clickable { thread { be = Rt.init() } }.padding(horizontal = 14.dp, vertical = 9.dp),
                                color = if (be == "none") Color(0xFFFF6B6B) else Color(0xFF4ADE80), fontSize = 13.sp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(clock, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(10.dp))
                        when (page) {
                            0 -> Column(Modifier.weight(1f)) {
                                LazyRow(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(games) { a ->
                                        val on = a.packageName == sel
                                        val ic = remember(a) { pm.getApplicationIcon(a.packageName).toBitmap(192, 192).asImageBitmap() }
                                        val sh = RoundedCornerShape(26.dp)
                                        Box(
                                            Modifier.fillMaxHeight().width(220.dp).clip(sh).border(if (on) 2.dp else 1.dp, if (on) Cy else Color.White.copy(.15f), sh)
                                                .clickable { sel = a.packageName; sp.edit().putString("sel", sel).apply() }
                                        ) {
                                            Image(ic, null, Modifier.fillMaxSize().blur(22.dp), contentScale = ContentScale.Crop)
                                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.75f)))))
                                            Image(ic, null, Modifier.align(Alignment.Center).size(84.dp).clip(RoundedCornerShape(20.dp)))
                                            Text(pm.getApplicationLabel(a).toString(), Modifier.align(Alignment.BottomStart).padding(16.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                            if (on) Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).size(28.dp).clip(CircleShape).background(Cy), contentAlignment = Alignment.Center) {
                                                Text("✓", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                GCard(hz, Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Stat("NHIỆT ĐỘ", "${b?.t ?: 0f}°C")
                                        Stat("CPU", "${f1((st?.cpu ?: 0) / 1000.0)} GHz")
                                        Stat("PING DNS", if (ping < 0) "-" else "$ping ms")
                                        Stat("PIN", "${b?.lv ?: 0}%")
                                        Spacer(Modifier.weight(1f))
                                        Button({ play() }, colors = ButtonDefaults.buttonColors(containerColor = Cy, contentColor = Color(0xFF06121C))) {
                                            Text("▶  $selName", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            1 -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GCard(hz, Modifier.weight(1.7f).fillMaxHeight()) {
                                    val fpsV = st?.fps ?: 0
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        listOf("CPU" to "${st?.cpuP ?: 0}%", "GPU" to "${st?.gpuP ?: 0}%", "RAM" to "$ram%", "FPS" to (if (fpsV > 0) "$fpsV" else "$refresh Hz")).forEachIndexed { i, (n, v) ->
                                            Column(
                                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.06f))
                                                    .border(if (m == i) 1.dp else 0.dp, Cy, RoundedCornerShape(14.dp)).clickable { m = i }.padding(12.dp)
                                            ) { Lbl(n); Text(v, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Spark(hist[m], if (m == 3) 144 else 100, Modifier.fillMaxWidth().weight(1f))
                                    Row(Modifier.fillMaxWidth()) {
                                        Text("60 GIÂY", fontSize = 10.sp, color = Color.White.copy(.5f), modifier = Modifier.weight(1f))
                                        Text("HIỆN TẠI", fontSize = 10.sp, color = Color.White.copy(.5f))
                                    }
                                }
                                GCard(hz, Modifier.weight(1f).fillMaxHeight()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Dọn RAM", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text("$ram%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button({ thread { msg = Rt.clean() } }, colors = ButtonDefaults.buttonColors(containerColor = Cy, contentColor = Color(0xFF06121C))) { Text("Dọn RAM") }
                                        Spacer(Modifier.width(12.dp))
                                        Text("Đã dùng ${f1(used / 1048576.0)} / ${f1(total / 1048576.0)} GB", fontSize = 12.sp, color = Color.White.copy(.7f))
                                    }
                                    if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp, color = Cy)
                                    Spacer(Modifier.height(12.dp))
                                    Lbl("ỨNG DỤNG NỀN NẶNG")
                                    LazyColumn {
                                        items(procs) { p ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(label(p.pkg), Modifier.weight(1f), maxLines = 1, fontSize = 14.sp)
                                                Text("${p.mb} MB", fontSize = 13.sp)
                                                Spacer(Modifier.width(8.dp))
                                                OutlinedButton({ thread { Rt.sh("am force-stop ${p.pkg}") } }) { Text("Dừng", color = Color(0xFFFF6B6B)) }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GCard(hz, Modifier.weight(1.8f).fillMaxHeight()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.width(64.dp).height(32.dp).border(2.dp, Cy, RoundedCornerShape(8.dp)).padding(3.dp)) {
                                            Box(Modifier.fillMaxHeight().fillMaxWidth((b?.lv ?: 0) / 100f).background(Color(0xFF4ADE80), RoundedCornerShape(5.dp)))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text("${b?.lv ?: 0}", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                                        Text("%", fontSize = 18.sp, color = Cy)
                                        Spacer(Modifier.weight(1f))
                                        Text(if ((b?.plug ?: 0) != 0) "Đang sạc" else "Không sạc", color = Cy)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        listOf(
                                            "NHIỆT ĐỘ" to "${b?.t ?: 0f}°C", "ĐIỆN ÁP" to "${String.format(Locale.US, "%.2f", (b?.mv ?: 0) / 1000.0)} V",
                                            "DÒNG" to "${b?.ma ?: 0} mA", "TÌNH TRẠNG" to (if (b?.health == 2) "Tốt" else "Cần chú ý")
                                        ).forEach { (n, v) ->
                                            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.06f)).padding(12.dp)) { Lbl(n); Text(v, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Lbl("NHIỆT ĐỘ PIN")
                                    Spacer(Modifier.height(8.dp))
                                    val frac = (((b?.t ?: 30f) - 30f) / 20f).coerceIn(.02f, .98f)
                                    Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF2E7D32), Color(0xFFFFB300), Color(0xFFE53935)))))
                                        Row(Modifier.fillMaxWidth()) {
                                            Spacer(Modifier.weight(frac)); Box(Modifier.width(6.dp).height(18.dp).clip(RoundedCornerShape(3.dp)).background(Color.White)); Spacer(Modifier.weight(1f - frac))
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                        listOf("30°", "40°", "45°", "50°").forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, color = Color.White.copy(.55f)) }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        listOf("BAN ĐẦU" to temps.firstOrNull(), "TRUNG BÌNH" to temps.takeIf { it.isNotEmpty() }?.average()?.toFloat(), "CAO NHẤT" to temps.maxOrNull()).forEach { (n, v) ->
                                            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.06f)).padding(10.dp)) {
                                                Text(n, Modifier.weight(1f), fontSize = 11.sp, color = Color.White.copy(.6f)); Text("${v ?: 0f}°", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    GCard(hz, Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) { Text("Tiết kiệm pin", fontWeight = FontWeight.Medium); Text("Giảm độ sáng, giới hạn tần số quét khi chơi.", fontSize = 12.sp, color = Color.White.copy(.65f)) }
                                            Switch(saver, { saver = it; thread { Rt.saver(it) } })
                                        }
                                    }
                                    GCard(hz, Modifier.fillMaxWidth()) {
                                        Text("Sạc", fontWeight = FontWeight.Medium)
                                        Text(
                                            when (b?.plug) { 1 -> "Củ sạc AC, ${b?.ma ?: 0} mA"; 2 -> "Cổng USB, ${b?.ma ?: 0} mA"; 4 -> "Sạc không dây, ${b?.ma ?: 0} mA"; else -> "Không cắm sạc" },
                                            fontSize = 13.sp, color = Color.White.copy(.7f)
                                        )
                                    }
                                }
                            }
                            else -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GCard(hz, Modifier.weight(1.8f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                    Text("Tần số quét", fontWeight = FontWeight.Medium)
                                    Seg(listOf("Mặc định", "60", "90", "120", "144"), g("hz", 0), Modifier.fillMaxWidth()) { s("hz", it) }
                                    Spacer(Modifier.height(12.dp))
                                    Text("Độ bão hòa màu  ${f1(g("sat", 100) / 100.0)}x", fontWeight = FontWeight.Medium)
                                    Slider(g("sat", 100) / 100f, { s("sat", (it * 20).roundToInt() * 5) }, valueRange = 0.8f..2f)
                                    Text("Độ phân giải game  ${g("res", 100)}%  (Android 13+)", fontWeight = FontWeight.Medium)
                                    Slider(g("res", 100).toFloat(), { s("res", (it / 5).roundToInt() * 5) }, valueRange = 50f..100f, steps = 9)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Giới hạn FPS", fontWeight = FontWeight.Medium)
                                    Seg(listOf("30", "45", "60", "90", "120", "Tối đa"), g("fps", 5), Modifier.fillMaxWidth()) { s("fps", it) }
                                }
                                GCard(hz, Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                    Text("Chế độ game", fontWeight = FontWeight.Medium)
                                    Seg(listOf("Tiêu chuẩn", "Hiệu năng", "Tiết kiệm"), g("gm", 0), Modifier.fillMaxWidth()) { s("gm", it) }
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) { Text("Hiệu năng cố định"); Text("Khóa xung nhịp cho ổn định", fontSize = 12.sp, color = Color.White.copy(.65f)) }
                                        Switch(fixed, { fixed = it; thread { Rt.fixed(it) } })
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Chặn thông báo", Modifier.weight(1f)); Switch(dnd, { dnd = it; sp.edit().putBoolean("dnd", it).apply() }) }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Dọn RAM khi mở game", Modifier.weight(1f)); Switch(cln, { cln = it; sp.edit().putBoolean("cln", it).apply() }) }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Bảng điều khiển trong game", Modifier.weight(1f))
                                        Switch(ov, { ov = it; sp.edit().putBoolean("ov", it).apply(); if (!it) ctx.stopService(Intent(ctx, Overlay::class.java)) })
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text("Mọi cài đặt được khôi phục khi bạn thoát game (nút ✕).", fontSize = 12.sp, color = Color.White.copy(.55f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
