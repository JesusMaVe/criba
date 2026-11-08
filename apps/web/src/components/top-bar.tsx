import { useState } from 'react';
import { Link } from '@tanstack/react-router';

export function TopBar({
  boardTitle,
  status,
  seq,
  onAddNode,
}: {
  boardTitle: string;
  status: string;
  seq: number;
  onAddNode: () => void;
}) {
  const [copied, setCopied] = useState(false);

  const share = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  };

  return (
    <header className="topbar">
      <nav className="breadcrumb">
        <Link to="/">Nodos</Link>
        <span className="sep">/</span>
        <strong>{boardTitle}</strong>
      </nav>
      <div className="topbar-actions">
        <span className="conn">
          <span className={`dot ${status}`} />
          {status} · seq {seq}
        </span>
        <button type="button" className="btn dark" onClick={share}>
          {copied ? 'Enlace copiado' : 'Compartir'}
        </button>
        <details className="menu">
          <summary aria-label="Más acciones">⋯</summary>
          <div className="menu-panel">
            <button type="button" onClick={onAddNode}>
              Añadir nodo a mano
            </button>
          </div>
        </details>
      </div>
    </header>
  );
}
