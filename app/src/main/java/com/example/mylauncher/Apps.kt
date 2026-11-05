package com.example.mylauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

enum class Cat(val title: String) {
    GAMES("Trò chơi"),
    SOCIAL("Mạng xã hội"),
    MEDIA("Giải trí"),
    TOOLS("Tiện ích"),
    OTHER("Khác")
}

data class AppEntry(
    val key: String,
    val pkg: String,
    val label: String,
    val component: ComponentName,
    val autoCat: Cat
)

data class IconPack(val pkg: String, val label: String)

object AppRepo {
    private val socialHints = listOf(
        "facebook", "instagram", "tiktok", "zing.zalo", "zalo", "telegram", "whatsapp",
        "com.twitter", "twitter", "discord", "snapchat", "viber", "reddit", "linkedin",
        "pinterest", "jp.naver.line", "tumblr", "thoughtcrime.securesms", "tencent.mm",
        "kakao", "skype", "weibo", "bereal", "mastodon", "bluesky", "threads"
    )

    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (ri in pm.queryIntentActivities(q, 0)) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == ctx.packageName) continue
            val cn = ComponentName(ai.packageName, ai.name)
            val key = cn.flattenToString()
            if (!seen.add(key)) continue
            out.add(
                AppEntry(
                    key = key,
                    pkg = ai.packageName,
                    label = ri.loadLabel(pm).toString(),
                    component = cn,
                    autoCat = detect(ai.applicationInfo)
                )
            )
        }
        return out.sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun detect(ai: ApplicationInfo?): Cat {
        if (ai == null) return Cat.OTHER
        val p = ai.packageName.lowercase()
        if (socialHints.any { p.contains(it) }) return Cat.SOCIAL
        if ((ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0) return Cat.GAMES
        return when (ai.category) {
            ApplicationInfo.CATEGORY_GAME -> Cat.GAMES
            ApplicationInfo.CATEGORY_SOCIAL -> Cat.SOCIAL
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_NEWS -> Cat.MEDIA
            ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ApplicationInfo.CATEGORY_MAPS -> Cat.TOOLS
            else -> if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) Cat.TOOLS else Cat.OTHER
        }
    }
}

/** Tải biểu tượng: ảnh tuỳ chọn > bộ biểu tượng (ADW/Nova/Go) > biểu tượng gốc. */
class IconProvider(private val ctx: Context, private val store: Store) {
    private val pm = ctx.packageManager
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val iconDir = File(ctx.filesDir, "icons").apply { mkdirs() }

    @Volatile private var exact: Map<String, String> = emptyMap()
    @Volatile private var byPkg: Map<String, String> = emptyMap()
    @Volatile private var packRes: Resources? = null
    @Volatile private var packName: String = ""
    @Volatile private var loadedFor: String? = null

    fun peek(app: AppEntry): ImageBitmap? = cache[app.key]

    fun invalidate() {
        cache.clear()
        loadedFor = null
    }

    private fun customFile(key: String) = File(iconDir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png")

    fun hasCustom(key: String) = customFile(key).exists()

    fun removeCustom(key: String) {
        customFile(key).delete()
        cache.remove(key)
    }

    fun clearAllCustom() {
        iconDir.listFiles()?.forEach { it.delete() }
        cache.clear()
    }

    fun get(app: AppEntry): ImageBitmap {
        ensurePack()
        return cache.getOrPut(app.key) { load(app) }
    }

    private fun load(app: AppEntry): ImageBitmap {
        val f = customFile(app.key)
        if (f.exists()) {
            BitmapFactory.decodeFile(f.path)?.let { return it.asImageBitmap() }
        }
        packDrawable(app)?.let { d ->
            try { return d.toBitmap(192, 192).asImageBitmap() } catch (_: Exception) {}
        }
        val d: Drawable = try {
            pm.getActivityIcon(app.component)
        } catch (_: Exception) {
            pm.defaultActivityIcon
        }
        return d.toBitmap(192, 192).asImageBitmap()
    }

    private fun packDrawable(app: AppEntry): Drawable? {
        val res = packRes ?: return null
        val name = exact[app.key] ?: byPkg[app.pkg] ?: return null
        val id = res.getIdentifier(name, "drawable", packName)
        if (id == 0) return null
        return try { res.getDrawable(id, null) } catch (_: Exception) { null }
    }

    private fun ensurePack() {
        val want = store.iconPack.value
        if (loadedFor != want) {
            synchronized(this) {
                if (loadedFor != want) {
                    loadPack(want)
                    loadedFor = want
                }
            }
        }
    }

    private fun loadPack(pkg: String) {
        exact = emptyMap(); byPkg = emptyMap(); packRes = null; packName = ""
        if (pkg.isEmpty()) return
        try {
            val res = pm.getResourcesForApplication(pkg)
            var parser: XmlPullParser? = null
            val id = res.getIdentifier("appfilter", "xml", pkg)
            if (id != 0) {
                parser = res.getXml(id)
            } else {
                try {
                    val p = XmlPullParserFactory.newInstance().newPullParser()
                    p.setInput(res.assets.open("appfilter.xml"), null)
                    parser = p
                } catch (_: Exception) {}
            }
            if (parser == null) return
            val e = HashMap<String, String>()
            val b = HashMap<String, String>()
            val rx = Regex("ComponentInfo\\{([^/]+)/([^}]+)\\}")
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null) {
                        val m = rx.find(comp)
                        if (m != null) {
                            val p = m.groupValues[1]
                            var c = m.groupValues[2]
                            if (c.startsWith(".")) c = p + c
                            e["$p/$c"] = dr
                            if (!b.containsKey(p)) b[p] = dr
                        }
                    }
                }
                ev = parser.next()
            }
            exact = e; byPkg = b; packRes = res; packName = pkg
        } catch (_: Exception) {
        }
    }

    fun listPacks(): List<IconPack> {
        val actions = listOf(
            "org.adw.launcher.THEMES", "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME"
        )
        val out = LinkedHashMap<String, IconPack>()
        for (a in actions) {
            for (ri in pm.queryIntentActivities(Intent(a), 0)) {
                val p = ri.activityInfo.packageName
                if (!out.containsKey(p)) out[p] = IconPack(p, ri.loadLabel(pm).toString())
            }
        }
        return out.values.sortedBy { it.label.lowercase() }
    }

    /** Lưu ảnh người dùng chọn làm biểu tượng riêng cho một ứng dụng. */
    fun saveCustom(key: String, uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            val side = minOf(bmp.width, bmp.height)
            val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
            FileOutputStream(customFile(key)).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cache.remove(key)
            true
        } catch (e: Exception) {
            false
        }
    }
}
