package com.criba.search;

import com.criba.board.BoardException;
import com.criba.board.BoardService;
import com.criba.shared.Actor;
import com.criba.shared.CurrentActor;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Encarga búsquedas al worker. Sin LLM todavía: la consulta va tal cual, sin dividirla. */
@Service
public class SearchService {

    public record Accepted(UUID searchId, String query) {
    }

    private static final int MAX_NODES = 50;

    private final BoardService board;
    private final ApplicationEventPublisher events;
    private final CurrentActor currentActor;

    SearchService(BoardService board, ApplicationEventPublisher events, CurrentActor currentActor) {
        this.board = board;
        this.events = events;
        this.currentActor = currentActor;
    }

    /** En una transacción: la publicación queda registrada junto con el resto o no queda. */
    @Transactional
    public Accepted start(UUID boardId, String query, UUID correlationId) {
        String question = board.snapshot(boardId).board().researchQuestion();
        String terms = hasText(query) ? query.trim() : question;
        if (!hasText(terms)) {
            throw new BoardException.MissingQuery();
        }

        Actor actor = currentActor.actor();
        UUID searchId = UUID.randomUUID();
        events.publishEvent(new SearchRequested(searchId, boardId, 1, "SearchRequested", actor, correlationId,
                Instant.now(), new SearchRequested.Payload(terms, MAX_NODES)));
        return new Accepted(searchId, terms);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
