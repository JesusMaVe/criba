package com.criba.board;

import java.time.Duration;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;

/**
 * El tópico de auditoría del tablero.
 *
 * <p>Clave {@code board_id}: todos los eventos de un tablero caen en la misma partición y llegan en
 * orden. {@code delete}, no {@code compact}: compactar por esta clave dejaría un solo evento por
 * tablero y borraría la historia.
 */
@Configuration
class EventsTopicConfiguration {

    static final String BOARD_EVENTS = "board.events";

    @Bean
    NewTopic boardEventsTopic() {
        return TopicBuilder.name(BOARD_EVENTS)
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG,
                        Long.toString(Duration.ofDays(30).toMillis()))
                .build();
    }
}
