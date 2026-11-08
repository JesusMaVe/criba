import { useState } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { useMutation } from '@tanstack/react-query';
import { createBoard } from '../lib/api';

export function IndexPage() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('Transparencia en research');
  const [question, setQuestion] = useState('¿Qué evidencia hay sobre la atención en LLMs?');

  const mutation = useMutation({
    mutationFn: () => createBoard(title, question),
    onSuccess: (board) => {
      void navigate({ to: '/boards/$boardId', params: { boardId: board.id } });
    },
  });

  return (
    <main className="panel">
      <h1>Criba</h1>
      <p className="muted">
        Tablero de research donde cada nodo conserva su procedencia y cada decisión su motivo.
      </p>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault();
          mutation.mutate();
        }}
      >
        <label>
          Título
          <input value={title} onChange={(event) => setTitle(event.target.value)} required />
        </label>
        <label>
          Pregunta de investigación
          <input value={question} onChange={(event) => setQuestion(event.target.value)} />
        </label>
        <button type="submit" disabled={mutation.isPending}>
          Crear tablero
        </button>
      </form>
      {mutation.isError ? <p className="error">{mutation.error.message}</p> : null}
    </main>
  );
}
