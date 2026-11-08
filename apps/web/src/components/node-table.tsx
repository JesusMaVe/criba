import { StateIcon, stateLabel } from './state';
import type { NodeRow } from '../lib/types';

export function NodeTable({
  nodes,
  selectedId,
  onSelect,
}: {
  nodes: NodeRow[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  return (
    <div className="table-wrap">
      <table className="nodes">
        <thead>
          <tr>
            <th>Fuente</th>
            <th>Estado</th>
            <th>Año</th>
            <th>Venue</th>
            <th>DOI</th>
          </tr>
        </thead>
        <tbody>
          {nodes.length === 0 ? (
            <tr>
              <td colSpan={5} className="muted">
                Todavía no hay nodos. Añade uno desde el menú y míralo aparecer en la otra pestaña.
              </td>
            </tr>
          ) : null}
          {nodes.map((node) => (
            <tr
              key={node.id}
              aria-selected={node.id === selectedId}
              onClick={() => onSelect(node.id)}
            >
              <td>
                <div className="cell-title">
                  <span>{node.title}</span>
                  {node.doi ? <span className="meta">{node.doi}</span> : null}
                </div>
              </td>
              <td>
                <span className="state-row">
                  <StateIcon state={node.state} />
                  {stateLabel(node.state)}
                </span>
              </td>
              <td>{node.year ?? '—'}</td>
              <td>{node.venue ?? '—'}</td>
              <td className="muted">{node.doi ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
