package com.criba.search;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class AgentResultListener {

    AgentResultListener(AgentResultHandler handler, JsonMapper json) {
    }

    @KafkaListener(topics = SearchKafkaConfiguration.AGENT_RESULTS, groupId = AgentResultHandler.CONSUMER)
    void on(ConsumerRecord<String, String> record) {
        throw new UnsupportedOperationException("por implementar");
    }
}
