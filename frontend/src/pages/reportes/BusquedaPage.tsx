import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { Bot, FileText, FolderSearch, GraduationCap, Mail, Search, Send, Sparkles } from "lucide-react";
import { api } from "../../api/cliente";
import type { RespuestaAsistente, RespuestaBusqueda, ResultadoBusqueda } from "../../api/tipos";
import { useAuth } from "../../auth/AuthContext";
import { Aviso, Cargando, Encabezado, ErrorApiAviso } from "../../components/ui";
import { formatoFecha } from "../../lib/estados";

const TIPOS: Record<ResultadoBusqueda["tipo"], { etiqueta: string; icono: typeof Mail }> = {
  CORRESPONDENCIA: { etiqueta: "Correspondencia", icono: Mail },
  TRAMITE: { etiqueta: "Trámite", icono: FolderSearch },
  DOCUMENTO: { etiqueta: "Documento", icono: FileText },
  TRABAJO_GRADO: { etiqueta: "Trabajo de grado", icono: GraduationCap },
};

/** CU-23 Búsqueda avanzada · CU-24 Búsqueda semántica · CU-25 Asistente. */
export default function BusquedaPage() {
  const { tienePermiso } = useAuth();
  const puedeAvanzada = tienePermiso("BUSQUEDA_AVANZADA") || tienePermiso("CONSULTAR_FILTROS");
  const puedeSemantica = tienePermiso("BUSQUEDA_SEMANTICA");
  const [modo, setModo] = useState<"avanzada" | "semantica">(puedeAvanzada ? "avanzada" : "semantica");
  const [texto, setTexto] = useState("");
  const [tipos, setTipos] = useState<string[]>([]);
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");

  const buscar = useMutation({
    mutationFn: () => modo === "avanzada"
      ? api<RespuestaBusqueda>("/busqueda", { params: { texto, tipos: tipos.join(",") || undefined, desde, hasta } })
      : api<RespuestaBusqueda>("/busqueda/semantica", { params: { consulta: texto, tipos: tipos.join(",") || undefined } }),
  });
  const enviar = (e: FormEvent) => { e.preventDefault(); if (texto.trim().length >= 2) buscar.mutate(); };
  const alternarTipo = (t: string) => setTipos(tipos.includes(t) ? tipos.filter((x) => x !== t) : [...tipos, t]);

  return (
    <>
      <Encabezado titulo="Búsqueda inteligente" descripcion="Encuentre información en correspondencia, trámites, documentos (incluido el texto OCR) y trabajos de grado. Solo verá lo que su rol permite." />
      <div className="grid gap-6 xl:grid-cols-3">
        <div className="space-y-4 xl:col-span-2">
          <form onSubmit={enviar} className="tarjeta space-y-3 p-4">
            <div className="flex gap-2" role="tablist">
              {puedeAvanzada && <button type="button" role="tab" aria-selected={modo === "avanzada"} className={modo === "avanzada" ? "btn-primario" : "btn-secundario"} onClick={() => { setModo("avanzada"); buscar.reset(); }}><Search className="h-4 w-4" /> Avanzada</button>}
              {puedeSemantica && <button type="button" role="tab" aria-selected={modo === "semantica"} className={modo === "semantica" ? "btn-primario" : "btn-secundario"} onClick={() => { setModo("semantica"); buscar.reset(); }}><Sparkles className="h-4 w-4" /> Semántica</button>}
            </div>
            <p className="text-xs text-slate-500">{modo === "avanzada" ? "Coincidencia de palabras con filtros por tipo y fecha." : "Busca por significado: encuentra documentos relacionados aunque no usen las mismas palabras."}</p>
            <div className="flex gap-2">
              <input className="campo" value={texto} onChange={(e) => setTexto(e.target.value)} placeholder={modo === "avanzada" ? "Código, asunto, número de trámite, estudiante…" : "Ej.: estrés de los estudiantes en exámenes"} aria-label="Texto de búsqueda" />
              <button className="btn-primario" disabled={texto.trim().length < 2 || buscar.isPending}><Search className="h-4 w-4" /> Buscar</button>
            </div>
            <div className="flex flex-wrap items-center gap-3 text-sm">
              {Object.entries(TIPOS).map(([k, v]) => (
                <label key={k} className="inline-flex items-center gap-1.5"><input type="checkbox" className="h-4 w-4 accent-marca-700" checked={tipos.includes(k)} onChange={() => alternarTipo(k)} /> {v.etiqueta}</label>
              ))}
              {modo === "avanzada" && <>
                <input type="date" className="campo max-w-[10rem]" value={desde} onChange={(e) => setDesde(e.target.value)} aria-label="Desde" />
                <input type="date" className="campo max-w-[10rem]" value={hasta} onChange={(e) => setHasta(e.target.value)} aria-label="Hasta" />
              </>}
            </div>
          </form>

          <ErrorApiAviso error={buscar.error} />
          {buscar.isPending && <Cargando texto="Buscando…" />}
          {buscar.data && (
            <section className="tarjeta">
              <div className="border-b border-slate-200 px-4 py-2 text-sm text-slate-600">
                {buscar.data.resultados.length} resultado(s) · fuentes consultadas: {buscar.data.tiposConsultados.map((t) => TIPOS[t as ResultadoBusqueda["tipo"]]?.etiqueta).join(", ")}
                {modo === "semantica" && ` · motor ${buscar.data.motor}`}
              </div>
              <ul className="divide-y divide-slate-100">
                {buscar.data.resultados.map((r) => {
                  const T = TIPOS[r.tipo];
                  return (
                    <li key={`${r.tipo}-${r.id}`} className="flex gap-3 px-4 py-3">
                      <T.icono className="mt-0.5 h-5 w-5 shrink-0 text-marca-600" />
                      <div className="min-w-0 flex-1">
                        <Link to={r.ruta} className="font-medium text-marca-800 hover:underline">{r.titulo}</Link>
                        <p className="text-sm text-slate-600">{r.detalle}</p>
                        <p className="text-xs text-slate-500">{T.etiqueta}{r.fecha && ` · ${formatoFecha(r.fecha)}`}</p>
                      </div>
                      {r.relevancia !== null && (
                        <div className="w-24 shrink-0 text-right text-xs text-slate-500">relevancia
                          <div className="mt-1 h-1.5 overflow-hidden rounded bg-slate-100"><div className="h-full bg-marca-600" style={{ width: `${Math.min(100, r.relevancia * 100)}%` }} /></div>
                        </div>
                      )}
                    </li>
                  );
                })}
                {buscar.data.resultados.length === 0 && <li className="px-4 py-8 text-center text-slate-500">Sin resultados.</li>}
              </ul>
            </section>
          )}
        </div>
        {tienePermiso("USAR_ASISTENTE") && <Asistente />}
      </div>
    </>
  );
}

type Mensaje = { de: "usuario" | "asistente"; texto: string; enlaces?: { texto: string; ruta: string }[] };

function Asistente() {
  const [mensajes, setMensajes] = useState<Mensaje[]>([{ de: "asistente", texto: "Hola. Puedo responder consultas frecuentes sobre trámites, correspondencia, el robot RPA, el POA y las alertas. ¿Qué necesita?" }]);
  const [pregunta, setPregunta] = useState("");
  const [sugerencias, setSugerencias] = useState<string[]>(["¿Cuántos trámites están vencidos?", "¿Qué requisitos necesito para una reincorporación?", "¿Cómo va el avance del POA?"]);
  const m = useMutation({
    mutationFn: (p: string) => api<RespuestaAsistente>("/asistente", { metodo: "POST", cuerpo: { pregunta: p } }),
    onSuccess: (r) => { setMensajes((x) => [...x, { de: "asistente", texto: r.texto, enlaces: r.enlaces }]); setSugerencias(r.sugerencias); },
  });
  const preguntar = (p: string) => {
    if (p.trim().length < 2) return;
    setMensajes((x) => [...x, { de: "usuario", texto: p.trim() }]);
    setPregunta("");
    m.mutate(p.trim());
  };
  return (
    <section className="tarjeta flex h-[70vh] flex-col">
      <div className="flex items-center gap-2 border-b border-slate-200 px-4 py-3 font-semibold"><Bot className="h-5 w-5 text-marca-600" /> Asistente</div>
      <div className="flex-1 space-y-3 overflow-y-auto p-4" aria-live="polite">
        {mensajes.map((x, i) => (
          <div key={i} className={x.de === "usuario" ? "ml-8 rounded-lg bg-marca-700 px-3 py-2 text-sm text-white" : "mr-8 rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-800"}>
            {x.texto}
            {x.enlaces && x.enlaces.length > 0 && <div className="mt-2 flex flex-wrap gap-2">{x.enlaces.map((e) => <Link key={e.ruta} to={e.ruta} className="rounded bg-white px-2 py-0.5 text-xs text-marca-700 underline">{e.texto}</Link>)}</div>}
          </div>
        ))}
        {m.isPending && <div className="mr-8 text-sm text-slate-500">Consultando…</div>}
        {m.error && <Aviso>{(m.error as Error).message}</Aviso>}
      </div>
      <div className="border-t border-slate-200 p-3">
        <div className="mb-2 flex flex-wrap gap-1">{sugerencias.slice(0, 3).map((s) => <button key={s} className="rounded-full border border-slate-200 px-2 py-0.5 text-xs text-slate-600 hover:bg-slate-50" onClick={() => preguntar(s)}>{s}</button>)}</div>
        <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); preguntar(pregunta); }}>
          <input className="campo" value={pregunta} onChange={(e) => setPregunta(e.target.value)} placeholder="Escriba su pregunta…" maxLength={500} aria-label="Pregunta al asistente" />
          <button className="btn-primario px-3" disabled={m.isPending || pregunta.trim().length < 2} aria-label="Enviar"><Send className="h-4 w-4" /></button>
        </form>
      </div>
    </section>
  );
}
