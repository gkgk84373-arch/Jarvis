package com.gopal.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LlmClient {

    private fun explain(code: Int): String = when (code) {
        401, 403 -> "key गलत है या इस अकाउंट को इजाज़त नहीं"
        404 -> "URL या मॉडल का नाम गलत है"
        429 -> "आज की फ्री लिमिट खत्म या बहुत तेज़ रिक्वेस्ट"
        else -> "सर्वर एरर"
    }

    /** सारे providers को क्रम से आज़माता है। पहला जो चले उसका जवाब लौटाता है। ब्लॉकिंग है, IO थ्रेड पर चलाओ। */
    fun chat(ctx: Context, messages: JSONArray, note: (String) -> Unit): String? {
        for (p in Prefs.providers(ctx)) {
            if (p.key.isBlank()) continue
            val r = call(p, messages)
            if (r.first in 200..299) return r.second
            note("${p.name}: ${r.first} ${explain(r.first)} → अगला AI")
        }
        return null
    }

    fun test(p: Provider): String {
        if (p.key.isBlank()) return "❌ key खाली है"
        val m = JSONArray().put(JSONObject().put("role", "user").put("content", "hi, एक शब्द में जवाब दो"))
        val r = call(p, m)
        return if (r.first in 200..299) "✅ चल रहा है" else "❌ ${r.first}: ${explain(r.first)}"
    }

    private fun call(p: Provider, messages: JSONArray): Pair<Int, String> {
        return try {
            val conn = URL(p.baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 45000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${p.key}")
            val body = JSONObject().put("model", p.model).put("messages", messages).put("temperature", 0.2)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                val txt = conn.inputStream.bufferedReader().readText()
                val content = JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                Pair(code, content)
            } else {
                Pair(code, conn.errorStream?.bufferedReader()?.readText() ?: "")
            }
        } catch (e: Exception) {
            Pair(-1, e.message ?: "नेटवर्क एरर")
        }
    }
}
