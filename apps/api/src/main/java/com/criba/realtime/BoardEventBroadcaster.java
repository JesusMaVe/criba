package com.criba.realtime;

import com.criba.shared.EventEnvelope;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reenvía cada hecho confirmado al canal del tablero.
 *
 * <p>El listener es {@code @ApplicationModuleListener}: se ejecuta después del commit y en su propia
 * transacción, así que el navegador nunca ve un hecho que la base todavía no tiene.
 */
@Component
class BoardEventBroadcaster {

    private final SimpMessagingTemplate messaging;

    BoardEventBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @ApplicationModuleListener
    void on(EventEnvelope envelope) {
        messaging.convertAndSend("/topic/boards/" + envelope.boardId(), envelope);
    }
}
