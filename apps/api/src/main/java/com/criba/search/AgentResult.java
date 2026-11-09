package com.criba.search;

import java.util.UUID;

/** Una propuesta del worker. El contrato es el JSON de {@code agent.results}. */
record AgentResult(UUID eventId, UUID boardId, String type, UUID correlationId, UUID causationId, Payload payload) {

    record Payload(
            String openalexId,
            String doi,
            String url,
            String title,
            String abstractText,
            Integer year,
            String venue,
            String reason) {
    }
}
