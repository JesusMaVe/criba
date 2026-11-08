package com.criba.board;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** Cuerpos de petición de la API del tablero. */
public final class Requests {

    private Requests() {
    }

    public record CreateBoard(@NotBlank String title, String researchQuestion) {
    }

    public record CreateNode(
            @NotBlank String title,
            String kind,
            String doi,
            String openalexId,
            String url,
            @JsonProperty("abstract") String abstractText,
            Integer year,
            String venue) {
    }

    public record Decision(@NotBlank String toState, String reason, UUID idempotencyKey) {
    }
}
