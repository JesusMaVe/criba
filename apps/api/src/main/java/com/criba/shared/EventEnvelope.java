package com.criba.shared;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * El sobre de todo evento del tablero.
 *
 * <p>Va tal cual a Kafka: el tópico y la clave salen de la anotación, de modo que todos los eventos
 * de un tablero caen en la misma partición y llegan en orden.
 *
 * <p>No se usa un tipo genérico a propósito: un {@code EventEnvelope<T>} complica la resolución de
 * listeners de Spring y la deserialización del otro lado, sin dar nada a cambio.
 */
@Externalized("board.events::#{#this.boardId}")
public record EventEnvelope(
        UUID eventId,
        UUID boardId,
        long seq,
        int schemaVersion,
        String type,
        Actor actor,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        DomainEvent payload) {

    /** Versión del esquema de eventos. Cambia solo con rupturas, y estas no se hacen. */
    public static final int SCHEMA_VERSION = 1;

    public static EventEnvelope of(
            DomainEvent payload,
            UUID boardId,
            long seq,
            Actor actor,
            UUID correlationId,
            UUID causationId) {

        return new EventEnvelope(
                UUID.randomUUID(),
                boardId,
                seq,
                SCHEMA_VERSION,
                payload.getClass().getSimpleName(),
                actor,
                correlationId,
                causationId,
                Instant.now(),
                payload);
    }
}
