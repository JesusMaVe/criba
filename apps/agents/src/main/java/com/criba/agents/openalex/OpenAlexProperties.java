package com.criba.agents.openalex;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey opcional; sin ella se comparte el presupuesto diario gratuito de la IP
 * @param requestsPerSecond ritmo máximo de esta instancia
 */
@ConfigurationProperties("criba.openalex")
public record OpenAlexProperties(URI baseUrl, String apiKey, double requestsPerSecond) {
}
