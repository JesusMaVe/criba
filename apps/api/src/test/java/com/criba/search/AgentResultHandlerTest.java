package com.criba.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.criba.board.BoardService;
import com.criba.board.Requests;
import com.criba.shared.Actor;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AgentResultHandlerTest {

    private final ProcessedEvents processed = mock(ProcessedEvents.class);
    private final BoardService board = mock(BoardService.class);
    private final AgentResultHandler handler = new AgentResultHandler(processed, board);
    private final UUID eventId = UUID.randomUUID();
    private final UUID boardId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();
    private final UUID causationId = UUID.randomUUID();

    private static AgentResult.Payload source(String title) {
        return new AgentResult.Payload("W1", "10.1/a", "https://doi.org/10.1/a", title, "Abstract", 2024, "Venue", null);
    }

    private AgentResult result(String type, AgentResult.Payload payload) {
        return new AgentResult(eventId, boardId, type, correlationId, causationId, payload);
    }

    private void firstTime(boolean first) {
        when(processed.markFirstTime(AgentResultHandler.CONSUMER, eventId)).thenReturn(first);
    }

    @Test
    @DisplayName("una fuente encontrada se propone al tablero en nombre del agente")
    void proponeElNodo() {
        firstTime(true);

        handler.handle(result("SourceFound", source("Un paper")));

        verify(board).addProposedNode(boardId,
                new Requests.CreateNode("Un paper", "PAPER", "10.1/a", "W1", "https://doi.org/10.1/a", "Abstract", 2024, "Venue"),
                Actor.agent("openalex"), correlationId, causationId);
    }

    @Test
    @DisplayName("un evento repetido no toca el tablero")
    void repetido() {
        firstTime(false);

        handler.handle(result("SourceFound", source("Un paper")));

        verifyNoInteractions(board);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("la API valida: una propuesta sin título no crea nodo")
    void sinTitulo(String title) {
        firstTime(true);

        handler.handle(result("SourceFound", source(title)));

        verifyNoInteractions(board);
    }

    @Test
    @DisplayName("una fuente sin datos no crea nodo")
    void sinPayload() {
        firstTime(true);

        handler.handle(result("SourceFound", null));

        verifyNoInteractions(board);
    }

    @Test
    @DisplayName("un fallo de búsqueda no crea nodos")
    void busquedaFallida() {
        firstTime(true);

        handler.handle(result("SearchFailed", new AgentResult.Payload(null, null, null, null, null, null, null, "503")));

        verify(board, never()).addProposedNode(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "Desconocido")
    @DisplayName("un tipo desconocido queda registrado y no crea nodos")
    void tipoDesconocido(String type) {
        firstTime(true);

        handler.handle(result(type, source("Un paper")));

        verify(processed).markFirstTime(eq(AgentResultHandler.CONSUMER), eq(eventId));
        verifyNoInteractions(board);
    }
}
