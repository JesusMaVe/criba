package com.criba.agents.openalex;

import java.util.Optional;

final class WorkMapper {

    private WorkMapper() {
    }

    static Optional<OpenAlexWork> toWork(OpenAlexResponse.Work work) {
        throw new UnsupportedOperationException("por implementar");
    }

    static String normalizeDoi(String raw) {
        throw new UnsupportedOperationException("por implementar");
    }

    static String normalizeUrl(String raw) {
        throw new UnsupportedOperationException("por implementar");
    }
}
