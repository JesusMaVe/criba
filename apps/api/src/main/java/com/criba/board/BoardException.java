package com.criba.board;

import java.util.Map;
import java.util.UUID;

/** Errores de dominio del tablero. Cada uno mapea a un código HTTP distinto en el controlador. */
public abstract class BoardException extends RuntimeException {

    protected BoardException(String message) {
        super(message);
    }

    public static final class NotFound extends BoardException {
        public NotFound(String resource, Object id) {
            super("No existe %s con id %s".formatted(resource, id));
        }
    }

    public static final class IllegalTransition extends BoardException {
        private final NodeState from;
        private final NodeState to;

        public IllegalTransition(NodeState from, NodeState to) {
            super("No se puede pasar de %s a %s".formatted(from, to));
            this.from = from;
            this.to = to;
        }

        public Map<String, Object> details() {
            return Map.of("from", from, "to", to);
        }
    }

    public static final class MissingReason extends BoardException {
        public MissingReason() {
            super("Descartar un nodo exige un motivo");
        }
    }

    public static final class UnknownState extends BoardException {
        public UnknownState(String raw) {
            super("Estado desconocido: %s".formatted(raw));
        }
    }

    public static final class Duplicate extends BoardException {
        private final UUID existingNodeId;

        public Duplicate(UUID existingNodeId) {
            super("El nodo ya existe en el tablero con id %s".formatted(existingNodeId));
            this.existingNodeId = existingNodeId;
        }

        public UUID existingNodeId() {
            return existingNodeId;
        }
    }
}
