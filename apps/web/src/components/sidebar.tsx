import { STATE_ORDER, StateIcon, stateLabel } from './state';
import type { NodeRow } from '../lib/types';

function countByState(nodes: NodeRow[]): Record<string, number> {
  const counts: Record<string, number> = Object.fromEntries(STATE_ORDER.map((s) => [s, 0]));
  counts.DESCARTADO = 0;
  for (const node of nodes) {
    counts[node.state] = (counts[node.state] ?? 0) + 1;
  }
  return counts;
}

export function Sidebar({
  question,
  nodes,
  showDiscarded,
  onToggleDiscarded,
}: {
  question: string | null;
  nodes: NodeRow[];
  showDiscarded: boolean;
  onToggleDiscarded: () => void;
}) {
  const counts = countByState(nodes);

  return (
    <aside className="sidebar">
      <section>
        <h2>Pregunta</h2>
        <p className="question">{question ?? 'Sin pregunta de investigación'}</p>
      </section>

      <section>
        <h2>Fuentes por estado</h2>
        <ul className="counts">
          {STATE_ORDER.map((state) => (
            <li key={state}>
              <StateIcon state={state} />
              <span className="label">{stateLabel(state)}</span>
              <span className="value">{counts[state] ?? 0}</span>
            </li>
          ))}
          <li className="discarded">
            <button type="button" onClick={onToggleDiscarded} aria-pressed={showDiscarded}>
              <StateIcon state="DESCARTADO" />
              <span className="label">
                {showDiscarded ? 'Ocultar descartados' : 'Descartados · ocultos'}
              </span>
              <span className="value">{counts.DESCARTADO ?? 0}</span>
            </button>
          </li>
        </ul>
      </section>

      <div className="sidebar-footer">
        <button
          type="button"
          className="btn block"
          disabled
          title="Los criterios versionados llegan en la Etapa 1"
        >
          <span>Criterios</span>
          <span className="arrow">sin definir</span>
        </button>
        <button
          type="button"
          className="btn block"
          disabled
          title="La búsqueda con agentes llega en la Etapa 2"
        >
          <span>Nueva búsqueda</span>
          <span className="arrow">etapa 2</span>
        </button>
      </div>
    </aside>
  );
}
