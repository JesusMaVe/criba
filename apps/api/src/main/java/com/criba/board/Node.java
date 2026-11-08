package com.criba.board;

import java.time.Instant;
import java.util.UUID;

public record Node(
        UUID id,
        UUID boardId,
        NodeKind kind,
        String naturalKey,
        String doi,
        String openalexId,
        String url,
        String title,
        String abstractText,
        Integer year,
        String venue,
        NodeState state,
        Double x,
        Double y,
        Instant createdAt) {

    public Node withState(NodeState newState) {
        return new Node(id, boardId, kind, naturalKey, doi, openalexId, url, title, abstractText, year,
                venue, newState, x, y, createdAt);
    }
}
