import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useParams } from '@tanstack/react-router';
import { Canvas } from '../components/canvas';
import { Modal } from '../components/modal';
import { NodeDetail, EmptyDetail } from '../components/node-detail';
import { NodeTable } from '../components/node-table';
import { SearchDialog } from '../components/search-dialog';
import { Sidebar } from '../components/sidebar';
import { TopBar } from '../components/top-bar';
import {
  createNode,
  decide,
  fetchBoard,
  fetchDecisions,
  startSearch,
  type CreateNodeBody,
} from '../lib/api';
import { applyEvent } from '../lib/apply-event';
import { queryClient } from '../lib/query-client';
import { subscribeToBoard, type ConnectionStatus } from '../lib/realtime';
import type { BoardSnapshot } from '../lib/types';

type ViewMode = 'grafo' | 'tabla';

export function BoardPage() {
  const { boardId } = useParams({ from: '/boards/$boardId' });
  const boardKey = useMemo(() => ['board', boardId] as const, [boardId]);

  const [mode, setMode] = useState<ViewMode>('grafo');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showDiscarded, setShowDiscarded] = useState(false);
  const [status, setStatus] = useState<ConnectionStatus>('conectando');
  const [addOpen, setAddOpen] = useState(false);
  const [discardOpen, setDiscardOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searching, setSearching] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const board = useQuery({ queryKey: boardKey, queryFn: () => fetchBoard(boardId) });

  const history = useQuery({
    queryKey: ['decisions', boardId, selectedId],
    queryFn: () => fetchDecisions(boardId, selectedId as string),
    enabled: selectedId !== null,
  });

  useEffect(() => {
    const unsubscribe = subscribeToBoard(
      boardId,
      (envelope) => {
        queryClient.setQueryData<BoardSnapshot>(boardKey, (current) =>
          current ? applyEvent(current, envelope) : current,
        );
        // El evento no lleva el abstract: se reconcilia con el servidor en segundo plano.
        if (envelope.payload.kind === 'NodeCreated') {
          void queryClient.invalidateQueries({ queryKey: boardKey });
          if (envelope.actor.type === 'AGENT') setSearching(null);
        }
        if (envelope.payload.kind === 'NodeDecisionMade') {
          void queryClient.invalidateQueries({ queryKey: ['decisions', boardId] });
        }
      },
      setStatus,
    );
    return unsubscribe;
  }, [boardId, boardKey]);

  const nodeMutation = useMutation({
    mutationFn: (body: CreateNodeBody) => createNode(boardId, body),
    onSuccess: () => {
      setActionError(null);
      setAddOpen(false);
    },
    onError: (error: Error) => setActionError(error.message),
  });

  const searchMutation = useMutation({
    mutationFn: (query: string) => startSearch(boardId, query),
    onSuccess: (accepted) => {
      setActionError(null);
      setSearchOpen(false);
      setSearching(accepted.query);
    },
    onError: (error: Error) => setActionError(error.message),
  });

  const decisionMutation = useMutation({
    mutationFn: (input: { nodeId: string; toState: string; reason?: string }) =>
      decide(boardId, input.nodeId, input.toState, input.reason),
    onSuccess: () => {
      setActionError(null);
      setDiscardOpen(false);
      void queryClient.invalidateQueries({ queryKey: ['decisions', boardId] });
    },
    onError: (error: Error) => setActionError(error.message),
  });

  if (board.isLoading) {
    return (
      <div className="cover">
        <p className="muted">Cargando tablero…</p>
      </div>
    );
  }
  if (board.isError || !board.data) {
    return (
      <div className="cover">
        <div className="cover-card">
          <h1>No se pudo abrir el tablero</h1>
          <p className="error">{board.error?.message ?? 'El tablero no existe'}</p>
          <Link to="/" className="btn">
            Volver
          </Link>
        </div>
      </div>
    );
  }

  const { board: meta, nodes } = board.data;
  const visible = nodes.filter((node) => showDiscarded || node.state !== 'DESCARTADO');
  const selected = nodes.find((node) => node.id === selectedId) ?? null;

  const onSubmitNode = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    nodeMutation.mutate({
      title: String(data.get('title') ?? '').trim(),
      kind: (String(data.get('kind') ?? 'PAPER') as 'PAPER' | 'WEB'),
      doi: (String(data.get('doi') ?? '').trim() || null) as string | null,
      year: data.get('year') ? Number(data.get('year')) : null,
      venue: (String(data.get('venue') ?? '').trim() || null) as string | null,
      abstract: (String(data.get('abstract') ?? '').trim() || null) as string | null,
    });
  };

  return (
    <div className="app">
      <TopBar
        boardTitle={meta.title}
        status={status}
        seq={meta.seq}
        onAddNode={() => setAddOpen(true)}
      />

      <div className="workspace">
        <Sidebar
          question={meta.researchQuestion}
          nodes={nodes}
          showDiscarded={showDiscarded}
          onToggleDiscarded={() => setShowDiscarded((value) => !value)}
          onNewSearch={() => {
            setActionError(null);
            setSearchOpen(true);
          }}
        />

        <div className="stage">
          <div className="stage-toolbar">
            <div className="segmented" role="group" aria-label="Vista">
              <button type="button" aria-pressed={mode === 'grafo'} onClick={() => setMode('grafo')}>
                Grafo
              </button>
              <button type="button" aria-pressed={mode === 'tabla'} onClick={() => setMode('tabla')}>
                Tabla
              </button>
            </div>
            {searching ? (
              <p className="search-notice" role="status">
                <span>
                  Buscando «{searching}» en OpenAlex. Si no llega ninguna fuente, prueba con otros
                  términos.
                </span>
                <button type="button" aria-label="Cerrar aviso" onClick={() => setSearching(null)}>
                  ×
                </button>
              </p>
            ) : null}
          </div>

          {mode === 'grafo' ? (
            <Canvas nodes={visible} selectedId={selectedId} onSelect={setSelectedId} />
          ) : (
            <NodeTable nodes={visible} selectedId={selectedId} onSelect={setSelectedId} />
          )}
        </div>

        <aside className="detail">
          {selected ? (
            <NodeDetail
              node={selected}
              history={history.data}
              busy={decisionMutation.isPending}
              error={actionError}
              onDecide={(toState) =>
                decisionMutation.mutate({ nodeId: selected.id, toState })
              }
              onRequestDiscard={() => setDiscardOpen(true)}
            />
          ) : (
            <EmptyDetail />
          )}
        </aside>
      </div>

      {addOpen ? (
        <Modal
          title="Añadir nodo a mano"
          description="Para las fuentes que la búsqueda no encuentre. La deduplicación por DOI ya funciona."
          onClose={() => setAddOpen(false)}
        >
          <form className="cover-form" onSubmit={onSubmitNode}>
            <div className="field">
              <label htmlFor="node-title">Título</label>
              <input id="node-title" name="title" required autoFocus />
            </div>
            <div className="form-grid">
              <div className="field">
                <label htmlFor="node-kind">Tipo</label>
                <select id="node-kind" name="kind" defaultValue="PAPER">
                  <option value="PAPER">Artículo</option>
                  <option value="WEB">Página web</option>
                </select>
              </div>
              <div className="field">
                <label htmlFor="node-year">Año</label>
                <input id="node-year" name="year" type="number" min="1900" max="2100" />
              </div>
              <div className="field">
                <label htmlFor="node-doi">DOI</label>
                <input id="node-doi" name="doi" placeholder="10.1000/xyz" />
              </div>
              <div className="field">
                <label htmlFor="node-venue">Venue</label>
                <input id="node-venue" name="venue" />
              </div>
              <div className="field full">
                <label htmlFor="node-abstract">Abstract</label>
                <textarea id="node-abstract" name="abstract" />
              </div>
            </div>
            {actionError ? <p className="error">{actionError}</p> : null}
            <div className="modal-actions">
              <button type="button" className="btn" onClick={() => setAddOpen(false)}>
                Cancelar
              </button>
              <button type="submit" className="btn primary" disabled={nodeMutation.isPending}>
                Añadir nodo
              </button>
            </div>
          </form>
        </Modal>
      ) : null}

      {searchOpen ? (
        <SearchDialog
          question={meta.researchQuestion}
          busy={searchMutation.isPending}
          error={actionError}
          onSubmit={(query) => searchMutation.mutate(query)}
          onClose={() => setSearchOpen(false)}
        />
      ) : null}

      {discardOpen && selected ? (
        <Modal
          title="Descartar nodo"
          description="El motivo queda en el historial y la decisión es reversible."
          onClose={() => setDiscardOpen(false)}
        >
          <form
            className="cover-form"
            onSubmit={(event) => {
              event.preventDefault();
              const reason = String(new FormData(event.currentTarget).get('reason') ?? '').trim();
              if (!reason) {
                setActionError('Descartar un nodo exige un motivo');
                return;
              }
              decisionMutation.mutate({
                nodeId: selected.id,
                toState: 'DESCARTADO',
                reason,
              });
            }}
          >
            <div className="field">
              <label htmlFor="reason">Motivo</label>
              <textarea
                id="reason"
                name="reason"
                required
                autoFocus
                placeholder="No usa un dataset público; el método no es replicable…"
              />
            </div>
            {actionError ? <p className="error">{actionError}</p> : null}
            <div className="modal-actions">
              <button type="button" className="btn" onClick={() => setDiscardOpen(false)}>
                Cancelar
              </button>
              <button type="submit" className="btn primary" disabled={decisionMutation.isPending}>
                Descartar
              </button>
            </div>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}
