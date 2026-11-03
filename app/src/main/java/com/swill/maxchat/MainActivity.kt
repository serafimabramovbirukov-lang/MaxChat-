package com.swill.maxchat

import android.os.Bundle
import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.database.*

class MainActivity : Activity() {
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var database: DatabaseReference
    private var currentRoom = "general" // Название комнаты по умолчанию

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация Firebase
        database = FirebaseDatabase.getInstance().getReference("chats")

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Поле для ввода комнаты (чтобы переключаться между чатами)
        val roomInput = EditText(this).apply { hint = "Комната"; setText(currentRoom) }
        val joinButton = Button(this).apply { text = "Сменить комнату" }
        mainLayout.addView(roomInput)
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

        // Смена комнаты
        joinButton.setOnClickListener {
            val newRoom = roomInput.text.toString().trim()
            if (newRoom.isNotEmpty()) {
                currentRoom = newRoom
                messagesLayout.removeAllViews()
                listenForMessages()
            }
        }

        // Отправка сообщения
        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                val message = mapOf(
                    "text" to text,
                    "timestamp" to System.currentTimeMillis()
                )
                database.child(currentRoom).push().setValue(message)
                editText.text.clear()
            }
        }

        listenForMessages()
    }

    private fun listenForMessages() {
        // Слушаем изменения в текущей комнате
        database.child(currentRoom).addValueEventListener(object : ValueEventListener {
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
