package com.criba.agents.openalex;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Separa las peticiones de esta instancia. El tiempo inactivo no se acumula como ráfaga. */
class RequestPacer {

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private boolean started;
    private long nextSlot;

    RequestPacer(double requestsPerSecond, LongSupplier nanoTime, Sleeper sleeper) {
        this.intervalNanos = Math.round(1_000_000_000 / requestsPerSecond);
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    /** {@code synchronized} a propósito: los hilos concurrentes esperan su turno en fila. */
    synchronized void acquire() throws InterruptedException {
        long now = nanoTime.getAsLong();
        long wait = started ? nextSlot - now : 0;
        started = true;
        if (wait > 0) {
            sleeper.sleep(Duration.ofNanos(wait));
            nextSlot += intervalNanos;
        }
        else {
            nextSlot = now + intervalNanos;
        }
    }
}
