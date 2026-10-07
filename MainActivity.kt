package com.jarvis.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sp = getSharedPreferences("j", 0)
        val d = resources.displayMetrics.density

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((20 * d).toInt(), (40 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        }
        val orb = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#00E5FF")) }
            layoutParams = LinearLayout.LayoutParams((130 * d).toInt(), (130 * d).toInt()).apply {
                topMargin = (20 * d).toInt(); bottomMargin = (40 * d).toInt()
            }
        }
        ObjectAnimator.ofPropertyValuesHolder(
            orb, PropertyValuesHolder.ofFloat("scaleX", 1f, 1.25f), PropertyValuesHolder.ofFloat("scaleY", 1f, 1.25f)
        ).apply { duration = 900; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start() }

        val key = EditText(this).apply {
            hint = "Paste Groq API key (gsk_...)"
            setText(sp.getString("key", ""))
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        fun save() { sp.edit().putString("key", key.text.toString().trim()).apply() }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)

        col.addView(orb)
        col.addView(key)
        col.addView(btn("1. Save key + allow permissions") { save(); requestPermissions(perms.toTypedArray(), 1) })
        col.addView(btn("2. Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(btn("3. Turn ON Jarvis phone control") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        col.addView(btn("4. Battery: set Jarvis to Unrestricted") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        col.addView(btn("START JARVIS") {
            save()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Do step 1 first", Toast.LENGTH_LONG).show()
            } else {
                startForegroundService(Intent(this, JarvisService::class.java))
            }
        })
        col.addView(btn("STOP") { stopService(Intent(this, JarvisService::class.java)) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            addView(col)
        })
    }
}
