package com.example.mylauncher

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MenuItem(val label: String, val destructive: Boolean = false, val action: () -> Unit)
class MenuSpec(val anchor: Rect, val title: String?, val items: List<MenuItem>)

@Stable
class LauncherState(val act: MainActivity) {
    val store = Store(act)
    val icons = IconProvider(act, store)
    lateinit var uiScope: CoroutineScope

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var byKey by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set

    // UI state
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

    private var drawerJob: Job? = null

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

    fun launch(app: AppEntry, r: Rect?) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            val opts = if (r != null && r.width > 0f) {
                ActivityOptions.makeScaleUpAnimation(
                    act.window.decorView, r.left.toInt(), r.top.toInt(), r.width.toInt(), r.height.toInt()
                )
            } else null
            launching = true
            act.startActivity(i, opts?.toBundle())
            drawerJob?.cancel()
            drawer = 0f
            query = ""
            openCat = null
            menu = null
        } catch (e: Exception) {
            launching = false
            toast("Không mở được ứng dụng")
        }
    }

    // ---------- Trang chủ / dock ----------
    private fun used(p: Page) = p.apps.size + p.widgets.size * store.columns.value * 2

    fun addToHome(app: AppEntry) {
        if (store.pages.any { app.key in it.apps }) { toast("Đã có trên màn hình chính"); return }
        val pages = store.pages.toMutableList()
        var idx = currentPage.coerceIn(0, pages.lastIndex)
        if (used(pages[idx]) >= store.columns.value * 5) {
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

    // ---------- Ngăn kéo ----------
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

    // ---------- Điều hướng ----------
    fun onHomePressed() {
        menu = null
        showSettings = false
        showWidgets = false
        openCat = null
        if (drawer > 0f) animateDrawer(0f)
        homeSignal++
    }

    fun onBack() {
        when {
            menu != null -> menu = null
            showWidgets -> showWidgets = false
            showSettings -> showSettings = false
            openCat != null -> openCat = null
            drawer > 0f -> if (query.isNotEmpty()) query = "" else animateDrawer(0f)
            else -> homeSignal++
        }
    }
}
