package com.example.mylauncher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

class BoolPref(private val sp: SharedPreferences, private val key: String, def: Boolean) {
    private var state by mutableStateOf(sp.getBoolean(key, def))
    var value: Boolean
        get() = state
        set(v) { state = v; sp.edit().putBoolean(key, v).apply() }
}

class IntPref(private val sp: SharedPreferences, private val key: String, def: Int) {
    private var state by mutableIntStateOf(sp.getInt(key, def))
    var value: Int
        get() = state
        set(v) { state = v; sp.edit().putInt(key, v).apply() }
}

class FloatPref(private val sp: SharedPreferences, private val key: String, def: Float) {
    private var state by mutableFloatStateOf(sp.getFloat(key, def))
    var value: Float
        get() = state
        set(v) { state = v; sp.edit().putFloat(key, v).apply() }
}

class StrPref(private val sp: SharedPreferences, private val key: String, def: String) {
    private var state by mutableStateOf(sp.getString(key, def) ?: def)
    var value: String
        get() = state
        set(v) { state = v; sp.edit().putString(key, v).apply() }
}

data class Page(val apps: List<String> = emptyList(), val widgets: List<Int> = emptyList())

@Stable
class Store(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    val doubleTapLock = BoolPref(sp, "double_tap_lock", true)
    val showLabels = BoolPref(sp, "labels", true)
    val blur = BoolPref(sp, "blur", true)
    val dynamicColor = BoolPref(sp, "dynamic_color", true)
    val iconSize = IntPref(sp, "icon_size", 56)
    val columns = IntPref(sp, "columns", 4)
    val iconShape = IntPref(sp, "icon_shape", 1)
    val glass = FloatPref(sp, "glass", 0.6f)
    val iconPack = StrPref(sp, "icon_pack", "")
    val aiKey = StrPref(sp, "ai_key", "")
    val aiModel = StrPref(sp, "ai_model", "claude-haiku-5-5")
    val swipeDownSearch = BoolPref(sp, "swipe_down_search", true)
    val onboarded = BoolPref(sp, "onboarded", false)

    var iconVersion by mutableIntStateOf(0)

    var pages by mutableStateOf(readPages())
        private set
    var dock by mutableStateOf(readStrings("dock"))
        private set
    var hidden by mutableStateOf(readStrings("hidden").toSet())
        private set
    var catOverride by mutableStateOf(readMap("cat"))
        private set

    var launchCounts by mutableStateOf(readMap("launches").mapValues { it.value.toIntOrNull() ?: 0 })
        private set

    fun bumpLaunch(key: String) {
        val m = launchCounts.toMutableMap()
        m[key] = (m[key] ?: 0) + 1
        launchCounts = m
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v.toString()) }
        sp.edit().putString("launches", o.toString()).apply()
    }

    fun seeded(): Boolean = sp.contains("pages")

    fun commitPages(p: List<Page>) {
        val list = p.ifEmpty { listOf(Page()) }
        pages = list
        val arr = JSONArray()
        list.forEach { pg ->
            arr.put(JSONObject().put("apps", JSONArray(pg.apps)).put("widgets", JSONArray(pg.widgets)))
        }
        sp.edit().putString("pages", arr.toString()).apply()
    }

    fun commitDock(d: List<String>) {
        dock = d
        sp.edit().putString("dock", JSONArray(d).toString()).apply()
    }

    fun commitHidden(h: Set<String>) {
        hidden = h
        sp.edit().putString("hidden", JSONArray(h.toList()).toString()).apply()
    }

    fun commitCat(m: Map<String, String>) {
        catOverride = m
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("cat", o.toString()).apply()
    }

    private fun readPages(): List<Page> {
        val s = sp.getString("pages", null) ?: return listOf(Page())
        return try {
            val arr = JSONArray(s)
            val out = ArrayList<Page>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val a = o.getJSONArray("apps")
                val w = o.getJSONArray("widgets")
                out.add(Page(List(a.length()) { a.getString(it) }, List(w.length()) { w.getInt(it) }))
            }
            if (out.isEmpty()) listOf(Page()) else out
        } catch (e: Exception) {
            listOf(Page())
        }
    }

    private fun readStrings(key: String): List<String> {
        val s = sp.getString(key, null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            List(a.length()) { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readMap(key: String): Map<String, String> {
        val s = sp.getString(key, null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            val m = HashMap<String, String>()
            o.keys().forEach { k -> m[k] = o.getString(k) }
            m
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
