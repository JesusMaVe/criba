package com.criba.agents;

import com.criba.agents.openalex.OpenAlexConnector;
import com.criba.agents.openalex.OpenAlexException;
import com.criba.agents.openalex.OpenAlexSearch;
import com.criba.agents.openalex.OpenAlexWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Ejecuta una búsqueda y publica lo encontrado. Un fallo de OpenAlex es un resultado más. */
@Component
class SearchTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchTaskHandler.class);

    private final OpenAlexConnector connector;
    private final ResultPublisher publisher;

    SearchTaskHandler(OpenAlexConnector connector, ResultPublisher publisher) {
        this.connector = connector;
        this.publisher = publisher;
    }

    void handle(TaskEnvelope task) {
        TaskEnvelope.Payload payload = task.payload();
        if (payload == null || payload.maxNodes() == null) {
            publisher.searchFailed(task, "la tarea no trae consulta y tope");
            return;
        }

        OpenAlexSearch search;
        try {
            search = connector.search(payload.query(), payload.maxNodes());
        }
        catch (OpenAlexException | IllegalArgumentException e) {
            log.warn("Búsqueda fallida: evento={} motivo={}", task.eventId(), e.getMessage());
            publisher.searchFailed(task, e.getMessage());
            return;
        }

        for (OpenAlexWork work : search.works()) {
            publisher.sourceFound(task, work);
        }
        log.info("Búsqueda terminada: evento={} fuentes={} total={} costoUsd={}",
                task.eventId(), search.works().size(), search.totalCount(), search.costUsd());
    }
}
