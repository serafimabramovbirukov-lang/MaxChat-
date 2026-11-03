const WebSocket = require('ws');

const port = process.env.PORT || 8000;
const wss = new WebSocket.Server({ port: port });

wss.on('connection', (ws) => {
  // По умолчанию клиент попадает в общую комнату
  ws.room = 'general';

  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message);
      
      // Если клиент прислал название комнаты, запоминаем его
      if (data.room) {
        ws.room = data.room;
      }

      // Если есть текст сообщения, рассылаем его всем в этой комнате
      if (data.text) {
        console.log(`[Комната: ${ws.room}] Сообщение: ${data.text}`);
        
        wss.clients.forEach((client) => {
          if (client.readyState === WebSocket.OPEN && client.room === ws.room) {
            client.send(JSON.stringify({ 
              room: ws.room, 
              text: data.text 
            }));
          }
        });
      }
    } catch (e) {
      console.log('Ошибка парсинга сообщения:', e);
    }
  });

  ws.on('close', () => {
    console.log('Клиент отключился');
  });
});

console.log(`Сервер запущен на порту ${port}`);
