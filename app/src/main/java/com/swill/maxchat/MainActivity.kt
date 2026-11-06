package com.swill.maxchat

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

    // Цвета Telegram
    private val tgHeaderBlue = Color.parseColor("#517DA2")
    private val tgBgLight = Color.parseColor("#EFEAE2")
    private val tgBgDark = Color.parseColor("#0E1621")
    private val tgMsgMyLight = Color.parseColor("#E1FFC7")
    private val tgMsgMyDark = Color.parseColor("#2B5278")
    private val tgMsgOtherLight = Color.WHITE
    private val tgMsgOtherDark = Color.parseColor("#182533")
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
    val bgColor = if (isDarkTheme) tgBgDark else Color.WHITE
    val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
    
    val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(64, 64, 64, 64); gravity = Gravity.CENTER; setBackgroundColor(bgColor) }
    val title = TextView(this).apply { text = "MaxChat"; textSize = 32f; setTextColor(tgHeaderBlue); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 32); gravity = Gravity.CENTER }
    
    val nameInput = EditText(this).apply { hint = "Ваше имя"; textSize = 16f; visibility = View.GONE }
    val phoneInput = EditText(this).apply { hint = "Номер телефона"; textSize = 16f }
    val passwordInput = EditText(this).apply { hint = "Пароль"; textSize = 16f; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
    
    val loginButton = Button(this).apply { text = "Войти"; setBackgroundColor(tgHeaderBlue); setTextColor(Color.WHITE) }
    val registerButton = Button(this).apply { text = "Регистрация"; setBackgroundColor(Color.GREEN); setTextColor(Color.WHITE) }
    
    layout.addView(title)
    layout.addView(nameInput)
    layout.addView(phoneInput)
    layout.addView(passwordInput)
    layout.addView(loginButton)
    layout.addView(registerButton)
    setContentView(layout)

    registerButton.setOnClickListener {
        if (nameInput.visibility == View.GONE) { 
            nameInput.visibility = View.VISIBLE
            registerButton.text = "Завершить"
            loginButton.visibility = View.GONE 
        } else {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val pass = passwordInput.text.toString().trim()
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
        val phone = phoneInput.text.toString().trim()
        val pass = passwordInput.text.toString().trim()
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
// --- СПИСОК ЧАТОВ (Стиль Telegram) ---
private fun showChatListScreen() {
    currentChatId = null
    val bgColor = if (isDarkTheme) tgBgDark else Color.WHITE
    val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
    val subTextColor = if (isDarkTheme) Color.parseColor("#7D8B99") else Color.GRAY

    val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
    
    // Шапка в стиле Telegram
    val headerLayout = LinearLayout(this).apply { 
        orientation = LinearLayout.HORIZONTAL
        setPadding(40, 40, 40, 24) 
        setBackgroundColor(tgHeaderBlue)
        gravity = Gravity.CENTER_VERTICAL 
    }
    val titleText = TextView(this).apply { 
        text = "MaxChat"; textSize = 22f; setTextColor(Color.WHITE); 
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f) 
    }
    val themeButton = Button(this).apply { text = if (isDarkTheme) "☀️" else "🌙"; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); textSize = 20f }
    val searchButton = Button(this).apply { text = "🔍"; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); textSize = 20f }
    val logoutButton = Button(this).apply { text = "Выйти"; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); textSize = 14f }
    
    headerLayout.addView(titleText)
    headerLayout.addView(themeButton)
    headerLayout.addView(searchButton)
    headerLayout.addView(logoutButton)
    mainLayout.addView(headerLayout)

    val scrollView = ScrollView(this)
    val chatListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    
    // Запросы в поддержку (для админа)
    if (myPhone == adminPhone) {
        val supportHeader = TextView(this).apply { 
            text = "Запросы в поддержку:"; textSize = 14f; setPadding(40, 16, 40, 16); setTextColor(subTextColor); 
        }
        chatListLayout.addView(supportHeader)
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val chatId = child.key ?: continue
                    if (chatId.startsWith("support_")) {
                        val userPhone = chatId.replace("support_", "")
                        if (userPhone != adminPhone) {
                            val req = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(40, 16, 40, 16); gravity = Gravity.CENTER_VERTICAL }
                            
                            val avatar = TextView(this@MainActivity).apply {
                                text = "?"; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#FF9800")); textSize = 20f; gravity = Gravity.CENTER
                                layoutParams = LinearLayout.LayoutParams(120, 120).apply { setMargins(0, 0, 32, 0) }
                            }
                            val textLayout = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f) }
                            val nameView = TextView(this@MainActivity).apply { text = "Запрос от $userPhone"; textSize = 16f; setTextColor(textColor); setTypeface(null, android.graphics.Typeface.BOLD) }
                            val subView = TextView(this@MainActivity).apply { text = "Нажмите, чтобы ответить"; textSize = 14f; setTextColor(subTextColor); setPadding(0, 8, 0, 0) }
                            
                            textLayout.addView(nameView)
                            textLayout.addView(subView)
                            req.addView(avatar)
                            req.addView(textLayout)
                            
                            req.setOnClickListener { showChatScreen("Поддержка: $userPhone", chatId) }
                            chatListLayout.addView(req)
                            chatListLayout.addView(View(this@MainActivity).apply { setBackgroundColor(if(isDarkTheme) Color.parseColor("#3E4A5A") else Color.parseColor("#E5E5E5")) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    } else {
        // Кнопка "Поддержка" для обычных юзеров
        val sup = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(40, 16, 40, 16); gravity = Gravity.CENTER_VERTICAL }
        val avatar = TextView(this).apply {
            text = "?"; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#FF9800")); textSize = 20f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(120, 120).apply { setMargins(0, 0, 32, 0) }
        }
        val textLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f) }
        val nameView = TextView(this).apply { text = "Поддержка"; textSize = 16f; setTextColor(textColor); setTypeface(null, android.graphics.Typeface.BOLD) }
        val subView = TextView(this).apply { text = "Напишите нам"; textSize = 14f; setTextColor(subTextColor); setPadding(0, 8, 0, 0) }
        textLayout.addView(nameView)
        textLayout.addView(subView)
        sup.addView(avatar)
        sup.addView(textLayout)
        sup.setOnClickListener { showChatScreen("Поддержка", "support_$myPhone") }
        
        chatListLayout.addView(sup)
        chatListLayout.addView(View(this).apply { setBackgroundColor(if(isDarkTheme) Color.parseColor("#3E4A5A") else Color.parseColor("#E5E5E5")) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
    }

    // Личные чаты
    for ((id, name) in chatHistory) {
        if (id == "general" || id.startsWith("support_")) continue
        val chat = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(40, 16, 40, 16); gravity = Gravity.CENTER_VERTICAL }
        
        val avatar = TextView(this).apply {
            text = name.first().toString().uppercase(); setTextColor(Color.WHITE); setBackgroundColor(tgHeaderBlue); textSize = 20f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(120, 120).apply { setMargins(0, 0, 32, 0) }
        }
        val textLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f) }
        val nameView = TextView(this).apply { text = name; textSize = 16f; setTextColor(textColor); setTypeface(null, android.graphics.Typeface.BOLD) }
        val subView = TextView(this).apply { text = "Нажмите, чтобы открыть"; textSize = 14f; setTextColor(subTextColor); setPadding(0, 8, 0, 0) }
        
        textLayout.addView(nameView)
        textLayout.addView(subView)
        chat.addView(avatar)
        chat.addView(textLayout)
        chat.setOnClickListener { showChatScreen(name, id) }
        
        chatListLayout.addView(chat)
        chatListLayout.addView(View(this).apply { setBackgroundColor(if(isDarkTheme) Color.parseColor("#3E4A5A") else Color.parseColor("#E5E5E5")) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
    }

    scrollView.addView(chatListLayout)
    mainLayout.addView(scrollView)
    setContentView(mainLayout)

    themeButton.setOnClickListener { toggleTheme() }
    searchButton.setOnClickListener { showSearchDialog() }
    logoutButton.setOnClickListener { 
        AlertDialog.Builder(this).setTitle("Выйти?").setPositiveButton("Да") { _, _ -> logout() }.setNegativeButton("Нет", null).show() 
    }
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
                            addChatToHistory(chatId, foundName)
                            showChatScreen(foundName, chatId)
                        } else { Toast.makeText(this@MainActivity, "Не найден", Toast.LENGTH_SHORT).show() }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
        }.setNegativeButton("Отмена", null).show()
}
    // --- ЭКРАН ЧАТА (Стиль Telegram) ---
    private fun showChatScreen(chatName: String, chatId: String) {
        currentChatId = chatId
        val bgColor = if (isDarkTheme) tgBgDark else tgBgLight
        val headerColor = tgHeaderBlue
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK

        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
        
        // Шапка
        val headerLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 40, 24, 24) 
            setBackgroundColor(headerColor)
            gravity = Gravity.CENTER_VERTICAL 
        }
        val backButton = Button(this).apply { text = "<"; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); textSize = 24f }
        backButton.setOnClickListener { showChatListScreen() }
        
        val headerTextLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f) }
        val headerTitle = TextView(this).apply { text = chatName; textSize = 18f; setTextColor(Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD) }
        val statusText = TextView(this).apply { text = "был(а) недавно"; textSize = 13f; setTextColor(Color.parseColor("#B0C4DE")) }
        headerTextLayout.addView(headerTitle)
        headerTextLayout.addView(statusText)

        headerLayout.addView(backButton)
        headerLayout.addView(headerTextLayout)
        
        if (chatId != "general") {
            val deleteButton = Button(this).apply { text = "X"; setTextColor(Color.RED); setBackgroundColor(Color.WHITE) }
            headerLayout.addView(deleteButton)
            deleteButton.setOnClickListener { 
                AlertDialog.Builder(this).setTitle("Очистить чат?")
                    .setMessage("Все сообщения в этом диалоге будут удалены.")
                    .setPositiveButton("Да") { _, _ -> 
                        database.child(chatId).removeValue()
                        showChatListScreen() 
                    }
                    .setNegativeButton("Нет", null).show() 
            }
        }
        
        mainLayout.addView(headerLayout)

        // Область сообщений
        scrollView = ScrollView(this)
        messagesLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); setBackgroundColor(bgColor) }
        scrollView.addView(messagesLayout)
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f))

        // Поле ввода
        val inputLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(if (isDarkTheme) Color.parseColor("#242F3D") else Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val attachButton = Button(this).apply { text = "+"; textSize = 24f; setTextColor(tgHeaderBlue); setBackgroundColor(Color.TRANSPARENT) }
        inputLayout.addView(attachButton)

        val editText = EditText(this).apply { 
            hint = "Сообщение..."; 
            setTextColor(textColor); 
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
        }
        inputLayout.addView(editText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f))
        
        val sendButton = Button(this).apply { text = "➤"; textSize = 20f; setTextColor(Color.WHITE); setBackgroundColor(tgHeaderBlue) }
        inputLayout.addView(sendButton)
        mainLayout.addView(inputLayout)

        // Обработчик фото/видео
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

        // Индикатор "Печатает..."
        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (chatId != "general" && !chatId.startsWith("support_")) {
                    database.child(chatId).child("typing_$myPhone").setValue(s.toString().isNotEmpty())
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Отправка
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

        // Статус онлайн
        val partnerPhone = chatId.replace("support_", "").replace(myPhone, "").replace("_", "")
        if (partnerPhone.isNotEmpty() && partnerPhone != myPhone) {
            usersRef.child(partnerPhone).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isOnline = snapshot.child("online").getValue(Boolean::class.java) ?: false
                    runOnUiThread { 
                        statusText.text = if (isOnline) "в сети" else "был(а) недавно"
                        statusText.setTextColor(if (isOnline) Color.parseColor("#90EE90") else Color.parseColor("#B0C4DE"))
                    }
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
                val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
                
                for (child in snapshot.children) {
                    var senderName = child.child("user").getValue(String::class.java) ?: ""
                    val senderPhone = child.child("phone").getValue(String::class.java) ?: ""
                    if (senderName.isEmpty()) senderName = if (senderPhone.isNotEmpty()) senderPhone else "Неизвестный"
                    
                    val content = child.child("text").getValue(String::class.java) ?: ""
                    val type = child.child("type").getValue(String::class.java) ?: "text"
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val isRead = child.child("read").getValue(Boolean::class.java) ?: false
                    
                    if (senderPhone != myPhone && type == "text") {
                        showNotification(senderName, content)
                        child.ref.child("read").setValue(true)
                    }

                    val isMine = senderPhone == myPhone
                    val bubbleColor = if (isMine) {
                        if (isDarkTheme) tgMsgMyDark else tgMsgMyLight
                    } else {
                        if (isDarkTheme) tgMsgOtherDark else tgMsgOtherLight
                    }

                    val container = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 8); setBackgroundColor(bubbleColor)
                        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        if (isMine) params.gravity = Gravity.END
                        params.setMargins(0, 4, 0, 4); layoutParams = params
                    }
                    
                    val nameView = TextView(this@MainActivity).apply { text = senderName; textSize = 12f; setTextColor(tgHeaderBlue); setTypeface(null, android.graphics.Typeface.BOLD) }
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
                    val infoText = if (isMine) {
                        val checkMark = if (isRead) "✓✓" else "✓"
                        "$timeString $checkMark"
                    } else timeString
                    
                    val timeView = TextView(this@MainActivity).apply { text = infoText; textSize = 10f; setTextColor(Color.GRAY); gravity = Gravity.END }
                    container.addView(timeView)

                    messagesLayout.addView(container)
                }
                
                val partnerPhone = chatId.replace("support_", "").replace(myPhone, "").replace("_", "")
                val isTyping = snapshot.child("typing_$partnerPhone").getValue(Boolean::class.java) ?: false
                if (isTyping) {
                    val typingView = TextView(this@MainActivity).apply { text = "печатает..."; textSize = 14f; setTextColor(tgHeaderBlue); setPadding(16, 8, 16, 8) }
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
