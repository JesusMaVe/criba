package com.criba.board;

import java.time.Instant;
import java.util.UUID;

/** Una decisión humana, inmutable y con su motivo. Es la auditoría del nodo. */
public record Decision(
        UUID id,
        UUID nodeId,
        UUID boardId,
        NodeState from,
        NodeState to,
        String reason,
        String actorId,
        Integer criteriaVersion,
        UUID idempotencyKey,
        long seq,
        Instant decidedAt) {
}
