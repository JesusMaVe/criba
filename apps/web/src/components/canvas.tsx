import { useEffect, useRef, useState } from 'react';
import { stateLabel } from './state';
import type { NodeRow } from '../lib/types';

const CARD_W = 250;
const PAD = 14;
const TITLE_Y = 48;
const LINE_H = 18;
const BOTTOM = 20;

type View = { x: number; y: number; k: number };

type CardStyle = {
  fill: string;
  stroke: string;
  strokeWidth: number;
  dash?: string;
  title: string;
  meta: string;
  opacity?: number;
};

const STYLE: Record<string, CardStyle> = {
  CANDIDATO: {
    fill: 'var(--surface)',
    stroke: 'var(--line-strong)',
    strokeWidth: 1,
    dash: '5 4',
    title: 'var(--muted)',
    meta: 'var(--muted)',
  },
  PRESELECCIONADO: {
    fill: 'var(--surface)',
    stroke: 'var(--accent)',
    strokeWidth: 2,
    title: 'var(--ink)',
    meta: 'var(--ink-soft)',
  },
  ACEPTADO: {
    fill: 'var(--surface)',
    stroke: 'var(--ink)',
    strokeWidth: 1.2,
    title: 'var(--ink)',
    meta: 'var(--ink-soft)',
  },
  DEFINITIVO: {
    fill: 'var(--accent)',
    stroke: 'var(--accent)',
    strokeWidth: 1,
    title: '#ffffff',
    meta: 'rgba(255,255,255,0.82)',
  },
  DESCARTADO: {
    fill: 'var(--surface)',
    stroke: 'var(--line-strong)',
    strokeWidth: 1,
    dash: '4 4',
    title: 'var(--muted)',
    meta: 'var(--muted)',
    opacity: 0.55,
  },
};

/** Corta el título en líneas de ancho fijo; SVG no parte el texto solo. */
function wrapTitle(title: string, maxChars = 30, maxLines = 4): string[] {
  const words = title.split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  let current = '';
  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (candidate.length > maxChars && current) {
      lines.push(current);
      current = word;
      if (lines.length === maxLines) break;
    } else {
      current = candidate;
    }
  }
  if (lines.length < maxLines && current) {
    lines.push(current);
  }
  if (lines.length === maxLines) {
    const last = lines[maxLines - 1]!;
    const joined = title.replace(/\s+/g, ' ');
    if (!joined.endsWith(last) && joined.length > last.length) {
      lines[maxLines - 1] = `${last.slice(0, Math.max(0, maxChars - 1))}…`;
    }
  }
  return lines.length ? lines : [''];
}

function cardHeight(lineCount: number): number {
  return TITLE_Y + lineCount * LINE_H + BOTTOM;
}

function StateGlyph({ state, color }: { state: string; color: string }) {
  const stroke = { fill: 'none', stroke: color, strokeWidth: 1.4 } as const;
  switch (state) {
    case 'CANDIDATO':
      return <circle cx={8} cy={8} r={5} {...stroke} />;
    case 'PRESELECCIONADO':
      return (
        <>
          <circle cx={8} cy={8} r={5} {...stroke} />
          <path d="M8 3a5 5 0 0 1 0 10z" fill={color} />
        </>
      );
    case 'ACEPTADO':
      return <circle cx={8} cy={8} r={5} fill={color} />;
    case 'DEFINITIVO':
      return (
        <>
          <circle cx={8} cy={8} r={6.3} fill={color} opacity={0.22} />
          <circle cx={8} cy={8} r={6.3} fill="none" stroke={color} strokeWidth={1.2} />
          <path
            d="M5.1 8.2 7.1 10.2 11 5.6"
            fill="none"
            stroke={color}
            strokeWidth={1.7}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </>
      );
    default:
      return (
        <>
          <path d="M4.5 4.5 11.5 11.5M11.5 4.5 4.5 11.5" {...stroke} strokeLinecap="round" />
        </>
      );
  }
}

export function Canvas({
  nodes,
  selectedId,
  onSelect,
}: {
  nodes: NodeRow[];
  selectedId: string | null;
  onSelect: (id: string | null) => void;
}) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const [view, setView] = useState<View | null>(null);
  const [panning, setPanning] = useState(false);
  const drag = useRef<{ x: number; y: number; vx: number; vy: number } | null>(null);

  const fit = () => {
    const svg = svgRef.current;
    if (!svg || nodes.length === 0) {
      setView({ x: 0, y: 0, k: 1 });
      return;
    }
    const box = svg.getBoundingClientRect();
    const xs = nodes.map((node) => node.x ?? 0);
    const ys = nodes.map((node) => node.y ?? 0);
    const minX = Math.min(...xs) - 40;
    const minY = Math.min(...ys) - 40;
    const maxX = Math.max(...xs) + CARD_W + 40;
    const maxY = Math.max(...ys) + 200;
    const k = Math.min(1, (box.width - 120) / (maxX - minX), (box.height - 140) / (maxY - minY));
    const scale = Number.isFinite(k) && k > 0.3 ? k : 1;
    setView({
      x: (box.width - (maxX - minX) * scale) / 2 - minX * scale,
      y: (box.height - (maxY - minY) * scale) / 2 - minY * scale,
      k: scale,
    });
  };

  useEffect(() => {
    if (view === null && nodes.length > 0) {
      fit();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodes.length, view]);

  const zoomBy = (factor: number) => {
    const svg = svgRef.current;
    if (!svg || !view) return;
    const box = svg.getBoundingClientRect();
    const cx = box.width / 2;
    const cy = box.height / 2;
    const k = Math.min(2, Math.max(0.35, view.k * factor));
    setView({
      k,
      x: cx - (cx - view.x) * (k / view.k),
      y: cy - (cy - view.y) * (k / view.k),
    });
  };

  const onPointerDown = (event: React.PointerEvent<SVGSVGElement>) => {
    if (!view) return;
    if ((event.target as Element).closest('.node-card')) return;
    (event.currentTarget as SVGSVGElement).setPointerCapture(event.pointerId);
    drag.current = { x: event.clientX, y: event.clientY, vx: view.x, vy: view.y };
    setPanning(true);
  };

  const onPointerMove = (event: React.PointerEvent<SVGSVGElement>) => {
    const start = drag.current;
    if (!start || !view) return;
    setView({
      ...view,
      x: start.vx + (event.clientX - start.x),
      y: start.vy + (event.clientY - start.y),
    });
  };

  const endPan = () => {
    drag.current = null;
    setPanning(false);
  };

  const onWheel = (event: React.WheelEvent<SVGSVGElement>) => {
    if (!view) return;
    event.preventDefault();
    const svg = svgRef.current;
    if (!svg) return;
    const box = svg.getBoundingClientRect();
    const px = event.clientX - box.left;
    const py = event.clientY - box.top;
    const k = Math.min(2, Math.max(0.35, view.k * (1 - event.deltaY * 0.0015)));
    setView({
      k,
      x: px - (px - view.x) * (k / view.k),
      y: py - (py - view.y) * (k / view.k),
    });
  };

  return (
    <>
      <svg
        ref={svgRef}
        className={`canvas${panning ? ' panning' : ''}`}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endPan}
        onPointerCancel={endPan}
        onWheel={onWheel}
        onClick={() => onSelect(null)}
        role="application"
        aria-label="Grafo de nodos"
      >
        <g transform={`translate(${view?.x ?? 0}, ${view?.y ?? 0}) scale(${view?.k ?? 1})`}>
          {nodes.map((node) => {
            const style = STYLE[node.state] ?? STYLE.CANDIDATO!;
            const lines = wrapTitle(node.title);
            const height = cardHeight(lines.length);
            const x = node.x ?? 0;
            const y = node.y ?? 0;
            const selected = node.id === selectedId;
            return (
              <g
                key={node.id}
                className={`node-card ${node.state.toLowerCase()}`}
                transform={`translate(${x}, ${y})`}
                opacity={style.opacity}
                onClick={(event) => {
                  event.stopPropagation();
                  onSelect(node.id);
                }}
              >
                {selected ? (
                  <rect
                    x={-5}
                    y={-5}
                    width={CARD_W + 10}
                    height={height + 10}
                    rx={14}
                    fill="none"
                    stroke="var(--ink)"
                    strokeWidth={2}
                  />
                ) : null}
                <rect
                  width={CARD_W}
                  height={height}
                  rx={10}
                  fill={style.fill}
                  stroke={style.stroke}
                  strokeWidth={style.strokeWidth}
                  strokeDasharray={style.dash}
                />
                <g transform={`translate(${PAD}, ${TITLE_Y - 32})`}>
                  <StateGlyph state={node.state} color={style.meta} />
                  <text x={22} y={11} className="node-state" fill={style.meta}>
                    {stateLabel(node.state)}
                  </text>
                </g>
                {lines.map((line, index) => (
                  <text
                    key={index}
                    x={PAD}
                    y={TITLE_Y + index * LINE_H}
                    className="node-title"
                    fill={style.title}
                  >
                    {line}
                  </text>
                ))}
                <text
                  x={PAD}
                  y={height - 12}
                  className="node-year"
                  fill={style.meta}
                  opacity={0.85}
                >
                  {node.year ?? 'Sin año'}
                </text>
              </g>
            );
          })}
        </g>
      </svg>

      <div className="stage-footer">
        Etapa 0 · los nodos se añaden a mano; las citas entre fuentes llegan después
      </div>
      <div className="zoom">
        <button type="button" aria-label="Alejar" onClick={() => zoomBy(1 / 1.2)}>
          −
        </button>
        <button type="button" aria-label="Acercar" onClick={() => zoomBy(1.2)}>
          +
        </button>
      </div>
    </>
  );
}
