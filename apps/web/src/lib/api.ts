import type { BoardSnapshot, DecisionView, NodeRow } from './types';

const BASE = '/api';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as {
      error?: string;
      message?: string;
    };
    throw new ApiError(response.status, body.error ?? 'ERROR', body.message ?? response.statusText);
  }
  return (await response.json()) as T;
}

export const fetchBoard = (boardId: string) => request<BoardSnapshot>(`/boards/${boardId}`);

export const fetchDecisions = (boardId: string, nodeId: string) =>
  request<DecisionView[]>(`/boards/${boardId}/nodes/${nodeId}/decisions`);

export const createBoard = (title: string, researchQuestion: string) =>
  request<{ id: string }>('/boards', {
    method: 'POST',
    body: JSON.stringify({ title, researchQuestion }),
  });

export type CreateNodeBody = {
  title: string;
  kind: 'PAPER' | 'WEB';
  doi?: string | null;
  year?: number | null;
  venue?: string | null;
  abstract?: string | null;
};

export const createNode = (boardId: string, body: CreateNodeBody) =>
  request<NodeRow>(`/boards/${boardId}/nodes`, {
    method: 'POST',
    body: JSON.stringify(body),
  });

export const decide = (boardId: string, nodeId: string, toState: string, reason?: string) =>
  request<{ seq: number; node: NodeRow }>(`/boards/${boardId}/nodes/${nodeId}/decisions`, {
    method: 'POST',
    body: JSON.stringify({ toState, reason: reason ?? null }),
  });
