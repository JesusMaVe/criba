package com.criba.agents.openalex;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Busca trabajos en OpenAlex y los devuelve normalizados y sin repetir.
 *
 * <p>Filtra por título y abstract en lugar de usar {@code search=}: cuesta lo mismo, no entra en el
 * texto completo y las búsquedas anónimas con {@code search=} se limitan cuando OpenAlex tiene carga.
 * Pagina por número porque el cursor exige una key más allá de la primera página.
 */
@Component
public class OpenAlexConnector {

    private static final Logger log = LoggerFactory.getLogger(OpenAlexConnector.class);

    private static final String SELECT = "id,doi,title,publication_year,primary_location,abstract_inverted_index";
    /** Una llamada cuesta lo mismo con 1 que con 100 resultados. */
    private static final int PER_PAGE = 100;
    private static final int MAX_RESULTS = 1_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    /** Más que esto retendría al worker: mejor fallar y que la operación se reanude. */
    private static final Duration MAX_WAIT = Duration.ofSeconds(45);

    private final OpenAlexProperties properties;
    private final JsonMapper json;
    private final RequestPacer pacer;
    private final RequestPacer.Sleeper sleeper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Autowired
    OpenAlexConnector(OpenAlexProperties properties, JsonMapper json) {
        this(properties, json, System::nanoTime, duration -> Thread.sleep(duration));
    }

    OpenAlexConnector(OpenAlexProperties properties, JsonMapper json, LongSupplier nanoTime,
            RequestPacer.Sleeper sleeper) {
        this.properties = properties;
        this.json = json;
        this.sleeper = sleeper;
        this.pacer = new RequestPacer(properties.requestsPerSecond(), nanoTime, sleeper);
    }

    public OpenAlexSearch search(String query, int maxResults) {
        String terms = sanitize(query);
        if (terms.isEmpty() || maxResults < 1 || maxResults > MAX_RESULTS) {
            throw new IllegalArgumentException("consulta vacía o tope fuera de 1.." + MAX_RESULTS);
        }

        Map<String, OpenAlexWork> works = new LinkedHashMap<>();
        BigDecimal cost = BigDecimal.ZERO;
        long total = 0;
        // Una página de margen por si se descartan repetidos o trabajos sin título; cada una se paga.
        int maxPages = (maxResults + PER_PAGE - 1) / PER_PAGE + 1;

        for (int page = 1; page <= maxPages; page++) {
            OpenAlexResponse response = fetch(uri(terms, page));
            total = response.meta().count();
            if (response.meta().costUsd() != null) {
                cost = cost.add(response.meta().costUsd());
            }
            for (OpenAlexResponse.Work work : response.results()) {
                WorkMapper.toWork(work).ifPresent(w -> works.putIfAbsent(w.naturalKey(), w));
            }
            if (works.size() >= maxResults || response.results().isEmpty() || page * PER_PAGE >= total) {
                break;
            }
        }

        List<OpenAlexWork> found = works.values().stream().limit(maxResults).toList();
        return new OpenAlexSearch(found, total, cost);
    }

    /** La coma y la barra son operadores en el valor de un filtro de OpenAlex. */
    private static String sanitize(String query) {
        return query == null ? "" : query.replaceAll("[,|]", " ").trim().replaceAll("\\s+", " ");
    }

    private URI uri(String terms, int page) {
        return URI.create(properties.baseUrl() + "/works"
                + "?filter=" + encode("title_and_abstract.search:" + terms)
                + "&sort=" + encode("relevance_score:desc")
                + "&select=" + encode(SELECT)
                + "&per_page=" + PER_PAGE
                + "&page=" + page);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private OpenAlexResponse fetch(URI uri) {
        HttpRequest request = request(uri);
        try {
            for (int attempt = 1; ; attempt++) {
                pacer.acquire();
                HttpResponse<String> response;
                try {
                    response = http.send(request, HttpResponse.BodyHandlers.ofString());
                }
                catch (IOException e) {
                    if (attempt == MAX_ATTEMPTS) {
                        throw new OpenAlexException("no se pudo contactar con OpenAlex", e);
                    }
                    sleeper.sleep(backoff(attempt));
                    continue;
                }

                int status = response.statusCode();
                if (status == 200) {
                    return parse(response.body());
                }
                if (!isRetryable(status) || attempt == MAX_ATTEMPTS || budgetExhausted(response)) {
                    throw new OpenAlexException("OpenAlex respondió " + status);
                }
                Duration wait = status == 429 ? retryAfter(response).orElse(backoff(attempt)) : backoff(attempt);
                if (wait.compareTo(MAX_WAIT) > 0) {
                    throw new OpenAlexException("OpenAlex respondió " + status + " y pide esperar " + wait);
                }
                log.warn("OpenAlex respondió {}; intento {} de {}, reintento en {}", status, attempt, MAX_ATTEMPTS, wait);
                sleeper.sleep(wait);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenAlexException("búsqueda interrumpida", e);
        }
    }

    /** La key va en la cabecera para que nunca aparezca en una URL ni en un log. */
    private HttpRequest request(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "criba-agents/0.1")
                .GET();
        String key = properties.apiKey();
        if (key != null && !key.isBlank()) {
            builder.header("Authorization", "Bearer " + key);
        }
        return builder.build();
    }

    private OpenAlexResponse parse(String body) {
        OpenAlexResponse response;
        try {
            response = json.readValue(body, OpenAlexResponse.class);
        }
        catch (JacksonException e) {
            throw new OpenAlexException("OpenAlex devolvió una respuesta ilegible", e);
        }
        if (response == null || response.meta() == null || response.results() == null) {
            throw new OpenAlexException("OpenAlex devolvió una respuesta incompleta");
        }
        return response;
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private static boolean budgetExhausted(HttpResponse<?> response) {
        return response.statusCode() == 429
                && response.headers().firstValue("X-RateLimit-Remaining").filter("0"::equals).isPresent();
    }

    private static Optional<Duration> retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After")
                .filter(value -> value.matches("\\d{1,9}"))
                .map(value -> Duration.ofSeconds(Long.parseLong(value)));
    }

    private static Duration backoff(int attempt) {
        return BASE_BACKOFF.multipliedBy(1L << (attempt - 1));
    }
}
