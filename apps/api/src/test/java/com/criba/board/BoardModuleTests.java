package com.criba.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.criba.shared.Actor;
import com.criba.shared.EventEnvelope;
import com.criba.shared.events.NodeCreated;
import com.criba.shared.events.NodeDecisionMade;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.PublishedEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Prueba el módulo {@code board} contra PostgreSQL y Kafka de verdad.
 *
 * <p>Es el único lugar donde se comprueba la promesa central: un comando válido produce exactamente
 * un evento, y uno inválido no produce ninguno.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Testcontainers
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class BoardModuleTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.2"));

    @Autowired
    BoardService service;

    @Test
    @DisplayName("una decisión válida publica un evento con su secuencia")
    void decisionValidaPublicaUnEvento(PublishedEvents events) {
        UUID correlationId = UUID.randomUUID();
        Views.BoardView board = service.createBoard(new Requests.CreateBoard("Tablero", "¿Pregunta?"), correlationId);
        Views.NodeView node = service.addNode(board.id(),
                new Requests.CreateNode("Un paper", "PAPER", "10.1000/xyz", null, null, null, 2024, "NeurIPS"),
                correlationId);

        Views.DecisionResult result = service.decide(board.id(), node.id(),
                new Requests.Decision("PRESELECCIONADO", null, null), correlationId);

        assertEquals("PRESELECCIONADO", result.node().state());
        assertEquals(3L, result.seq(), "tablero=1, nodo=2, decisión=3");

        List<EventEnvelope> envelopes = envelopes(events);
        assertEquals(3, envelopes.size());
        EventEnvelope ultimo = envelopes.getLast();
        assertInstanceOf(NodeDecisionMade.class, ultimo.payload());
        assertEquals(result.seq(), ultimo.seq());
        assertEquals(board.id(), ultimo.boardId());
    }

    @Test
    @DisplayName("una transición ilegal no publica nada")
    void transicionIlegalNoPublicaNada(PublishedEvents events) {
        UUID correlationId = UUID.randomUUID();
        Views.BoardView board = service.createBoard(new Requests.CreateBoard("Tablero", null), correlationId);
        Views.NodeView node = service.addNode(board.id(),
                new Requests.CreateNode("Un paper", "PAPER", "10.1000/ilegal", null, null, null, 2024, null),
                correlationId);

        assertThrows(BoardException.IllegalTransition.class, () -> service.decide(board.id(), node.id(),
                new Requests.Decision("DEFINITIVO", null, null), correlationId));

        assertEquals(0, decisiones(events));
    }

    @Test
    @DisplayName("descartar sin motivo se rechaza sin escribir ni publicar")
    void descartarSinMotivoSeRechaza(PublishedEvents events) {
        UUID correlationId = UUID.randomUUID();
        Views.BoardView board = service.createBoard(new Requests.CreateBoard("Tablero", null), correlationId);
        Views.NodeView node = service.addNode(board.id(),
                new Requests.CreateNode("Un paper", "PAPER", "10.1000/sinmotivo", null, null, null, 2024, null),
                correlationId);

        assertThrows(BoardException.MissingReason.class, () -> service.decide(board.id(), node.id(),
                new Requests.Decision("DESCARTADO", "   ", null), correlationId));

        assertEquals("CANDIDATO", service.snapshot(board.id()).nodes().getFirst().state());
        assertEquals(0, decisiones(events));
    }

    @Test
    @DisplayName("repetir la misma petición no produce una segunda decisión")
    void laIdempotenciaEvitaLaSegundaDecision(PublishedEvents events) {
        UUID correlationId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        Views.BoardView board = service.createBoard(new Requests.CreateBoard("Tablero", null), correlationId);
        Views.NodeView node = service.addNode(board.id(),
                new Requests.CreateNode("Un paper", "PAPER", "10.1000/idem", null, null, null, 2024, null),
                correlationId);

        Views.DecisionResult primera = service.decide(board.id(), node.id(),
                new Requests.Decision("PRESELECCIONADO", null, idempotencyKey), correlationId);
        Views.DecisionResult segunda = service.decide(board.id(), node.id(),
                new Requests.Decision("PRESELECCIONADO", null, idempotencyKey), correlationId);

        assertEquals(primera.seq(), segunda.seq());
        assertEquals(1, decisiones(events));
    }

    @Test
    @DisplayName("descartar y volver deja el nodo en el estado del que salió")
    void descartarEsReversible() {
        UUID correlationId = UUID.randomUUID();
        Views.BoardView board = service.createBoard(new Requests.CreateBoard("Tablero", null), correlationId);
        Views.NodeView node = service.addNode(board.id(),
                new Requests.CreateNode("Un paper", "PAPER", "10.1000/reversible", null, null, null, 2024, null),
                correlationId);

        service.decide(board.id(), node.id(), new Requests.Decision("PRESELECCIONADO", null, null), correlationId);
        service.decide(board.id(), node.id(),
                new Requests.Decision("DESCARTADO", "No usa dataset público", null), correlationId);
        assertEquals("DESCARTADO", service.snapshot(board.id()).nodes().getFirst().state());

        Views.DecisionResult vuelta = service.decide(board.id(), node.id(),
                new Requests.Decision("PRESELECCIONADO", "Revisión: sí lo usa", null), correlationId);
        assertEquals("PRESELECCIONADO", vuelta.node().state());
        assertTrue(vuelta.seq() > 4);
    }

    @Test
    @DisplayName("un nodo propuesto queda como candidato y su evento nombra al agente")
    void unNodoPropuestoQuedaComoCandidato(PublishedEvents events) {
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();
        Views.BoardView board = service.createBoard(new Requests.CreateBoard("Tablero", null), correlationId);

        Views.NodeView node = service.addProposedNode(board.id(),
                new Requests.CreateNode("Un paper", "PAPER", "10.1000/propuesto", "W1", null, "Abstract", 2024, null),
                Actor.agent("openalex"), correlationId, causationId).orElseThrow();

        assertEquals("CANDIDATO", node.state());
        assertEquals("Abstract", node.abstractText());
        EventEnvelope evento = envelopes(events).getLast();
        assertInstanceOf(NodeCreated.class, evento.payload());
        assertEquals(Actor.agent("openalex"), evento.actor());
        assertEquals(causationId, evento.causationId());
    }

    @Test
    @DisplayName("un nodo propuesto repetido se ignora sin error y sin evento")
    void unNodoPropuestoRepetidoSeIgnora(PublishedEvents events) {
        UUID correlationId = UUID.randomUUID();
        Views.BoardView board = service.createBoard(new Requests.CreateBoard("Tablero", null), correlationId);
        Requests.CreateNode propuesta =
                new Requests.CreateNode("Un paper", "PAPER", "10.1000/doble", "W1", null, null, 2024, null);

        service.addProposedNode(board.id(), propuesta, Actor.agent("openalex"), correlationId, null);
        Optional<Views.NodeView> segunda =
                service.addProposedNode(board.id(), propuesta, Actor.agent("openalex"), correlationId, null);

        assertTrue(segunda.isEmpty());
        assertEquals(1, service.snapshot(board.id()).nodes().size());
        assertEquals(1, envelopes(events).stream().filter(e -> e.payload() instanceof NodeCreated).count());
    }

    @Test
    @DisplayName("una propuesta para un tablero inexistente se ignora")
    void propuestaSinTablero() {
        assertTrue(service.addProposedNode(UUID.randomUUID(),
                new Requests.CreateNode("Un paper", "PAPER", "10.1000/huerfano", null, null, null, 2024, null),
                Actor.agent("openalex"), UUID.randomUUID(), null).isEmpty());
    }

    private static List<EventEnvelope> envelopes(PublishedEvents events) {
        List<EventEnvelope> collected = new ArrayList<>();
        events.ofType(EventEnvelope.class).forEach(collected::add);
        return collected;
    }

    private static long decisiones(PublishedEvents events) {
        return envelopes(events).stream()
                .filter(envelope -> envelope.payload() instanceof NodeDecisionMade)
                .count();
    }
}
