package com.criba.search;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.criba.board.BoardService;
import com.criba.board.Requests;
import com.criba.board.Views;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** El lado de la API del circuito, contra PostgreSQL y Kafka de verdad. */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Testcontainers
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class SearchModuleTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.2"));

    @Autowired
    SearchService search;

    @Autowired
    BoardService board;

    @Autowired
    KafkaTemplate<String, byte[]> kafka;

    private UUID newBoard() {
        return board.createBoard(new Requests.CreateBoard("Tablero", "¿Pregunta?"), UUID.randomUUID()).id();
    }

    private void sendResult(UUID boardId, String json) throws Exception {
        kafka.send(SearchKafkaConfiguration.AGENT_RESULTS, boardId.toString(), json.getBytes(StandardCharsets.UTF_8)).get();
    }

    private static String source(UUID eventId, UUID boardId, String doi) {
        return """
                {"eventId":"%s","boardId":"%s","schemaVersion":1,"type":"SourceFound",
                 "actor":{"type":"AGENT","id":"openalex"},"correlationId":"%s","causationId":"%s",
                 "occurredAt":"2026-11-08T00:00:00Z",
                 "payload":{"openalexId":"W1","doi":"%s","url":"https://doi.org/%s","title":"Un paper",
                            "abstractText":"Texto","year":2024,"venue":"Venue"}}
                """.formatted(eventId, boardId, UUID.randomUUID(), UUID.randomUUID(), doi, doi);
    }

    private List<Views.NodeView> nodes(UUID boardId) {
        return board.snapshot(boardId).nodes();
    }

    @Test
    @DisplayName("encargar una búsqueda publica la tarea en agent.tasks con la clave del tablero")
    void encargarPublicaLaTarea() throws Exception {
        UUID boardId = newBoard();
        UUID correlationId = UUID.randomUUID();

        SearchService.Accepted accepted = search.start(boardId, "revisiones", correlationId);

        JsonNode task = JsonMapper.builder().build().readTree(awaitTask(boardId));
        assertEquals(accepted.searchId().toString(), task.path("eventId").asString());
        assertEquals("SearchRequested", task.path("type").asString());
        assertEquals(correlationId.toString(), task.path("correlationId").asString());
        assertEquals("revisiones", task.path("payload").path("query").asString());
        assertEquals(50, task.path("payload").path("maxNodes").asInt());
    }

    @Test
    @DisplayName("una fuente encontrada aparece en el tablero como candidato")
    void unaFuenteCreaElNodo() throws Exception {
        UUID boardId = newBoard();

        sendResult(boardId, source(UUID.randomUUID(), boardId, "10.1/creado"));

        await().atMost(TIMEOUT).until(() -> nodes(boardId).size() == 1);
        Views.NodeView node = nodes(boardId).getFirst();
        assertEquals("CANDIDATO", node.state());
        assertEquals("10.1/creado", node.doi());
        assertEquals("Texto", node.abstractText());
    }

    @Test
    @DisplayName("ni un evento repetido ni otra fuente con el mismo DOI duplican el nodo")
    void noDuplica() throws Exception {
        UUID boardId = newBoard();
        UUID eventId = UUID.randomUUID();

        sendResult(boardId, source(eventId, boardId, "10.1/doble"));
        sendResult(boardId, source(eventId, boardId, "10.1/doble"));
        sendResult(boardId, source(UUID.randomUUID(), boardId, "10.1/doble"));
        // Misma clave, misma partición: cuando este aparece, los anteriores ya pasaron.
        sendResult(boardId, source(UUID.randomUUID(), boardId, "10.1/siguiente"));

        await().atMost(TIMEOUT).until(() -> nodes(boardId).size() >= 2);
        assertEquals(List.of("10.1/doble", "10.1/siguiente"), nodes(boardId).stream().map(Views.NodeView::doi).toList());
    }

    @Test
    @DisplayName("un registro ilegible o de un tablero inexistente no bloquea la partición")
    void noBloquea() throws Exception {
        UUID boardId = newBoard();

        kafka.send(SearchKafkaConfiguration.AGENT_RESULTS, boardId.toString(), "ilegible".getBytes(StandardCharsets.UTF_8)).get();
        kafka.send(SearchKafkaConfiguration.AGENT_RESULTS, boardId.toString(),
                source(UUID.randomUUID(), UUID.randomUUID(), "10.1/huerfano").getBytes(StandardCharsets.UTF_8)).get();
        sendResult(boardId, source(UUID.randomUUID(), boardId, "10.1/valido"));

        await().atMost(TIMEOUT).until(() -> nodes(boardId).size() == 1);
    }

    private String awaitTask(UUID boardId) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "prueba-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(SearchKafkaConfiguration.AGENT_TASKS));
            AtomicReference<String> found = new AtomicReference<>();
            await().atMost(TIMEOUT).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    if (boardId.toString().equals(record.key())) {
                        found.set(record.value());
                    }
                }
                return found.get() != null;
            });
            return found.get();
        }
    }
}
