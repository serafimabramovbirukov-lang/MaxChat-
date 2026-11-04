package com.swill.maxchat

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.google.firebase.database.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var database: DatabaseReference
    private lateinit var usersRef: DatabaseReference
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    
    private var currentChatId: String? = null
    private var myPhone: String = ""
    private var myName: String = ""
    private val adminPhone = "+79111426979" 
    private val chatHistory = mutableListOf<Pair<String, String>>()
    private var isDarkTheme = false

    private val PICK_IMAGE_REQUEST = 1
    private val PICK_VIDEO_REQUEST = 2

    private val cloudName = "bo2cg6du"
    private val uploadPreset = "maxchat_upload"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance().getReference("chats")
        usersRef = FirebaseDatabase.getInstance().getReference("users")
        createNotificationChannel()

        val prefs = getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE)
        myPhone = prefs.getString("phone", "") ?: ""
        myName = prefs.getString("name", "") ?: ""
        isDarkTheme = prefs.getBoolean("darkTheme", false)
        loadChatHistory()

        if (myPhone.isEmpty() || myName.isEmpty()) {
            showAuthScreen()
        } else {
            usersRef.child(myPhone).child("online").setValue(true)
            showChatListScreen()
            listenForNewChats()
        }
    }

    override fun onPause() {
        super.onPause()
        if (myPhone.isNotEmpty()) {
            usersRef.child(myPhone).child("online").setValue(false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (myPhone.isNotEmpty()) {
            usersRef.child(myPhone).child("online").setValue(true)
        }
    }

    private fun toggleTheme() {
        isDarkTheme = !isDarkTheme
        getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE).edit().putBoolean("darkTheme", isDarkTheme).apply()
        if (currentChatId != null) {
            showChatScreen(chatHistory.find { it.first == currentChatId }?.second ?: "Чат", currentChatId!!)
        } else {
            showChatListScreen()
        }
    }
    // --- УВЕДОМЛЕНИЯ ---
private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel("maxchat_channel", "MaxChat Notifications", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

private fun showNotification(senderName: String, text: String) {
    val builder = NotificationCompat.Builder(this, "maxchat_channel")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Новое сообщение от $senderName")
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
    getSystemService(NotificationManager::class.java).notify(1, builder.build())
}

// --- ПОИСК НОВЫХ ЧАТОВ ---
private fun listenForNewChats() {
    database.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            for (child in snapshot.children) {
                val chatId = child.key ?: continue
                if (chatId.contains(myPhone) && chatId != "general") {
                    if (chatHistory.none { it.first == chatId }) {
                        val otherPhone = chatId.replace("support_", "").replace(myPhone, "").replace("_", "")
                        if (otherPhone.isNotEmpty() && otherPhone != myPhone) {
                            usersRef.child(otherPhone).addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userSnapshot: DataSnapshot) {
                                    val name = userSnapshot.child("name").getValue(String::class.java) ?: "Пользователь $otherPhone"
                                    addChatToHistory(chatId, name)
                                    showChatListScreen()
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            })
                        }
                    }
                }
            }
        }
        override fun onCancelled(error: DatabaseError) {}
    })
}
// --- ЭКРАН ВХОДА ---
private fun showAuthScreen() {
    val bgColor = if (isDarkTheme) Color.BLACK else Color.WHITE
    val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
    
    val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(64, 64, 64, 64); gravity = Gravity.CENTER; setBackgroundColor(bgColor) }
    val title = TextView(this).apply { text = "Вход в MaxChat"; textSize = 24f; setTextColor(textColor); setPadding(0, 0, 0, 32); gravity = Gravity.CENTER }
    val nameInput = EditText(this).apply { hint = "Ваше имя"; textSize = 18f; visibility = View.GONE }
    val phoneInput = EditText(this).apply { hint = "Номер телефона"; textSize = 18f }
    val passwordInput = EditText(this).apply { hint = "Пароль"; textSize = 18f; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
    val loginButton = Button(this).apply { text = "Войти"; textSize = 18f }
    val registerButton = Button(this).apply { text = "Зарегистрироваться"; textSize = 18f; setBackgroundColor(Color.GREEN); setTextColor(Color.WHITE) }
    
    layout.addView(title); layout.addView(nameInput); layout.addView(phoneInput); layout.addView(passwordInput); layout.addView(loginButton); layout.addView(registerButton)
    setContentView(layout)

    registerButton.setOnClickListener {
        if (nameInput.visibility == View.GONE) { nameInput.visibility = View.VISIBLE; registerButton.text = "Завершить"; loginButton.visibility = View.GONE } 
        else {
            val name = nameInput.text.toString().trim(); val phone = phoneInput.text.toString().trim(); val pass = passwordInput.text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty() && pass.isNotEmpty()) {
                usersRef.child(phone).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) Toast.makeText(this@MainActivity, "Номер занят!", Toast.LENGTH_SHORT).show()
                        else {
                            usersRef.child(phone).setValue(mapOf("name" to name, "phone" to phone, "password" to pass, "online" to true))
                            myName = name; myPhone = phone; saveLogin(); showChatListScreen(); listenForNewChats()
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            } else Toast.makeText(this, "Заполните поля!", Toast.LENGTH_SHORT).show()
        }
    }

    loginButton.setOnClickListener {
        val phone = phoneInput.text.toString().trim(); val pass = passwordInput.text.toString().trim()
        if (phone.isNotEmpty() && pass.isNotEmpty()) {
            usersRef.child(phone).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        if (snapshot.child("password").getValue(String::class.java) == pass) {
                            myName = snapshot.child("name").getValue(String::class.java) ?: "User"
                            myPhone = phone
                            usersRef.child(myPhone).child("online").setValue(true)
                            saveLogin(); showChatListScreen(); listenForNewChats()
                        } else Toast.makeText(this@MainActivity, "Неверный пароль!", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this@MainActivity, "Нет такого пользователя", Toast.LENGTH_SHORT).show()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else Toast.makeText(this, "Введите данные", Toast.LENGTH_SHORT).show()
    }
}

// --- СПИСОК ЧАТОВ ---
private fun showChatListScreen() {
    currentChatId = null
    val bgColor = if (isDarkTheme) Color.BLACK else Color.WHITE
    val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK

    val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
    val headerLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); setBackgroundColor(Color.parseColor("#007bff")); gravity = Gravity.CENTER_VERTICAL }
    val titleText = TextView(this).apply { text = "MaxChat ($myName)"; textSize = 20f; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f) }
    val themeButton = Button(this).apply { text = if (isDarkTheme) "☀️" else "🌙"; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT) }
    val searchButton = Button(this).apply { text = "Найти"; setTextColor(Color.parseColor("#007bff")); setBackgroundColor(Color.WHITE) }
    val logoutButton = Button(this).apply { text = "Выйти"; setTextColor(Color.RED); setBackgroundColor(Color.WHITE) }
    
    headerLayout.addView(titleText); headerLayout.addView(themeButton); headerLayout.addView(searchButton); headerLayout.addView(logoutButton); mainLayout.addView(headerLayout)

    val scrollView = ScrollView(this); val chatListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    
    if (myPhone == adminPhone) {
        val supportHeader = TextView(this).apply { text = "Запросы в поддержку:"; textSize = 16f; setPadding(32, 16, 32, 16); setTextColor(Color.GRAY); setTypeface(null, android.graphics.Typeface.BOLD) }
        chatListLayout.addView(supportHeader)
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val chatId = child.key ?: continue
                    if (chatId.startsWith("support_")) {
                        val userPhone = chatId.replace("support_", "")
                        if (userPhone != adminPhone) {
                            val req = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 16, 32, 16); gravity = Gravity.CENTER_VERTICAL }
                            req.addView(ImageView(this@MainActivity).apply { setImageResource(android.R.drawable.ic_menu_help); layoutParams = LinearLayout.LayoutParams(100, 100) })
                            req.addView(TextView(this@MainActivity).apply { text = "Запрос от $userPhone"; textSize = 16f; setTextColor(textColor); setPadding(16, 0, 0, 0) })
                            req.setOnClickListener { showChatScreen("Поддержка: $userPhone", chatId) }
                            chatListLayout.addView(req)
                            chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    } else {
        val sup = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_VERTICAL }
        sup.addView(ImageView(this@MainActivity).apply { setImageResource(android.R.drawable.ic_menu_help); layoutParams = LinearLayout.LayoutParams(120, 120) })
        sup.addView(TextView(this@MainActivity).apply { text = "Поддержка"; textSize = 18f; setTextColor(textColor); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(32, 0, 0, 0) })
        sup.setOnClickListener { showChatScreen("Поддержка", "support_$myPhone") }
        chatListLayout.addView(sup)
        chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
    }

    val gen = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_VERTICAL }
    gen.addView(ImageView(this@MainActivity).apply { setImageResource(android.R.drawable.ic_menu_agenda); layoutParams = LinearLayout.LayoutParams(120, 120) })
    gen.addView(TextView(this@MainActivity).apply { text = "Общий чат"; textSize = 18f; setTextColor(textColor); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(32, 0, 0, 0) })
    gen.setOnClickListener { addChatToHistory("general", "Общий чат"); showChatScreen("Общий чат", "general") }
    chatListLayout.addView(gen)
    chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

    for ((id, name) in chatHistory) {
        if (id == "general" || id.startsWith("support_")) continue
        val chat = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_VERTICAL }
        chat.addView(ImageView(this@MainActivity).apply { setImageResource(android.R.drawable.ic_menu_myplaces); layoutParams = LinearLayout.LayoutParams(120, 120) })
        chat.addView(TextView(this@MainActivity).apply { text = name; textSize = 18f; setTextColor(textColor); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(32, 0, 0, 0) })
        chat.setOnClickListener { showChatScreen(name, id) }
        chatListLayout.addView(chat)
        chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
    }

    scrollView.addView(chatListLayout); mainLayout.addView(scrollView); setContentView(mainLayout)
    
    themeButton.setOnClickListener { toggleTheme() }
    searchButton.setOnClickListener { showSearchDialog() }
    logoutButton.setOnClickListener { AlertDialog.Builder(this).setTitle("Выйти?").setPositiveButton("Да") { _, _ -> logout() }.setNegativeButton("Нет", null).show() }
}
// --- ЭКРАН ВХОДА ---
private fun showAuthScreen() {
    val bgColor = if (isDarkTheme) Color.BLACK else Color.WHITE
    val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
    
    val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(64, 64, 64, 64); gravity = Gravity.CENTER; setBackgroundColor(bgColor) }
    val title = TextView(this).apply { text = "Вход в MaxChat"; textSize = 24f; setTextColor(textColor); setPadding(0, 0, 0, 32); gravity = Gravity.CENTER }
    val nameInput = EditText(this).apply { hint = "Ваше имя"; textSize = 18f; visibility = View.GONE }
    val phoneInput = EditText(this).apply { hint = "Номер телефона"; textSize = 18f }
    val passwordInput = EditText(this).apply { hint = "Пароль"; textSize = 18f; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
    val loginButton = Button(this).apply { text = "Войти"; textSize = 18f }
    val registerButton = Button(this).apply { text = "Зарегистрироваться"; textSize = 18f; setBackgroundColor(Color.GREEN); setTextColor(Color.WHITE) }
    
    layout.addView(title); layout.addView(nameInput); layout.addView(phoneInput); layout.addView(passwordInput); layout.addView(loginButton); layout.addView(registerButton)
    setContentView(layout)

    registerButton.setOnClickListener {
        if (nameInput.visibility == View.GONE) { nameInput.visibility = View.VISIBLE; registerButton.text = "Завершить"; loginButton.visibility = View.GONE } 
        else {
            val name = nameInput.text.toString().trim(); val phone = phoneInput.text.toString().trim(); val pass = passwordInput.text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty() && pass.isNotEmpty()) {
                usersRef.child(phone).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) Toast.makeText(this@MainActivity, "Номер занят!", Toast.LENGTH_SHORT).show()
                        else {
                            usersRef.child(phone).setValue(mapOf("name" to name, "phone" to phone, "password" to pass, "online" to true))
                            myName = name; myPhone = phone; saveLogin(); showChatListScreen(); listenForNewChats()
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            } else Toast.makeText(this, "Заполните поля!", Toast.LENGTH_SHORT).show()
        }
    }

    loginButton.setOnClickListener {
        val phone = phoneInput.text.toString().trim(); val pass = passwordInput.text.toString().trim()
        if (phone.isNotEmpty() && pass.isNotEmpty()) {
            usersRef.child(phone).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        if (snapshot.child("password").getValue(String::class.java) == pass) {
                            myName = snapshot.child("name").getValue(String::class.java) ?: "User"
                            myPhone = phone
                            usersRef.child(myPhone).child("online").setValue(true)
                            saveLogin(); showChatListScreen(); listenForNewChats()
                        } else Toast.makeText(this@MainActivity, "Неверный пароль!", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this@MainActivity, "Нет такого пользователя", Toast.LENGTH_SHORT).show()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else Toast.makeText(this, "Введите данные", Toast.LENGTH_SHORT).show()
    }
}

// --- СПИСОК ЧАТОВ ---
private fun showChatListScreen() {
    currentChatId = null
    val bgColor = if (isDarkTheme) Color.BLACK else Color.WHITE
    val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK

    val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
    val headerLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); setBackgroundColor(Color.parseColor("#007bff")); gravity = Gravity.CENTER_VERTICAL }
    val titleText = TextView(this).apply { text = "MaxChat ($myName)"; textSize = 20f; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f) }
    val themeButton = Button(this).apply { text = if (isDarkTheme) "☀️" else "🌙"; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT) }
    val searchButton = Button(this).apply { text = "Найти"; setTextColor(Color.parseColor("#007bff")); setBackgroundColor(Color.WHITE) }
    val logoutButton = Button(this).apply { text = "Выйти"; setTextColor(Color.RED); setBackgroundColor(Color.WHITE) }
    
    headerLayout.addView(titleText); headerLayout.addView(themeButton); headerLayout.addView(searchButton); headerLayout.addView(logoutButton); mainLayout.addView(headerLayout)

    val scrollView = ScrollView(this); val chatListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    
    if (myPhone == adminPhone) {
        val supportHeader = TextView(this).apply { text = "Запросы в поддержку:"; textSize = 16f; setPadding(32, 16, 32, 16); setTextColor(Color.GRAY); setTypeface(null, android.graphics.Typeface.BOLD) }
        chatListLayout.addView(supportHeader)
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val chatId = child.key ?: continue
                    if (chatId.startsWith("support_")) {
                        val userPhone = chatId.replace("support_", "")
                        if (userPhone != adminPhone) {
                            val req = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 16, 32, 16); gravity = Gravity.CENTER_VERTICAL }
                            req.addView(ImageView(this@MainActivity).apply { setImageResource(android.R.drawable.ic_menu_help); layoutParams = LinearLayout.LayoutParams(100, 100) })
                            req.addView(TextView(this@MainActivity).apply { text = "Запрос от $userPhone"; textSize = 16f; setTextColor(textColor); setPadding(16, 0, 0, 0) })
                            req.setOnClickListener { showChatScreen("Поддержка: $userPhone", chatId) }
                            chatListLayout.addView(req)
                            chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    } else {
        val sup = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_VERTICAL }
        sup.addView(ImageView(this@MainActivity).apply { setImageResource(android.R.drawable.ic_menu_help); layoutParams = LinearLayout.LayoutParams(120, 120) })
        sup.addView(TextView(this@MainActivity).apply { text = "Поддержка"; textSize = 18f; setTextColor(textColor); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(32, 0, 0, 0) })
        sup.setOnClickListener { showChatScreen("Поддержка", "support_$myPhone") }
        chatListLayout.addView(sup)
        chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
    }

    val gen = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_VERTICAL }
    gen.addView(ImageView(this@MainActivity).apply { setImageResource(android.R.drawable.ic_menu_agenda); layoutParams = LinearLayout.LayoutParams(120, 120) })
    gen.addView(TextView(this@MainActivity).apply { text = "Общий чат"; textSize = 18f; setTextColor(textColor); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(32, 0, 0, 0) })
    gen.setOnClickListener { addChatToHistory("general", "Общий чат"); showChatScreen("Общий чат", "general") }
    chatListLayout.addView(gen)
    chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

    for ((id, name) in chatHistory) {
        if (id == "general" || id.startsWith("support_")) continue
        val chat = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_VERTICAL }
        chat.addView(ImageView(this@MainActivity).apply { setImageResource(android.R.drawable.ic_menu_myplaces); layoutParams = LinearLayout.LayoutParams(120, 120) })
        chat.addView(TextView(this@MainActivity).apply { text = name; textSize = 18f; setTextColor(textColor); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(32, 0, 0, 0) })
        chat.setOnClickListener { showChatScreen(name, id) }
        chatListLayout.addView(chat)
        chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
    }

    scrollView.addView(chatListLayout); mainLayout.addView(scrollView); setContentView(mainLayout)
    
    themeButton.setOnClickListener { toggleTheme() }
    searchButton.setOnClickListener { showSearchDialog() }
    logoutButton.setOnClickListener { AlertDialog.Builder(this).setTitle("Выйти?").setPositiveButton("Да") { _, _ -> logout() }.setNegativeButton("Нет", null).show() }
}
    // --- ПОИСК ---
    private fun showSearchDialog() {
        val input = EditText(this).apply { hint = "Введите номер" }
        AlertDialog.Builder(this).setTitle("Найти пользователя").setView(input)
            .setPositiveButton("Найти") { _, _ ->
                val searchPhone = input.text.toString().trim()
                if (searchPhone.isNotEmpty() && searchPhone != myPhone) {
                    usersRef.child(searchPhone).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                val foundName = snapshot.child("name").getValue(String::class.java) ?: "Пользователь"
                                val chatId = if (myPhone < searchPhone) "${myPhone}_$searchPhone" else "${searchPhone}_$myPhone"
                                addChatToHistory(chatId, foundName); showChatScreen(foundName, chatId)
                            } else { Toast.makeText(this@MainActivity, "Не найден", Toast.LENGTH_SHORT).show() }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }.setNegativeButton("Отмена", null).show()
    }

    // --- ЭКРАН ЧАТА ---
    private fun showChatScreen(chatName: String, chatId: String) {
        currentChatId = chatId
        val bgColor = if (isDarkTheme) Color.BLACK else Color.WHITE
        val msgBgColor = if (isDarkTheme) Color.parseColor("#222222") else Color.WHITE
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK

        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
        val headerLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 32, 16, 16); setBackgroundColor(Color.parseColor("#007bff")); gravity = Gravity.CENTER_VERTICAL }
        val backButton = Button(this).apply { text = "<" }
        backButton.setOnClickListener { showChatListScreen() }
        
        val headerTextLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f) }
        val headerTitle = TextView(this).apply { text = chatName; textSize = 20f; setTextColor(Color.WHITE) }
        val statusText = TextView(this).apply { text = "Онлайн"; textSize = 12f; setTextColor(Color.GREEN) }
        headerTextLayout.addView(headerTitle)
        headerTextLayout.addView(statusText)

        headerLayout.addView(backButton); headerLayout.addView(headerTextLayout)
        
        if (myPhone == adminPhone && chatId.startsWith("support_")) {
            val deleteButton = Button(this).apply { text = "X"; setTextColor(Color.RED); setBackgroundColor(Color.WHITE) }
            headerLayout.addView(deleteButton)
            deleteButton.setOnClickListener { AlertDialog.Builder(this).setTitle("Удалить?").setPositiveButton("Да") { _, _ -> database.child(chatId).removeValue(); showChatListScreen() }.setNegativeButton("Нет", null).show() }
        }
        
        mainLayout.addView(headerLayout)

        scrollView = ScrollView(this)
        messagesLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); setBackgroundColor(bgColor) }
        scrollView.addView(messagesLayout)
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f))

        val inputLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 16, 16, 16); setBackgroundColor(if (isDarkTheme) Color.parseColor("#333333") else Color.WHITE) }
        
        val attachButton = Button(this).apply { text = "+"; textSize = 24f }
        inputLayout.addView(attachButton)

        val editText = EditText(this).apply { hint = "Сообщение..."; setTextColor(textColor); setHintTextColor(Color.GRAY) }
        inputLayout.addView(editText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f))
        val sendButton = Button(this).apply { text = "->" }
        inputLayout.addView(sendButton)
        mainLayout.addView(inputLayout)

        attachButton.setOnClickListener {
            val options = arrayOf("Фото", "Видео")
            AlertDialog.Builder(this).setTitle("Отправить").setItems(options) { _, which ->
                if (which == 0) {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(intent, PICK_IMAGE_REQUEST)
                } else {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(intent, PICK_VIDEO_REQUEST)
                }
            }.show()
        }

        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (chatId != "general" && !chatId.startsWith("support_")) {
                    database.child(chatId).child("typing_$myPhone").setValue(s.toString().isNotEmpty())
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                val message = mapOf("user" to myName, "phone" to myPhone, "text" to text, "type" to "text", "timestamp" to System.currentTimeMillis(), "read" to false)
                database.child(chatId).push().setValue(message)
                database.child(chatId).child("typing_$myPhone").setValue(false)
                editText.text.clear()
            }
        }

        setContentView(mainLayout)
        listenForMessages(chatId)

        val partnerPhone = chatId.replace("support_", "").replace(myPhone, "").replace("_", "")
        if (partnerPhone.isNotEmpty() && partnerPhone != myPhone) {
            usersRef.child(partnerPhone).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isOnline = snapshot.child("online").getValue(Boolean::class.java) ?: false
                    runOnUiThread { statusText.text = if (isOnline) "Онлайн" else "Не в сети" }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    // --- ОТОБРАЖЕНИЕ ---
    private fun listenForMessages(chatId: String) {
        database.child(chatId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesLayout.removeAllViews()
                val msgBgColor = if (isDarkTheme) Color.parseColor("#222222") else Color.WHITE
                val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
                
                for (child in snapshot.children) {
                    val senderName = child.child("user").getValue(String::class.java) ?: "Аноним"
                    val senderPhone = child.child("phone").getValue(String::class.java) ?: ""
                    val content = child.child("text").getValue(String::class.java) ?: ""
                    val type = child.child("type").getValue(String::class.java) ?: "text"
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val isRead = child.child("read").getValue(Boolean::class.java) ?: false
                    
                    if (senderPhone != myPhone && type == "text") {
                        showNotification(senderName, content)
                        child.ref.child("read").setValue(true)
                    }

                    val container = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 8); setBackgroundColor(msgBgColor)
                        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        params.setMargins(0, 4, 0, 4); layoutParams = params
                    }
                    val nameView = TextView(this@MainActivity).apply { text = senderName; textSize = 12f; setTextColor(Color.parseColor("#007bff")); setTypeface(null, android.graphics.Typeface.BOLD) }
                    container.addView(nameView)

                    when (type) {
                        "image" -> {
                            val img = ImageView(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(600, 600); scaleType = ImageView.ScaleType.CENTER_CROP }
                            Glide.with(this@MainActivity).load(content).into(img)
                            container.addView(img)
                        }
                        "video" -> {
                            val playerView = PlayerView(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(600, 400) }
                            val exoPlayer = ExoPlayer.Builder(this@MainActivity).build()
                            exoPlayer.setMediaItem(MediaItem.fromUri(content))
                            exoPlayer.prepare()
                            playerView.player = exoPlayer
                            container.addView(playerView)
                        }
                        else -> {
                            val textView = TextView(this@MainActivity).apply { text = content; textSize = 16f; setTextColor(textColor) }
                            container.addView(textView)
                        }
                    }

                    val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                    val infoText = if (senderPhone == myPhone) {
                        val checkMark = if (isRead) "✓✓" else "✓"
                        "$timeString $checkMark"
                    } else {
                        timeString
                    }
                    val timeView = TextView(this@MainActivity).apply { text = infoText; textSize = 10f; setTextColor(Color.GRAY); gravity = Gravity.END }
                    container.addView(timeView)

                    messagesLayout.addView(container)
                }
                
                val partnerPhone = chatId.replace("support_", "").replace(myPhone, "").replace("_", "")
                val isTyping = snapshot.child("typing_$partnerPhone").getValue(Boolean::class.java) ?: false
                if (isTyping) {
                    val typingView = TextView(this@MainActivity).apply { text = "Печатает..."; textSize = 14f; setTextColor(Color.GRAY); setPadding(16, 8, 16, 8) }
                    messagesLayout.addView(typingView)
                }
                
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- ЗАГРУЗКА ФАЙЛОВ ---
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null && data.data != null) {
            val uri = data.data!!
            if (requestCode == PICK_IMAGE_REQUEST) uploadToCloudinary(uri, "image")
            else if (requestCode == PICK_VIDEO_REQUEST) uploadToCloudinary(uri, "video")
        }
    }

    private fun uploadToCloudinary(uri: Uri, type: String) {
        val chatId = currentChatId ?: return
        Toast.makeText(this, "Загрузка...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    val client = OkHttpClient()
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "file", bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                        .addFormDataPart("upload_preset", uploadPreset)
                        .build()

                    val resourceType = if (type == "image") "image" else "video"
                    val url = "https://api.cloudinary.com/v1_1/$cloudName/$resourceType/upload"
                    val request = Request.Builder().url(url).post(requestBody).build()
                    
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && responseBody != null) {
                        val json = JSONObject(responseBody)
                        val secureUrl = json.getString("secure_url")
                        
                        runOnUiThread {
                            val message = mapOf("user" to myName, "phone" to myPhone, "text" to secureUrl, "type" to type, "timestamp" to System.currentTimeMillis(), "read" to false)
                            database.child(chatId).push().setValue(message)
                        }
                    } else {
                        runOnUiThread { Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show() }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ---
    private fun saveLogin() { getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE).edit().putString("name", myName).putString("phone", myPhone).apply() }
    private fun logout() { 
        if (myPhone.isNotEmpty()) usersRef.child(myPhone).child("online").setValue(false)
        getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        myPhone = ""; myName = ""; chatHistory.clear(); showAuthScreen() 
    }
    private fun saveChatHistory() { getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE).edit().putString("chatHistory", chatHistory.joinToString("|") { "${it.first}:${it.second}" }).apply() }
    private fun loadChatHistory() { val h = getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE).getString("chatHistory", "") ?: ""; if (h.isNotEmpty()) { chatHistory.clear(); h.split("|").forEach { val p = it.split(":"); if (p.size == 2) chatHistory.add(Pair(p[0], p[1])) } } }
    private fun addChatToHistory(id: String, name: String) { if (chatHistory.none { it.first == id }) { chatHistory.add(Pair(id, name)); saveChatHistory() } }
}
