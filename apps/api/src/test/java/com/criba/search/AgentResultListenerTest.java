package com.criba.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class AgentResultListenerTest {

    private final AgentResultHandler handler = mock(AgentResultHandler.class);
    private final AgentResultListener listener = new AgentResultListener(handler, JsonMapper.builder().build());

    private void deliver(String value) {
        listener.on(new ConsumerRecord<>("agent.results", 0, 0L, "k", value));
    }

    @Test
    @DisplayName("lee el JSON que publica el worker, ignorando los campos que no usa")
    void leeElContrato() {
        UUID eventId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        deliver("""
                {"eventId":"%s","boardId":"%s","schemaVersion":1,"type":"SourceFound",
                 "actor":{"type":"AGENT","id":"openalex"},"occurredAt":"2026-11-08T00:00:00Z",
                 "payload":{"openalexId":"W1","doi":"10.1/a","title":"Un paper","abstractText":"A","year":2024,"venue":"V"}}
                """.formatted(eventId, boardId));

        ArgumentCaptor<AgentResult> result = ArgumentCaptor.forClass(AgentResult.class);
        verify(handler).handle(result.capture());
        assertEquals(eventId, result.getValue().eventId());
        assertEquals(boardId, result.getValue().boardId());
        assertEquals("SourceFound", result.getValue().type());
        assertEquals("Un paper", result.getValue().payload().title());
        assertEquals(2024, result.getValue().payload().year());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"no es json", "null", "{}", "{\"eventId\":\"x\"}",
            "{\"eventId\":\"11111111-1111-1111-1111-111111111111\"}"})
    @DisplayName("un registro ilegible se descarta sin bloquear la partición")
    void ilegible(String value) {
        deliver(value);

        verifyNoInteractions(handler);
    }

    @Test
    @DisplayName("un fallo al guardar se propaga para reintentar")
    void falloAlGuardar() {
        doThrow(new IllegalStateException("sin base")).when(handler).handle(any());
        String value = "{\"eventId\":\"%s\",\"boardId\":\"%s\",\"type\":\"SourceFound\"}"
                .formatted(UUID.randomUUID(), UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> deliver(value));
    }
}
