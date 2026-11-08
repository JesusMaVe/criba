import { useState } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { useMutation } from '@tanstack/react-query';
import { createBoard } from '../lib/api';

export function IndexPage() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('LLMs en revisiones sistemáticas');
  const [question, setQuestion] = useState(
    '¿Qué tan confiable es usar LLMs para cribar estudios en revisiones sistemáticas?',
  );

  const mutation = useMutation({
    mutationFn: () => createBoard(title, question),
    onSuccess: (board) => {
      void navigate({ to: '/boards/$boardId', params: { boardId: board.id } });
    },
  });

  return (
    <div className="cover">
      <div className="cover-card">
        <div>
          <h1>Criba</h1>
          <p>
            Un tablero donde cada fuente conserva su procedencia y cada decisión su motivo. La IA
            sugiere; tú decides.
          </p>
        </div>

        <form
          className="cover-form"
          onSubmit={(event) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          <div className="field">
            <label htmlFor="board-title">Título</label>
            <input
              id="board-title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="board-question">Pregunta de investigación</label>
            <textarea
              id="board-question"
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
            />
          </div>
          {mutation.isError ? <p className="error">{mutation.error.message}</p> : null}
          <button type="submit" className="btn primary" disabled={mutation.isPending}>
            Crear tablero
          </button>
        </form>
      </div>
    </div>
  );
}
