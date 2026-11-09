package com.criba.agents.openalex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** Contra un servidor HTTP local: se ejercita el cliente real, sin llamar a OpenAlex. */
class OpenAlexConnectorTest {

    private record Reply(int status, Map<String, String> headers, String body) {
    }

    private record Seen(String rawQuery, String authorization, String userAgent) {
        Map<String, String> params() {
            return List.of(rawQuery.split("&")).stream()
                    .map(pair -> pair.split("=", 2))
                    .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
        }
    }

    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<Seen> requests = new ArrayList<>();
    private final List<Duration> sleeps = new ArrayList<>();
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/works", this::handle);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        requests.add(new Seen(exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("User-Agent")));
        Reply reply = replies.isEmpty() ? new Reply(500, Map.of(), "sin respuesta") : replies.poll();
        reply.headers().forEach(exchange.getResponseHeaders()::set);
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(reply.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private OpenAlexConnector connector(String apiKey) {
        OpenAlexProperties properties = new OpenAlexProperties(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), apiKey, 1_000_000_000);
        return new OpenAlexConnector(properties, JsonMapper.builder().build(), System::nanoTime, sleeps::add);
    }

    private OpenAlexConnector connector() {
        return connector("");
    }

    private void ok(long count, String... works) {
        replies.add(new Reply(200, Map.of(), """
                {"meta":{"notice":"ignorado","count":%d,"page":1,"per_page":100,"cost_usd":0.001},
                 "results":[%s],"group_by":[]}
                """.formatted(count, String.join(",", works))));
    }

    private void fail(int status, Map<String, String> headers) {
        replies.add(new Reply(status, headers, "{\"error\":\"x\"}"));
    }

    private static String work(int n) {
        return """
                {"id":"https://openalex.org/W%d","doi":"https://doi.org/10.1/%d","title":"T%d"}
                """.formatted(n, n, n);
    }

    private static String[] works(int from, int to) {
        return IntStream.rangeClosed(from, to).mapToObj(OpenAlexConnectorTest::work).toArray(String[]::new);
    }

    private static List<String> ids(OpenAlexSearch search) {
        return search.works().stream().map(OpenAlexWork::openalexId).toList();
    }

    @Test
    @DisplayName("convierte una respuesta con la forma real e ignora los campos que no usa")
    void convierteLaRespuestaReal() {
        ok(9112, """
                {"id":"https://openalex.org/W2095601341","doi":"https://doi.org/10.1186/2046-4053-3-74",
                 "title":"Systematic review automation technologies","publication_year":2014,
                 "primary_location":{"id":"doi:x","is_oa":true,"landing_page_url":"https://doi.org/10.1186/2046-4053-3-74",
                   "pdf_url":null,"source":{"id":"https://openalex.org/S1","display_name":"Systematic Reviews","issn":["1"]}},
                 "abstract_inverted_index":{"Dos":[0],"palabras":[1]}}
                """);

        OpenAlexSearch search = connector().search("revisiones", 50);

        assertEquals(List.of(new OpenAlexWork("W2095601341", "10.1186/2046-4053-3-74",
                "https://doi.org/10.1186/2046-4053-3-74", "Systematic review automation technologies",
                "Dos palabras", 2014, "Systematic Reviews")), search.works());
        assertEquals(9112, search.totalCount());
        assertEquals(new BigDecimal("0.001"), search.costUsd());
    }

    @Test
    @DisplayName("pide por título y abstract, ordenado por relevancia y solo los campos necesarios")
    void construyeLaConsulta() {
        ok(0);

        connector().search("revisión sistemática", 50);

        Map<String, String> params = requests.getFirst().params();
        assertEquals("title_and_abstract.search:revisión sistemática", params.get("filter"));
        assertEquals("relevance_score:desc", params.get("sort"));
        assertEquals("100", params.get("per_page"));
        assertEquals("1", params.get("page"));
        assertEquals("id,doi,title,publication_year,primary_location,abstract_inverted_index", params.get("select"));
        assertEquals("criba-agents/0.1", requests.getFirst().userAgent());
    }

    @Test
    @DisplayName("las comas y barras de la pregunta no se interpretan como operadores del filtro")
    void neutralizaLosOperadores() {
        ok(0);

        connector().search("  a, b | c  ", 50);

        assertEquals("title_and_abstract.search:a b c", requests.getFirst().params().get("filter"));
    }

    @Test
    @DisplayName("sin key no envía credenciales")
    void sinKey() {
        ok(0);

        connector().search("q", 50);

        assertNull(requests.getFirst().authorization());
    }

    @Test
    @DisplayName("la key viaja en la cabecera y nunca en la URL")
    void conKey() {
        ok(0);

        connector("clave-secreta").search("q", 50);

        assertEquals("Bearer clave-secreta", requests.getFirst().authorization());
        assertFalse(requests.getFirst().rawQuery().contains("clave-secreta"));
    }

    @Test
    @DisplayName("una página basta para el tope de 50 nodos, sin gastar otra llamada")
    void cortaEnElMaximo() {
        ok(500, works(1, 100));

        OpenAlexSearch search = connector().search("q", 50);

        assertEquals(50, search.works().size());
        assertEquals("W1", ids(search).getFirst());
        assertEquals(1, requests.size());
    }

    @Test
    @DisplayName("pagina hasta reunir los pedidos y suma el costo de cada llamada")
    void pagina() {
        ok(500, works(1, 100));
        ok(500, works(101, 200));

        OpenAlexSearch search = connector().search("q", 150);

        assertEquals(150, search.works().size());
        assertEquals(List.of("1", "2"), requests.stream().map(r -> r.params().get("page")).toList());
        assertEquals(new BigDecimal("0.002"), search.costUsd());
    }

    @Test
    @DisplayName("se detiene en la última página aunque falten resultados")
    void paraEnLaUltimaPagina() {
        ok(100, works(1, 100));

        assertEquals(100, connector().search("q", 200).works().size());
        assertEquals(1, requests.size());
    }

    @Test
    @DisplayName("una página vacía termina la búsqueda")
    void paraEnPaginaVacia() {
        ok(500);

        assertEquals(List.of(), connector().search("q", 50).works());
        assertEquals(1, requests.size());
    }

    @Test
    @DisplayName("deduplica por DOI, también entre páginas, y prefiere la primera aparición")
    void deduplica() {
        String repetido = """
                {"id":"https://openalex.org/W999","doi":"https://doi.org/10.1/1","title":"Copia"}
                """;
        String[] primera = works(1, 100);
        ok(300, primera);
        ok(300, repetido, work(101));

        OpenAlexSearch search = connector().search("q", 200);

        assertEquals(101, search.works().size());
        assertFalse(ids(search).contains("W999"));
    }

    @Test
    @DisplayName("deduplica por OpenAlex cuando no hay DOI")
    void deduplicaSinDoi() {
        String sinDoi = """
                {"id":"https://openalex.org/W7","title":"T"}
                """;
        ok(2, sinDoi, sinDoi);

        assertEquals(List.of("W7"), ids(connector().search("q", 50)));
    }

    @Test
    @DisplayName("omite los trabajos sin título sin perder los demás")
    void omiteSinTitulo() {
        ok(2, "{\"id\":\"https://openalex.org/W1\",\"title\":null}", work(2));

        assertEquals(List.of("W2"), ids(connector().search("q", 50)));
    }

    @Test
    @DisplayName("el número de páginas está acotado aunque casi todo se descarte")
    void acotaLasPaginas() {
        String sinTitulo = "{\"id\":\"https://openalex.org/W1\",\"title\":null}";
        for (int i = 0; i < 5; i++) {
            ok(10_000, sinTitulo);
        }

        connector().search("q", 50);

        assertEquals(2, requests.size());
    }

    @Test
    @DisplayName("un 429 respeta el Retry-After y reintenta")
    void reintentaTras429() {
        fail(429, Map.of("Retry-After", "30"));
        ok(1, work(1));

        assertEquals(List.of("W1"), ids(connector().search("q", 50)));
        assertEquals(List.of(Duration.ofSeconds(30)), sleeps);
        assertEquals(2, requests.size());
    }

    @Test
    @DisplayName("un 429 sin Retry-After útil usa retroceso exponencial")
    void retrocesoSinRetryAfter() {
        fail(429, Map.of());
        fail(429, Map.of("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT"));
        ok(1, work(1));

        connector().search("q", 50);

        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), sleeps);
    }

    @Test
    @DisplayName("una espera más larga que el máximo no retiene al worker")
    void noEsperaDemasiado() {
        fail(429, Map.of("Retry-After", "3600"));

        assertThrows(OpenAlexException.class, () -> connector().search("q", 50));
        assertEquals(1, requests.size());
        assertEquals(List.of(), sleeps);
    }

    @Test
    @DisplayName("con el presupuesto diario agotado no reintenta")
    void presupuestoAgotado() {
        fail(429, Map.of("X-RateLimit-Remaining", "0", "Retry-After", "5"));

        assertThrows(OpenAlexException.class, () -> connector().search("q", 50));
        assertEquals(1, requests.size());
    }

    @Test
    @DisplayName("un 5xx se reintenta de forma acotada")
    void reintenta5xx() {
        fail(503, Map.of());
        ok(1, work(1));

        assertEquals(List.of("W1"), ids(connector().search("q", 50)));
        assertEquals(List.of(Duration.ofSeconds(1)), sleeps);
    }

    @Test
    @DisplayName("agotados los intentos, falla")
    void agotaIntentos() {
        fail(500, Map.of());
        fail(500, Map.of());
        fail(500, Map.of());
        fail(500, Map.of());

        assertThrows(OpenAlexException.class, () -> connector().search("q", 50));
        assertEquals(3, requests.size());
    }

    @Test
    @DisplayName("un error del cliente no se reintenta y no filtra la key")
    void error4xxNoSeReintenta() {
        fail(403, Map.of());

        OpenAlexException error = assertThrows(OpenAlexException.class,
                () -> connector("clave-secreta").search("q", 50));

        assertEquals(1, requests.size());
        assertTrue(error.getMessage().contains("403"));
        assertFalse(error.getMessage().contains("clave-secreta"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html>", "null", "{}", "{\"meta\":{\"count\":1}}", "{\"results\":[]}"})
    @DisplayName("una respuesta ilegible o incompleta falla sin reintentar")
    void respuestaIlegible(String body) {
        replies.add(new Reply(200, Map.of(), body));

        assertThrows(OpenAlexException.class, () -> connector().search("q", 50));
        assertEquals(1, requests.size());
    }

    @Test
    @DisplayName("un error de red se reintenta y termina fallando")
    void errorDeRed() {
        OpenAlexConnector connector = connector();
        server.stop(0);

        assertThrows(OpenAlexException.class, () -> connector.search("q", 50));
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), sleeps);
    }

    @Test
    @DisplayName("una interrupción corta la búsqueda y conserva la bandera del hilo")
    void interrupcion() {
        fail(503, Map.of());
        OpenAlexProperties properties = new OpenAlexProperties(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "", 1_000_000_000);
        OpenAlexConnector connector = new OpenAlexConnector(properties, JsonMapper.builder().build(),
                System::nanoTime, duration -> {
                    throw new InterruptedException();
                });

        assertThrows(OpenAlexException.class, () -> connector.search("q", 50));
        assertTrue(Thread.interrupted());
    }

    @Test
    @DisplayName("rechaza consultas vacías y topes fuera de rango sin llamar")
    void validaArgumentos() {
        OpenAlexConnector connector = connector();

        assertThrows(IllegalArgumentException.class, () -> connector.search(null, 50));
        assertThrows(IllegalArgumentException.class, () -> connector.search(" ,| ", 50));
        assertThrows(IllegalArgumentException.class, () -> connector.search("q", 1_001));
        assertEquals(0, requests.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("el tope debe ser positivo")
    void topePositivo(int max) {
        OpenAlexConnector connector = connector();

        assertThrows(IllegalArgumentException.class, () -> connector.search("q", max));
    }
}
