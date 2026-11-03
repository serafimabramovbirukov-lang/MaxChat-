package com.swill.maxchat

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.firebase.database.*

class MainActivity : Activity() {
    private var currentChatId: String? = null
    private lateinit var database: DatabaseReference
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private var userName: String = ""

    private val chats = listOf(
        Triple("general", "Общий чат", android.R.drawable.ic_menu_agenda),
        Triple("friends", "Друзья", android.R.drawable.ic_menu_myplaces),
        Triple("work", "Работа", android.R.drawable.ic_menu_manage)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Проверяем, входил ли пользователь ранее
        val prefs = getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE)
        userName = prefs.getString("userName", "") ?: ""

        database = FirebaseDatabase.getInstance().getReference("chats")

        if (userName.isEmpty()) {
            showLoginScreen()
        } else {
            showChatListScreen()
        }
    }

    // ЭКРАН ВХОДА
    private fun showLoginScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Вход в MaxChat"
            textSize = 24f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }

        val nameInput = EditText(this).apply { 
            hint = "Введите ваше настоящее имя" 
            textSize = 18f
        }

        val loginButton = Button(this).apply { 
            text = "Войти" 
            textSize = 18f
        }

        layout.addView(title)
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
                Toast.makeText(this, "Имя обязательно для входа!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ЭКРАН СПИСКА ЧАТОВ
    private fun showChatListScreen() {
        currentChatId = null
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val header = TextView(this).apply {
            text = "MaxChat (Вы: $userName)"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#007bff"))
            setPadding(32, 32, 32, 32)
        }
        mainLayout.addView(header)

        val scrollView = ScrollView(this)
        val chatListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for ((id, name, iconRes) in chats) {
            val chatLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 32, 32, 32)
                gravity = Gravity.CENTER_VERTICAL
            }

            val avatar = ImageView(this).apply {
                setImageResource(iconRes)
                setBackgroundColor(Color.parseColor("#E0E0E0"))
                val params = LinearLayout.LayoutParams(120, 120)
                params.setMargins(0, 0, 32, 0)
                layoutParams = params
            }
            chatLayout.addView(avatar)

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

            textLayout.addView(chatName)
            chatLayout.addView(textLayout)

            chatLayout.setOnClickListener {
                currentChatId = id
                showChatScreen(name, id)
            }

            chatListLayout.addView(chatLayout)
            
            val divider = View(this).apply { setBackgroundColor(Color.parseColor("#E0E0E0")) }
            chatListLayout.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }

        scrollView.addView(chatListLayout)
        mainLayout.addView(scrollView)
        setContentView(mainLayout)
    }

    // ЭКРАН ЧАТА
    private fun showChatScreen(chatName: String, chatId: String) {
        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Шапка с кнопкой очистки
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val clearButton = Button(this).apply { 
            text = "Очистить" 
            setTextColor(Color.RED)
            setBackgroundColor(Color.WHITE)
        }

        headerLayout.addView(backButton)
        headerLayout.addView(headerTitle)
        headerLayout.addView(clearButton)
        mainLayout.addView(headerLayout)

        // Область сообщений
        scrollView = ScrollView(this)
        messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#f0f0f0"))
        }
        scrollView.addView(messagesLayout)
        
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        ))

        // Поле ввода
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.WHITE)
        }

        val editText = EditText(this).apply { hint = "Сообщение..." }
        inputLayout.addView(editText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f))

        val sendButton = Button(this).apply { text = "->" }
        inputLayout.addView(sendButton)
        mainLayout.addView(inputLayout)

        setContentView(mainLayout)

        // Отправка сообщения
        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                val message = mapOf(
                    "user" to userName,
                    "text" to text, 
                    "timestamp" to System.currentTimeMillis()
                )
                database.child(chatId).push().setValue(message)
                editText.text.clear()
            }
        }

        // Логика очистки чата
        clearButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Очистить чат?")
                .setMessage("Вы уверены, что хотите удалить все сообщения в этом чате?")
                .setPositiveButton("Да") { _, _ ->
                    database.child(chatId).removeValue()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Чат очищен", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        listenForMessages(chatId)
    }

    private fun listenForMessages(chatId: String) {
        database.child(chatId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesLayout.removeAllViews()
                for (child in snapshot.children) {
                    val senderName = child.child("user").getValue(String::class.java) ?: "Аноним"
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    
                    val messageContainer = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 8, 16, 8)
                        setBackgroundColor(Color.WHITE)
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, 4, 0, 4)
                        layoutParams = params
                    }

                    val nameView = TextView(this@MainActivity).apply {
                        this.text = senderName
                        textSize = 12f
                        setTextColor(Color.parseColor("#007bff"))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }

                    val textView = TextView(this@MainActivity).apply {
                        this.text = text
                        textSize = 16f
                        setTextColor(Color.BLACK)
                    }

                    messageContainer.addView(nameView)
                    messageContainer.addView(textView)
                    messagesLayout.addView(messageContainer)
                }
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
