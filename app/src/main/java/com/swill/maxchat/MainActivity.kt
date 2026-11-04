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
    private var chatPartnerName: String = ""
    
    private val adminPhone = "+79111426979" 
    private val chatHistory = mutableListOf<Pair<String, String>>()

    private val PICK_IMAGE_REQUEST = 1
    private val PICK_VIDEO_REQUEST = 2

    private val cloudName = "bo2cg6du"
    private val uploadPreset = "maxchat_upload"

    private var typingListener: ValueEventListener? = null
    private var onlineListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance().getReference("chats")
        usersRef = FirebaseDatabase.getInstance().getReference("users")

        createNotificationChannel()

        val prefs = getSharedPreferences("MaxChatPrefs", Context.MODE_PRIVATE)
        myPhone = prefs.getString("phone", "") ?: ""
        myName = prefs.getString("name", "") ?: ""
        loadChatHistory()

        if (myPhone.isEmpty() || myName.isEmpty()) {
            showAuthScreen()
        } else {
            // Отмечаем себя как онлайн
            usersRef.child(myPhone).child("online").setValue(true)
            showChatListScreen()
            listenForNewChats()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (myPhone.isNotEmpty()) {
            usersRef.child(myPhone).child("online").setValue(false)
        }
        typingListener?.let { currentChatId?.let { chatId -> database.child(chatId).child("typing").removeEventListener(it) } }
        onlineListener?.let { chatPartnerName.let { name -> /* userRef.removeEventListener */ } }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "MaxChat Notifications"
            val descriptionText = "Уведомления о новых сообщениях"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("maxchat_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(senderName: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, "maxchat_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Новое сообщение от $senderName")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        notificationManager.notify(1, builder.build())
    }

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
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(64, 64, 64, 64); gravity = Gravity.CENTER; setBackgroundColor(Color.WHITE) }
        val title = TextView(this).apply { text = "Вход в MaxChat"; textSize = 24f; setPadding(0, 0, 0, 32); gravity = Gravity.CENTER }
        val nameInput = EditText(this).apply { hint = "Ваше имя (при регистрации)"; textSize = 18f; visibility = View.GONE }
        val phoneInput = EditText(this).apply { hint = "Номер телефона (+7999...)"; textSize = 18f }
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
        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val headerLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); setBackgroundColor(Color.parseColor("#007bff")); gravity = Gravity.CENTER_VERTICAL }
        val titleText = TextView(this).apply { text = "MaxChat ($myName)"; textSize = 20f; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f) }
        val searchButton = Button(this).apply { text = "Найти"; setTextColor(Color.parseColor("#007bff")); setBackgroundColor(Color.WHITE) }
        val logoutButton = Button(this).apply { text = "Выйти"; setTextColor(Color.RED); setBackgroundColor(Color.WHITE) }
        headerLayout.addView(titleText); headerLayout.addView(searchButton); headerLayout.addView(logoutButton); mainLayout.addView(headerLayout)

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
                                req.addView(TextView(this@MainActivity).apply { text = "Запрос от $userPhone"; textSize = 16f; setPadding(16, 0, 0, 0) })
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
            sup.addView(TextView(this@MainActivity).apply { text = "Поддержка"; textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(32, 0, 0, 0) })
            sup.setOnClickListener { showChatScreen("Поддержка", "support_$myPhone") }
            chatListLayout.addView(sup)
            chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }

        val gen = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_VERTICAL }
        gen.addView(ImageView(this@MainActivity).apply { setImageResource(android.R.drawable.ic_menu_agenda); layoutParams = LinearLayout.LayoutParams(120, 120) })
        gen.addView(TextView(this@MainActivity).apply { text = "Общий чат"; textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(32, 0, 0, 0) })
        gen.setOnClickListener { addChatToHistory("general", "Общий чат"); showChatScreen("Общий чат", "general") }
        chatListLayout.addView(gen)
        chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        for ((id, name) in chatHistory) {
            if (id == "general" || id.startsWith("support_")) continue
            val chat = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_VERTICAL }
            chat.addView(ImageView(this@MainActivity).apply { setImageResource(android.R.drawable.ic_menu_myplaces); layoutParams = LinearLayout.LayoutParams(120, 120) })
            chat.addView(TextView(this@MainActivity).apply { text = name; textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(32, 0, 0, 0) })
            chat.setOnClickListener { showChatScreen(name, id) }
            chatListLayout.addView(chat)
            chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }

        scrollView.addView(chatListLayout); mainLayout.addView(scrollView); setContentView(mainLayout)
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
        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val headerLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 32, 16, 16); setBackgroundColor(Color.parseColor("#007bff")); gravity = Gravity.CENTER_VERTICAL }
        val backButton = Button(this).apply { text = "<" }
        backButton.setOnClickListener { showChatListScreen() }
        
        // Блок с именем и статусом
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
        messagesLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); setBackgroundColor(Color.parseColor("#f0f0f0")) }
        scrollView.addView(messagesLayout)
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f))

        val inputLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 16, 16, 16); setBackgroundColor(Color.WHITE) }
        
        val attachButton = Button(this).apply { text = "+"; textSize = 24f }
        inputLayout.addView(attachButton)

        val editText = EditText(this).apply { hint = "Сообщение..." }
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

        // Слушатель "Печатает..."
        editText.addTextChangedListener(object : android.text.TextWatcher {
            override
