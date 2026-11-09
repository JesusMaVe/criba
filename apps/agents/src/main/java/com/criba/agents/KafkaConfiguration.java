package com.criba.agents;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConfiguration {

    static final String AGENT_TASKS = "agent.tasks";

    /** Clave {@code board_id}: las tareas de un tablero caen en la misma partición y en orden. */
    @Bean
    NewTopic agentTasksTopic() {
        return TopicBuilder.name(AGENT_TASKS)
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG,
                        Long.toString(Duration.ofDays(7).toMillis()))
                .build();
    }

    /**
     * Reintenta sin límite lo que el listener deja escapar, que hoy solo es un fallo de la base.
     *
     * <p>El manejador por defecto se rinde a los 10 intentos y salta el registro: una caída de
     * PostgreSQL de unos segundos perdería tareas.
     */
    @Bean
    DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(2_000, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
