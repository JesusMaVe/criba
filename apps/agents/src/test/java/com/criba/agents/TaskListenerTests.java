package com.criba.agents;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.criba.agents.openalex.OpenAlexConnector;
import com.criba.agents.openalex.OpenAlexSearch;
import com.criba.agents.openalex.OpenAlexWork;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Prueba el consumidor contra PostgreSQL y Kafka de verdad. */
@SpringBootTest
@Testcontainers
class TaskListenerTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.2"));

    @MockitoBean
    OpenAlexConnector connector;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("una tarea repetida se registra una sola vez")
    void unaTareaRepetidaSeRegistraUnaVez() throws Exception {
        UUID board = UUID.randomUUID();
        UUID repeated = UUID.randomUUID();
        UUID next = UUID.randomUUID();

        send(board, task(repeated, board));
        send(board, task(repeated, board));
        // Misma clave, misma partición: cuando esta aparece, el duplicado ya pasó.
        send(board, task(next, board));

        awaitProcessed(next);
        assertEquals(1, processedCount(repeated));
    }

    @Test
    @DisplayName("un registro ilegible no bloquea la partición")
    void unRegistroIlegibleNoBloquea() throws Exception {
        UUID board = UUID.randomUUID();
        UUID valid = UUID.randomUUID();

        send(board, "esto no es json");
        send(board, "{\"eventId\":\"tampoco-un-uuid\"}");
        send(board, null);
        send(board, task(valid, board));

        awaitProcessed(valid);
    }

    @Test
    @DisplayName("el offset se confirma después de procesar")
    void elOffsetSeConfirma() throws Exception {
        UUID board = UUID.randomUUID();
        UUID event = UUID.randomUUID();

        RecordMetadata sent = send(board, task(event, board));

        awaitProcessed(event);
        TopicPartition partition = new TopicPartition(sent.topic(), sent.partition());
        try (Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            await().atMost(TIMEOUT).untilAsserted(() -> {
                Map<TopicPartition, OffsetAndMetadata> committed = admin
                        .listConsumerGroupOffsets(TaskListener.CONSUMER)
                        .partitionsToOffsetAndMetadata()
                        .get();
                assertEquals(sent.offset() + 1, committed.get(partition).offset());
            });
        }
    }

    @Test
    @DisplayName("un fallo de la base no pierde la tarea")
    void unFalloDeLaBaseNoPierdeLaTarea() throws Exception {
        UUID board = UUID.randomUUID();
        UUID event = UUID.randomUUID();

        jdbc.sql("alter table agents.processed_event rename to processed_event_fuera").update();
        try {
            send(board, task(event, board));
            // Más que lo que tarda el manejador por defecto en agotar sus reintentos y saltarla.
            Thread.sleep(3_000);
        }
        finally {
            jdbc.sql("alter table agents.processed_event_fuera rename to processed_event").update();
        }

        awaitProcessed(event);
    }

    @Test
    @DisplayName("una búsqueda publica cada fuente encontrada en agent.results, con la clave del tablero")
    void unaBusquedaPublicaSusFuentes() throws Exception {
        UUID board = UUID.randomUUID();
        UUID task = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        when(connector.search("revisiones", 50)).thenReturn(new OpenAlexSearch(
                List.of(new OpenAlexWork("W1", "10.1/a", "https://doi.org/10.1/a", "Un paper", "Texto", 2024, "Venue")),
                1, new BigDecimal("0.001")));

        send(board, """
                {"eventId":"%s","boardId":"%s","type":"SearchRequested","correlationId":"%s",
                 "payload":{"query":"revisiones","maxNodes":50}}
                """.formatted(task, board, correlation));

        JsonNode result = JsonMapper.builder().build().readTree(awaitResult(board));
        assertEquals("SourceFound", result.path("type").asString());
        assertEquals(board.toString(), result.path("boardId").asString());
        assertEquals(task.toString(), result.path("causationId").asString());
        assertEquals(correlation.toString(), result.path("correlationId").asString());
        assertEquals("AGENT", result.path("actor").path("type").asString());
        assertEquals("W1", result.path("payload").path("openalexId").asString());
        assertEquals("10.1/a", result.path("payload").path("doi").asString());
        assertEquals("Un paper", result.path("payload").path("title").asString());
        assertEquals("Texto", result.path("payload").path("abstractText").asString());
        assertEquals(2024, result.path("payload").path("year").asInt());
        assertEquals("Venue", result.path("payload").path("venue").asString());
    }

    private String awaitResult(UUID board) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "prueba-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaConfiguration.AGENT_RESULTS));
            AtomicReference<String> found = new AtomicReference<>();
            await().atMost(TIMEOUT).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    if (board.toString().equals(record.key())) {
                        found.set(record.value());
                    }
                }
                return found.get() != null;
            });
            return found.get();
        }
    }

    private RecordMetadata send(UUID board, String value) throws Exception {
        return kafka.send(KafkaConfiguration.AGENT_TASKS, board.toString(), value).get().getRecordMetadata();
    }

    /** Lleva campos que el worker no conoce: el sobre real también los tiene. */
    private static String task(UUID eventId, UUID boardId) {
        return """
                {"eventId":"%s","boardId":"%s","seq":1,"schemaVersion":1,"type":"Prueba",
                 "actor":{"type":"USER","id":"local-user"},"payload":{}}
                """.formatted(eventId, boardId);
    }

    private void awaitProcessed(UUID eventId) {
        await().atMost(TIMEOUT).until(() -> processedCount(eventId) > 0);
    }

    private long processedCount(UUID eventId) {
        return jdbc.sql("select count(*) from agents.processed_event where event_id = :id")
                .param("id", eventId)
                .query(Long.class)
                .single();
    }
}
