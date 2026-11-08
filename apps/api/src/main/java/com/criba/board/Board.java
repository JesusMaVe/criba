package com.criba.board;

import java.time.Instant;
import java.util.UUID;

public record Board(
        UUID id,
        String title,
        String researchQuestion,
        String ownerId,
        long seq,
        Instant createdAt) {

    public Board withSeq(long newSeq) {
        return new Board(id, title, researchQuestion, ownerId, newSeq, createdAt);
    }
}
