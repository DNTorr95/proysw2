import { AlertTriangle, CheckCircle2, Loader2, Server, Bot, XCircle } from "lucide-react";
import type { PasoRpa } from "../../api/tipos";
import { formatoDuracion } from "../../lib/estados";

const ICONO = {
  OK: <CheckCircle2 className="h-5 w-5 text-green-600" />,
  ADVERTENCIA: <AlertTriangle className="h-5 w-5 text-amber-500" />,
  ERROR: <XCircle className="h-5 w-5 text-red-600" />,
  EN_CURSO: <Loader2 className="h-5 w-5 animate-spin text-marca-600" />,
};

/** Registro de pasos (evidencias) de una ejecución RPA. */
export function PasosEjecucion({ pasos, enCurso }: { pasos: PasoRpa[]; enCurso?: boolean }) {
  if (pasos.length === 0) {
    return <p className="flex items-center gap-2 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> En cola…</p>;
  }
  return (
    <ol className="space-y-3">
      {pasos.map((p, i) => (
        <li key={`${p.paso}-${i}`} className="flex gap-3">
          <div className="mt-0.5">{ICONO[p.estado] ?? ICONO.OK}</div>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-medium">{p.descripcion}</span>
              <span className="inline-flex items-center gap-1 rounded bg-slate-100 px-1.5 text-[10px] uppercase text-slate-500" title={p.origen === "TRABAJADOR" ? "Trabajador RPA" : "Backend"}>
                {p.origen === "TRABAJADOR" ? <Bot className="h-3 w-3" /> : <Server className="h-3 w-3" />} {p.origen === "TRABAJADOR" ? "robot" : "backend"}
              </span>
              <span className="text-xs text-slate-400">{formatoDuracion(p.duracionMs)}</span>
            </div>
            {p.detalle && <p className="mt-0.5 break-words text-xs text-slate-600">{p.detalle}</p>}
          </div>
        </li>
      ))}
      {enCurso && pasos.every((p) => p.estado !== "EN_CURSO") && (
        <li className="flex items-center gap-3 text-sm text-slate-500"><Loader2 className="h-5 w-5 animate-spin" /> Registrando resultado…</li>
      )}
    </ol>
  );
}
