package com.criba.search;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** La API declara también estos tópicos: debe poder arrancar antes que el worker. */
@Configuration
class SearchKafkaConfiguration {

    static final String AGENT_TASKS = "agent.tasks";
    static final String AGENT_RESULTS = "agent.results";

    @Bean
    NewTopic agentTasksTopic() {
        return integrationTopic(AGENT_TASKS);
    }

    @Bean
    NewTopic agentResultsTopic() {
        return integrationTopic(AGENT_RESULTS);
    }

    /** El manejador por defecto se rinde a los 10 intentos: una caída de la base perdería fuentes. */
    @Bean
    DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(2_000, FixedBackOff.UNLIMITED_ATTEMPTS));
    }

    private static NewTopic integrationTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(Duration.ofDays(7).toMillis()))
                .build();
    }
}
