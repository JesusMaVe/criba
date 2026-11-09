package com.criba.search;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class ProcessedEvents {

    private final JdbcClient jdbc;

    ProcessedEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Lo decide la restricción y no un {@code select} previo, que tendría una carrera. */
    boolean markFirstTime(String consumer, UUID eventId) {
        return jdbc.sql("""
                        insert into processed_event (consumer, event_id)
                        values (:consumer, :eventId)
                        on conflict (consumer, event_id) do nothing
                        """)
                .param("consumer", consumer)
                .param("eventId", eventId)
                .update() == 1;
    }
}
