package com.criba.board;

import static com.criba.board.NodeState.ACEPTADO;
import static com.criba.board.NodeState.CANDIDATO;
import static com.criba.board.NodeState.DESCARTADO;
import static com.criba.board.NodeState.DEFINITIVO;
import static com.criba.board.NodeState.PRESELECCIONADO;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransitionsTest {

    @Test
    @DisplayName("todo destino declarado por un estado es alcanzable")
    void destinosDeclaradosSonAlcanzables() {
        for (NodeState from : NodeState.values()) {
            for (NodeState to : from.explicitTargets()) {
                assertTrue(Transitions.allowed(from, to, Optional.empty()),
                        () -> "%s debería poder pasar a %s".formatted(from, to));
            }
        }
    }

    @Test
    @DisplayName("cualquier par no declarado se rechaza")
    void paresNoDeclaradosSeRechazan() {
        for (NodeState from : NodeState.values()) {
            for (NodeState to : NodeState.values()) {
                if (from == to || from.explicitTargets().contains(to)) {
                    continue;
                }
                assertFalse(Transitions.allowed(from, to, Optional.empty()),
                        () -> "%s no debería poder pasar a %s".formatted(from, to));
            }
        }
    }

    @Test
    @DisplayName("un nodo no puede quedarse en el mismo estado")
    void noHayTransicionesAlMismoEstado() {
        for (NodeState state : NodeState.values()) {
            assertFalse(Transitions.allowed(state, state, Optional.of(state)));
        }
    }

    @Test
    @DisplayName("descartado solo vuelve al estado del que salió")
    void descartadoSoloVuelveAlEstadoAnterior() {
        assertTrue(Transitions.allowed(DESCARTADO, ACEPTADO, Optional.of(ACEPTADO)));
        assertFalse(Transitions.allowed(DESCARTADO, ACEPTADO, Optional.of(CANDIDATO)));
        assertFalse(Transitions.allowed(DESCARTADO, ACEPTADO, Optional.empty()));
    }

    @Test
    @DisplayName("descartado no salta directo a definitivo")
    void descartadoNoSaltaADefinitivo() {
        assertFalse(Transitions.allowed(DESCARTADO, DEFINITIVO, Optional.of(DEFINITIVO).filter(s -> false)));
        assertFalse(Transitions.allowed(DESCARTADO, PRESELECCIONADO, Optional.of(ACEPTADO)));
        assertTrue(Transitions.allowed(DESCARTADO, DEFINITIVO, Optional.of(DEFINITIVO)));
    }
}
