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

class AiResult(val text: String?, val error: String?, val code: Int = 0)

/** Trợ lý AI dùng Google Gemini API (generativelanguage.googleapis.com). */
object Ai {
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    // Thứ tự ưu tiên khi tự chọn model (nhanh + rẻ trước)
    private val preferred = listOf(
        "gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-flash-latest",
        "gemini-4-hydrogen", "gemini-3.6-flash", "gemini-3-flash-preview", "gemini-2.5-flash"
    )

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

    private fun open(url: String, key: String, post: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = if (post) "POST" else "GET"
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("x-goog-api-key", key)
        if (post) {
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
        }
        return c
    }

    private fun errorOf(code: Int, txt: String): String {
        val msg = try {
            JSONObject(txt).getJSONObject("error").getString("message")
        } catch (_: Exception) {
            txt.take(200)
        }
        return "Lỗi $code: $msg"
    }

    suspend fun ask(key: String, model: String, system: String, user: String, maxTokens: Int = 1024): AiResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", user)))
                        )
                    )
                    .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens))
                val name = model.removePrefix("models/")
                val c = open("$BASE/models/$name:generateContent", key, true)
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val txt = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) return@withContext AiResult(null, errorOf(code, txt), code)
                val root = JSONObject(txt)
                val cands = root.optJSONArray("candidates")
                if (cands == null || cands.length() == 0) {
                    val why = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                    return@withContext AiResult(null, "Gemini không trả lời" + if (why.isNotEmpty()) " ($why)" else "", code)
                }
                val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                val sb = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        if (p.optBoolean("thought", false)) continue
                        if (p.has("text")) sb.append(p.getString("text"))
                    }
                }
                val out = sb.toString().trim()
                if (out.isEmpty()) AiResult(null, "Gemini trả về nội dung trống", code) else AiResult(out, null, code)
            } catch (e: Exception) {
                AiResult(null, e.message ?: "Lỗi mạng", -1)
            }
        }

    /** Danh sách model hỗ trợ generateContent của khoá này. */
    suspend fun listModels(key: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val c = open("$BASE/models?pageSize=200", key, false)
            if (c.responseCode !in 200..299) return@withContext emptyList<String>()
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(txt).optJSONArray("models") ?: return@withContext emptyList<String>()
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods")
                var ok = false
                if (methods != null) {
                    for (j in 0 until methods.length()) {
                        if (methods.getString(j) == "generateContent") ok = true
                    }
                }
                if (ok) out.add(m.getString("name").removePrefix("models/"))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun pickModel(available: List<String>): String? {
        for (p in preferred) if (p in available) return p
        return available.firstOrNull { it.contains("flash-lite") }
            ?: available.firstOrNull { it.contains("flash") && !it.contains("image") && !it.contains("tts") && !it.contains("live") }
            ?: available.firstOrNull { it.startsWith("gemini") && !it.contains("image") && !it.contains("tts") && !it.contains("embedding") }
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
