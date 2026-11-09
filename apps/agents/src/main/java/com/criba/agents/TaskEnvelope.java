package com.criba.agents;

import java.util.UUID;

/**
 * Lo que el worker necesita leer del sobre de una tarea.
 *
 * <p>El contrato entre deployables es el JSON, no una clase compartida: los demás campos del sobre
 * se ignoran hasta que haga falta usarlos.
 */
record TaskEnvelope(UUID eventId, UUID boardId, String type, UUID correlationId, Payload payload) {

    /** Los datos de una búsqueda. Los demás tipos de tarea no los llevan. */
    record Payload(String query, Integer maxNodes) {
    }
}
