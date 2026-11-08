package com.criba.board;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/boards")
class BoardController {

    private final BoardService service;

    BoardController(BoardService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Views.BoardView create(
            @Valid @RequestBody Requests.CreateBoard request,
            @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId) {

        return service.createBoard(request, correlationId(correlationId));
    }

    @GetMapping("/{boardId}")
    Views.BoardSnapshot snapshot(@PathVariable UUID boardId) {
        return service.snapshot(boardId);
    }

    @PostMapping("/{boardId}/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    Views.NodeView addNode(
            @PathVariable UUID boardId,
            @Valid @RequestBody Requests.CreateNode request,
            @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId) {

        return service.addNode(boardId, request, correlationId(correlationId));
    }

    @PostMapping("/{boardId}/nodes/{nodeId}/decisions")
    Views.DecisionResult decide(
            @PathVariable UUID boardId,
            @PathVariable UUID nodeId,
            @Valid @RequestBody Requests.Decision request,
            @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId) {

        return service.decide(boardId, nodeId, request, correlationId(correlationId));
    }

    private static UUID correlationId(UUID header) {
        return header != null ? header : UUID.randomUUID();
    }
}
