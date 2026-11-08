/**
 * Comprueba el canal de hechos sin abrir un navegador.
 *
 * Uso: node scripts/check-realtime.mjs <boardId>
 * Espera un evento del tablero y lo imprime; falla si no llega en 10 segundos.
 */
import { Client } from '@stomp/stompjs';

const boardId = process.argv[2];
if (!boardId) {
  console.error('uso: node scripts/check-realtime.mjs <boardId>');
  process.exit(1);
}

const brokerURL = process.env.WS_URL ?? 'ws://localhost:8081/ws';
const client = new Client({ brokerURL });

const timeout = setTimeout(() => {
  console.error(`sin eventos para el tablero ${boardId} en 10s`);
  void client.deactivate();
  process.exit(1);
}, 10_000);

client.onConnect = () => {
  console.log(`conectado a ${brokerURL}`);
  client.subscribe(`/topic/boards/${boardId}`, (message) => {
    const event = JSON.parse(message.body);
    clearTimeout(timeout);
    console.log(`evento seq=${event.seq} tipo=${event.type}`);
    console.log(JSON.stringify(event, null, 2));
    void client.deactivate();
    process.exit(0);
  });
};

client.onStompError = (frame) => {
  console.error('error STOMP:', frame.headers['message']);
  process.exit(1);
};

client.activate();
