package com.criba.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.criba.board.BoardException;
import com.criba.board.BoardService;
import com.criba.board.Views;
import com.criba.shared.Actor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class SearchServiceTest {

    private final BoardService board = mock(BoardService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final SearchService service =
            new SearchService(board, events, () -> Actor.user("local-user"));
    private final UUID boardId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    private void boardWithQuestion(String question) {
        when(board.snapshot(boardId)).thenReturn(new Views.BoardSnapshot(
                new Views.BoardView(boardId, "T", question, "local-user", 0, Instant.now()), List.of()));
    }

    private SearchRequested published() {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        return (SearchRequested) event.getValue();
    }

    @Test
    @DisplayName("la consulta explícita manda sobre la pregunta del tablero, y el tope es 50")
    void consultaExplicita() {
        boardWithQuestion("¿Pregunta?");

        SearchService.Accepted accepted = service.start(boardId, "  revisiones sistemáticas ", correlationId);

        SearchRequested task = published();
        assertEquals("revisiones sistemáticas", task.payload().query());
        assertEquals(50, task.payload().maxNodes());
        assertEquals("revisiones sistemáticas", accepted.query());
        assertEquals(task.eventId(), accepted.searchId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("sin consulta se busca la pregunta del tablero tal cual")
    void usaLaPregunta(String query) {
        boardWithQuestion("¿Pregunta?");

        service.start(boardId, query, correlationId);

        assertEquals("¿Pregunta?", published().payload().query());
    }

    @Test
    @DisplayName("el sobre lleva lo que el worker y la trazabilidad necesitan")
    void sobre() {
        boardWithQuestion("¿Pregunta?");

        service.start(boardId, "q", correlationId);

        SearchRequested task = published();
        assertNotNull(task.eventId());
        assertNotNull(task.occurredAt());
        assertEquals(boardId, task.boardId());
        assertEquals(1, task.schemaVersion());
        assertEquals("SearchRequested", task.type());
        assertEquals(Actor.user("local-user"), task.actor());
        assertEquals(correlationId, task.correlationId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("sin consulta ni pregunta se rechaza y no se encarga nada")
    void sinConsultaNiPregunta(String question) {
        boardWithQuestion(question);

        assertThrows(BoardException.MissingQuery.class, () -> service.start(boardId, null, correlationId));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("un tablero inexistente no encarga nada")
    void tableroInexistente() {
        when(board.snapshot(boardId)).thenThrow(new BoardException.NotFound("tablero", boardId));

        assertThrows(BoardException.NotFound.class, () -> service.start(boardId, "q", correlationId));
        verify(events, never()).publishEvent(any(Object.class));
    }
}
