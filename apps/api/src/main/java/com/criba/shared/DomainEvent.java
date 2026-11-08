package com.criba.shared;

import com.criba.shared.events.BoardCreated;
import com.criba.shared.events.NodeCreated;
import com.criba.shared.events.NodeDecisionMade;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Un hecho ocurrido en un módulo del tablero.
 *
 * <p>Abierta a propósito, no {@code sealed}: los tipos de evento son aditivos y cada módulo
 * declarará los suyos. El discriminador {@code kind} viaja dentro del JSON del payload para que un
 * consumidor pueda deserializar sin adivinar el tipo.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = BoardCreated.class, name = "BoardCreated"),
    @JsonSubTypes.Type(value = NodeCreated.class, name = "NodeCreated"),
    @JsonSubTypes.Type(value = NodeDecisionMade.class, name = "NodeDecisionMade")
})
public interface DomainEvent {
}
