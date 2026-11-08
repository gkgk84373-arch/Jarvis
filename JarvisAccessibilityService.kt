package com.gopal.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
    }

    private val nodes = ArrayList<AccessibilityNodeInfo>()

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** स्क्रीन को छोटे टेक्स्ट में बदलता है। हर लाइन का [N] ही id है जो AI इस्तेमाल करता है। */
    fun readScreen(): String {
        nodes.clear()
        val root = rootInActiveWindow ?: return "(स्क्रीन पढ़ नहीं पाया)"
        val sb = StringBuilder("ऐप: ${root.packageName}\n")
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || nodes.size >= 80) return
            if (n.isVisibleToUser) {
                val t = (n.text ?: n.contentDescription)?.toString()?.take(60)
                if (n.isClickable || n.isEditable || n.isScrollable || !t.isNullOrBlank()) {
                    nodes.add(n)
                    sb.append("[${nodes.size - 1}] ")
                    sb.append(n.className?.toString()?.substringAfterLast('.') ?: "View")
                    sb.append(" \"").append(if (n.isPassword) "(पासवर्ड)" else (t ?: "")).append("\"")
                    if (n.isClickable) sb.append(" click")
                    if (n.isEditable) sb.append(" edit")
                    if (n.isScrollable) sb.append(" scroll")
                    sb.append("\n")
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return sb.toString()
    }

    fun nodeText(id: Int): String {
        val n = nodes.getOrNull(id) ?: return ""
        return (n.text ?: n.contentDescription)?.toString() ?: ""
    }

    fun global(name: String): String {
        val ok = when (name) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            else -> false
        }
        return if (ok) "हो गया" else "नहीं हो सका"
    }

    fun tap(id: Int, long: Boolean = false): String {
        val n = nodes.getOrNull(id) ?: return "id $id नहीं मिला"
        var c: AccessibilityNodeInfo? = n
        while (c != null && !(if (long) c.isLongClickable else c.isClickable)) c = c.parent
        if (c != null) {
            val act = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
            if (c.performAction(act)) return "टैप किया"
        }
        val r = Rect()
        n.getBoundsInScreen(r)
        val x = r.centerX().toFloat()
        val y = r.centerY().toFloat()
        return if (gesture(x, y, x, y, if (long) 700 else 60)) "टैप किया" else "टैप नहीं हो सका"
    }

    fun type(id: Int, text: String): String {
        val n = (if (id >= 0) nodes.getOrNull(id) else null)
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return "लिखने की जगह नहीं मिली"
        if (n.isPassword) return "पासवर्ड वाली जगह में नहीं लिखूँगा"
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return if (n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) "लिख दिया" else "लिख नहीं सका"
    }

    fun enter(): String {
        val n = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return "कोई चालू इनपुट नहीं"
        val ok = n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        return if (ok) "Enter दबाया" else "Enter नहीं चला"
    }

    fun scroll(dir: String): String {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val ok = when (dir) {
            "down" -> gesture(w / 2, h * 0.7f, w / 2, h * 0.3f, 350)
            "up" -> gesture(w / 2, h * 0.3f, w / 2, h * 0.7f, 350)
            "left" -> gesture(w * 0.3f, h / 2, w * 0.7f, h / 2, 350)
            "right" -> gesture(w * 0.7f, h / 2, w * 0.3f, h / 2, 350)
            else -> false
        }
        return if (ok) "स्क्रॉल किया" else "स्क्रॉल नहीं हुआ"
    }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long): Boolean {
        val p = Path()
        p.moveTo(x1, y1)
        if (x1 != x2 || y1 != y2) p.lineTo(x2, y2)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, dur))
            .build()
        return dispatchGesture(g, null, null)
    }
}
