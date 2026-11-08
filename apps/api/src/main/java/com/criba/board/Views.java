package com.criba.board;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representaciones de salida de la API del tablero. */
public final class Views {

    private Views() {
    }

    public record BoardView(
            UUID id, String title, String researchQuestion, String ownerId, long seq, Instant createdAt) {

        static BoardView of(Board board) {
            return new BoardView(board.id(), board.title(), board.researchQuestion(), board.ownerId(),
                    board.seq(), board.createdAt());
        }
    }

    public record NodeView(
            UUID id,
            UUID boardId,
            String kind,
            String title,
            String doi,
            String url,
            String abstractText,
            String venue,
            Integer year,
            String state,
            Double x,
            Double y) {

        static NodeView of(Node node) {
            return new NodeView(node.id(), node.boardId(), node.kind().name(), node.title(), node.doi(),
                    node.url(), node.abstractText(), node.venue(), node.year(), node.state().name(),
                    node.x(), node.y());
        }
    }

    public record BoardSnapshot(BoardView board, List<NodeView> nodes) {
    }

    /** Una decisión pasada, tal como quedó en la auditoría. */
    public record DecisionView(
            String from, String to, String reason, String actorId, long seq, Instant decidedAt) {

        static DecisionView of(Decision decision) {
            return new DecisionView(decision.from().name(), decision.to().name(), decision.reason(),
                    decision.actorId(), decision.seq(), decision.decidedAt());
        }
    }

    /** Respuesta a una decisión: la secuencia permite al cliente saber si su evento ya llegó. */
    public record DecisionResult(long seq, NodeView node) {
    }
}
