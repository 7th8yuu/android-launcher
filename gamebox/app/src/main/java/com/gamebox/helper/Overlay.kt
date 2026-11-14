package com.gamebox.helper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

class Chart(c: Context) : View(c) {
    private val d = ArrayDeque<Int>()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; style = Paint.Style.STROKE; color = Color.rgb(125, 211, 252) }
    private val grid = Paint().apply { strokeWidth = 2f; color = Color.argb(70, 255, 255, 255) }
    fun add(v: Int) { d.addLast(v); if (d.size > 60) d.removeFirst(); invalidate() }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawLine(0f, h / 2, w, h / 2, grid) // mốc 60 FPS
        if (d.size < 2) return
        val p = Path()
        d.forEachIndexed { i, v ->
            val x = w * i / 59; val y = h - h * v.coerceIn(0, 120) / 120f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        c.drawPath(p, line)
    }
}

// Nút lục giác kiểu bảng điều khiển trong game
class Hex(c: Context, val ic: String, var lb: String, val tap: (Hex) -> Unit) : View(c) {
    var on = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    init { setOnClickListener { tap(this) } }
    override fun onMeasure(w: Int, h: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension((70 * d).toInt(), (80 * d).toInt())
    }
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f; val cy = 28 * d; val r = 25 * d
        val path = Path()
        for (i in 0 until 6) {
            val a = Math.toRadians(60.0 * i - 90)
            val x = (cx + r * Math.cos(a)).toFloat(); val y = (cy + r * Math.sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.style = Paint.Style.FILL; p.color = if (on) Color.argb(235, 110, 200, 250) else Color.argb(90, 20, 50, 80); c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = Color.argb(210, 125, 211, 252); c.drawPath(path, p)
        t.textSize = 20 * d; t.color = Color.WHITE; c.drawText(ic, cx, cy + 7 * d, t)
        t.textSize = 10 * d; t.color = Color.WHITE; c.drawText(lb, cx, 74 * d, t)
    }
}

class Overlay : Service() {
    private lateinit var wm: WindowManager
    private lateinit var box: LinearLayout
    @Volatile private var run = true

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("o", "Overlay", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "o").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Game Box").build())
        wm = getSystemService(WindowManager::class.java)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun bg() = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.argb(205, 16, 44, 72), Color.argb(185, 8, 18, 32))).apply {
            cornerRadius = 22 * d; setStroke(dp(1), Color.argb(150, 125, 211, 252))
        }
        val info = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f; typeface = Typeface.MONOSPACE }
        val ch = Chart(this)
        val grid = android.widget.GridLayout(this).apply { columnCount = 4 }
        val hzs = intArrayOf(60, 90, 120); var hi = 0
        fun hex(ic: String, lb: String, tap: (Hex) -> Unit) { grid.addView(Hex(this, ic, lb, tap)) }
        hex("🧹", "Dọn RAM") { thread { Rt.clean() } }
        hex("🔕", "Chặn noti") { it.on = !it.on; it.invalidate(); val v = it.on; thread { Rt.dnd(v) } }
        hex("🔄", "Quét 60Hz") {
            hi = (hi + 1) % 3; it.lb = "Quét ${hzs[hi]}Hz"; it.on = true; it.invalidate()
            val v = hzs[hi]; thread { Rt.sh("settings put system peak_refresh_rate $v", "settings put system min_refresh_rate $v") }
        }
        hex("📸", "Chụp MH") { thread { Rt.sh("screencap -p /sdcard/Pictures/GameBox_${System.currentTimeMillis()}.png") } }
        hex("🚀", "Turbo") { it.on = !it.on; it.invalidate(); val v = it.on; thread { Rt.mode(if (v) 1 else 0) } }
        hex("👆", "Hiện chạm") { it.on = !it.on; it.invalidate(); val v = if (it.on) 1 else 0; thread { Rt.sh("settings put system show_touches $v") } }
        hex("🏠", "Về app") { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        hex("✕", "Thoát") { val p = Rt.pkg; Rt.pkg = ""; thread { Rt.restore(p); Rt.mode(0); Rt.dnd(false) }; stopSelf() }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)); background = bg(); visibility = View.GONE
            addView(info); addView(ch, LinearLayout.LayoutParams(dp(280), dp(40))); addView(grid)
        }
        val pill = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 12f; setPadding(dp(14), dp(6), dp(14), dp(6)); background = bg()
            setOnClickListener { panel.visibility = if (panel.visibility == View.GONE) View.VISIBLE else View.GONE }
        }
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; addView(pill); addView(panel) }
        val lp = WindowManager.LayoutParams(
            -2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(6) }
        wm.addView(box, lp)
        thread {
            val fmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            while (run) {
                val s = Rt.stats(); val (t, a) = Rt.mem(); val u = 100 - (a * 100 / t).toInt()
                box.post {
                    pill.text = "FPS ${s.fps}  ·  RAM $u%  ▾"
                    info.text = "${fmt.format(java.util.Date())}\nFPS ${s.fps}   RAM $u%   CPU ${s.cpuP}%\nCPU ${s.cpu}MHz ${s.cg}\nGPU ${s.gpu}MHz ${s.gg}"
                    ch.add(s.fps)
                }
                Thread.sleep(1000)
            }
        }
    }

    override fun onDestroy() { run = false; runCatching { wm.removeView(box) } }
}
