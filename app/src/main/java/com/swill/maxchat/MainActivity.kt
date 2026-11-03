package com.swill.maxchat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаем главный контейнер (вертикальный)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Создаем область для сообщений (с прокруткой)
        val scrollView = ScrollView(this)
        val messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(messagesLayout)
        
        // Добавляем область сообщений в главный контейнер
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        ))

        // Создаем нижнюю панель для ввода
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Поле ввода
        val editText = EditText(this).apply {
            hint = "Сообщение..."
        }
        inputLayout.addView(editText, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        ))

        // Кнопка отправки
        val sendButton = Button(this).apply {
            text = "->"
        }
        inputLayout.addView(sendButton)

        mainLayout.addView(inputLayout)

        // Обработка нажатия на кнопку
        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                // Добавляем сообщение в список
                val messageView = TextView(this).apply {
                    this.text = text
                    textSize = 18f
                    setPadding(0, 8, 0, 8)
                }
                messagesLayout.addView(messageView)
                editText.text.clear()
                
                // Прокручиваем вниз
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }

        // Устанавливаем наш интерфейс
        setContentView(mainLayout)
    }
}
