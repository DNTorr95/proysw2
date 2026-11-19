import { useState, type ReactNode } from "react";

/*
 * Gráficos del dashboard (CU-26), en SVG propio y siguiendo la guía de visualización:
 * barras <= 24px con extremo redondeado de 4px y base recta, separación de 2px entre barras contiguas,
 * cuadrícula de 1px tenue, leyenda siempre que hay 2+ series, valores directos selectivos,
 * tooltip al pasar el cursor y vista de tabla para accesibilidad. Paleta categórica validada (azul, naranja).
 */

export const SERIES = ["#2a78d6", "#eb6834"];
const GRID = "#e5e7eb";

function niceMax(max: number) {
  if (max <= 0) return 4;
  const pot = 10 ** Math.floor(Math.log10(max));
  const n = max / pot;
  const paso = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
  return paso * pot;
}

/** Rectángulo con 4px redondeados en el extremo de datos y recto en la base. */
function barraVertical(x: number, y: number, w: number, h: number) {
  const r = Math.min(4, h, w / 2);
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`;
}


type Tip = { x: number; y: number; contenido: ReactNode } | null;

function Tooltip({ tip }: { tip: Tip }) {
  if (!tip) return null;
  return (
    <div className="pointer-events-none absolute z-10 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 shadow-md"
      style={{ left: tip.x, top: tip.y, transform: "translate(-50%, -110%)" }}>
      {tip.contenido}
    </div>
  );
}

export function TarjetaGrafico({ titulo, subtitulo, tabla, children }: {
  titulo: string; subtitulo?: string; tabla: { columnas: string[]; filas: (string | number)[][] }; children: ReactNode;
}) {
  const [verTabla, setVerTabla] = useState(false);
  return (
    <section className="tarjeta p-5">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-slate-900">{titulo}</h2>
          {subtitulo && <p className="text-xs text-slate-500">{subtitulo}</p>}
        </div>
        <button className="text-xs text-marca-700 underline" onClick={() => setVerTabla(!verTabla)}>{verTabla ? "Ver gráfico" : "Ver tabla"}</button>
      </div>
      {verTabla ? (
        <table className="tabla">
          <thead><tr>{tabla.columnas.map((c) => <th key={c}>{c}</th>)}</tr></thead>
          <tbody className="divide-y divide-slate-100">{tabla.filas.map((f, i) => <tr key={i}>{f.map((v, j) => <td key={j} className={typeof v === "number" ? "text-right" : ""}>{v}</td>)}</tr>)}</tbody>
        </table>
      ) : children}
    </section>
  );
}

/**
 * Barras horizontales de una sola serie (sin leyenda: el título nombra la serie). En HTML, no en SVG escalable,
 * para que el texto mantenga su tamaño en cualquier ancho de tarjeta.
 */
export function BarrasHorizontales({ datos, formato = (v) => String(v), etiquetaAncho = 150 }: {
  datos: { etiqueta: string; valor: number; detalle?: string }[];
  formato?: (v: number) => string;
  etiquetaAncho?: number;
}) {
  const [tip, setTip] = useState<Tip>(null);
  const max = niceMax(Math.max(...datos.map((d) => d.valor), 0));
  return (
    <div className="relative" onMouseLeave={() => setTip(null)}>
      <ul className="space-y-1.5">
        {datos.map((d) => (
          <li key={d.etiqueta} className="flex items-center gap-2 text-xs"
            onMouseMove={(e) => {
              const r = (e.currentTarget.parentElement!.parentElement as HTMLElement).getBoundingClientRect();
              setTip({ x: e.clientX - r.left, y: e.clientY - r.top, contenido: <><strong>{d.etiqueta}</strong>: {formato(d.valor)}{d.detalle && <div className="text-slate-500">{d.detalle}</div>}</> });
            }}>
            <span className="shrink-0 truncate text-right text-slate-600" style={{ width: etiquetaAncho }} title={d.etiqueta}>{d.etiqueta}</span>
            <span className="relative flex h-5 flex-1 items-center border-l border-slate-200">
              <span className="h-4 rounded-r" style={{ width: `${max ? (d.valor / max) * 88 : 0}%`, minWidth: d.valor > 0 ? 2 : 0, background: SERIES[0] }} />
              <span className="ml-1.5 font-medium text-slate-800">{formato(d.valor)}</span>
            </span>
          </li>
        ))}
      </ul>
      <Tooltip tip={tip} />
    </div>
  );
}

/** Columnas agrupadas de dos series, con leyenda y valores en el tooltip. */
export function ColumnasAgrupadas({ categorias, series }: {
  categorias: string[];
  series: { nombre: string; valores: number[] }[];
}) {
  const [tip, setTip] = useState<Tip>(null);
  const ancho = 560, alto = 220, izq = 34, abajo = 24, arriba = 8;
  const plotW = ancho - izq - 8, plotH = alto - abajo - arriba;
  const max = niceMax(Math.max(...series.flatMap((s) => s.valores), 0));
  const banda = plotW / categorias.length;
  const barra = Math.min(22, (banda * 0.6 - 2) / series.length);
  const ticks = [0, max / 2, max];
  return (
    <div className="relative">
      <div className="mb-2 flex gap-4 text-xs text-slate-600">
        {series.map((s, i) => <span key={s.nombre} className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm" style={{ background: SERIES[i] }} />{s.nombre}</span>)}
      </div>
      <svg viewBox={`0 0 ${ancho} ${alto}`} className="w-full" role="img" aria-label="Gráfico de columnas agrupadas">
        {ticks.map((t) => {
          const y = arriba + plotH - (t / max) * plotH;
          return (
            <g key={t}>
              <line x1={izq} x2={ancho - 8} y1={y} y2={y} stroke={GRID} strokeWidth={1} />
              <text x={izq - 6} y={y + 4} textAnchor="end" className="fill-slate-500 text-[10px]">{Math.round(t)}</text>
            </g>
          );
        })}
        {categorias.map((c, ci) => {
          const x0 = izq + ci * banda + (banda - (barra * series.length + 2 * (series.length - 1))) / 2;
          return (
            <g key={c}
              onMouseMove={(e) => {
                const r = (e.currentTarget.ownerSVGElement as SVGSVGElement).getBoundingClientRect();
                setTip({ x: e.clientX - r.left, y: e.clientY - r.top, contenido: <><strong>{c}</strong>{series.map((s, i) => <div key={s.nombre}><span className="mr-1 inline-block h-2 w-2 rounded-sm" style={{ background: SERIES[i] }} />{s.nombre}: {s.valores[ci]}</div>)}</> });
              }}
              onMouseLeave={() => setTip(null)}>
              <rect x={izq + ci * banda} y={arriba} width={banda} height={plotH} fill="transparent" />
              {series.map((s, si) => {
                const h = (s.valores[ci] / max) * plotH;
                return h > 0 ? <path key={s.nombre} d={barraVertical(x0 + si * (barra + 2), arriba + plotH - h, barra, h)} fill={SERIES[si]} /> : null;
              })}
              <text x={izq + ci * banda + banda / 2} y={alto - 6} textAnchor="middle" className="fill-slate-500 text-[10px]">{c}</text>
            </g>
          );
        })}
      </svg>
      <Tooltip tip={tip} />
    </div>
  );
}

/** Indicador principal (número grande) con contexto. */
export function Indicador({ titulo, valor, detalle, tono }: { titulo: string; valor: string; detalle?: ReactNode; tono?: "alerta" }) {
  return (
    <div className={`tarjeta p-4 ${tono === "alerta" ? "border-l-4 border-l-red-500" : ""}`}>
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{titulo}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{valor}</p>
      {detalle && <div className="mt-1 text-xs text-slate-500">{detalle}</div>}
    </div>
  );
}
