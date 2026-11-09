package com.criba.agents;

import com.criba.agents.openalex.OpenAlexConnector;
import org.springframework.stereotype.Component;

@Component
class SearchTaskHandler {

    SearchTaskHandler(OpenAlexConnector connector, ResultPublisher publisher) {
    }

    void handle(TaskEnvelope task) {
        throw new UnsupportedOperationException("por implementar");
    }
}
