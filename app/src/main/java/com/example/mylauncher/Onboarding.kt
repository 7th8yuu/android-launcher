package com.example.mylauncher

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Ứng dụng khởi động (icon "My Launcher" ngoài màn hình):
 * Xin chào → Xin quyền → Chọn launcher mặc định → Vào launcher.
 */
class OnboardingActivity : ComponentActivity() {

    private var tick by mutableIntStateOf(0)
    private lateinit var store: Store

    private val reqContacts = registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    private val reqRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        store = Store(this)
        val restart = intent.getBooleanExtra(EXTRA_RESTART, false)
        val start = if (!restart && store.onboarded.value && Sys.isDefaultLauncher(this)) 3 else 0
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                OnboardingScreen(
                    startPage = start,
                    tick = tick,
                    store = store,
                    onContacts = { reqContacts.launch(android.Manifest.permission.READ_CONTACTS) },
                    onListener = { openSafe(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onAccess = { openSafe(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onDefault = { requestDefaultLauncher() },
                    onEnter = { enterLauncher() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }

    private fun openSafe(i: Intent) {
        try { startActivity(i) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun requestDefaultLauncher() {
        if (Sys.isDefaultLauncher(this)) { tick++; return }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    reqRole.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            openSafe(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun enterLauncher() {
        store.onboarded.value = true
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    companion object {
        const val EXTRA_RESTART = "restart"
    }
}

private val greetings = listOf(
    "Xin chào", "Hello", "你好", "こんにちは", "안녕하세요", "Bonjour", "Hola", "Привет", "Ciao", "สวัสดี"
)

@Composable
private fun OnboardingScreen(
    startPage: Int,
    tick: Int,
    store: Store,
    onContacts: () -> Unit,
    onListener: () -> Unit,
    onAccess: () -> Unit,
    onDefault: () -> Unit,
    onEnter: () -> Unit
) {
    val ctx = LocalContext.current
    var page by remember { mutableIntStateOf(startPage) }
    val isDefault = remember(tick) { Sys.isDefaultLauncher(ctx) }

    // Tự chuyển sang bước cuối khi đã đặt làm launcher mặc định
    LaunchedEffect(isDefault, page) {
        if (page == 2 && isDefault) {
            delay(900)
            page = 3
        }
    }
    BackHandler(enabled = page > 0) { page -= 1 }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .height(6.dp)
                            .width(if (i == page) 22.dp else 6.dp)
                            .background(Color.White.copy(alpha = if (i <= page) 0.95f else 0.3f), CircleShape)
                    )
                }
            }
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween<IntOffset>(450, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(450))) togetherWith
                            (slideOutHorizontally(tween<IntOffset>(450)) { -it / 3 } + fadeOut(tween(300)))
                    } else {
                        (slideInHorizontally(tween<IntOffset>(450, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(450))) togetherWith
                            (slideOutHorizontally(tween<IntOffset>(450)) { it / 3 } + fadeOut(tween(300)))
                    }
                },
                label = "onb"
            ) { p ->
                when (p) {
                    0 -> HelloPage { page = 1 }
                    1 -> PermsPage(tick, store, onContacts, onListener, onAccess) { page = 2 }
                    2 -> DefaultPage(isDefault, onDefault) { page = 3 }
                    else -> DonePage(isDefault, onEnter)
                }
            }
        }
    }
}

@Composable
private fun AuroraBackground() {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "a"
    )
    val b by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse), label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF06060F), Color(0xFF10102A))))
        fun blob(c: Color, cx: Float, cy: Float, r: Float) {
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r, center = Offset(cx, cy)
            )
        }
        blob(Color(0xFF7C4DFF), size.width * (0.15f + 0.6f * a), size.height * (0.15f + 0.2f * b), size.width * 0.85f)
        blob(Color(0xFF00B0FF), size.width * (0.9f - 0.6f * b), size.height * (0.55f + 0.2f * a), size.width * 0.8f)
        blob(Color(0xFFFF4081), size.width * (0.3f + 0.4f * b), size.height * (0.95f - 0.25f * a), size.width * 0.7f)
    }
}

@Composable
private fun CtaButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF10102A))
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun HelloPage(onNext: () -> Unit) {
    var gi by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1700)
            gi = (gi + 1) % greetings.size
        }
    }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = gi,
            transitionSpec = {
                (fadeIn(tween(500)) + slideInVertically(tween<IntOffset>(500)) { it / 3 }) togetherWith
                    (fadeOut(tween(350)) + slideOutVertically(tween<IntOffset>(350)) { -it / 3 })
            },
            label = "hello"
        ) { i ->
            Text(greetings[i], color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Chào mừng đến với My Launcher",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 18.sp,
            modifier = Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30.dp.toPx() }
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 50.dp.toPx() }) {
            CtaButton("Bắt đầu", onNext)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PermRow(title: String, desc: String, granted: Boolean, action: String, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), 0.6f)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Text("✓ Đã cấp", color = Color(0xFF8CE99A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Button(onClick = onAction, shape = RoundedCornerShape(20.dp)) { Text(action) }
        }
    }
}

@Composable
private fun PermsPage(
    tick: Int,
    store: Store,
    onContacts: () -> Unit,
    onListener: () -> Unit,
    onAccess: () -> Unit,
    onNext: () -> Unit
) {
    val ctx = LocalContext.current
    val contacts = remember(tick) { Sys.hasContacts(ctx) }
    val listener = remember(tick) { Sys.isListenerEnabled(ctx) }
    val access = remember(tick) { Sys.isAccessibilityEnabled(ctx) }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        Text("Cấp một vài quyền", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Tất cả đều không bắt buộc — bạn có thể bỏ qua và bật sau trong Cài đặt.",
            color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermRow("Danh bạ", "Tìm người liên hệ ngay trong ô tìm kiếm.", contacts, "Cấp", onContacts)
            PermRow("Truy cập thông báo", "Hiện chấm đỏ trên icon. Nội dung thông báo không bị đọc hay lưu.", listener, "Bật", onListener)
            PermRow("Trợ năng", "Chạm 2 lần để tắt màn hình. Chỉ dùng để khoá màn hình.", access, "Bật", onAccess)
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), 0.6f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Trợ lý AI (tuỳ chọn)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Dán khoá API Anthropic để hỏi AI trong ô tìm kiếm. Khi dùng, câu hỏi và tên các ứng dụng đã cài sẽ được gửi tới api.anthropic.com. Khoá chỉ lưu trên máy này.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                )
                GlassField(store.aiKey.value, { store.aiKey.value = it }, "sk-ant-…", password = true)
            }
        }
        Spacer(Modifier.height(12.dp))
        CtaButton("Tiếp tục", onNext)
    }
}

@Composable
private fun DefaultPage(isDefault: Boolean, onDefault: () -> Unit, onSkip: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val k by pulse.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k"
    )
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(120.dp)
                .graphicsLayer { scaleX = k; scaleY = k }
                .glass(RoundedCornerShape(32.dp), 0.8f),
            contentAlignment = Alignment.Center
        ) { Text(if (isDefault) "✓" else "⌂", color = Color.White, fontSize = 54.sp) }
        Spacer(Modifier.height(28.dp))
        Text(
            if (isDefault) "Đã là launcher mặc định" else "Chọn My Launcher làm màn hình chính",
            color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Text(
            if (isDefault) "Tuyệt! Đang chuyển tiếp…"
            else "Android sẽ hiện hộp thoại chọn ứng dụng màn hình chính. Hãy chọn “My Launcher” rồi bấm “Luôn luôn”.",
            color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp)
        )
        Spacer(Modifier.weight(1f))
        if (!isDefault) {
            CtaButton("Đặt làm launcher mặc định", onDefault)
            TextButton(onClick = onSkip) { Text("Để sau", color = Color.White.copy(alpha = 0.8f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DonePage(isDefault: Boolean, onEnter: () -> Unit) {
    val prog = remember { Animatable(0f) }
    LaunchedEffect(Unit) { prog.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(140.dp)) {
            val w = size.width
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val ring = (prog.value * 1.5f).coerceIn(0f, 1f)
            drawCircle(Color.White.copy(alpha = 0.10f * ring), radius = w / 2f)
            drawArc(
                Color(0xFF34C759), -90f, 360f * ring, false,
                Offset(stroke.width / 2, stroke.width / 2),
                Size(w - stroke.width, w - stroke.width), style = stroke
            )
            val path = Path().apply {
                moveTo(w * 0.29f, w * 0.52f)
                lineTo(w * 0.45f, w * 0.68f)
                lineTo(w * 0.73f, w * 0.36f)
            }
            val pm = PathMeasure()
            pm.setPath(path, false)
            val dst = Path()
            pm.getSegment(0f, pm.length * ((prog.value - 0.45f) / 0.55f).coerceIn(0f, 1f), dst, true)
            drawPath(dst, Color.White, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.height(28.dp))
        Text("Mọi thứ đã sẵn sàng", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            if (isDefault) "My Launcher là màn hình chính của bạn."
            else "Bạn có thể đặt làm launcher mặc định sau trong Cài đặt launcher.",
            color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
        )
        Text(
            "Mẹo: vuốt xuống để tìm kiếm / hỏi AI, vuốt lên để mở thư viện ứng dụng, giữ lâu để chỉnh sửa.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp)
        )
        Spacer(Modifier.weight(1f))
        CtaButton("Vào launcher", onEnter)
        Spacer(Modifier.height(8.dp))
    }
}
