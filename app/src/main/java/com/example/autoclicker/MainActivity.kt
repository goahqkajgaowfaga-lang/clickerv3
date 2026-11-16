package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }

        val info = TextView(this).apply {
            textSize = 16f
            text = "1. Нажмите кнопку ниже и включите службу «AutoClicker» в Специальных возможностях.\n\n" +
                "2. На экране появятся панель (СТАРТ/СТОП, слайдер 1–20 CPS) и красный прицел.\n\n" +
                "3. Перетащите прицел на кнопку атаки в игре и нажмите СТАРТ."
        }

        val btn = Button(this).apply {
            text = "Открыть настройки доступности"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        root.addView(info)
        root.addView(btn)
        setContentView(root)
    }
}
