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
    private lateinit var database: DatabaseReference
    private lateinit var usersRef: DatabaseReference
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    
    private var currentChatId: String? = null
    private var myPhone: String = ""
    private var myName: String = ""
    
    private val chatHistory = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        database = FirebaseDatabase.getInstance().getReference("chats")
        usersRef = FirebaseDatabase.getInstance().getReference("users")

        val prefs = getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE)
        myPhone = prefs.getString("phone", "") ?: ""
        myName = prefs.getString("name", "") ?: ""

        loadChatHistory()

        // Проверяем, залогинен ли пользователь
        if (myPhone.isEmpty() || myName.isEmpty()) {
            showAuthScreen()
        } else {
            showChatListScreen()
        }
    }

    // --- ЭКРАН ВХОДА/РЕГИСТРАЦИИ ---
    private fun showAuthScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply { text = "Вход в MaxChat"; textSize = 24f; setPadding(0, 0, 0, 32); gravity = Gravity.CENTER }
        
        val nameInput = EditText(this).apply { hint = "Ваше имя (при регистрации)"; textSize = 18f; visibility = View.GONE }
        val phoneInput = EditText(this).apply { hint = "Номер телефона (+7999...)"; textSize = 18f }
        val passwordInput = EditText(this).apply { hint = "Пароль"; textSize = 18f; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        
        val loginButton = Button(this).apply { text = "Войти"; textSize = 18f }
        val registerButton = Button(this).apply { text = "Зарегистрироваться"; textSize = 18f; setBackgroundColor(Color.GREEN); setTextColor(Color.WHITE) }

        // Подсказка про правильность номера
        val warningText = TextView(this).apply {
            text = "Внимание! Вводите номер внимательно. Если ошибетесь, вас не смогут найти."
            textSize = 12f
            setTextColor(Color.RED)
            setPadding(0, 16, 0, 16)
            visibility = View.GONE
        }

        layout.addView(title)
        layout.addView(nameInput)
        layout.addView(phoneInput)
        layout.addView(passwordInput)
        layout.addView(warningText)
        layout.addView(loginButton)
        layout.addView(registerButton)
        setContentView(layout)

        // Переключение на режим регистрации
        registerButton.setOnClickListener {
            if (nameInput.visibility == View.GONE) {
                // Показываем поля для регистрации
                nameInput.visibility = View.VISIBLE
                warningText.visibility = View.VISIBLE
                registerButton.text = "Завершить регистрацию"
                loginButton.visibility = View.GONE
            } else {
                // Выполняем регистрацию
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val pass = passwordInput.text.toString().trim()

                if (name.isNotEmpty() && phone.isNotEmpty() && pass.isNotEmpty()) {
                    // Проверяем, занят ли номер
                    usersRef.child(phone).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                Toast.makeText(this@MainActivity, "Этот номер уже зарегистрирован!", Toast.LENGTH_SHORT).show()
                            } else {
                                // Регистрируем
                                val userProfile = mapOf("name" to name, "phone" to phone, "password" to pass)
                                usersRef.child(phone).setValue(userProfile)
                                
                                myName = name
                                myPhone = phone
                                saveLogin()
                                showChatListScreen()
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                } else {
                    Toast.makeText(this, "Заполните все поля!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Вход для существующих пользователей
        loginButton.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            val pass = passwordInput.text.toString().trim()

            if (phone.isNotEmpty() && pass.isNotEmpty()) {
                usersRef.child(phone).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val savedPass = snapshot.child("password").getValue(String::class.java) ?: ""
                            if (savedPass == pass) {
                                myName = snapshot.child("name").getValue(String::class.java) ?: "User"
                                myPhone = phone
                                saveLogin()
                                showChatListScreen()
                            } else {
                                Toast.makeText(this@MainActivity, "Неверный пароль!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Пользователь не найден. Зарегистрируйтесь.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            } else {
                Toast.makeText(this, "Введите номер и пароль", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveLogin() {
        getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE)
            .edit().putString("name", myName).putString("phone", myPhone).apply()
    }

    private fun logout() {
        getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        myPhone = ""
        myName = ""
        chatHistory.clear()
        showAuthScreen()
    }

    // Сохранение и загрузка истории
    private fun saveChatHistory() {
        val prefs = getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE)
        val historyString = chatHistory.joinToString("|") { "${it.first}:${it.second}" }
        prefs.edit().putString("chatHistory", historyString).apply()
    }

    private fun loadChatHistory() {
        val prefs = getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE)
        val historyString = prefs.getString("chatHistory", "") ?: ""
        if (historyString.isNotEmpty()) {
            chatHistory.clear()
            historyString.split("|").forEach {
                val parts = it.split(":")
                if (parts.size == 2) chatHistory.add(Pair(parts[0], parts[1]))
            }
        }
    }

    private fun addChatToHistory(chatId: String, chatName: String) {
        if (chatHistory.none { it.first == chatId }) {
            chatHistory.add(Pair(chatId, chatName))
            saveChatHistory()
        }
    }

    // --- СПИСОК ЧАТОВ ---
    private fun showChatListScreen() {
        currentChatId = null
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#007bff"))
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = "MaxChat ($myName)"
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val searchButton = Button(this).apply { text = "Найти"; setTextColor(Color.parseColor("#007bff")); setBackgroundColor(Color.WHITE) }
        val logoutButton = Button(this).apply { text = "Выйти"; setTextColor(Color.RED); setBackgroundColor(Color.WHITE) }

        headerLayout.addView(titleText)
        headerLayout.addView(searchButton)
        headerLayout.addView(logoutButton)
        mainLayout.addView(headerLayout)

        val scrollView = ScrollView(this)
        val chatListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Общий чат
        val generalChat = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER_VERTICAL
        }
        val generalIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_agenda)
            layoutParams = LinearLayout.LayoutParams(120, 120).apply { setMargins(0, 0, 32, 0) }
        }
        val generalText = TextView(this).apply { text = "Общий чат"; textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD) }
        generalChat.addView(generalIcon)
        generalChat.addView(generalText)
        generalChat.setOnClickListener { 
            addChatToHistory("general", "Общий чат")
            showChatScreen("Общий чат", "general") 
        }
        chatListLayout.addView(generalChat)
        
        chatListLayout.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E0E0E0")) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // Личные чаты
        for ((id, name) in chatHistory) {
            if (id == "general") continue
            val chatLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 32, 32, 32)
                gravity = Gravity.CENTER_VERTICAL
            }
            val icon = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_myplaces)
                layoutParams = LinearLayout.LayoutParams(120, 120).apply { setMargins(0, 0, 32, 0) }
            }
            val chatNameText = TextView(this).apply { text = name; textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD) }
            chatLayout.addView(icon)
            chatLayout.addView(chatNameText)
            chatLayout.setOnClickListener { showChatScreen(name, id) }
            chatListLayout.addView(chatLayout)
            chatListLayout.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E0E0E0")) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }

        scrollView.addView(chatListLayout)
        mainLayout.addView(scrollView)
        setContentView(mainLayout)

        searchButton.setOnClickListener { showSearchDialog() }
        logoutButton.setOnClickListener { 
            AlertDialog.Builder(this)
                .setTitle("Выйти?")
                .setMessage("Вы уверены, что хотите выйти из аккаунта?")
                .setPositiveButton("Да") { _, _ -> logout() }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    // --- ПОИСК ---
    private fun showSearchDialog() {
        val input = EditText(this).apply { hint = "Введите номер" }
        AlertDialog.Builder(this)
            .setTitle("Найти пользователя")
            .setView(input)
            .setPositiveButton("Найти") { _, _ ->
                val searchPhone = input.text.toString().trim()
                if (searchPhone.isNotEmpty()) {
                    if (searchPhone == myPhone) {
                        Toast.makeText(this, "Это ваш номер!", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    usersRef.child(searchPhone).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                val foundName = snapshot.child("name").getValue(String::class.java) ?: "Пользователь"
                                val chatId = if (myPhone < searchPhone) "${myPhone}_$searchPhone" else "${searchPhone}_$myPhone"
                                addChatToHistory(chatId, foundName)
                                showChatScreen(foundName, chatId)
                            } else {
                                Toast.makeText(this@MainActivity, "Пользователь не найден", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // --- ЧАТ ---
    private fun showChatScreen(chatName: String, chatId: String) {
        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

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

        headerLayout.addView(backButton)
        headerLayout.addView(headerTitle)
        mainLayout.addView(headerLayout)

        scrollView = ScrollView(this)
        messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#f0f0f0"))
        }
        scrollView.addView(messagesLayout)
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f))

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

        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                val message = mapOf(
                    "user" to myName,
                    "phone" to myPhone,
                    "text" to text, 
                    "timestamp" to System.currentTimeMillis()
                )
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
                    val senderName = child.child("user").getValue(String::class.java) ?: "Аноним"
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    
                    val messageContainer = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 8, 16, 8)
                        setBackgroundColor(Color.WHITE)
                        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
