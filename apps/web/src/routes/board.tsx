import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useParams } from '@tanstack/react-router';
import { createNode, decide, fetchBoard, type CreateNodeBody } from '../lib/api';
import { applyEvent } from '../lib/apply-event';
import { queryClient } from '../lib/query-client';
import { subscribeToBoard, type ConnectionStatus } from '../lib/realtime';
import type { BoardSnapshot } from '../lib/types';

const NEXT_STATES: Record<string, string[]> = {
  CANDIDATO: ['PRESELECCIONADO', 'DESCARTADO'],
  PRESELECCIONADO: ['ACEPTADO', 'CANDIDATO', 'DESCARTADO'],
  ACEPTADO: ['DEFINITIVO', 'PRESELECCIONADO', 'DESCARTADO'],
  DEFINITIVO: ['ACEPTADO', 'DESCARTADO'],
  // El servidor solo permite volver al estado del que se salió; aquí se ofrece el abanico
  // para que el 409 sea visible en lugar de silencioso.
  DESCARTADO: ['CANDIDATO', 'PRESELECCIONADO', 'ACEPTADO', 'DEFINITIVO'],
};

export function BoardPage() {
  const { boardId } = useParams({ from: '/boards/$boardId' });
  const [status, setStatus] = useState<ConnectionStatus>('conectando');
  const [actionError, setActionError] = useState<string | null>(null);
  const boardKey = ['board', boardId] as const;

  const board = useQuery({
    queryKey: boardKey,
    queryFn: () => fetchBoard(boardId),
  });

  const subscription = useRef<(() => void) | null>(null);

  useEffect(() => {
    subscription.current = subscribeToBoard(
      boardId,
      (envelope) => {
        queryClient.setQueryData<BoardSnapshot>(boardKey, (current) =>
          current ? applyEvent(current, envelope) : current,
        );
      },
      setStatus,
    );
    return () => {
      subscription.current?.();
      subscription.current = null;
    };
  }, [boardId]);

  const nodeMutation = useMutation({
    mutationFn: (body: CreateNodeBody) => createNode(boardId, body),
    onSuccess: () => setActionError(null),
    onError: (error: Error) => setActionError(error.message),
  });

  const decisionMutation = useMutation({
    mutationFn: (input: { nodeId: string; toState: string; reason?: string }) =>
      decide(boardId, input.nodeId, input.toState, input.reason),
    onSuccess: () => setActionError(null),
    onError: (error: Error) => setActionError(error.message),
  });

  if (board.isLoading) {
    return <main className="panel">Cargando tablero…</main>;
  }
  if (board.isError || !board.data) {
    return (
      <main className="panel">
        <p className="error">{board.error?.message ?? 'Tablero no encontrado'}</p>
        <Link to="/">Volver</Link>
      </main>
    );
  }

  const { board: meta, nodes } = board.data;

  return (
    <main className="panel wide">
      <header className="board-header">
        <div>
          <Link to="/" className="muted">
            ← Criba
          </Link>
          <h1>{meta.title}</h1>
          <p className="muted">{meta.researchQuestion}</p>
        </div>
        <div className="status">
          <span className={`dot ${status}`} />
          {status} · seq {meta.seq}
        </div>
      </header>

      <form
        className="row"
        onSubmit={(event) => {
          event.preventDefault();
          const form = event.currentTarget;
          const data = new FormData(form);
          nodeMutation.mutate(
            {
              title: String(data.get('title') ?? ''),
              kind: (String(data.get('kind') ?? 'PAPER') as 'PAPER' | 'WEB'),
              doi: (String(data.get('doi') ?? '') || null) as string | null,
              year: data.get('year') ? Number(data.get('year')) : null,
              venue: (String(data.get('venue') ?? '') || null) as string | null,
            },
            { onSuccess: () => form.reset() },
          );
        }}
      >
        <input name="title" placeholder="Título del paper o página" required />
        <select name="kind" defaultValue="PAPER">
          <option value="PAPER">Paper</option>
          <option value="WEB">Página web</option>
        </select>
        <input name="doi" placeholder="DOI (opcional)" />
        <input name="year" placeholder="Año" type="number" min="1900" max="2100" />
        <input name="venue" placeholder="Venue" />
        <button type="submit" disabled={nodeMutation.isPending}>
          Añadir nodo
        </button>
      </form>

      {actionError ? <p className="error">{actionError}</p> : null}

      <table className="nodes">
        <thead>
          <tr>
            <th>Título</th>
            <th>Kind</th>
            <th>Año</th>
            <th>Venue</th>
            <th>Estado</th>
            <th>Decidir</th>
          </tr>
        </thead>
        <tbody>
          {nodes.length === 0 ? (
            <tr>
              <td colSpan={6} className="muted">
                Todavía no hay nodos. Añade uno y míralo aparecer en la otra pestaña.
              </td>
            </tr>
          ) : null}
          {nodes.map((node) => (
            <tr key={node.id}>
              <td>
                {node.title}
                {node.doi ? <span className="muted"> · {node.doi}</span> : null}
              </td>
              <td>{node.kind}</td>
              <td>{node.year ?? '—'}</td>
              <td>{node.venue ?? '—'}</td>
              <td>
                <span className={`state state-${node.state.toLowerCase()}`}>{node.state}</span>
              </td>
              <td className="actions">
                {(NEXT_STATES[node.state] ?? []).map((target) => (
                  <button
                    key={target}
                    type="button"
                    disabled={decisionMutation.isPending}
                    onClick={() => {
                      if (target === 'DESCARTADO') {
                        const reason = window.prompt('Motivo del descarte (obligatorio):') ?? '';
                        if (!reason.trim()) {
                          setActionError('Descartar un nodo exige un motivo');
                          return;
                        }
                        decisionMutation.mutate({ nodeId: node.id, toState: target, reason });
                        return;
                      }
                      decisionMutation.mutate({ nodeId: node.id, toState: target });
                    }}
                  >
                    {target}
                  </button>
                ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  );
}
