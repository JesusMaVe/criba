package com.criba.agents.openalex;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** El JSON tal como lo devuelve OpenAlex. Solo se usa en el borde del conector. */
record OpenAlexResponse(Meta meta, List<Work> results) {

    record Meta(long count, @JsonProperty("cost_usd") BigDecimal costUsd) {
    }

    record Work(
            String id,
            String doi,
            String title,
            @JsonProperty("publication_year") Integer publicationYear,
            @JsonProperty("primary_location") Location primaryLocation,
            @JsonProperty("abstract_inverted_index") Map<String, List<Integer>> abstractInvertedIndex) {
    }

    record Location(@JsonProperty("landing_page_url") String landingPageUrl, Source source) {
    }

    record Source(@JsonProperty("display_name") String displayName) {
    }
}
