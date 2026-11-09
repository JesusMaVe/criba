package com.criba.search;

import com.criba.shared.Actor;
import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/** La tarea que recibe el worker. La clave {@code board_id} mantiene en orden las de un tablero. */
@Externalized("agent.tasks::#{#this.boardId}")
public record SearchRequested(
        UUID eventId,
        UUID boardId,
        int schemaVersion,
        String type,
        Actor actor,
        UUID correlationId,
        Instant occurredAt,
        Payload payload) {

    public record Payload(String query, int maxNodes) {
    }
}
