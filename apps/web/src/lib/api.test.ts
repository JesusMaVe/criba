import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, startSearch } from './api';

function stubFetch(status: number, body: unknown) {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify(body), { status }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function sentBody(fetchMock: ReturnType<typeof stubFetch>) {
  const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
  return JSON.parse(init.body as string) as { query: string | null };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('startSearch', () => {
  it('encarga la búsqueda del tablero con la consulta sin espacios sobrantes', async () => {
    const fetchMock = stubFetch(202, { searchId: 's1', query: 'revisiones' });

    const accepted = await startSearch('b1', '  revisiones ');

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/boards/b1/searches');
    expect(init.method).toBe('POST');
    expect(sentBody(fetchMock)).toEqual({ query: 'revisiones' });
    expect(accepted).toEqual({ searchId: 's1', query: 'revisiones' });
  });

  it('una consulta vacía viaja como null para que la API use la pregunta del tablero', async () => {
    const fetchMock = stubFetch(202, { searchId: 's1', query: '¿Pregunta?' });

    await startSearch('b1', '   ');

    expect(sentBody(fetchMock)).toEqual({ query: null });
  });

  it('propaga el error de la API con su mensaje', async () => {
    stubFetch(422, { error: 'MISSING_QUERY', message: 'Falta la consulta' });

    await expect(startSearch('b1', '')).rejects.toMatchObject({
      constructor: ApiError,
      status: 422,
      code: 'MISSING_QUERY',
      message: 'Falta la consulta',
    });
  });
});
