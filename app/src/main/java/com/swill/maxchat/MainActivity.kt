package com.swill.maxchat

import android.os.Bundle
import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private var socket: Socket? = null
    private var out: PrintWriter? = null
    private var reader: BufferedReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

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

        val editText = EditText(this).apply {
            hint = "Сообщение..."
        }
        inputLayout.addView(editText, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        ))

        val sendButton = Button(this).apply {
            text = "->"
        }
        inputLayout.addView(sendButton)

        mainLayout.addView(inputLayout)

        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                thread {
                    out?.println(text)
                }
                editText.text.clear()
            }
        }

        setContentView(mainLayout)
        connectToServer()
    }

    private fun connectToServer() {
        thread {
            try {
                // Подключаемся к Cloudflare через порт 443 (SSL)
                val sslSocketFactory = javax.net.ssl.SSLSocketFactory.getDefault()
                val rawSocket = Socket("mysty-dawn-51cd.serafimabrambirukov.workers.dev", 443)
                val sslSocket = sslSocketFactory.createSocket(rawSocket, "mysty-dawn-51cd.serafimabrambirukov.workers.dev", 443, true) as javax.net.ssl.SSLSocket
                
                socket = sslSocket
                out = PrintWriter(sslSocket.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(sslSocket.getInputStream()))

                addMessage("Система: Подключено к серверу!")
                
                // Слушаем входящие сообщения
                var line: String?
                while (reader?.readLine().also { line = it } != null) {
                    addMessage(line ?: "")
                }
            } catch (e: Exception) {
                addMessage("Ошибка: ${e.message}")
            }
        }
    }

    private fun addMessage(text: String) {
        runOnUiThread {
            val messageView = TextView(this).apply {
                this.text = text
                textSize = 18f
                setPadding(0, 8, 0, 8)
            }
            messagesLayout.addView(messageView)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.close()
    }
}
