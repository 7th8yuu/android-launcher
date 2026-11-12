package com.example.mylauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class AiAction {
    class Open(val label: String) : AiAction()
    class Web(val url: String) : AiAction()
    class Alarm(val h: Int, val m: Int) : AiAction()
    class Timer(val sec: Int) : AiAction()
    class Setting(val key: String) : AiAction()
}

fun AiAction.describe(): String = when (this) {
    is AiAction.Open -> "Mở ${this.label}"
    is AiAction.Web -> "Mở trang web"
    is AiAction.Alarm -> "Đặt báo thức %02d:%02d".format(this.h, this.m)
    is AiAction.Timer -> "Hẹn giờ ${this.sec / 60} phút ${this.sec % 60} giây"
    is AiAction.Setting -> "Mở cài đặt: ${this.key}"
}

class AiResult(val text: String?, val error: String?)

object Ai {
    fun system(appLabels: String): String =
        "Bạn là trợ lý tích hợp trong launcher Android 'My Launcher'. Trả lời ngắn gọn, thân thiện, cùng ngôn ngữ với người dùng. " +
            "Nếu người dùng muốn thực hiện một hành động, thêm vào CUỐI câu trả lời một hoặc nhiều dòng riêng đúng định dạng: " +
            "'ACTION: open <tên ứng dụng chính xác trong danh sách>', 'ACTION: web <url https>', 'ACTION: alarm HH:MM' (24 giờ), " +
            "'ACTION: timer <số giây>', " +
            "'ACTION: settings <wifi|bluetooth|display|sound|battery|storage|apps|location|accessibility|language|date|airplane|settings>'. " +
            "Không bịa tên ứng dụng. Danh sách ứng dụng đã cài: $appLabels"

    const val CATEGORIZE_SYSTEM =
        "Phân loại mỗi ứng dụng Android vào đúng một nhóm: GAMES, SOCIAL, MEDIA, TOOLS, OTHER. " +
            "GAMES=trò chơi; SOCIAL=mạng xã hội và nhắn tin; MEDIA=nhạc, video, ảnh, tin tức, giải trí; " +
            "TOOLS=tiện ích, hệ thống, năng suất, bản đồ, ngân hàng; OTHER=còn lại. " +
            "Mỗi dòng đầu vào có dạng 'package | tên'. Chỉ trả về MỘT JSON object dạng {\"package\":\"NHÓM\"}, không thêm chữ nào khác."

    suspend fun ask(key: String, model: String, system: String, user: String, maxTokens: Int = 700): AiResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", maxTokens)
                    .put("system", system)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
                val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 15000
                c.readTimeout = 60000
                c.doOutput = true
                c.setRequestProperty("content-type", "application/json")
                c.setRequestProperty("x-api-key", key)
                c.setRequestProperty("anthropic-version", "2023-06-01")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val txt = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    val msg = try {
                        JSONObject(txt).getJSONObject("error").getString("message")
                    } catch (_: Exception) {
                        txt.take(200)
                    }
                    return@withContext AiResult(null, "Lỗi $code: $msg")
                }
                val arr = JSONObject(txt).getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("type") == "text") sb.append(o.getString("text"))
                }
                AiResult(sb.toString().trim(), null)
            } catch (e: Exception) {
                AiResult(null, e.message ?: "Lỗi mạng")
            }
        }

    /** Tách các dòng "ACTION: ..." ra khỏi nội dung trả lời. */
    fun parse(text: String): Pair<String, List<AiAction>> {
        val body = ArrayList<String>()
        val acts = ArrayList<AiAction>()
        for (line in text.lines()) {
            val t = line.trim()
            if (t.startsWith("ACTION:", ignoreCase = true)) {
                val rest = t.substring(7).trim()
                val sp = rest.indexOf(' ')
                val cmd = (if (sp < 0) rest else rest.substring(0, sp)).lowercase()
                val arg = if (sp < 0) "" else rest.substring(sp + 1).trim()
                when (cmd) {
                    "open" -> if (arg.isNotEmpty()) acts.add(AiAction.Open(arg))
                    "web" -> if (arg.startsWith("https://")) acts.add(AiAction.Web(arg))
                    "alarm" -> {
                        val m = Regex("(\\d{1,2}):(\\d{2})").find(arg)
                        if (m != null) {
                            val h = m.groupValues[1].toInt()
                            val mi = m.groupValues[2].toInt()
                            if (h in 0..23 && mi in 0..59) acts.add(AiAction.Alarm(h, mi))
                        }
                    }
                    "timer" -> {
                        val n = arg.toIntOrNull()
                        if (n != null && n in 1..86400) acts.add(AiAction.Timer(n))
                    }
                    "settings" -> {
                        val k = arg.lowercase()
                        if (Sys.settingEntries.any { it.key == k }) acts.add(AiAction.Setting(k))
                    }
                }
            } else {
                body.add(line)
            }
        }
        return Pair(body.joinToString("\n").trim(), acts)
    }

    /** Lấy JSON object đầu tiên trong câu trả lời (bỏ rào ```json nếu có). */
    fun extractJson(text: String): JSONObject? {
        val a = text.indexOf('{')
        val b = text.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try { JSONObject(text.substring(a, b + 1)) } catch (_: Exception) { null }
    }
}
