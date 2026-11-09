package com.criba.search;

import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consume {@code agent.results}.
 *
 * <p>Un fallo de la base se propaga para reintentar. Un registro ilegible no mejora con reintentos:
 * se registra y se confirma para no bloquear la partición.
 */
@Component
class AgentResultListener {

    private static final Logger log = LoggerFactory.getLogger(AgentResultListener.class);

    private final AgentResultHandler handler;
    private final JsonMapper json;

    AgentResultListener(AgentResultHandler handler, JsonMapper json) {
        this.handler = handler;
        this.json = json;
    }

    @KafkaListener(topics = SearchKafkaConfiguration.AGENT_RESULTS, groupId = AgentResultHandler.CONSUMER)
    void on(ConsumerRecord<String, String> record) {
        Optional<AgentResult> result = parse(record.value());
        if (result.isEmpty()) {
            log.error("Resultado ilegible descartado: partición={} offset={}", record.partition(), record.offset());
            return;
        }
        handler.handle(result.get());
    }

    private Optional<AgentResult> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(json.readValue(value, AgentResult.class))
                    .filter(result -> result.eventId() != null && result.boardId() != null);
        }
        catch (JacksonException e) {
            return Optional.empty();
        }
    }
}
