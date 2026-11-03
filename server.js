const WebSocket = require('ws');

const port = process.env.PORT || 8000;
const wss = new WebSocket.Server({ port: port });

wss.on('connection', (ws) => {
  console.log('Клиент подключился');

  ws.on('message', (message) => {
    const text = message.toString();
    console.log('Сообщение:', text);

    // Рассылаем сообщение всем подключенным клиентам
    wss.clients.forEach((client) => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(text);
      }
    });
  });

  ws.on('close', () => {
    console.log('Клиент отключился');
  });
});

console.log(`Сервер запущен на порту ${port}`);
