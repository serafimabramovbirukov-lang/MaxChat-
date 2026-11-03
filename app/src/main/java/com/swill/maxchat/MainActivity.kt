package com.swill.maxchat

import android.os.Bundle
import android.app.Activity
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Создаем простой текст на весь экран
        val textView = TextView(this).apply {
            text = "MaxChat успешно запущен!"
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        
        // Устанавливаем его как главный экран
        setContentView(textView)
    }
}
