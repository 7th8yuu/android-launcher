package com.example.mylauncher

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class SettingEntry(val key: String, val title: String, val words: String, val action: String)

object Sys {
    val settingEntries = listOf(
        SettingEntry("wifi", "Wi-Fi", "wifi wi-fi mạng internet", Settings.ACTION_WIFI_SETTINGS),
        SettingEntry("bluetooth", "Bluetooth", "bluetooth tai nghe", Settings.ACTION_BLUETOOTH_SETTINGS),
        SettingEntry("display", "Màn hình & độ sáng", "màn hình độ sáng display brightness hiển thị", Settings.ACTION_DISPLAY_SETTINGS),
        SettingEntry("sound", "Âm thanh", "âm thanh sound volume âm lượng chuông", Settings.ACTION_SOUND_SETTINGS),
        SettingEntry("battery", "Pin", "pin battery tiết kiệm", Settings.ACTION_BATTERY_SAVER_SETTINGS),
        SettingEntry("storage", "Bộ nhớ", "bộ nhớ storage dung lượng", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SettingEntry("apps", "Ứng dụng", "ứng dụng apps quản lý", Settings.ACTION_APPLICATION_SETTINGS),
        SettingEntry("location", "Vị trí", "vị trí location gps", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        SettingEntry("accessibility", "Trợ năng", "trợ năng accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SettingEntry("language", "Ngôn ngữ", "ngôn ngữ language", Settings.ACTION_LOCALE_SETTINGS),
        SettingEntry("date", "Ngày & giờ", "ngày giờ date time", Settings.ACTION_DATE_SETTINGS),
        SettingEntry("airplane", "Chế độ máy bay", "máy bay airplane flight", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        SettingEntry("settings", "Cài đặt hệ thống", "cài đặt settings", Settings.ACTION_SETTINGS)
    )

    fun isDefaultLauncher(ctx: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == ctx.packageName
    }

    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, LockService::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun isListenerEnabled(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun hasContacts(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
}

sealed class Hit {
    class Calc(val expr: String, val result: String) : Hit()
    class AppHit(val app: AppEntry) : Hit()
    class SettingHit(val entry: SettingEntry) : Hit()
    class ContactHit(val name: String, val number: String, val uri: Uri) : Hit()
}

object ContactSearch {
    fun contacts(ctx: Context, q: String): List<Hit.ContactHit> {
        if (!Sys.hasContacts(ctx)) return emptyList()
        val out = ArrayList<Hit.ContactHit>()
        try {
            val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val cols = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
            )
            ctx.contentResolver.query(
                phone, cols,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$q%"), null
            )?.use { c ->
                val seen = HashSet<Long>()
                while (c.moveToNext() && out.size < 4) {
                    val id = c.getLong(2)
                    if (!seen.add(id)) continue
                    out.add(
                        Hit.ContactHit(
                            c.getString(0) ?: "",
                            c.getString(1) ?: "",
                            ContactsContract.Contacts.getLookupUri(id, c.getString(3))
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}

/** Máy tính nhỏ cho ô tìm kiếm: + - * / ^ ( ) % */
object Calculator {
    private val allowed = Regex("^[0-9+\\-*/().,^%×÷\\s]+\$")

    fun evaluate(q: String): String? {
        if (!allowed.matches(q)) return null
        if (!q.any { it.isDigit() } || !q.any { it in "+-*/^×÷%" }) return null
        val s = q.replace("×", "*").replace("÷", "/").replace(",", ".").replace(" ", "")
        return try {
            val p = Parser(s)
            val v = p.expr()
            if (p.i != s.length || v.isNaN() || v.isInfinite()) null else format(v)
        } catch (e: Exception) {
            null
        }
    }

    private fun format(v: Double): String {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return v.toLong().toString()
        return java.math.BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()
    }

    private class Parser(val s: String) {
        var i = 0

        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double {
            var v = pow()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val r = pow()
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        private fun pow(): Double {
            val b = unary()
            if (i < s.length && s[i] == '^') {
                i++
                return Math.pow(b, pow())
            }
            return b
        }

        private fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return atom()
        }

        private fun atom(): Double {
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                if (i >= s.length || s[i] != ')') throw IllegalArgumentException("paren")
                i++
                return v
            }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw IllegalArgumentException("num")
            var v = s.substring(st, i).toDouble()
            if (i < s.length && s[i] == '%') { i++; v /= 100.0 }
            return v
        }
    }
}
