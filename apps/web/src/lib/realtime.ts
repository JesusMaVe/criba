import { Client } from '@stomp/stompjs';
import type { EventEnvelope } from './types';

/**
 * El WebSocket habla directo con el API; en dev el API vive en el 8081.
 * Se puede sobreescribir con VITE_WS_URL.
 */
const WS_URL =
  import.meta.env.VITE_WS_URL ?? `ws://${window.location.hostname}:8081/ws`;

export type ConnectionStatus = 'conectando' | 'conectado' | 'desconectado';

export function subscribeToBoard(
  boardId: string,
  onEvent: (envelope: EventEnvelope) => void,
  onStatus?: (status: ConnectionStatus) => void,
): () => void {
  const client = new Client({ brokerURL: WS_URL, reconnectDelay: 2000 });

  client.onConnect = () => {
    onStatus?.('conectado');
    client.subscribe(`/topic/boards/${boardId}`, (message) => {
      onEvent(JSON.parse(message.body) as EventEnvelope);
    });
  };
  client.onWebSocketClose = () => onStatus?.('desconectado');
  client.onStompError = () => onStatus?.('desconectado');

  onStatus?.('conectando');
  client.activate();

  return () => {
    void client.deactivate();
  };
}
