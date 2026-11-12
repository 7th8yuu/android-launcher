package com.example.mylauncher

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MenuItem(val label: String, val destructive: Boolean = false, val action: () -> Unit)
class MenuSpec(
    val anchor: Rect,
    val title: String?,
    val items: List<MenuItem>,
    val preview: AppEntry? = null
)

@Stable
class LauncherState(val act: MainActivity) {
    val store = Store(act)
    val icons = IconProvider(act, store)
    lateinit var uiScope: CoroutineScope

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var byKey by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set

    // ---- UI state ----
    var drawer by mutableFloatStateOf(0f)
    var query by mutableStateOf("")
    var openCat by mutableStateOf<Cat?>(null)
    var libraryMode by mutableStateOf(true)
    var menu by mutableStateOf<MenuSpec?>(null)
    var showSettings by mutableStateOf(false)
    var showWidgets by mutableStateOf(false)
    var launching by mutableStateOf(false)
    var homeSignal by mutableIntStateOf(0)
    var pageRequest by mutableIntStateOf(-1)
    var currentPage by mutableIntStateOf(0)
    var editMode by mutableStateOf(false)
    var permTick by mutableIntStateOf(0)

    // ---- Spotlight / AI ----
    var spot by mutableFloatStateOf(0f)
    var spotQuery by mutableStateOf("")
    var pillBounds by mutableStateOf(Rect.Zero)
    var hits by mutableStateOf<List<Hit>>(emptyList())
    var aiBusy by mutableStateOf(false)
    var aiText by mutableStateOf("")
    var aiError by mutableStateOf<String?>(null)
    var aiActions by mutableStateOf<List<AiAction>>(emptyList())

    // ---- Hiệu ứng mở / đóng ứng dụng ----
    var ghostApp by mutableStateOf<AppEntry?>(null)
    var ghostFrom by mutableStateOf(Rect.Zero)
    val ghostP = Animatable(0f)

    private var drawerJob: Job? = null
    private var spotJob: Job? = null

    private val seedPkgs = listOf(
        "com.android.settings", "com.android.vending", "com.google.android.apps.photos",
        "com.google.android.youtube", "com.android.chrome", "com.google.android.gm",
        "com.google.android.apps.maps", "com.google.android.calendar",
        "com.google.android.deskclock", "com.google.android.calculator",
        "com.android.camera", "com.google.android.apps.messaging",
        "com.sec.android.app.camera", "com.sec.android.gallery3d", "com.miui.gallery"
    )

    fun toast(msg: String) = Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()

    // ---------- Ứng dụng ----------
    fun reloadApps() {
        act.lifecycleScope.launch {
            val list = withContext(Dispatchers.Default) { AppRepo.load(act) }
            apps = list
            byKey = list.associateBy { it.key }
            seedIfNeeded(list)
        }
    }

    private fun seedIfNeeded(list: List<AppEntry>) {
        if (store.seeded() || list.isEmpty()) return
        val pm = act.packageManager
        fun pkgOf(i: Intent): String? =
            pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val dockPkgs = listOfNotNull(
            pkgOf(Intent(Intent.ACTION_DIAL)),
            pkgOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)),
            pkgOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            pkgOf(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        ).distinct()
        val dockKeys = dockPkgs.mapNotNull { p -> list.firstOrNull { it.pkg == p }?.key }
        val pageKeys = LinkedHashSet<String>()
        seedPkgs.forEach { p ->
            val a = list.firstOrNull { it.pkg == p && it.key !in dockKeys }
            if (a != null) pageKeys.add(a.key)
        }
        for (a in list) {
            if (pageKeys.size >= 8) break
            if (a.key !in dockKeys) pageKeys.add(a.key)
        }
        store.commitDock(dockKeys)
        store.commitPages(listOf(Page(apps = pageKeys.take(8))))
    }

    fun catOf(app: AppEntry): Cat {
        val o = store.catOverride[app.key] ?: return app.autoCat
        return try { Cat.valueOf(o) } catch (e: Exception) { app.autoCat }
    }

    fun setCategory(app: AppEntry, cat: Cat?) {
        val m = store.catOverride.toMutableMap()
        if (cat == null) m.remove(app.key) else m[app.key] = cat.name
        store.commitCat(m)
    }

    fun suggestions(): List<AppEntry> =
        store.launchCounts.entries
            .sortedByDescending { it.value }
            .mapNotNull { byKey[it.key] }
            .filter { it.key !in store.hidden }
            .take(4)

    // ---------- Mở / đóng ứng dụng kiểu iOS ----------
    fun launch(app: AppEntry, r: Rect?) {
        store.bumpLaunch(app.key)
        drawerJob?.cancel()
        spotJob?.cancel()
        drawer = 0f; spot = 0f
        query = ""; spotQuery = ""
        openCat = null; menu = null
        if (r == null || r.width <= 0f) {
            startApp(app)
            return
        }
        ghostApp = app
        ghostFrom = r
        launching = true
        uiScope.launch {
            val anim = this.launch { ghostP.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 300f)) }
            delay(160)
            if (!startApp(app)) {
                anim.cancel()
                ghostP.snapTo(0f)
                ghostApp = null
                launching = false
            } else {
                // Phòng khi ứng dụng không che launcher (cửa sổ nổi...) thì thu hiệu ứng lại
                delay(2500)
                if (ghostApp != null && act.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onReturn()
            }
        }
    }

    private fun startApp(app: AppEntry): Boolean {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            val opts = ActivityOptions.makeCustomAnimation(act, android.R.anim.fade_in, android.R.anim.fade_out)
            act.startActivity(i, opts.toBundle())
            true
        } catch (e: Exception) {
            toast("Không mở được ứng dụng")
            false
        }
    }

    /** Gọi khi launcher hiện lại: icon thu về chỗ cũ như iOS. */
    fun onReturn() {
        launching = false
        if (ghostApp != null) {
            uiScope.launch {
                ghostP.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 320f))
                ghostApp = null
            }
        }
    }

    // ---------- Trang chủ / dock ----------
    private fun used(p: Page) = p.apps.size + p.widgets.size * store.columns.value * 2

    fun addToHome(app: AppEntry) {
        if (store.pages.any { app.key in it.apps }) { toast("Đã có trên màn hình chính"); return }
        val pages = store.pages.toMutableList()
        var idx = currentPage.coerceIn(0, pages.lastIndex)
        val reserved = if (idx == 0) store.columns.value * 3 else 0
        if (used(pages[idx]) + reserved >= store.columns.value * 6) {
            pages.add(Page())
            idx = pages.lastIndex
        }
        pages[idx] = pages[idx].copy(apps = pages[idx].apps + app.key)
        store.commitPages(pages)
        toast("Đã thêm vào màn hình chính")
    }

    fun removeFromPage(page: Int, key: String) {
        val pages = store.pages.toMutableList()
        if (page !in pages.indices) return
        pages[page] = pages[page].copy(apps = pages[page].apps - key)
        store.commitPages(pages)
    }

    fun movePage(page: Int, key: String, delta: Int) {
        val pages = store.pages.toMutableList()
        val to = page + delta
        if (page !in pages.indices || to !in pages.indices) return
        pages[page] = pages[page].copy(apps = pages[page].apps - key)
        pages[to] = pages[to].copy(apps = pages[to].apps + key)
        store.commitPages(pages)
        pageRequest = to
    }

    /** Kéo thả sắp xếp trong chế độ chỉnh sửa. */
    fun dragReorder(page: Int, d: DragCtx) {
        val k = d.key ?: return
        val target = d.rects.entries.firstOrNull { it.key != k && it.value.contains(d.pos) }?.key ?: return
        val pages = store.pages.toMutableList()
        val list = pages.getOrNull(page)?.apps?.toMutableList() ?: return
        val from = list.indexOf(k)
        val to = list.indexOf(target)
        if (from < 0 || to < 0) return
        list.removeAt(from)
        list.add(to, k)
        pages[page] = pages[page].copy(apps = list)
        store.commitPages(pages)
    }

    fun addToDock(app: AppEntry) {
        if (app.key in store.dock) { toast("Đã có trong dock"); return }
        if (store.dock.size >= 5) { toast("Dock đã đầy (tối đa 5)"); return }
        store.commitDock(store.dock + app.key)
    }

    fun removeFromDock(key: String) = store.commitDock(store.dock - key)

    fun hide(app: AppEntry) {
        store.commitHidden(store.hidden + app.key)
        store.commitDock(store.dock - app.key)
        store.commitPages(store.pages.map { it.copy(apps = it.apps - app.key) })
        toast("Đã ẩn ${app.label} (bỏ ẩn trong Cài đặt)")
    }

    fun unhide(key: String) = store.commitHidden(store.hidden - key)

    fun addPage() {
        store.commitPages(store.pages + Page())
        pageRequest = store.pages.lastIndex
    }

    fun removePage(index: Int) {
        val pages = store.pages.toMutableList()
        if (pages.size <= 1 || index !in pages.indices) return
        pages[index].widgets.forEach { act.widgetHost.deleteAppWidgetId(it) }
        pages.removeAt(index)
        store.commitPages(pages)
    }

    fun addWidget(page: Int, id: Int) {
        val pages = store.pages.toMutableList()
        val idx = page.coerceIn(0, pages.lastIndex)
        pages[idx] = pages[idx].copy(widgets = pages[idx].widgets + id)
        store.commitPages(pages)
    }

    fun removeWidget(page: Int, id: Int) {
        act.widgetHost.deleteAppWidgetId(id)
        val pages = store.pages.toMutableList()
        if (page !in pages.indices) return
        pages[page] = pages[page].copy(widgets = pages[page].widgets - id)
        store.commitPages(pages)
    }

    // ---------- Ngăn kéo (thư viện ứng dụng) ----------
    fun dragDrawer(deltaUpPx: Float, screenH: Float) {
        drawerJob?.cancel()
        drawer = (drawer + deltaUpPx / (0.5f * screenH)).coerceIn(0f, 1f)
    }

    fun settleDrawer(velocityY: Float) {
        val target = when {
            velocityY < -900f -> 1f
            velocityY > 900f -> 0f
            drawer > 0.5f -> 1f
            else -> 0f
        }
        animateDrawer(target)
    }

    fun animateDrawer(target: Float) {
        drawerJob?.cancel()
        drawerJob = uiScope.launch {
            animate(
                initialValue = drawer,
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
            ) { v, _ -> drawer = v.coerceIn(0f, 1f) }
            if (target == 0f) {
                query = ""
                openCat = null
            }
        }
    }

    // ---------- Spotlight ----------
    fun openSpotlight() {
        spotQuery = ""
        clearAi()
        animateSpot(1f)
    }

    fun closeSpotlight() = animateSpot(0f)

    fun beginSpotDrag() {
        spotJob?.cancel()
        spotQuery = ""
        clearAi()
    }

    fun dragSpot(deltaDownPx: Float, screenH: Float) {
        spotJob?.cancel()
        spot = (spot + deltaDownPx / (0.3f * screenH)).coerceIn(0f, 1f)
    }

    fun settleSpot(velocityY: Float) {
        val target = when {
            velocityY > 900f -> 1f
            velocityY < -900f -> 0f
            spot > 0.5f -> 1f
            else -> 0f
        }
        animateSpot(target)
    }

    private fun animateSpot(target: Float) {
        spotJob?.cancel()
        spotJob = uiScope.launch {
            animate(
                initialValue = spot,
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            ) { v, _ -> spot = v.coerceIn(0f, 1f) }
            if (target == 0f) {
                spotQuery = ""
                hits = emptyList()
                clearAi()
            }
        }
    }

    fun onSpotQuery(v: String) {
        spotQuery = v
        clearAi()
    }

    private fun clearAi() {
        aiBusy = false
        aiText = ""
        aiError = null
        aiActions = emptyList()
    }

    suspend fun runSearch() {
        val q = spotQuery.trim()
        if (q.isEmpty()) { hits = emptyList(); return }
        val visible = apps.filter { it.key !in store.hidden }
        val counts = store.launchCounts
        val local = withContext(Dispatchers.Default) {
            val out = ArrayList<Hit>()
            Calculator.evaluate(q)?.let { out.add(Hit.Calc(q, it)) }
            visible.filter { it.label.contains(q, ignoreCase = true) }
                .sortedWith(
                    compareByDescending<AppEntry> { it.label.startsWith(q, ignoreCase = true) }
                        .thenByDescending { counts[it.key] ?: 0 }
                )
                .take(6)
                .forEach { out.add(Hit.AppHit(it)) }
            if (q.length >= 2) {
                val ql = q.lowercase()
                Sys.settingEntries
                    .filter { "${it.title} ${it.words}".lowercase().contains(ql) }
                    .take(3)
                    .forEach { out.add(Hit.SettingHit(it)) }
            }
            out
        }
        val contacts = if (q.length >= 2) withContext(Dispatchers.IO) { ContactSearch.contacts(act, q) } else emptyList()
        hits = local + contacts
    }

    fun askAi(q: String) {
        val key = store.aiKey.value.trim()
        if (key.isEmpty()) { toast("Chưa có khoá API — thêm trong Cài đặt → AI"); return }
        aiBusy = true
        aiText = ""
        aiError = null
        aiActions = emptyList()
        act.lifecycleScope.launch {
            val labels = apps.map { it.label }.distinct().take(200).joinToString(", ")
            val r = Ai.ask(key, store.aiModel.value.trim(), Ai.system(labels), q)
            aiBusy = false
            val t = r.text
            if (t != null) {
                val (body, acts) = Ai.parse(t)
                aiText = body.ifEmpty { if (acts.isEmpty()) t else "" }
                aiActions = acts
            } else {
                aiError = r.error ?: "Lỗi không xác định"
            }
        }
    }

    fun runAiAction(a: AiAction) {
        when (a) {
            is AiAction.Open -> {
                val app = apps.firstOrNull { it.label.equals(a.label, ignoreCase = true) }
                    ?: apps.firstOrNull { it.label.contains(a.label, ignoreCase = true) }
                if (app != null) launch(app, null) else toast("Không tìm thấy “${a.label}”")
            }
            is AiAction.Web -> act.openUrl(a.url)
            is AiAction.Alarm -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, a.h)
                    .putExtra(AlarmClock.EXTRA_MINUTES, a.m)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "My Launcher")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                act.safeStart(i, "Không đặt được báo thức")
                toast("Đã đặt báo thức %02d:%02d".format(a.h, a.m))
            }
            is AiAction.Timer -> {
                val i = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, a.sec)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                act.safeStart(i, "Không hẹn giờ được")
            }
            is AiAction.Setting -> {
                val e = Sys.settingEntries.firstOrNull { it.key == a.key }
                if (e != null) act.safeStart(Intent(e.action), "Không mở được cài đặt")
            }
        }
    }

    fun testAi() {
        val key = store.aiKey.value.trim()
        if (key.isEmpty()) { toast("Hãy nhập khoá API trước"); return }
        toast("Đang kiểm tra…")
        act.lifecycleScope.launch {
            val r = Ai.ask(key, store.aiModel.value.trim(), "Trả lời đúng một từ.", "Nói OK", 20)
            toast(if (r.text != null) "Kết nối thành công ✓" else (r.error ?: "Thất bại"))
        }
    }

    /** Nhờ AI phân loại các ứng dụng vào Trò chơi / Mạng xã hội / Giải trí / Tiện ích / Khác. */
    fun aiCategorize() {
        val key = store.aiKey.value.trim()
        if (key.isEmpty()) { toast("Hãy nhập khoá API trước"); return }
        toast("AI đang phân loại ứng dụng…")
        act.lifecycleScope.launch {
            val list = apps.distinctBy { it.pkg }.take(250)
            val user = list.joinToString("\n") { "${it.pkg} | ${it.label}" }
            val r = Ai.ask(key, store.aiModel.value.trim(), Ai.CATEGORIZE_SYSTEM, user, 4000)
            val json = r.text?.let { Ai.extractJson(it) }
            if (json == null) {
                toast(r.error ?: "AI trả về định dạng không đọc được")
                return@launch
            }
            val m = store.catOverride.toMutableMap()
            var n = 0
            for (a in apps) {
                val name = json.optString(a.pkg, "")
                if (name.isNotEmpty() && Cat.values().any { it.name == name }) { m[a.key] = name; n++ }
            }
            store.commitCat(m)
            toast("Đã phân loại $n ứng dụng")
        }
    }

    // ---------- Điều hướng ----------
    fun onHomePressed() {
        menu = null
        showSettings = false
        showWidgets = false
        openCat = null
        editMode = false
        if (drawer > 0f) animateDrawer(0f)
        if (spot > 0f) closeSpotlight()
        homeSignal++
    }

    fun onBack() {
        when {
            menu != null -> menu = null
            showWidgets -> showWidgets = false
            showSettings -> showSettings = false
            spot > 0f -> if (spotQuery.isNotEmpty()) onSpotQuery("") else closeSpotlight()
            openCat != null -> openCat = null
            drawer > 0f -> if (query.isNotEmpty()) query = "" else animateDrawer(0f)
            editMode -> editMode = false
            else -> homeSignal++
        }
    }
}
