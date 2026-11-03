package com.swill.maxchat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*

class MainActivity : Activity() {
    private var currentChatId: String? = null
    private lateinit var database: DatabaseReference
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private var lastMessageTime = 0L

    private val chats = listOf(
        Triple("general", "Общий чат", android.R.drawable.ic_menu_agenda),
        Triple("friends", "Друзья", android.R.drawable.ic_menu_myplaces),
        Triple("work", "Работа", android.R.drawable.ic_menu_manage)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance().getReference("chats")
        createNotificationChannel()
        showChatListScreen()
    }

    // Создаем канал для уведомлений (обязательно для Android 8+)
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

    private fun showChatListScreen() {
        currentChatId = null
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

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

            val lastMessage = TextView(this).apply {
                text = "Нажмите, чтобы открыть"
                textSize = 14f
                setTextColor(Color.GRAY)
            }

            textLayout.addView(chatName)
            textLayout.addView(lastMessage)
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
        
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        ))

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
                var latestTime = 0L
                var latestText = ""

                for (child in snapshot.children) {
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    val time = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    
                    val messageView = TextView(this@MainActivity).apply {
                        this.text = text
                        textSize = 18f
                        setPadding(16, 16, 16, 16)
                        setBackgroundColor(Color.WHITE)
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, 8, 0, 8)
                        layoutParams = params
                    }
                    messagesLayout.addView(messageView)

                    if (time > latestTime) {
                        latestTime = time
                        latestText = text
                    }
                }
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

                // Если пришло новое сообщение (и это не наше) — вибрируем и показываем уведомление
                if (latestTime > lastMessageTime && latestTime != 0L) {
                    lastMessageTime = latestTime
                    vibrateAndNotify(latestText)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun vibrateAndNotify(text: String) {
        // Вибрация
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(500)
        }

        // Уведомление
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, "maxchat_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Новое сообщение")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(1, builder.build())
    }
}
