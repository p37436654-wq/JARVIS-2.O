package com.jarvis.app

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import org.json.JSONObject

@Suppress("DEPRECATION")
object Actions {
    private fun go(c: Context, i: Intent) = c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun number(c: Context, to: String): String {
        if (to.none { it.isLetter() } && to.any { it.isDigit() }) return to.filter { it.isDigit() || it == '+' }
        c.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$to%"), null
        )?.use { if (it.moveToFirst()) return it.getString(0) }
        throw Exception("no contact named $to")
    }

    /** Runs one action. Returns "ok" or an error text (sent back to the brain). */
    fun run(c: Context, a: JSONObject): String {
        return try {
            val ctl = ControlService.inst
            when (val d = a.optString("do")) {
                "open_app" -> {
                    val n = a.optString("name")
                    val pm = c.packageManager
                    val l = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    val hit = l.firstOrNull { it.loadLabel(pm).toString().equals(n, true) }
                        ?: l.firstOrNull { it.loadLabel(pm).toString().contains(n, true) }
                        ?: throw Exception("app $n not found")
                    go(c, pm.getLaunchIntentForPackage(hit.activityInfo.packageName)!!)
                }
                "call" -> go(c, Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number(c, a.optString("to"))))))
                "sms" -> {
                    val sm = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                    sm.sendMultipartTextMessage(number(c, a.optString("to")), null, sm.divideMessage(a.optString("text")), null, null)
                }
                "alarm" -> go(c, Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour"))
                    .putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute"))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Jarvis"))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                "timer" -> go(c, Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, a.optInt("seconds"))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                "flashlight" -> {
                    val cm = c.getSystemService(CameraManager::class.java)
                    val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    cm.setTorchMode(id, a.optBoolean("on", true))
                }
                "volume" -> {
                    val am = c.getSystemService(AudioManager::class.java)
                    val v = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * a.optInt("level", 50) / 100
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_SHOW_UI)
                }
                "url" -> go(c, Intent(Intent.ACTION_VIEW, Uri.parse(a.optString("url"))))
                "search" -> go(c, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, a.optString("query")))
                "navigate" -> go(c, Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(a.optString("place")))))
                "music" -> {
                    val q = a.optString("query")
                    try {
                        go(c, Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).putExtra(SearchManager.QUERY, q))
                    } catch (e: Exception) {
                        go(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
                    }
                }
                "settings" -> go(c, Intent(when (a.optString("page")) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                    "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                    "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                    "data" -> Settings.ACTION_WIRELESS_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }))
                "system" -> if (ctl?.global(a.optString("what")) != true) throw Exception("phone control is off or action unknown")
                "click" -> if (ctl?.click(a.optString("text")) != true) throw Exception("could not click '" + a.optString("text") + "'")
                "type" -> if (ctl?.type(a.optString("text")) != true) throw Exception("could not type")
                "enter" -> ctl?.enter()
                "scroll" -> ctl?.scroll(a.optString("dir") != "up")
                "wait" -> Thread.sleep(a.optLong("ms", 1000).coerceIn(0L, 5000L))
                else -> throw Exception("unknown action $d")
            }
            "ok"
        } catch (e: Exception) {
            "failed: " + (e.message ?: e.javaClass.simpleName)
        }
    }
}
