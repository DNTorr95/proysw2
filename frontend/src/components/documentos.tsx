import { useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, Eye, FileText, Loader2, Search, Upload } from "lucide-react";
import { abrirDocumento, api, type Pagina } from "../api/cliente";
import type { Archivo, Estudiante } from "../api/tipos";
import { ACEPTA_ARCHIVOS, MAX_BYTES, formatoTamano } from "../lib/estados";
import { Aviso, ErrorApiAviso, formatoFechaHora } from "./ui";

/** Lista de archivos con acciones de ver y descargar (CU-07). */
export function ListaArchivos({ archivos, vacio = "Sin documentos adjuntos." }: { archivos: Archivo[]; vacio?: string }) {
  const [error, setError] = useState<unknown>(null);
  if (archivos.length === 0) return <p className="text-sm text-slate-500">{vacio}</p>;
  const abrir = (a: Archivo, enLinea: boolean) => {
    setError(null);
    abrirDocumento(a.id, a.nombreArchivo, enLinea).catch(setError);
  };
  return (
    <div className="space-y-2">
      <ErrorApiAviso error={error} />
      <ul className="divide-y divide-slate-100 rounded-md border border-slate-200">
        {archivos.map((a) => (
          <li key={a.id} className="flex items-center gap-3 px-3 py-2 text-sm">
            <FileText className="h-4 w-4 shrink-0 text-slate-400" />
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{a.nombreArchivo}</p>
              <p className="truncate text-xs text-slate-500" title={`SHA-256 ${a.hashSha256}`}>
                {formatoTamano(a.tamanoBytes)} · {formatoFechaHora(a.fechaCarga)} · SHA-256 {a.hashSha256.substring(0, 12)}…
                {a.descripcion && ` · ${a.descripcion}`}
              </p>
            </div>
            {(a.tipoMime === "application/pdf" || a.tipoMime.startsWith("image/")) && (
              <button className="rounded p-1.5 text-slate-600 hover:bg-slate-100" title="Ver" aria-label={`Ver ${a.nombreArchivo}`} onClick={() => abrir(a, true)}>
                <Eye className="h-4 w-4" />
              </button>
            )}
            <button className="rounded p-1.5 text-slate-600 hover:bg-slate-100" title="Descargar" aria-label={`Descargar ${a.nombreArchivo}`} onClick={() => abrir(a, false)}>
              <Download className="h-4 w-4" />
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Selector de archivo con validación local de tipo y tamaño antes de enviarlo. */
export function SubirArchivo({ onSubir, etiqueta = "Adjuntar archivo", compacto = false }: {
  onSubir: (archivo: File) => Promise<unknown>;
  etiqueta?: string;
  compacto?: boolean;
}) {
  const ref = useRef<HTMLInputElement>(null);
  const [subiendo, setSubiendo] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  const elegir = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    e.target.value = "";
    if (!f) return;
    setError(null);
    setAviso(null);
    const ext = f.name.split(".").pop()?.toLowerCase() ?? "";
    if (!["pdf", "jpg", "jpeg", "png", "docx"].includes(ext)) return setAviso("Solo se admiten archivos PDF, JPG, PNG o DOCX.");
    if (f.size > MAX_BYTES) return setAviso("El archivo supera el tamaño máximo de 10 MB.");
    setSubiendo(true);
    try {
      await onSubir(f);
    } catch (err) {
      setError(err);
    } finally {
      setSubiendo(false);
    }
  };

  return (
    <div className="space-y-2">
      <input ref={ref} type="file" accept={ACEPTA_ARCHIVOS} className="hidden" onChange={elegir} />
      <button type="button" className={compacto ? "btn-secundario px-2 py-1 text-xs" : "btn-secundario"} disabled={subiendo} onClick={() => ref.current?.click()}>
        {subiendo ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />} {etiqueta}
      </button>
      {aviso && <Aviso>{aviso}</Aviso>}
      <ErrorApiAviso error={error} />
    </div>
  );
}

/** Buscador de estudiantes por registro, CI o nombre. */
export function BuscadorEstudiante({ seleccionado, onSeleccionar }: {
  seleccionado: Estudiante | null;
  onSeleccionar: (e: Estudiante | null) => void;
}) {
  const [texto, setTexto] = useState("");
  const resultados = useQuery({
    queryKey: ["estudiantes", texto],
    queryFn: () => api<Pagina<Estudiante>>("/estudiantes", { params: { texto, tamano: 8 } }),
    enabled: texto.trim().length >= 2 && !seleccionado,
  });

  if (seleccionado) {
    return (
      <div className="flex items-center justify-between rounded-md border border-marca-200 bg-marca-50 px-3 py-2 text-sm">
        <div>
          <p className="font-medium">{seleccionado.nombreCompleto}</p>
          <p className="text-xs text-slate-600">Registro {seleccionado.registro} · CI {seleccionado.ci} · Plan {seleccionado.plan} · {seleccionado.estado}</p>
        </div>
        <button type="button" className="text-xs text-marca-700 underline" onClick={() => onSeleccionar(null)}>Cambiar</button>
      </div>
    );
  }
  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
      <input className="campo pl-9" placeholder="Registro, CI o nombre (mín. 2 caracteres)" value={texto}
        onChange={(e) => setTexto(e.target.value)} aria-label="Buscar estudiante" />
      {texto.trim().length >= 2 && (
        <ul className="tarjeta absolute z-10 mt-1 max-h-64 w-full overflow-y-auto">
          {resultados.isLoading && <li className="px-3 py-2 text-sm text-slate-500">Buscando…</li>}
          {resultados.data?.contenido.map((e) => (
            <li key={e.id}>
              <button type="button" className="w-full px-3 py-2 text-left text-sm hover:bg-slate-50" onClick={() => { onSeleccionar(e); setTexto(""); }}>
                <span className="font-medium">{e.nombreCompleto}</span>
                <span className="ml-2 text-xs text-slate-500">{e.registro} · Plan {e.plan}</span>
              </button>
            </li>
          ))}
          {resultados.data?.contenido.length === 0 && <li className="px-3 py-2 text-sm text-slate-500">Sin resultados.</li>}
        </ul>
      )}
    </div>
  );
}
