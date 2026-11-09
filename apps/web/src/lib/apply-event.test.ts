import { describe, expect, it } from 'vitest';
import { applyEvent } from './apply-event';
import type { BoardSnapshot, EventEnvelope, EventPayload, NodeRow } from './types';

const BOARD_ID = 'tablero-1';

const node: NodeRow = {
  id: 'nodo-1',
  boardId: BOARD_ID,
  kind: 'PAPER',
  title: 'Un paper',
  doi: null,
  url: null,
  abstractText: null,
  venue: null,
  year: 2024,
  state: 'CANDIDATO',
  x: 10,
  y: 20,
};

const snapshot: BoardSnapshot = {
  board: {
    id: BOARD_ID,
    title: 'Tablero',
    researchQuestion: null,
    ownerId: 'local-user',
    seq: 5,
    createdAt: '2026-11-08T00:00:00Z',
  },
  nodes: [node],
};

function envelope(seq: number, payload: EventPayload): EventEnvelope {
  return {
    eventId: `evento-${seq}`,
    boardId: BOARD_ID,
    seq,
    schemaVersion: 1,
    type: payload.kind,
    actor: { type: 'USER', id: 'local-user' },
    correlationId: null,
    causationId: null,
    occurredAt: '2026-11-08T00:00:00Z',
    payload,
  };
}

function created(nodeId: string): EventPayload {
  return {
    kind: 'NodeCreated',
    nodeId,
    nodeKind: 'WEB',
    title: 'Otra fuente',
    doi: null,
    openalexId: null,
    url: 'https://example.org',
    year: null,
    venue: null,
    state: 'CANDIDATO',
    x: 1,
    y: 2,
  };
}

const decision: EventPayload = {
  kind: 'NodeDecisionMade',
  nodeId: node.id,
  fromState: 'CANDIDATO',
  toState: 'PRESELECCIONADO',
  reason: null,
};

describe('applyEvent', () => {
  it.each([4, 5])('descarta un evento con seq %i, viejo o repetido', (seq) => {
    expect(applyEvent(snapshot, envelope(seq, decision))).toBe(snapshot);
  });

  it('agrega el nodo creado y avanza la secuencia', () => {
    const result = applyEvent(snapshot, envelope(6, created('nodo-2')));

    expect(result.board.seq).toBe(6);
    expect(result.nodes.map((n) => n.id)).toEqual(['nodo-1', 'nodo-2']);
    expect(result.nodes[1]).toMatchObject({ boardId: BOARD_ID, kind: 'WEB', state: 'CANDIDATO' });
  });

  it('no duplica un nodo que ya está en el tablero', () => {
    const result = applyEvent(snapshot, envelope(6, created(node.id)));

    expect(result.nodes).toEqual([node]);
    expect(result.board.seq).toBe(6);
  });

  it('cambia solo el estado del nodo decidido', () => {
    const other = { ...node, id: 'nodo-2' };
    const result = applyEvent({ ...snapshot, nodes: [node, other] }, envelope(6, decision));

    expect(result.nodes[0].state).toBe('PRESELECCIONADO');
    expect(result.nodes[1]).toBe(other);
  });

  it('un evento sin efecto en los nodos también avanza la secuencia', () => {
    const payload: EventPayload = {
      kind: 'BoardCreated',
      boardId: BOARD_ID,
      title: 'Tablero',
      researchQuestion: null,
      ownerId: 'local-user',
    };

    const result = applyEvent(snapshot, envelope(6, payload));

    expect(result.board.seq).toBe(6);
    expect(result.nodes).toBe(snapshot.nodes);
  });
});
