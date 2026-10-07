package com.jarvis.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Always-on voice loop: listens for "Jarvis ...", asks the brain, speaks, acts. */
class JarvisService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)
    private var tts: TextToSpeech? = null
    private var ttsOk = false
    private var sr: SpeechRecognizer? = null
    private var wl: PowerManager.WakeLock? = null
    @Volatile private var busy = false
    private var awakeUntil = 0L
    private val wake = Regex("\\b(jarvis|jarvish|jervis|jarvi)\\b", RegexOption.IGNORE_CASE)
    private val again = Runnable { listen() }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "jarvis")
            .setContentTitle("Jarvis is listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)

        wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:listen")
        wl?.acquire()

        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("en", "IN"))
                tts?.setPitch(0.75f)
                tts?.setSpeechRate(1.05f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { done() }
                    override fun onError(id: String?) { done() }
                })
                ttsOk = true
                say(if (key().isEmpty()) "Boss, please add your Groq key in the app." else "Jarvis online. Ready, boss.")
            } else {
                sched(500)
            }
        }
    }

    private fun key(): String = getSharedPreferences("j", 0).getString("key", "")?.trim() ?: ""

    private fun done() {
        if (pending.decrementAndGet() <= 0) { pending.set(0); sched(300) }
    }

    private fun say(t: String) {
        if (!ttsOk || t.isBlank()) return
        pending.incrementAndGet()
        h.post { sr?.cancel() }
        if (tts?.speak(t, TextToSpeech.QUEUE_ADD, null, "u" + System.nanoTime()) != TextToSpeech.SUCCESS) done()
    }

    private fun sched(ms: Long) { h.removeCallbacks(again); h.postDelayed(again, ms) }

    private fun listen() {
        if (busy || pending.get() > 0) return
        if (sr == null) sr = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
        try {
            sr?.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            )
        } catch (e: Exception) { sched(1500) }
    }

    private val listener = object : RecognitionListener {
        override fun onResults(r: Bundle?) {
            handle(r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: "")
        }
        override fun onError(e: Int) {
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { stopSelf(); return }
            if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) { sr?.cancel(); sched(1200) } else sched(300)
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(b: Bundle?) {}
        override fun onEvent(t: Int, b: Bundle?) {}
    }

    private fun handle(t: String) {
        val now = System.currentTimeMillis()
        when {
            wake.containsMatchIn(t) -> {
                val cmd = t.replaceFirst(wake, "").trim(' ', ',', '.', '!', '?')
                if (cmd.length < 2) { awakeUntil = now + 10000; say("Yes boss?") } else process(cmd)
            }
            now < awakeUntil && t.isNotBlank() -> process(t)
            else -> sched(100)
        }
    }

    private fun process(cmd: String) {
        if (key().isEmpty()) { say("Boss, add your Groq key in the app first."); return }
        busy = true
        worker.execute {
            var input = cmd
            try {
                for (step in 0 until 6) {
                    val r = Brain.ask(key(), input, info())
                    say(r.optString("say"))
                    val fails = StringBuilder()
                    val acts = r.optJSONArray("actions")
                    if (acts != null) {
                        for (i in 0 until acts.length()) {
                            val res = Actions.run(this, acts.getJSONObject(i))
                            if (res != "ok") fails.append(res).append("; ")
                            Thread.sleep(800)
                        }
                    }
                    if (!r.optBoolean("more")) break
                    Thread.sleep(1200)
                    input = "RESULT: $fails\nSCREEN: " + (ControlService.inst?.screenText() ?: "(phone control is OFF)")
                }
            } catch (e: Exception) {
                say("Sorry boss. " + (e.message ?: "Something failed"))
            }
            h.post { busy = false; awakeUntil = System.currentTimeMillis() + 8000; sched(300) }
        }
    }

    private fun info(): String {
        val bat = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val time = SimpleDateFormat("EEE d MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        return "Now: $time. Battery: $bat%. Phone control (accessibility) is " + (if (ControlService.inst != null) "ON" else "OFF") + "."
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        sr?.destroy()
        tts?.shutdown()
        worker.shutdownNow()
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
