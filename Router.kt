package com.gopal.jarvis

import android.content.Context
import android.content.Intent

/** सादे कमांड जो AI के बिना तुरंत चलते हैं। */
object Router {
    private val aliases = mapOf(
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp", "व्हाट्स ऐप" to "whatsapp",
        "यूट्यूब" to "youtube", "क्रोम" to "chrome", "कैमरा" to "camera", "गैलरी" to "gallery",
        "सेटिंग" to "settings", "सेटिंग्स" to "settings", "फोन" to "phone", "फ़ोन" to "phone",
        "कैलकुलेटर" to "calculator", "इंस्टाग्राम" to "instagram", "टेलीग्राम" to "telegram",
        "फेसबुक" to "facebook", "जीमेल" to "gmail", "मैप्स" to "maps", "प्ले स्टोर" to "play store"
    )
    private val openWords = listOf("खोल दो", "खोलो", "खोलें", "खोल", "open", "launch", "चालू करो")

    fun findApp(ctx: Context, name: String): Pair<String, String>? {
        val q = aliases[name.trim().lowercase()] ?: name.trim().lowercase()
        if (q.isBlank()) return null
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        return apps.firstOrNull { it.first.lowercase() == q }
            ?: apps.firstOrNull { it.first.lowercase().contains(q) }
    }

    fun openApp(ctx: Context, name: String): String {
        val app = findApp(ctx, name) ?: return "ऐप '$name' नहीं मिला"
        val i = ctx.packageManager.getLaunchIntentForPackage(app.second) ?: return "ऐप नहीं खुल सका"
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "${app.first} खोल दिया"
    }

    /** लोकल रूट में आने वाला कमांड हो तो नतीजा लौटाता है, वरना null (तब AI एजेंट चलेगा)। */
    fun tryLocal(ctx: Context, text: String): String? {
        val t = text.lowercase().trim().trimEnd('.', '।')
        val svc = JarvisAccessibilityService.instance
        when (t) {
            "back", "वापस", "पीछे", "वापस जाओ", "पीछे जाओ" -> return svc?.global("back") ?: "Accessibility चालू नहीं है"
            "home", "होम", "होम स्क्रीन", "होम पर जाओ" -> return svc?.global("home") ?: "Accessibility चालू नहीं है"
            "recents", "रीसेंट", "रीसेंट ऐप्स" -> return svc?.global("recents") ?: "Accessibility चालू नहीं है"
            "scroll down", "नीचे स्क्रॉल करो", "नीचे जाओ" -> return svc?.scroll("down") ?: "Accessibility चालू नहीं है"
            "scroll up", "ऊपर स्क्रॉल करो", "ऊपर जाओ" -> return svc?.scroll("up") ?: "Accessibility चालू नहीं है"
        }
        var rest = t
        var hit = false
        for (w in openWords) {
            if (rest.contains(w)) { rest = rest.replace(w, " "); hit = true }
        }
        rest = rest.replace("ऐप", " ").trim().replace(Regex("\\s+"), " ")
        if (hit && rest.isNotBlank() && rest.split(" ").size <= 3 && findApp(ctx, rest) != null) {
            return openApp(ctx, rest)
        }
        return null
    }
}
