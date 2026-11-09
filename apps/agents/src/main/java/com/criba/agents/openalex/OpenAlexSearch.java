package com.criba.agents.openalex;

import java.math.BigDecimal;
import java.util.List;

/**
 * El resultado de una búsqueda.
 *
 * @param totalCount coincidencias totales en OpenAlex, no las devueltas
 * @param costUsd lo que costaron las llamadas hechas, para cargarlo al presupuesto de la app
 */
public record OpenAlexSearch(List<OpenAlexWork> works, long totalCount, BigDecimal costUsd) {
}
