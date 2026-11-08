import type { BoardSnapshot, EventEnvelope } from './types';

/**
 * Aplica un hecho a la copia local del tablero.
 *
 * <p>Si el evento es viejo o repetido se descarta: la secuencia por tablero es la que evita que un
 * frame atrasado devuelva la interfaz a un estado anterior.
 */
export function applyEvent(snapshot: BoardSnapshot, envelope: EventEnvelope): BoardSnapshot {
  if (envelope.seq <= snapshot.board.seq) {
    return snapshot;
  }

  const payload = envelope.payload;
  let nodes = snapshot.nodes;

  if (payload.kind === 'NodeCreated') {
    if (!nodes.some((node) => node.id === payload.nodeId)) {
      nodes = [
        ...nodes,
        {
          id: payload.nodeId,
          boardId: envelope.boardId,
          kind: payload.nodeKind,
          title: payload.title,
          doi: payload.doi,
          venue: payload.venue,
          year: payload.year,
          state: payload.state,
        },
      ];
    }
  } else if (payload.kind === 'NodeDecisionMade') {
    nodes = nodes.map((node) =>
      node.id === payload.nodeId ? { ...node, state: payload.toState } : node,
    );
  }

  return { board: { ...snapshot.board, seq: envelope.seq }, nodes };
}
