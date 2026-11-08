package com.gopal.jarvis

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

data class Provider(val name: String, val baseUrl: String, val model: String, val key: String)

object Prefs {
    // मॉडल के नाम बदल सकते हैं, इसलिए Settings में एडिट करने लायक रखे हैं
    private val defaults = listOf(
        Provider("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", ""),
        Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash", ""),
        Provider("OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", ""),
        Provider("Cerebras", "https://api.cerebras.ai/v1", "llama-3.3-70b", "")
    )

    private fun sp(c: Context) = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun providers(c: Context): List<Provider> {
        val s = sp(c)
        return defaults.map { d ->
            Provider(
                d.name,
                s.getString("${d.name}.url", d.baseUrl) ?: d.baseUrl,
                s.getString("${d.name}.model", d.model) ?: d.model,
                s.getString("${d.name}.key", "") ?: ""
            )
        }
    }

    fun save(c: Context, p: Provider) {
        sp(c).edit()
            .putString("${p.name}.url", p.baseUrl)
            .putString("${p.name}.model", p.model)
            .putString("${p.name}.key", p.key)
            .apply()
    }

    fun confirmEnabled(c: Context): Boolean = sp(c).getBoolean("confirm", true)
    fun setConfirm(c: Context, v: Boolean) { sp(c).edit().putBoolean("confirm", v).apply() }
}

object Bus {
    val log = MutableStateFlow<List<String>>(emptyList())
    fun add(s: String) { log.value = (log.value + s).takeLast(200) }
}
