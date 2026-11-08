package com.criba.shared.events;

import com.criba.shared.DomainEvent;
import java.util.UUID;

public record BoardCreated(
        UUID boardId,
        String title,
        String researchQuestion,
        String ownerId) implements DomainEvent {
}
