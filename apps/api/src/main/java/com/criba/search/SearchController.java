package com.criba.search;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/boards/{boardId}/searches")
class SearchController {

    record StartSearch(String query) {
    }

    private final SearchService service;

    SearchController(SearchService service) {
        this.service = service;
    }

    /** 202: la búsqueda queda encargada; los nodos llegan después por el WebSocket. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    SearchService.Accepted start(
            @PathVariable UUID boardId,
            @RequestBody(required = false) StartSearch request,
            @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId) {

        return service.start(boardId, request == null ? null : request.query(),
                correlationId != null ? correlationId : UUID.randomUUID());
    }
}
