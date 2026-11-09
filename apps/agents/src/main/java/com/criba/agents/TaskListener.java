package com.criba.agents;

import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consume {@code agent.tasks}, deduplica y confirma. Todavía no ejecuta ninguna tarea.
 *
 * <p>El offset se confirma al volver de este método ({@code ack-mode: record}). Un fallo de la base
 * se propaga para reintentar; un registro ilegible no mejora con reintentos, así que se registra y
 * se confirma para no bloquear la partición.
 */
@Component
class TaskListener {

    static final String CONSUMER = "agents";

    private static final Logger log = LoggerFactory.getLogger(TaskListener.class);

    private final ProcessedEvents processedEvents;
    private final JsonMapper json;

    TaskListener(ProcessedEvents processedEvents, JsonMapper json) {
        this.processedEvents = processedEvents;
        this.json = json;
    }

    @KafkaListener(topics = KafkaConfiguration.AGENT_TASKS, groupId = CONSUMER)
    void on(ConsumerRecord<String, String> record) {
        Optional<TaskEnvelope> parsed = parse(record.value());
        if (parsed.isEmpty()) {
            log.error("Tarea ilegible descartada: partición={} offset={}", record.partition(), record.offset());
            return;
        }

        TaskEnvelope task = parsed.get();
        boolean firstTime = processedEvents.markFirstTime(CONSUMER, task.eventId());
        log.info("{}: tipo={} evento={} tablero={} partición={} offset={}",
                firstTime ? "Tarea nueva" : "Duplicado ignorado",
                task.type(), task.eventId(), task.boardId(), record.partition(), record.offset());
    }

    private Optional<TaskEnvelope> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(json.readValue(value, TaskEnvelope.class))
                    .filter(task -> task.eventId() != null);
        }
        catch (JacksonException e) {
            return Optional.empty();
        }
    }
}
