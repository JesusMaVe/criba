package com.criba.search;

import com.criba.board.BoardService;
import com.criba.board.Requests;
import com.criba.shared.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida y persiste lo que propone el worker.
 *
 * <p>La marca de procesado y el nodo van en la misma transacción: si algo falla no queda ninguno de
 * los dos, y la reentrega vuelve a intentarlo.
 */
@Component
class AgentResultHandler {

    static final String CONSUMER = "api-results";

    private static final Logger log = LoggerFactory.getLogger(AgentResultHandler.class);

    private final ProcessedEvents processedEvents;
    private final BoardService board;

    AgentResultHandler(ProcessedEvents processedEvents, BoardService board) {
        this.processedEvents = processedEvents;
        this.board = board;
    }

    @Transactional
    public void handle(AgentResult result) {
        if (!processedEvents.markFirstTime(CONSUMER, result.eventId())) {
            log.info("Resultado repetido ignorado: evento={}", result.eventId());
            return;
        }
        switch (result.type()) {
            case "SourceFound" -> propose(result);
            case "SearchFailed" -> log.warn("Búsqueda fallida: tablero={} tarea={} payload={}",
                    result.boardId(), result.causationId(), result.payload());
            case null, default -> log.warn("Resultado de tipo desconocido: evento={} tipo={}",
                    result.eventId(), result.type());
        }
    }

    private void propose(AgentResult result) {
        AgentResult.Payload source = result.payload();
        if (source == null || source.title() == null || source.title().isBlank()) {
            log.warn("Fuente rechazada por no tener título: evento={}", result.eventId());
            return;
        }
        board.addProposedNode(result.boardId(),
                new Requests.CreateNode(source.title(), "PAPER", source.doi(), source.openalexId(), source.url(),
                        source.abstractText(), source.year(), source.venue()),
                Actor.agent("openalex"), result.correlationId(), result.causationId());
    }
}
