package com.swill.maxchat

import android.os.Bundle
import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage

class MainActivity : Activity() {
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private var webSocket: WebSocket? = null
    private val client = HttpClient.newHttpClient()

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
                // Отправляем сообщение на сервер
                webSocket?.sendText(text, true)
                editText.text.clear()
            }
        }

        setContentView(mainLayout)
        
        // Подключаемся к серверу Cloudflare
        connectToServer()
    }

    private fun connectToServer() {
        val serverUri = URI.create("wss://mysty-dawn-51cd.serafimabramovbirukov.workers.dev/")
        
        client.newWebSocketBuilder()
            .buildAsync(serverUri, object : WebSocket.Listener {
                override fun onOpen(webSocket: WebSocket) {
                    addMessage("Система: Подключено к серверу!")
                }

                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    runOnUiThread {
                        addMessage(data.toString())
                    }
                    return super.onText(webSocket, data, last)
                }

                override fun onError(webSocket: WebSocket, error: Throwable) {
                    runOnUiThread {
                        addMessage("Система: Ошибка подключения")
                    }
                }
            })
            .thenAccept { ws ->
                webSocket = ws
            }
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
        webSocket?.sendClose(1000, "Bye")
    }
}
