package com.criba.search;

import com.criba.board.BoardService;
import com.criba.shared.CurrentActor;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    public record Accepted(UUID searchId, String query) {
    }

    SearchService(BoardService board, ApplicationEventPublisher events, CurrentActor currentActor) {
    }

    public Accepted start(UUID boardId, String query, UUID correlationId) {
        throw new UnsupportedOperationException("por implementar");
    }
}
