// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Sidebar } from './sidebar';

afterEach(cleanup);

describe('Sidebar', () => {
  it('Nueva búsqueda está disponible y abre el diálogo', () => {
    const onNewSearch = vi.fn();
    render(
      <Sidebar
        question={null}
        nodes={[]}
        showDiscarded={false}
        onToggleDiscarded={vi.fn()}
        onNewSearch={onNewSearch}
      />,
    );

    const button = screen.getByRole<HTMLButtonElement>('button', { name: /Nueva búsqueda/ });
    expect(button.disabled).toBe(false);

    fireEvent.click(button);

    expect(onNewSearch).toHaveBeenCalledOnce();
  });
});
