// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SearchDialog } from './search-dialog';

function setup(props: Partial<Parameters<typeof SearchDialog>[0]> = {}) {
  const onSubmit = vi.fn();
  const onClose = vi.fn();
  render(
    <SearchDialog
      question="¿Se puede automatizar la criba?"
      busy={false}
      error={null}
      onSubmit={onSubmit}
      onClose={onClose}
      {...props}
    />,
  );
  return { onSubmit, onClose };
}

const input = () => screen.getByLabelText<HTMLInputElement>('Términos de búsqueda');
const submit = () => screen.getByRole<HTMLButtonElement>('button', { name: 'Buscar' });
const type = (value: string) => fireEvent.change(input(), { target: { value } });

afterEach(cleanup);

describe('SearchDialog', () => {
  it('avisa del alcance antes de buscar', () => {
    setup();

    expect(screen.getByRole('dialog', { name: 'Nueva búsqueda' }).textContent).toMatch(/hasta 50 fuentes/i);
    expect(screen.getByRole('dialog').textContent).toMatch(/en inglés/i);
  });

  it('envía los términos escritos', () => {
    const { onSubmit } = setup();

    type('citation screening');
    fireEvent.click(submit());

    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('citation screening');
  });

  it('vacío busca la pregunta del tablero, y lo dice', () => {
    const { onSubmit } = setup();

    expect(screen.getByRole('dialog').textContent).toMatch(/se usa la pregunta/i);
    expect(input().placeholder).toBe('¿Se puede automatizar la criba?');
    fireEvent.click(submit());

    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('');
  });

  it.each([null, '   '])('sin pregunta (%j) exige términos', (question) => {
    setup({ question });

    expect(submit().disabled).toBe(true);
    expect(screen.getByRole('dialog').textContent).not.toMatch(/se usa la pregunta/i);

    type('   ');
    expect(submit().disabled).toBe(true);

    type('citation screening');
    expect(submit().disabled).toBe(false);
  });

  it('mientras se encarga no se puede enviar dos veces', () => {
    const { onSubmit } = setup({ busy: true });

    expect(submit().disabled).toBe(true);
    fireEvent.submit(input().form as HTMLFormElement);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('muestra el error', () => {
    setup({ error: 'No se pudo encargar' });

    expect(screen.getByText('No se pudo encargar')).toBeTruthy();
  });

  it('se cancela sin buscar', () => {
    const { onSubmit, onClose } = setup();

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(onClose).toHaveBeenCalledOnce();
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
