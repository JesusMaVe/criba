package com.criba.board;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de un nodo y las transiciones permitidas entre ellos.
 *
 * <p>{@code DESCARTADO} no aparece como origen en ningún par: para revertir hay que mirar la
 * decisión anterior, porque descartar no es un sumidero sino una puerta de un solo sentido que se
 * abre de vuelta al estado del que se salió. Esa parte la resuelve {@link Transitions}.
 */
public enum NodeState {

    CANDIDATO,
    PRESELECCIONADO,
    ACEPTADO,
    DEFINITIVO,
    DESCARTADO;

    private static final Map<NodeState, Set<NodeState>> EXPLICIT = new EnumMap<>(NodeState.class);

    static {
        EXPLICIT.put(CANDIDATO, EnumSet.of(PRESELECCIONADO, DESCARTADO));
        EXPLICIT.put(PRESELECCIONADO, EnumSet.of(ACEPTADO, CANDIDATO, DESCARTADO));
        EXPLICIT.put(ACEPTADO, EnumSet.of(DEFINITIVO, PRESELECCIONADO, DESCARTADO));
        EXPLICIT.put(DEFINITIVO, EnumSet.of(ACEPTADO, DESCARTADO));
        EXPLICIT.put(DESCARTADO, EnumSet.noneOf(NodeState.class));
    }

    /** Destinos alcanzables sin consultar el historial. */
    public Set<NodeState> explicitTargets() {
        return Set.copyOf(EXPLICIT.get(this));
    }

    public boolean isDiscarded() {
        return this == DESCARTADO;
    }
}
