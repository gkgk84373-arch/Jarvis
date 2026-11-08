package com.gopal.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class AgentService : Service() {

    companion object {
        @Volatile var instance: AgentService? = null
        const val ACTION_LISTEN = "com.gopal.jarvis.LISTEN"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, AgentService::class.java))
        }
        fun submit(text: String) { instance?.queue?.trySend(text) }
        fun listenNow() { instance?.listenOnce() }
        fun stopAll() { instance?.agent?.stop = true }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val queue = Channel<String>(Channel.UNLIMITED)
    private lateinit var voice: Voice
    lateinit var agent: Agent
    private var overlay: View? = null
    @Volatile private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        voice = Voice(this)
        agent = Agent(this, voice)

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val listenPi = PendingIntent.getService(
            this, 0, Intent(this, AgentService::class.java).setAction(ACTION_LISTEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "jarvis")
            .setContentTitle("Gopal Jarvis चालू है")
            .setContentText("बोलने के लिए 'सुनो' दबाओ")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPi)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_btn_speak_now, "सुनो", listenPi).build())
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        // कमांड की कतार: एक के बाद एक काम चलते हैं
        scope.launch {
            for (cmd in queue) {
                try {
                    agent.run(cmd)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Bus.add("❌ ${e.message}")
                }
            }
        }
        if (Settings.canDrawOverlays(this)) showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LISTEN) listenOnce()
        return START_STICKY
    }

    fun listenOnce() {
        if (listening) return
        listening = true
        scope.launch {
            try {
                Bus.add("🎤 सुन रहा हूँ…")
                val t = voice.listen()
                if (t.isNullOrBlank()) Bus.add("कुछ सुनाई नहीं दिया") else queue.send(t)
            } finally {
                listening = false
            }
        }
    }

    private fun showOverlay() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun bubble(label: String, color: Int, onClick: () -> Unit): TextView {
                val tv = TextView(this)
                tv.text = label
                tv.textSize = 22f
                tv.gravity = Gravity.CENTER
                tv.setTextColor(Color.WHITE)
                tv.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                val lp = LinearLayout.LayoutParams(150, 150)
                lp.setMargins(8, 8, 8, 8)
                tv.layoutParams = lp
                tv.setOnClickListener { onClick() }
                return tv
            }
            box.addView(bubble("🎤", Color.parseColor("#1565C0")) { listenOnce() })
            box.addView(bubble("⏹", Color.parseColor("#C62828")) { agent.stop = true })
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            wm.addView(box, lp)
            overlay = box
        } catch (e: Exception) {
            Bus.add("Overlay नहीं बन सका: ${e.message}")
        }
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        try {
            overlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        } catch (e: Exception) { }
        voice.shutdown()
        super.onDestroy()
    }
}
