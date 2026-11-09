package com.criba.search;

import com.criba.board.BoardService;
import org.springframework.stereotype.Component;

@Component
class AgentResultHandler {

    static final String CONSUMER = "api-results";

    AgentResultHandler(ProcessedEvents processedEvents, BoardService board) {
    }

    void handle(AgentResult result) {
        throw new UnsupportedOperationException("por implementar");
    }
}
