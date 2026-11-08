package com.criba.board;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Acceso a datos del tablero, con SQL explícito.
 *
 * <p>No se usa un ORM a propósito: las entidades tienen id asignado (UUID) y la escritura es un
 * puñado de sentencias. Con SQL se ve exactamente qué se bloquea y en qué orden, que es lo que
 * importa aquí — {@link #nextSeq} es lo que serializa los comandos de un mismo tablero.
 */
@Component
class BoardStore {

    private static final String NODE_COLUMNS =
            "id, board_id, kind, natural_key, doi, openalex_id, url, title, abstract, year, venue, "
                    + "state, x, y, created_at";

    private static final RowMapper<Node> NODE_MAPPER = (rs, rowNum) -> new Node(
            rs.getObject("id", UUID.class),
            rs.getObject("board_id", UUID.class),
            NodeKind.valueOf(rs.getString("kind")),
            rs.getString("natural_key"),
            rs.getString("doi"),
            rs.getString("openalex_id"),
            rs.getString("url"),
            rs.getString("title"),
            rs.getString("abstract"),
            rs.getObject("year", Integer.class),
            rs.getString("venue"),
            NodeState.valueOf(rs.getString("state")),
            rs.getObject("x", Double.class),
            rs.getObject("y", Double.class),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private static final RowMapper<Board> BOARD_MAPPER = (rs, rowNum) -> new Board(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("research_question"),
            rs.getString("owner_id"),
            rs.getLong("seq"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private static final RowMapper<Decision> DECISION_MAPPER = (rs, rowNum) -> new Decision(
            rs.getObject("id", UUID.class),
            rs.getObject("node_id", UUID.class),
            rs.getObject("board_id", UUID.class),
            NodeState.valueOf(rs.getString("from_state")),
            NodeState.valueOf(rs.getString("to_state")),
            rs.getString("reason"),
            rs.getString("actor_id"),
            rs.getObject("criteria_version", Integer.class),
            rs.getObject("idempotency_key", UUID.class),
            rs.getLong("seq"),
            rs.getObject("decided_at", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    BoardStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertBoard(Board board) {
        jdbc.sql("""
                        insert into board (id, title, research_question, owner_id, seq, created_at)
                        values (:id, :title, :researchQuestion, :ownerId, :seq, :createdAt)
                        """)
                .param("id", board.id())
                .param("title", board.title())
                .param("researchQuestion", board.researchQuestion(), Types.VARCHAR)
                .param("ownerId", board.ownerId())
                .param("seq", board.seq())
                .param("createdAt", OffsetDateTime.ofInstant(board.createdAt(), java.time.ZoneOffset.UTC))
                .update();
    }

    Optional<Board> findBoard(UUID boardId) {
        return jdbc.sql("select * from board where id = :id")
                .param("id", boardId)
                .query(BOARD_MAPPER)
                .optional();
    }

    /**
     * Siguiente número de secuencia del tablero.
     *
     * <p>El {@code update} toma el bloqueo de la fila y la incrementa en una sola sentencia: dos
     * comandos concurrentes sobre el mismo tablero se serializan aquí, y por eso los eventos salen
     * en orden sin necesidad de un contador en memoria.
     */
    long nextSeq(UUID boardId) {
        return jdbc.sql("update board set seq = seq + 1 where id = :id returning seq")
                .param("id", boardId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new BoardException.NotFound("tablero", boardId));
    }

    void insertNode(Node node) {
        jdbc.sql("""
                        insert into node (id, board_id, kind, natural_key, doi, openalex_id, url, title,
                                          abstract, year, venue, state, x, y, created_at)
                        values (:id, :boardId, :kind, :naturalKey, :doi, :openalexId, :url, :title,
                                :abstractText, :year, :venue, :state, :x, :y, :createdAt)
                        """)
                .param("id", node.id())
                .param("boardId", node.boardId())
                .param("kind", node.kind().name())
                .param("naturalKey", node.naturalKey())
                .param("doi", node.doi(), Types.VARCHAR)
                .param("openalexId", node.openalexId(), Types.VARCHAR)
                .param("url", node.url(), Types.VARCHAR)
                .param("title", node.title())
                .param("abstractText", node.abstractText(), Types.VARCHAR)
                .param("year", node.year(), Types.INTEGER)
                .param("venue", node.venue(), Types.VARCHAR)
                .param("state", node.state().name())
                .param("x", node.x(), Types.DOUBLE)
                .param("y", node.y(), Types.DOUBLE)
                .param("createdAt", OffsetDateTime.ofInstant(node.createdAt(), java.time.ZoneOffset.UTC))
                .update();
    }

    Optional<Node> findNode(UUID boardId, UUID nodeId) {
        return jdbc.sql("select " + NODE_COLUMNS + " from node where board_id = :boardId and id = :id")
                .param("boardId", boardId)
                .param("id", nodeId)
                .query(NODE_MAPPER)
                .optional();
    }

    Optional<Node> findNodeByNaturalKey(UUID boardId, String naturalKey) {
        return jdbc.sql("select " + NODE_COLUMNS
                        + " from node where board_id = :boardId and natural_key = :naturalKey")
                .param("boardId", boardId)
                .param("naturalKey", naturalKey)
                .query(NODE_MAPPER)
                .optional();
    }

    List<Node> listNodes(UUID boardId) {
        return jdbc.sql("select " + NODE_COLUMNS
                        + " from node where board_id = :boardId order by created_at, id")
                .param("boardId", boardId)
                .query(NODE_MAPPER)
                .list();
    }

    long countNodes(UUID boardId) {
        return jdbc.sql("select count(*) from node where board_id = :boardId")
                .param("boardId", boardId)
                .query(Long.class)
                .single();
    }

    void updateNodeState(UUID nodeId, NodeState state) {
        jdbc.sql("update node set state = :state where id = :id")
                .param("state", state.name())
                .param("id", nodeId)
                .update();
    }

    void insertDecision(Decision decision) {
        jdbc.sql("""
                        insert into node_decision (id, node_id, board_id, from_state, to_state, reason,
                                                   actor_id, criteria_version, idempotency_key, seq, decided_at)
                        values (:id, :nodeId, :boardId, :fromState, :toState, :reason, :actorId,
                                :criteriaVersion, :idempotencyKey, :seq, :decidedAt)
                        """)
                .param("id", decision.id())
                .param("nodeId", decision.nodeId())
                .param("boardId", decision.boardId())
                .param("fromState", decision.from().name())
                .param("toState", decision.to().name())
                .param("reason", decision.reason(), Types.VARCHAR)
                .param("actorId", decision.actorId())
                .param("criteriaVersion", decision.criteriaVersion(), Types.INTEGER)
                .param("idempotencyKey", decision.idempotencyKey(), Types.OTHER)
                .param("seq", decision.seq())
                .param("decidedAt", OffsetDateTime.ofInstant(decision.decidedAt(), java.time.ZoneOffset.UTC))
                .update();
    }

    List<Decision> listDecisions(UUID nodeId) {
        return jdbc.sql("select * from node_decision where node_id = :nodeId order by seq desc")
                .param("nodeId", nodeId)
                .query(DECISION_MAPPER)
                .list();
    }

    Optional<Decision> findLastDecision(UUID nodeId) {
        return jdbc.sql("select * from node_decision where node_id = :nodeId order by seq desc limit 1")
                .param("nodeId", nodeId)
                .query(DECISION_MAPPER)
                .optional();
    }

    Optional<Decision> findDecisionByKey(UUID nodeId, UUID idempotencyKey) {
        return jdbc.sql("select * from node_decision where node_id = :nodeId and idempotency_key = :key")
                .param("nodeId", nodeId)
                .param("key", idempotencyKey)
                .query(DECISION_MAPPER)
                .optional();
    }

    Instant now() {
        return Instant.now();
    }
}
