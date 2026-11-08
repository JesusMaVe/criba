/** Etiquetas e iconos de estado. El icono cambia de forma, no solo de color. */

export const STATE_LABEL: Record<string, string> = {
  CANDIDATO: 'Candidato',
  PRESELECCIONADO: 'Preseleccionado',
  ACEPTADO: 'Aceptado',
  DEFINITIVO: 'Definitivo',
  DESCARTADO: 'Descartado',
};

export const STATE_ORDER = [
  'CANDIDATO',
  'PRESELECCIONADO',
  'ACEPTADO',
  'DEFINITIVO',
] as const;

export function stateLabel(state: string): string {
  return STATE_LABEL[state] ?? state;
}

export function StateIcon({ state, size = 14 }: { state: string; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 14 14', 'aria-hidden': true } as const;
  switch (state) {
    case 'CANDIDATO':
      return (
        <svg {...common} fill="none" stroke="currentColor" strokeWidth={1.4}>
          <circle cx="7" cy="7" r="4.6" />
        </svg>
      );
    case 'PRESELECCIONADO':
      return (
        <svg {...common} fill="none" stroke="currentColor" strokeWidth={1.4}>
          <circle cx="7" cy="7" r="4.6" />
          <path d="M7 2.4a4.6 4.6 0 0 1 0 9.2z" fill="currentColor" stroke="none" />
        </svg>
      );
    case 'ACEPTADO':
      return (
        <svg {...common} fill="currentColor">
          <circle cx="7" cy="7" r="4.8" />
        </svg>
      );
    case 'DEFINITIVO':
      return (
        <svg {...common}>
          <circle cx="7" cy="7" r="6.2" fill="currentColor" stroke="none" />
          <path
            d="M4.1 7.2 6.1 9.2 9.9 4.8"
            fill="none"
            stroke="#fff"
            strokeWidth={1.6}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      );
    case 'DESCARTADO':
      return (
        <svg {...common} fill="none" stroke="currentColor" strokeWidth={1.4} strokeLinecap="round">
          <path d="M3.6 3.6 10.4 10.4M10.4 3.6 3.6 10.4" />
        </svg>
      );
    default:
      return null;
  }
}

export function StateBadge({ state }: { state: string }) {
  return (
    <span className="state-row">
      <StateIcon state={state} />
      {stateLabel(state)}
    </span>
  );
}
