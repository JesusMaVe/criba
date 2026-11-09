package com.criba.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.criba.agents.openalex.OpenAlexConnector;
import com.criba.agents.openalex.OpenAlexException;
import com.criba.agents.openalex.OpenAlexSearch;
import com.criba.agents.openalex.OpenAlexWork;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SearchTaskHandlerTest {

    private final OpenAlexConnector connector = mock(OpenAlexConnector.class);
    private final ResultPublisher publisher = mock(ResultPublisher.class);
    private final SearchTaskHandler handler = new SearchTaskHandler(connector, publisher);

    private static TaskEnvelope task(TaskEnvelope.Payload payload) {
        return new TaskEnvelope(UUID.randomUUID(), UUID.randomUUID(), "SearchRequested", UUID.randomUUID(), payload);
    }

    private static OpenAlexWork work(String id) {
        return new OpenAlexWork(id, null, null, "T", null, null, null);
    }

    @Test
    @DisplayName("publica cada fuente en el orden en que OpenAlex las devolvió")
    void publicaLasFuentes() {
        TaskEnvelope task = task(new TaskEnvelope.Payload("revisiones", 50));
        when(connector.search("revisiones", 50))
                .thenReturn(new OpenAlexSearch(List.of(work("W1"), work("W2")), 2, BigDecimal.ONE));

        handler.handle(task);

        InOrder order = inOrder(publisher);
        order.verify(publisher).sourceFound(task, work("W1"));
        order.verify(publisher).sourceFound(task, work("W2"));
        verify(publisher, never()).searchFailed(any(), any());
    }

    @Test
    @DisplayName("si OpenAlex falla, lo informa como SearchFailed y no deja escapar el error")
    void informaElFallo() {
        TaskEnvelope task = task(new TaskEnvelope.Payload("revisiones", 50));
        OpenAlexException error = mock(OpenAlexException.class);
        when(error.getMessage()).thenReturn("OpenAlex respondió 503");
        when(connector.search(anyString(), anyInt())).thenThrow(error);

        handler.handle(task);

        verify(publisher).searchFailed(task, "OpenAlex respondió 503");
        verify(publisher, never()).sourceFound(any(), any());
    }

    @Test
    @DisplayName("una consulta que el conector rechaza también se informa")
    void consultaInvalida() {
        TaskEnvelope task = task(new TaskEnvelope.Payload(" ", 50));
        when(connector.search(" ", 50)).thenThrow(new IllegalArgumentException("consulta vacía"));

        handler.handle(task);

        verify(publisher).searchFailed(task, "consulta vacía");
    }

    @Test
    @DisplayName("una tarea sin datos de búsqueda se informa sin llamar a OpenAlex")
    void sinDatos() {
        TaskEnvelope sinPayload = task(null);
        TaskEnvelope sinTope = task(new TaskEnvelope.Payload("q", null));

        handler.handle(sinPayload);
        handler.handle(sinTope);

        verifyNoInteractions(connector);
        verify(publisher).searchFailed(sinPayload, "la tarea no trae consulta y tope");
        verify(publisher).searchFailed(sinTope, "la tarea no trae consulta y tope");
    }

    @Test
    @DisplayName("un fallo al publicar se propaga: no es un fallo de la búsqueda")
    void falloDePublicacion() {
        TaskEnvelope task = task(new TaskEnvelope.Payload("q", 50));
        when(connector.search("q", 50)).thenReturn(new OpenAlexSearch(List.of(work("W1")), 1, BigDecimal.ONE));
        doThrow(new IllegalStateException("sin Kafka")).when(publisher).sourceFound(any(), any());

        assertThrows(IllegalStateException.class, () -> handler.handle(task));
        verify(publisher, never()).searchFailed(any(), any());
    }
}
