package com.swill.maxchat

import android.graphics.Color
import android.os.Bundle
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.firebase.database.*

class MainActivity : Activity() {
    private var currentChatId: String? = null
    private lateinit var database: DatabaseReference
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var chatListLayout: LinearLayout

    // Список наших чатов (можно добавлять свои)
    private val chats = listOf(
        Triple("general", "Общий чат", "#4CAF50"),
        Triple("friends", "Друзья", "#2196F3"),
        Triple("work", "Работа", "#FF9800")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance().getReference("chats")
        showChatListScreen()
    }

    // Экран со списком чатов
    private fun showChatListScreen() {
        currentChatId = null
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

        chatListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

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
                showChatScreen(name, id)
            }

            chatListLayout.addView(chatLayout)
            
            val divider = View(this).apply {
                setBackgroundColor(Color.LTGRAY)
            }
            chatListLayout.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }

        mainLayout.addView(chatListLayout)
        setContentView(mainLayout)
    }

    // Экран самого чата
    private fun showChatScreen(chatName: String, chatId: String) {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Шапка чата с кнопкой "Назад"
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 32, 16, 16)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }

        val backButton = Button(this).apply { text = "<" }
        backButton.setOnClickListener {
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
        scrollView = ScrollView(this)
        messagesLayout = LinearLayout(this).apply {
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
            if (text.isNotBlank()) {
                val message = mapOf("text" to text, "timestamp" to System.currentTimeMillis())
                database.child(chatId).push().setValue(message)
                editText.text.clear()
            }
        }

        // Слушаем сообщения именно этого чата
        listenForMessages(chatId)
    }

    private fun listenForMessages(chatId: String) {
        database.child(chatId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesLayout.removeAllViews()
                for (child in snapshot.children) {
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    val messageView = TextView(this@MainActivity).apply {
                        this.text = text
                        textSize = 18f
                        setPadding(0, 8, 0, 8)
                    }
                    messagesLayout.addView(messageView)
                }
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
