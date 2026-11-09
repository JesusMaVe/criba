package com.criba.agents;

import com.criba.agents.openalex.OpenAlexWork;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Publica las propuestas del worker. La API decide si las acepta y es quien escribe el tablero. */
@Component
class ResultPublisher {

    static final String SOURCE_FOUND = "SourceFound";
    static final String SEARCH_FAILED = "SearchFailed";

    private record Actor(String type, String id) {
    }

    private record ResultEnvelope(
            UUID eventId,
            UUID boardId,
            int schemaVersion,
            String type,
            Actor actor,
            UUID correlationId,
            UUID causationId,
            Instant occurredAt,
            Object payload) {
    }

    private record SearchFailure(String reason) {
    }

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    ResultPublisher(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    void sourceFound(TaskEnvelope task, OpenAlexWork work) {
        publish(task, SOURCE_FOUND, work);
    }

    void searchFailed(TaskEnvelope task, String reason) {
        publish(task, SEARCH_FAILED, new SearchFailure(reason));
    }

    private void publish(TaskEnvelope task, String type, Object payload) {
        ResultEnvelope envelope = new ResultEnvelope(UUID.randomUUID(), task.boardId(), 1, type,
                new Actor("AGENT", "openalex"), task.correlationId(), task.eventId(), Instant.now(), payload);
        try {
            kafka.send(KafkaConfiguration.AGENT_RESULTS, task.boardId().toString(), json.writeValueAsString(envelope))
                    .get(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("publicación interrumpida", e);
        }
        catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("no se pudo publicar en " + KafkaConfiguration.AGENT_RESULTS, e);
        }
    }
}
