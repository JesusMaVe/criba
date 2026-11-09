package com.criba.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class TaskListenerTest {

    private final ProcessedEvents processed = mock(ProcessedEvents.class);
    private final SearchTaskHandler searches = mock(SearchTaskHandler.class);
    private final TaskListener listener = new TaskListener(processed, JsonMapper.builder().build(), searches);
    private final UUID eventId = UUID.randomUUID();
    private final UUID boardId = UUID.randomUUID();

    private void deliver(String type) {
        String value = """
                {"eventId":"%s","boardId":"%s","type":"%s","payload":{"query":"q","maxNodes":50},"extra":1}
                """.formatted(eventId, boardId, type);
        listener.on(new ConsumerRecord<>("agent.tasks", 0, 0L, boardId.toString(), value));
    }

    @Test
    @DisplayName("una búsqueda nueva se ejecuta con sus datos")
    void ejecutaLaBusqueda() {
        when(processed.markFirstTime(TaskListener.CONSUMER, eventId)).thenReturn(true);

        deliver("SearchRequested");

        ArgumentCaptor<TaskEnvelope> task = ArgumentCaptor.forClass(TaskEnvelope.class);
        verify(searches).handle(task.capture());
        org.junit.jupiter.api.Assertions.assertEquals("q", task.getValue().payload().query());
        org.junit.jupiter.api.Assertions.assertEquals(50, task.getValue().payload().maxNodes());
    }

    @Test
    @DisplayName("una búsqueda repetida no vuelve a llamar a OpenAlex")
    void noRepite() {
        when(processed.markFirstTime(eq(TaskListener.CONSUMER), any())).thenReturn(false);

        deliver("SearchRequested");

        verify(searches, never()).handle(any());
    }

    @Test
    @DisplayName("una tarea de otro tipo se registra y no ejecuta nada")
    void otroTipo() {
        when(processed.markFirstTime(TaskListener.CONSUMER, eventId)).thenReturn(true);

        deliver("Otra");

        verify(processed).markFirstTime(TaskListener.CONSUMER, eventId);
        verify(searches, never()).handle(any());
    }
}
