package com.criba.board;

import com.criba.shared.Actor;
import com.criba.shared.CurrentActor;
import com.criba.shared.EventEnvelope;
import com.criba.shared.events.BoardCreated;
import com.criba.shared.events.NodeCreated;
import com.criba.shared.events.NodeDecisionMade;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La autoridad del tablero: valida el comando y, solo si es válido, publica el hecho.
 *
 * <p>Todo lo que se escribe pasa por aquí y en una sola transacción: la fila de estado, la decisión
 * inmutable y la publicación del evento. Si algo falla, no queda ni nodo ni evento.
 */
@Service
public class BoardService {

    private final BoardStore store;
    private final ApplicationEventPublisher events;
    private final CurrentActor currentActor;

    BoardService(BoardStore store, ApplicationEventPublisher events, CurrentActor currentActor) {
        this.store = store;
        this.events = events;
        this.currentActor = currentActor;
    }

    @Transactional
    public Views.BoardView createBoard(Requests.CreateBoard request, UUID correlationId) {
        Actor actor = currentActor.actor();
        Board board = new Board(UUID.randomUUID(), request.title(), request.researchQuestion(),
                actor.id(), 0L, Instant.now());
        store.insertBoard(board);

        long seq = store.nextSeq(board.id());
        events.publishEvent(EventEnvelope.of(
                new BoardCreated(board.id(), board.title(), board.researchQuestion(), board.ownerId()),
                board.id(), seq, actor, correlationId, null));

        return Views.BoardView.of(board.withSeq(seq));
    }

    @Transactional
    public Views.NodeView addNode(UUID boardId, Requests.CreateNode request, UUID correlationId) {
        Actor actor = currentActor.actor();
        requireBoard(boardId);

        String naturalKey = NaturalKey.of(request.doi(), request.openalexId(), request.url(),
                request.title(), request.year());
        Optional<Node> existing = store.findNodeByNaturalKey(boardId, naturalKey);
        if (existing.isPresent()) {
            throw new BoardException.Duplicate(existing.get().id());
        }

        // Posición inicial determinista, en zigzag de dos columnas. El layout de verdad
        // (grafo de citas) llega con views en la Etapa 5; aquí solo hace falta que no se solapen.
        int index = (int) store.countNodes(boardId);
        int column = index % 2;
        int row = index / 2;
        double x = 150 + column * 300 + (row % 3) * 16;
        double y = 110 + row * 205 + (column == 1 ? 44 : 0);

        NodeKind kind = parseKind(request.kind());
        Node node = new Node(UUID.randomUUID(), boardId, kind, naturalKey, request.doi(),
                request.openalexId(), request.url(), request.title(), request.abstractText(),
                request.year(), request.venue(), NodeState.CANDIDATO, x, y, Instant.now());
        store.insertNode(node);

        long seq = store.nextSeq(boardId);
        events.publishEvent(EventEnvelope.of(
                new NodeCreated(node.id(), kind.name(), node.title(), node.doi(), node.openalexId(),
                        node.url(), node.year(), node.venue(), node.state().name(), node.x(), node.y()),
                boardId, seq, actor, correlationId, null));

        return Views.NodeView.of(node);
    }

    /** Crea el nodo que propone un agente. Vacío si el tablero no existe o el nodo ya está. */
    @Transactional
    public Optional<Views.NodeView> addProposedNode(UUID boardId, Requests.CreateNode request, Actor actor,
            UUID correlationId, UUID causationId) {
        throw new UnsupportedOperationException("por implementar");
    }

    @Transactional
    public Views.DecisionResult decide(
            UUID boardId, UUID nodeId, Requests.Decision request, UUID correlationId) {

        Actor actor = currentActor.actor();
        requireBoard(boardId);
        Node node = store.findNode(boardId, nodeId)
                .orElseThrow(() -> new BoardException.NotFound("nodo", nodeId));

        // La misma petición repetida no produce una segunda decisión ni un segundo evento.
        if (request.idempotencyKey() != null) {
            Optional<Decision> previous = store.findDecisionByKey(nodeId, request.idempotencyKey());
            if (previous.isPresent()) {
                return new Views.DecisionResult(previous.get().seq(), Views.NodeView.of(node));
            }
        }

        NodeState target = parseState(request.toState());
        String reason = normalizeReason(request.reason());
        if (target.isDiscarded() && reason == null) {
            throw new BoardException.MissingReason();
        }

        Optional<NodeState> stateBeforeDiscard = node.state().isDiscarded()
                ? store.findLastDecision(nodeId).map(Decision::from)
                : Optional.empty();
        if (!Transitions.allowed(node.state(), target, stateBeforeDiscard)) {
            throw new BoardException.IllegalTransition(node.state(), target);
        }

        long seq = store.nextSeq(boardId);
        Decision decision = new Decision(UUID.randomUUID(), nodeId, boardId, node.state(), target, reason,
                actor.id(), null, request.idempotencyKey(), seq, Instant.now());
        store.insertDecision(decision);
        store.updateNodeState(nodeId, target);

        events.publishEvent(EventEnvelope.of(
                new NodeDecisionMade(nodeId, decision.from().name(), decision.to().name(),
                        decision.reason(), decision.criteriaVersion()),
                boardId, seq, actor, correlationId, null));

        return new Views.DecisionResult(seq, Views.NodeView.of(node.withState(target)));
    }

    @Transactional(readOnly = true)
    public java.util.List<Views.DecisionView> decisions(UUID boardId, UUID nodeId) {
        requireBoard(boardId);
        store.findNode(boardId, nodeId)
                .orElseThrow(() -> new BoardException.NotFound("nodo", nodeId));
        return store.listDecisions(nodeId).stream().map(Views.DecisionView::of).toList();
    }

    @Transactional(readOnly = true)
    public Views.BoardSnapshot snapshot(UUID boardId) {
        Board board = requireBoard(boardId);
        return new Views.BoardSnapshot(
                Views.BoardView.of(board),
                store.listNodes(boardId).stream().map(Views.NodeView::of).toList());
    }

    private Board requireBoard(UUID boardId) {
        return store.findBoard(boardId)
                .orElseThrow(() -> new BoardException.NotFound("tablero", boardId));
    }

    private static NodeKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return NodeKind.PAPER;
        }
        try {
            return NodeKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            return NodeKind.PAPER;
        }
    }

    private static NodeState parseState(String raw) {
        try {
            return NodeState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException | NullPointerException e) {
            throw new BoardException.UnknownState(String.valueOf(raw));
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
