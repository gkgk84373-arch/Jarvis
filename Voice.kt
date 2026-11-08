package com.gopal.jarvis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class Voice(private val ctx: Context) {
    @Volatile private var ready = false
    private val tts: TextToSpeech = TextToSpeech(ctx) { status ->
        if (status == TextToSpeech.SUCCESS) ready = true
    }

    /** बोलता है और बोलना खत्म होने तक रुकता है (ताकि माइक अपनी ही आवाज़ न पकड़े)। */
    suspend fun say(text: String) {
        if (!ready || text.isBlank()) return
        val done = CompletableDeferred<Unit>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { done.complete(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { done.complete(Unit) }
        })
        tts.language = Locale("hi", "IN")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        withTimeoutOrNull(20000) { done.await() }
        delay(300)
    }

    /** एक बार सुनता है और बोली हुई बात का टेक्स्ट लौटाता है (न सुनाई दे तो null)। */
    suspend fun listen(): String? = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return@withContext null
        suspendCancellableCoroutine<String?> { cont ->
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            }
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) {
                    r.destroy()
                    if (cont.isActive) cont.resume(null)
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    r.destroy()
                    if (cont.isActive) cont.resume(t)
                }
            })
            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { r.destroy() } }
            r.startListening(intent)
        }
    }

    fun shutdown() { tts.shutdown() }
}
