import { useState } from 'react';
import { StateIcon, stateLabel } from './state';
import type { DecisionView, NodeRow } from '../lib/types';

const FORWARD: Record<string, { to: string; label: string }> = {
  CANDIDATO: { to: 'PRESELECCIONADO', label: 'Preseleccionar' },
  PRESELECCIONADO: { to: 'ACEPTADO', label: 'Aceptar' },
  ACEPTADO: { to: 'DEFINITIVO', label: 'Marcar definitivo' },
  DEFINITIVO: { to: 'ACEPTADO', label: 'Reabrir' },
};

const KIND_LABEL: Record<string, string> = {
  PAPER: 'Artículo',
  WEB: 'Página web',
};

function sourceHref(node: NodeRow): string | null {
  if (node.url) return node.url;
  if (node.doi) return `https://doi.org/${node.doi}`;
  return null;
}

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('es', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso));
}

export function NodeDetail({
  node,
  history,
  busy,
  error,
  onDecide,
  onRequestDiscard,
}: {
  node: NodeRow;
  history: DecisionView[] | undefined;
  busy: boolean;
  error: string | null;
  onDecide: (toState: string) => void;
  onRequestDiscard: () => void;
}) {
  const [showHistory, setShowHistory] = useState(false);
  const href = sourceHref(node);
  const forward = FORWARD[node.state];
  const lastFrom = history?.[0]?.from;

  return (
    <>
      <div className="state-row">
        <StateIcon state={node.state} />
        {stateLabel(node.state)}
      </div>

      <h1>{node.title}</h1>

      <div className="meta-line">
        {node.year ? <span>{node.year}</span> : null}
        {node.year ? <span>·</span> : null}
        <span>{KIND_LABEL[node.kind] ?? node.kind}</span>
        {href ? (
          <>
            <span>·</span>
            <a href={href} target="_blank" rel="noreferrer">
              Ver fuente
            </a>
          </>
        ) : null}
      </div>

      <section>
        <p className="section-label">La IA sugiere</p>
        <div className="placeholder">
          Sin evaluar todavía. El evaluador llega en la Etapa 3 y sus respuestas a tus criterios
          aparecerán aquí, con el fragmento del abstract que las sostiene.
        </div>
      </section>

      {node.abstractText ? (
        <section>
          <p className="section-label">Abstract</p>
          <div className="quote">
            {node.abstractText.length > 280
              ? `${node.abstractText.slice(0, 280)}…`
              : node.abstractText}
          </div>
        </section>
      ) : null}

      {error ? <p className="error">{error}</p> : null}

      <div className="detail-actions">
        <div className="actions-row">
          {node.state === 'DESCARTADO' ? (
            <button
              type="button"
              className="btn primary"
              disabled={busy || !lastFrom}
              onClick={() => lastFrom && onDecide(lastFrom)}
            >
              Restaurar
            </button>
          ) : forward ? (
            <button
              type="button"
              className="btn primary"
              disabled={busy}
              onClick={() => onDecide(forward.to)}
            >
              {forward.label}
            </button>
          ) : null}
          {node.state !== 'DESCARTADO' ? (
            <button type="button" className="btn" disabled={busy} onClick={() => onRequestDiscard()}>
              Descartar…
            </button>
          ) : null}
        </div>

        <p className="detail-note">
          Tú decides. Al descartar te pediremos el motivo.{' '}
          <button type="button" onClick={() => setShowHistory((value) => !value)}>
            Historial ({history?.length ?? 0})
          </button>
        </p>

        {showHistory ? (
          <div className="history">
            {(history ?? []).length === 0 ? (
              <p className="muted small">Sin decisiones todavía.</p>
            ) : (
              (history ?? []).map((decision) => (
                <div className="history-item" key={decision.seq}>
                  <div>
                    {stateLabel(decision.from)} → <strong>{stateLabel(decision.to)}</strong>
                  </div>
                  {decision.reason ? (
                    <div className="muted">«{decision.reason}»</div>
                  ) : null}
                  <div className="when">{formatDate(decision.decidedAt)}</div>
                </div>
              ))
            )}
          </div>
        ) : null}
      </div>
    </>
  );
}

export function EmptyDetail() {
  return (
    <div className="empty-detail">
      <StateIcon state="CANDIDATO" size={18} />
      <p>Selecciona un nodo para ver su procedencia y decidir qué hacer con él.</p>
    </div>
  );
}
