package com.criba.agents.openalex;

/** Un trabajo de OpenAlex ya normalizado, con los campos con los que la API crea un nodo. */
public record OpenAlexWork(
        String openalexId,
        String doi,
        String url,
        String title,
        String abstractText,
        Integer year,
        String venue) {

    /** Identidad para deduplicar: el mismo orden de prioridad que {@code NaturalKey} en la API. */
    String naturalKey() {
        return doi != null ? "doi:" + doi : "openalex:" + openalexId;
    }
}
