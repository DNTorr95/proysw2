import { useEffect, type ReactNode } from "react";
import { AlertCircle, CheckCircle2, ChevronLeft, ChevronRight, Loader2, X } from "lucide-react";
import { ErrorApi } from "../api/cliente";

export function Encabezado({ titulo, descripcion, acciones }: { titulo: string; descripcion?: string; acciones?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{titulo}</h1>
        {descripcion && <p className="mt-1 text-sm text-slate-500">{descripcion}</p>}
      </div>
      {acciones && <div className="flex flex-wrap gap-2">{acciones}</div>}
    </div>
  );
}

export function Cargando({ texto = "Cargando…" }: { texto?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500">
      <Loader2 className="h-4 w-4 animate-spin" /> {texto}
    </div>
  );
}

export function Aviso({ tipo = "error", children }: { tipo?: "error" | "exito" | "info"; children: ReactNode }) {
  const estilos = {
    error: "border-red-200 bg-red-50 text-red-800",
    exito: "border-green-200 bg-green-50 text-green-800",
    info: "border-marca-200 bg-marca-50 text-marca-800",
  }[tipo];
  const Icono = tipo === "exito" ? CheckCircle2 : AlertCircle;
  return (
    <div role={tipo === "error" ? "alert" : "status"} className={`flex items-start gap-2 rounded-md border px-3 py-2 text-sm ${estilos}`}>
      <Icono className="mt-0.5 h-4 w-4 shrink-0" />
      <div>{children}</div>
    </div>
  );
}

/** Muestra el mensaje de un error de la API, con el detalle por campo si existe. */
export function ErrorApiAviso({ error }: { error: unknown }) {
  if (!error) return null;
  const e = error instanceof ErrorApi ? error : new ErrorApi(0, "Ocurrió un error inesperado");
  return (
    <Aviso>
      <p>{e.message}</p>
      {e.detalles && (
        <ul className="mt-1 list-inside list-disc">
          {Object.entries(e.detalles).map(([campo, msg]) => (
            <li key={campo}>{msg}</li>
          ))}
        </ul>
      )}
    </Aviso>
  );
}

export function Modal({ abierto, titulo, onCerrar, children, ancho = "max-w-lg" }: {
  abierto: boolean;
  titulo: string;
  onCerrar: () => void;
  children: ReactNode;
  ancho?: string;
}) {
  useEffect(() => {
    if (!abierto) return;
    const tecla = (e: KeyboardEvent) => e.key === "Escape" && onCerrar();
    window.addEventListener("keydown", tecla);
    return () => window.removeEventListener("keydown", tecla);
  }, [abierto, onCerrar]);
  if (!abierto) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" onMouseDown={onCerrar}>
      <div role="dialog" aria-modal="true" aria-label={titulo}
        className={`tarjeta max-h-[90vh] w-full overflow-y-auto ${ancho}`} onMouseDown={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
          <h2 className="text-base font-semibold">{titulo}</h2>
          <button onClick={onCerrar} className="rounded p-1 text-slate-500 hover:bg-slate-100" aria-label="Cerrar">
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="p-5">{children}</div>
      </div>
    </div>
  );
}

export function Paginacion({ pagina, totalPaginas, total, onCambiar }: {
  pagina: number;
  totalPaginas: number;
  total: number;
  onCambiar: (p: number) => void;
}) {
  return (
    <div className="flex items-center justify-between border-t border-slate-200 px-3 py-2 text-sm text-slate-600">
      <span>{total} registro(s)</span>
      <div className="flex items-center gap-2">
        <button className="btn-secundario px-2 py-1" disabled={pagina <= 0} onClick={() => onCambiar(pagina - 1)} aria-label="Página anterior">
          <ChevronLeft className="h-4 w-4" />
        </button>
        <span>
          Página {totalPaginas === 0 ? 0 : pagina + 1} de {totalPaginas}
        </span>
        <button className="btn-secundario px-2 py-1" disabled={pagina + 1 >= totalPaginas} onClick={() => onCambiar(pagina + 1)} aria-label="Página siguiente">
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

export function Insignia({ color = "gris", children }: { color?: "verde" | "rojo" | "ambar" | "gris" | "azul"; children: ReactNode }) {
  const c = {
    verde: "bg-green-100 text-green-800",
    rojo: "bg-red-100 text-red-800",
    ambar: "bg-amber-100 text-amber-800",
    gris: "bg-slate-100 text-slate-700",
    azul: "bg-marca-100 text-marca-800",
  }[color];
  return <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${c}`}>{children}</span>;
}

export function formatoFechaHora(iso: string | null | undefined) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-BO", { dateStyle: "short", timeStyle: "medium" });
}
