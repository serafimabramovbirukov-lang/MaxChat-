private fun connectToServer() {
    // Используем https:// и добавляем заголовок Upgrade вручную
    val url = "https://mysty-dawn-51cd.serafimabramovbirukov.workers.dev/"
    
    val request = Request.Builder()
        .url(url)
        .addHeader("Upgrade", "websocket")
        .addHeader("Connection", "Upgrade")
        .build()
    
    webSocket = client.newWebSocket(request, object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            addMessage("Система: Подключено к серверу!")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            addMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            addMessage("Ошибка: ${t.message}")
        }
    })
}
