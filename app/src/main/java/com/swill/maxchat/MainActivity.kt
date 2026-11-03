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

    // Список чатов: ID, Название, Иконка (из стандартных), Последнее сообщение (для теста)
    private val chats = listOf(
        Triple("general", "Общий чат", android.R.drawable.ic_menu_agenda),
        Triple("friends", "Друзья", android.R.drawable.ic_menu_myplaces),
        Triple("work", "Работа", android.R.drawable.ic_menu_manage)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance().getReference("chats")
        showChatListScreen()
    }

    // Экран списка чатов (как в Telegram/WhatsApp)
    private fun showChatListScreen() {
        currentChatId = null
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Шапка приложения (синяя)
        val header = TextView(this).apply {
            text = "MaxChat"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#007bff"))
            setPadding(32, 32, 32, 32)
        }
        mainLayout.addView(header)

        val scrollView = ScrollView(this)
        val chatListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Проходим по списку чатов и создаем красивые элементы
        for ((id, name, iconRes) in chats) {
            val chatLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 32, 32, 32)
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.WHITE)
            }

            // Аватарка (картинка)
            val avatar = ImageView(this).apply {
                setImageResource(iconRes)
                setBackgroundColor(Color.parseColor("#E0E0E0"))
                val params = LinearLayout.LayoutParams(120, 120)
                params.setMargins(0, 0, 32, 0)
                layoutParams = params
            }
            chatLayout.addView(avatar)

            // Текст: Название и последнее сообщение
            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }

            val chatName = TextView(this).apply {
                text = name
                textSize = 18f
                setTextColor(Color.BLACK)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val lastMessage = TextView(this).apply {
                text = "Нажмите, чтобы открыть" // Заглушка, потом заменим на реальное сообщение
                textSize = 14f
                setTextColor(Color.GRAY)
            }

            textLayout.addView(chatName)
            textLayout.addView(lastMessage)
            chatLayout.addView(textLayout)

            // Время (справа)
            val timeView = TextView(this).apply {
                text = "12:00" // Заглушка, потом сделаем реальное время
                textSize = 12f
                setTextColor(Color.GRAY)
            }
            chatLayout.addView(timeView)

            chatLayout.setOnClickListener {
                currentChatId = id
                showChatScreen(name, id)
            }

            chatListLayout.addView(chatLayout)

            // Разделитель (тонкая линия)
            val divider = View(this).apply {
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
            chatListLayout.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }

        scrollView.addView(chatListLayout)
        mainLayout.addView(scrollView)
        setContentView(mainLayout)
    }

    // Экран самого чата
    private fun showChatScreen(chatName: String, chatId: String) {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 32, 16, 16)
            setBackgroundColor(Color.parseColor("#007bff"))
            gravity = Gravity.CENTER_VERTICAL
        }

        val backButton = Button(this).apply { text = "<" }
        backButton.setOnClickListener { showChatListScreen() }
        
        val headerTitle = TextView(this).apply {
            text = chatName
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(16, 0, 0, 0)
        }

        headerLayout.addView(backButton)
        headerLayout.addView(headerTitle)
        mainLayout.addView(headerLayout)

        // Область сообщений (светло-серый фон, как в мессенджерах)
        scrollView = ScrollView(this)
        messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#f0f0f0"))
        }
        scrollView.addView(messagesLayout)
        
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        ))

        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.WHITE)
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

        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                val message = mapOf("text" to text, "timestamp" to System.currentTimeMillis())
                database.child(chatId).push().setValue(message)
                editText.text.clear()
            }
        }

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
                        setPadding(16, 16, 16, 16)
                        setBackgroundColor(Color.WHITE)
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, 8, 0, 8)
                        layoutParams = params
                    }
                    messagesLayout.addView(messageView)
                }
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
