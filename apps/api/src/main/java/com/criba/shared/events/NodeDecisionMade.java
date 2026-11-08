package com.criba.shared.events;

import com.criba.shared.DomainEvent;
import java.util.UUID;

/**
 * Un cambio de estado hecho por una persona.
 *
 * <p>Lleva {@code fromState} porque {@code DESCARTADO} no es un sumidero: revertir es una decisión
 * nueva cuyo destino es el estado anterior, y sin el origen no se podría reconstruir.
 */
public record NodeDecisionMade(
        UUID nodeId,
        String fromState,
        String toState,
        String reason,
        Integer criteriaVersion) implements DomainEvent {
}
