package com.swill.maxchat

import android.os.Bundle
import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import okhttp3.*
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        scrollView = ScrollView(this)
        messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(messagesLayout)
        
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        ))

        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val editText = EditText(this).apply {
            hint = "Сообщение..."
        }
        inputLayout.addView(editText, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        ))

        val sendButton = Button(this).apply {
            text = "->"
        }
        inputLayout.addView(sendButton)

        mainLayout.addView(inputLayout)

        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                webSocket?.send(text)
                editText.text.clear()
            }
        }

        setContentView(mainLayout)
        connectToServer()
    }

    private fun connectToServer() {
        // Адрес от RelaxDev (обязательно wss://)
        val request = Request.Builder().url("wss://maxchat.isodomain.tech/").build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                addMessage("Система: Подключено к серверу!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                addMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                addMessage("Ошибка: ${t.message}")
            }
        })
    }

    private fun addMessage(text: String) {
        runOnUiThread {
            val messageView = TextView(this).apply {
                this.text = text
                textSize = 18f
                setPadding(0, 8, 0, 8)
            }
            messagesLayout.addView(messageView)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Bye")
    }
}
