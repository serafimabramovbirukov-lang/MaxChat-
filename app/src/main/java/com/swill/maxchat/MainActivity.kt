package com.swill.maxchat

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private var isConnecting = false
    private var currentChatId = ""
    private var userName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE)
        userName = prefs.getString("userName", "") ?: ""

        if (userName.isEmpty()) {
            showLoginScreen()
        } else {
            showChatListScreen()
        }
    }

    // 1. ЭКРАН ВХОДА
    private fun showLoginScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
        }

        val nameInput = EditText(this).apply { hint = "Введите ваше имя" }
        val loginButton = Button(this).apply { text = "Войти" }

        layout.addView(nameInput)
        layout.addView(loginButton)
        setContentView(layout)

        loginButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                userName = name
                getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE)
                    .edit().putString("userName", name).apply()
                showChatListScreen()
            } else {
                Toast.makeText(this, "Введите имя", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. ЭКРАН СПИСКА ЧАТОВ (С аватарками)
    private fun showChatListScreen() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "Чаты"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        mainLayout.addView(title)

        // Список чатов (можно добавлять свои)
        val chats = listOf(
            Triple("general", "Общий чат", "#4CAF50"),
            Triple("friends", "Друзья", "#2196F3"),
            Triple("work", "Работа", "#FF9800")
        )

        for ((id, name, colorHex) in chats) {
            val chatLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
                gravity = Gravity.CENTER_VERTICAL
            }

            // Аватарка (цветной кружок с первой буквой)
            val avatar = TextView(this).apply {
                text = name.first().toString()
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor(colorHex))
                width = 100
                height = 100
            }
            
            // Обрезаем в круг (упрощенно)
            val avatarParams = LinearLayout.LayoutParams(100, 100)
            avatarParams.setMargins(0, 0, 32, 0)
            avatar.layoutParams = avatarParams

            val chatName = TextView(this).apply {
                text = name
                textSize = 18f
            }

            chatLayout.addView(avatar)
            chatLayout.addView(chatName)

            chatLayout.setOnClickListener {
                currentChatId = id
                showChatScreen(name)
            }

            mainLayout.addView(chatLayout)
            
            // Разделитель
            val divider = View(this).apply {
                setBackgroundColor(Color.LTGRAY)
            }
            mainLayout.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }

        setContentView(mainLayout)
        connectToServer()
    }

    // 3. ЭКРАН САМОГО ЧАТА
    private fun showChatScreen(chatName: String) {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Шапка чата
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 32, 16, 16)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }

        val backButton = Button(this).apply { text = "<" }
        backButton.setOnClickListener {
            currentChatId = ""
            showChatListScreen()
        }
        
        val headerTitle = TextView(this).apply {
            text = chatName
            textSize = 20f
            setPadding(16, 0, 0, 0)
        }

        headerLayout.addView(backButton)
        headerLayout.addView(headerTitle)
        mainLayout.addView(headerLayout)

        // Область сообщений
        val scrollView = ScrollView(this)
        val messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        scrollView.addView(messagesLayout)
        
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        ))

        // Поле ввода
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        val editText = EditText(this).apply { hint = "Сообщение..." }
        inputLayout.addView(editText, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        ))

        val sendButton = Button(this).apply { text = "->" }
        inputLayout.addView(sendButton)
        mainLayout.addView(inputLayout)

        setContentView(mainLayout)

        // Отправка сообщения
        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank() && currentChatId.isNotEmpty()) {
                val json = JSONObject()
                json.put("room", currentChatId)
                json.put("user", userName)
                json.put("text", text)
                
                webSocket?.send(json.toString())
                editText.text.clear()
            }
        }

        // Обновляем сообщения при получении
        // (логика добавления сообщений в messagesLayout)
    }

    private fun connectToServer() {
        if (isConnecting) return
        isConnecting = true
        
        val request = Request.Builder().url("wss://maxchat.relaxdev.ru/").build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val room = json.getString("room")
                    val user = json.optString("user", "Аноним")
                    val msgText = json.getString("text")
                    
                    // Если сообщение из текущего открытого чата
                    if (room == currentChatId) {
                        runOnUiThread {
                            // Находим текущий экран и добавляем сообщение
                            val root = window.decorView.findViewById<LinearLayout>(android.R.id.content)
                            val scrollView = (root.getChildAt(0) as LinearLayout).getChildAt(1) as ScrollView
                            val messagesLayout = scrollView.getChildAt(0) as LinearLayout
                            
                            val messageView = TextView(this@MainActivity).apply {
                                this.text = "$user: $msgText"
                                textSize = 18f
                                setPadding(0, 8, 0, 8)
                            }
                            messagesLayout.addView(messageView)
                            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        }
                    }
                } catch (e: Exception) {
                    // Обработка ошибок
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                // Переподключение
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    connectToServer()
                }, 3000)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Bye")
    }
}
