import { useState, type FormEvent } from 'react';
import { Modal } from './modal';

export function SearchDialog({
  question,
  busy,
  error,
  onSubmit,
  onClose,
}: {
  question: string | null;
  busy: boolean;
  error: string | null;
  onSubmit: (query: string) => void;
  onClose: () => void;
}) {
  const [query, setQuery] = useState('');
  const fallback = question?.trim() ? question : null;
  const canSubmit = !busy && (query.trim() !== '' || fallback !== null);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (canSubmit) onSubmit(query);
  };

  return (
    <Modal
      title="Nueva búsqueda"
      description="Busca hasta 50 fuentes en OpenAlex, por título y abstract. Funciona mejor con términos concretos en inglés."
      onClose={onClose}
    >
      <form className="cover-form" onSubmit={submit}>
        <div className="field">
          <label htmlFor="search-query">Términos de búsqueda</label>
          <input
            id="search-query"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={fallback ?? 'systematic review automation'}
            autoFocus
          />
        </div>
        {fallback ? <p className="muted">Si lo dejas vacío, se usa la pregunta del tablero.</p> : null}
        {error ? <p className="error">{error}</p> : null}
        <div className="modal-actions">
          <button type="button" className="btn" onClick={onClose}>
            Cancelar
          </button>
          <button type="submit" className="btn primary" disabled={!canSubmit}>
            Buscar
          </button>
        </div>
      </form>
    </Modal>
  );
}
