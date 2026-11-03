package com.swill.maxchat

import android.os.Bundle
import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private var isConnecting = false
    // Текущая комната (по умолчанию "general")
    private var currentRoom = "general"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // --- Поле для ввода названия комнаты ---
        val roomInput = EditText(this).apply {
            hint = "Название комнаты (например: Друзья)"
            setText(currentRoom)
        }
        mainLayout.addView(roomInput)

        // --- Кнопка смены комнаты ---
        val joinButton = Button(this).apply {
            text = "Сменить комнату"
        }
        mainLayout.addView(joinButton)

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

        // Обработка смены комнаты
        joinButton.setOnClickListener {
            val newRoom = roomInput.text.toString().trim()
            if (newRoom.isNotEmpty()) {
                currentRoom = newRoom
                addMessage("Система: Вы перешли в комнату '$currentRoom'")
                
                // Отправляем серверу уведомление о смене комнаты
                val json = JSONObject()
                json.put("room", currentRoom)
                webSocket?.send(json.toString())
            }
        }

        // Обработка отправки сообщения
        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                // Формируем JSON: { "room": "Друзья", "text": "Привет" }
                val json = JSONObject()
                json.put("room", currentRoom)
                json.put("text", text)
                
                webSocket?.send(json.toString())
                editText.text.clear()
            }
        }

        setContentView(mainLayout)
        connectToServer()
    }

    private fun connectToServer() {
        if (isConnecting) return
        isConnecting = true
        
        val request = Request.Builder().url("wss://maxchat.relaxdev.ru/").build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
                addMessage("Система: Подключено к серверу!")
                
                // При подключении сразу сообщаем, в какой мы комнате
                val json = JSONObject()
                json.put("room", currentRoom)
                webSocket.send(json.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    // Сервер присылает JSON: { "room": "Друзья", "text": "Привет" }
                    val json = JSONObject(text)
                    val room = json.getString("room")
                    val msgText = json.getString("text")
                    
                    // Показываем сообщение только если оно из нашей комнаты
                    if (room == currentRoom) {
                        addMessage("[$room] $msgText")
                    }
                } catch (e: Exception) {
                    addMessage(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                addMessage("Ошибка: ${t.message}. Переподключение...")
                
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    connectToServer()
                }, 3000)
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
