package com.criba.board;

import java.util.Optional;

/** Regla única de transición de estados. Vive en un solo sitio y se prueba por separado. */
public final class Transitions {

    private Transitions() {
    }

    /**
     * @param from               estado actual del nodo
     * @param to                 estado solicitado
     * @param stateBeforeDiscard el estado del que se salió al descartar, solo si {@code from} es
     *                           {@code DESCARTADO}
     */
    public static boolean allowed(NodeState from, NodeState to, Optional<NodeState> stateBeforeDiscard) {
        if (from == to) {
            return false;
        }
        if (from.isDiscarded()) {
            return stateBeforeDiscard.map(previous -> previous == to).orElse(false);
        }
        return from.explicitTargets().contains(to);
    }
}
