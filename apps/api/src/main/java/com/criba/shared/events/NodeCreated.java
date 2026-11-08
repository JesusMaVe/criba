package com.criba.shared.events;

import com.criba.shared.DomainEvent;
import java.util.UUID;

public record NodeCreated(
        UUID nodeId,
        String nodeKind,
        String title,
        String doi,
        String openalexId,
        String url,
        Integer year,
        String venue,
        String state) implements DomainEvent {
}
