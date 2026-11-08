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
            String venue,
            Integer year,
            String state,
            Double x,
            Double y) {

        static NodeView of(Node node) {
            return new NodeView(node.id(), node.boardId(), node.kind().name(), node.title(), node.doi(),
                    node.venue(), node.year(), node.state().name(), node.x(), node.y());
        }
    }

    public record BoardSnapshot(BoardView board, List<NodeView> nodes) {
    }

    /** Respuesta a una decisión: la secuencia permite al cliente saber si su evento ya llegó. */
    public record DecisionResult(long seq, NodeView node) {
    }
}
