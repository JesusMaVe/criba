package com.criba.agents.openalex;

import java.time.Duration;
import java.util.function.LongSupplier;

class RequestPacer {

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    RequestPacer(double requestsPerSecond, LongSupplier nanoTime, Sleeper sleeper) {
    }

    void acquire() throws InterruptedException {
        throw new UnsupportedOperationException("por implementar");
    }
}
