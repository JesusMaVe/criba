package com.criba.agents.openalex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestPacerTest {

    private long now = 1_000_000_000L;
    private final List<Duration> sleeps = new ArrayList<>();
    private final RequestPacer pacer = new RequestPacer(10, () -> now, duration -> {
        sleeps.add(duration);
        now += duration.toNanos();
    });

    @Test
    @DisplayName("la primera petición sale sin esperar")
    void laPrimeraNoEspera() throws Exception {
        pacer.acquire();

        assertEquals(List.of(), sleeps);
    }

    @Test
    @DisplayName("peticiones seguidas se separan por el intervalo")
    void lasSeguidasEsperan() throws Exception {
        pacer.acquire();
        pacer.acquire();
        pacer.acquire();

        assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(100)), sleeps);
    }

    @Test
    @DisplayName("descuenta el tiempo que ya pasó desde la anterior")
    void descuentaElTiempoTranscurrido() throws Exception {
        pacer.acquire();
        now += Duration.ofMillis(40).toNanos();
        pacer.acquire();

        assertEquals(List.of(Duration.ofMillis(60)), sleeps);
    }

    @Test
    @DisplayName("el tiempo inactivo no se acumula como ráfaga")
    void noAcumulaRafagas() throws Exception {
        pacer.acquire();
        now += Duration.ofSeconds(10).toNanos();
        pacer.acquire();
        pacer.acquire();

        assertEquals(List.of(Duration.ofMillis(100)), sleeps);
    }
}
