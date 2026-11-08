export type NodeRow = {
  id: string;
  boardId: string;
  kind: string;
  title: string;
  doi: string | null;
  url: string | null;
  abstractText: string | null;
  venue: string | null;
  year: number | null;
  state: string;
  x: number | null;
  y: number | null;
};

export type BoardSnapshot = {
  board: {
    id: string;
    title: string;
    researchQuestion: string | null;
    ownerId: string;
    seq: number;
    createdAt: string;
  };
  nodes: NodeRow[];
};

export type DecisionView = {
  from: string;
  to: string;
  reason: string | null;
  actorId: string;
  seq: number;
  decidedAt: string;
};

export type BoardCreatedPayload = {
  kind: 'BoardCreated';
  boardId: string;
  title: string;
  researchQuestion: string | null;
  ownerId: string;
};

export type NodeCreatedPayload = {
  kind: 'NodeCreated';
  nodeId: string;
  /** PAPER o WEB. El campo se llama nodeKind para no chocar con el discriminador `kind`. */
  nodeKind: string;
  title: string;
  doi: string | null;
  openalexId: string | null;
  url: string | null;
  year: number | null;
  venue: string | null;
  state: string;
  x: number | null;
  y: number | null;
};

export type NodeDecisionMadePayload = {
  kind: 'NodeDecisionMade';
  nodeId: string;
  fromState: string;
  toState: string;
  reason: string | null;
};

export type EventPayload = BoardCreatedPayload | NodeCreatedPayload | NodeDecisionMadePayload;

/** Un hecho tal como viaja por Kafka y por el WebSocket. */
export type EventEnvelope = {
  eventId: string;
  boardId: string;
  seq: number;
  schemaVersion: number;
  type: string;
  actor: { type: string; id: string };
  correlationId: string | null;
  causationId: string | null;
  occurredAt: string;
  payload: EventPayload;
};
