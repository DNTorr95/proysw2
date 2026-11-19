import { useState, type FormEvent } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Eraser, Pencil, Plus, ShieldCheck } from "lucide-react";
import { api, subirArchivo, type Pagina } from "../api/cliente";
import type { Correspondencia, CorrespondenciaDetalle, Destinatario, EstadoCorrespondencia } from "../api/tipos";
import { useAuth } from "../auth/AuthContext";
import { ListaArchivos, SubirArchivo } from "../components/documentos";
import { Cargando, Encabezado, ErrorApiAviso, Insignia, Modal, Paginacion, formatoFechaHora } from "../components/ui";
import { ESTADOS_CORRESPONDENCIA, formatoFecha, hoyLocal } from "../lib/estados";

const FILTROS = { texto: "", tipo: "", estado: "", idDestinatario: "", autorizado: "", desde: "", hasta: "" };

/** CU-05 Gestionar correspondencia institucional · CU-06 Seguimiento. */
export default function CorrespondenciaPage() {
  const { tienePermiso } = useAuth();
  const puedeGestionar = tienePermiso("GESTIONAR_CORRESPONDENCIA");
  const [f, setF] = useState(FILTROS);
  const [pagina, setPagina] = useState(0);
  const [creando, setCreando] = useState(false);
  const [detalle, setDetalle] = useState<number | null>(null);

  const destinatarios = useQuery({ queryKey: ["destinatarios"], queryFn: () => api<Destinatario[]>("/destinatarios") });
  const lista = useQuery({
    queryKey: ["correspondencia", f, pagina],
    queryFn: () => api<Pagina<Correspondencia>>("/correspondencia", { params: { ...f, pagina, tamano: 15 } }),
    placeholderData: keepPreviousData,
  });
  const fijar = (k: keyof typeof FILTROS) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setF({ ...f, [k]: e.target.value });
    setPagina(0);
  };

  return (
    <>
      <Encabezado titulo="Correspondencia institucional" descripcion="Oficios recibidos y emitidos por la Dirección de Carrera"
        acciones={puedeGestionar && <button className="btn-primario" onClick={() => setCreando(true)}><Plus className="h-4 w-4" /> Registrar oficio</button>} />

      <div className="tarjeta">
        <div className="grid gap-3 border-b border-slate-200 p-3 sm:grid-cols-2 lg:grid-cols-8">
          <input className="campo lg:col-span-2" placeholder="Código, asunto o dependencia" value={f.texto} onChange={fijar("texto")} aria-label="Buscar" />
          <select className="campo" value={f.tipo} onChange={fijar("tipo")} aria-label="Tipo">
            <option value="">Recibida y emitida</option><option value="RECIBIDA">Recibida</option><option value="EMITIDA">Emitida</option>
          </select>
          <select className="campo" value={f.estado} onChange={fijar("estado")} aria-label="Estado">
            <option value="">Todos los estados</option>
            {Object.entries(ESTADOS_CORRESPONDENCIA).map(([k, v]) => <option key={k} value={k}>{v.etiqueta}</option>)}
          </select>
          <select className="campo" value={f.idDestinatario} onChange={fijar("idDestinatario")} aria-label="Dependencia">
            <option value="">Todas las dependencias</option>
            {destinatarios.data?.map((d) => <option key={d.id} value={d.id}>{d.unidad}</option>)}
          </select>
          <input className="campo" type="date" value={f.desde} onChange={fijar("desde")} aria-label="Desde" />
          <input className="campo" type="date" value={f.hasta} onChange={fijar("hasta")} aria-label="Hasta" />
          <button className="btn-secundario" onClick={() => { setF(FILTROS); setPagina(0); }}><Eraser className="h-4 w-4" /> Limpiar</button>
        </div>

        {lista.isLoading ? <Cargando /> : lista.error ? <div className="p-4"><ErrorApiAviso error={lista.error} /></div> : (
          <>
            <div className="overflow-x-auto">
              <table className="tabla">
                <thead><tr><th>Código</th><th>Fecha</th><th>Tipo</th><th>Asunto</th><th>Dependencia</th><th>Estado</th><th>Autorizado</th></tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {lista.data?.contenido.map((c) => (
                    <tr key={c.id} className="cursor-pointer hover:bg-slate-50" onClick={() => setDetalle(c.id)}>
                      <td className="whitespace-nowrap font-medium text-marca-700">{c.codigo}</td>
                      <td className="whitespace-nowrap">{formatoFecha(c.fechaDocumento)}</td>
                      <td><Insignia color={c.tipo === "RECIBIDA" ? "azul" : "verde"}>{c.tipo === "RECIBIDA" ? "Recibida" : "Emitida"}</Insignia></td>
                      <td className="max-w-xs">{c.asunto}</td>
                      <td className="max-w-[12rem] text-xs">{c.unidad}</td>
                      <td><Insignia color={ESTADOS_CORRESPONDENCIA[c.estado]?.color}>{ESTADOS_CORRESPONDENCIA[c.estado]?.etiqueta}</Insignia></td>
                      <td>{c.autorizado ? <CheckCircle2 className="h-4 w-4 text-green-600" aria-label="Autorizado" /> : <span className="text-xs text-slate-400">No</span>}</td>
                    </tr>
                  ))}
                  {lista.data?.contenido.length === 0 && <tr><td colSpan={7} className="py-8 text-center text-slate-500">No hay correspondencia para los filtros indicados.</td></tr>}
                </tbody>
              </table>
            </div>
            {lista.data && <Paginacion pagina={lista.data.pagina} totalPaginas={lista.data.totalPaginas} total={lista.data.totalElementos} onCambiar={setPagina} />}
          </>
        )}
      </div>

      {creando && <FormularioOficio destinatarios={destinatarios.data ?? []} onCerrar={(id) => { setCreando(false); if (id) setDetalle(id); }} />}
      {detalle !== null && <DetalleOficio id={detalle} destinatarios={destinatarios.data ?? []} onCerrar={() => setDetalle(null)} />}
    </>
  );
}

function FormularioOficio({ destinatarios, oficio, onCerrar }: {
  destinatarios: Destinatario[];
  oficio?: Correspondencia;
  onCerrar: (idCreado?: number) => void;
}) {
  const qc = useQueryClient();
  const hoy = hoyLocal();
  const [d, setD] = useState({
    tipo: oficio?.tipo ?? "RECIBIDA", asunto: oficio?.asunto ?? "", descripcion: oficio?.descripcion ?? "",
    fechaDocumento: oficio?.fechaDocumento ?? hoy, idDestinatario: oficio ? String(oficio.idDestinatario) : "",
  });
  const guardar = useMutation({
    mutationFn: () => oficio
      ? api<Correspondencia>(`/correspondencia/${oficio.id}`, { metodo: "PUT", cuerpo: { ...d, idDestinatario: Number(d.idDestinatario) } })
      : api<Correspondencia>("/correspondencia", { metodo: "POST", cuerpo: { ...d, idDestinatario: Number(d.idDestinatario) } }),
    onSuccess: (c) => { qc.invalidateQueries({ queryKey: ["correspondencia"] }); onCerrar(oficio ? undefined : c.id); },
  });
  const set = (k: keyof typeof d) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setD({ ...d, [k]: e.target.value });
  const enviar = (e: FormEvent) => { e.preventDefault(); guardar.mutate(); };

  return (
    <Modal abierto titulo={oficio ? `Editar ${oficio.codigo}` : "Registrar oficio"} onCerrar={() => onCerrar()} ancho="max-w-2xl">
      <form onSubmit={enviar} className="space-y-4" noValidate>
        <ErrorApiAviso error={guardar.error} />
        {!oficio && <p className="text-xs text-slate-500">El código único (DCP-REC/EMI-gestión-número) lo asigna el sistema al guardar.</p>}
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="etiqueta" htmlFor="tipo">Tipo</label>
            <select id="tipo" className="campo" value={d.tipo} onChange={set("tipo")} disabled={!!oficio}>
              <option value="RECIBIDA">Recibida</option><option value="EMITIDA">Emitida</option>
            </select>
          </div>
          <div>
            <label className="etiqueta" htmlFor="fecha">Fecha del documento</label>
            <input id="fecha" type="date" max={hoy} className="campo" value={d.fechaDocumento} onChange={set("fechaDocumento")} />
          </div>
          <div className="sm:col-span-2">
            <label className="etiqueta" htmlFor="dest">{d.tipo === "RECIBIDA" ? "Remitente (dependencia de origen)" : "Destinatario"}</label>
            <select id="dest" className="campo" value={d.idDestinatario} onChange={set("idDestinatario")}>
              <option value="">Seleccione…</option>
              {destinatarios.filter((x) => x.activo).map((x) => <option key={x.id} value={x.id}>{x.unidad} — {x.nombre}{x.cargo ? ` (${x.cargo})` : ""}</option>)}
            </select>
          </div>
          <div className="sm:col-span-2">
            <label className="etiqueta" htmlFor="asunto">Asunto</label>
            <input id="asunto" className="campo" maxLength={300} value={d.asunto} onChange={set("asunto")} />
          </div>
          <div className="sm:col-span-2">
            <label className="etiqueta" htmlFor="desc">Descripción / referencia (CITE)</label>
            <textarea id="desc" rows={3} className="campo" maxLength={4000} value={d.descripcion} onChange={set("descripcion")} />
          </div>
        </div>
        <div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
          <button type="button" className="btn-secundario" onClick={() => onCerrar()}>Cancelar</button>
          <button className="btn-primario" disabled={guardar.isPending || !d.asunto || !d.idDestinatario}>Guardar</button>
        </div>
      </form>
    </Modal>
  );
}

function DetalleOficio({ id, destinatarios, onCerrar }: { id: number; destinatarios: Destinatario[]; onCerrar: () => void }) {
  const qc = useQueryClient();
  const { tienePermiso } = useAuth();
  const [editando, setEditando] = useState(false);
  const [seg, setSeg] = useState<{ estado: EstadoCorrespondencia | ""; observacion: string }>({ estado: "", observacion: "" });
  const det = useQuery({ queryKey: ["correspondencia", id], queryFn: () => api<CorrespondenciaDetalle>(`/correspondencia/${id}`) });
  const refrescar = () => { qc.invalidateQueries({ queryKey: ["correspondencia"] }); };

  const autorizar = useMutation({
    mutationFn: (valor: boolean) => api(`/correspondencia/${id}/autorizacion`, { metodo: "PATCH", cuerpo: { autorizado: valor } }),
    onSuccess: refrescar,
  });
  const seguimiento = useMutation({
    mutationFn: () => api(`/correspondencia/${id}/seguimiento`, { metodo: "POST", cuerpo: seg }),
    onSuccess: () => { setSeg({ estado: "", observacion: "" }); refrescar(); },
  });

  const c = det.data?.correspondencia;
  const archivada = c?.estado === "ARCHIVADA";
  return (
    <Modal abierto titulo={c ? `${c.codigo}` : "Correspondencia"} onCerrar={onCerrar} ancho="max-w-4xl">
      {det.isLoading || !c || !det.data ? <Cargando /> : (
        <div className="space-y-6">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-lg font-medium">{c.asunto}</p>
              <p className="text-sm text-slate-500">
                {c.tipo === "RECIBIDA" ? "Recibida de" : "Emitida a"} {c.unidad} ({c.destinatario}) · Documento del {formatoFecha(c.fechaDocumento)} · Registrado {formatoFechaHora(c.fechaRegistro)} por {c.registradoPor}
              </p>
              {c.descripcion && <p className="mt-2 whitespace-pre-line text-sm">{c.descripcion}</p>}
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Insignia color={ESTADOS_CORRESPONDENCIA[c.estado]?.color}>{ESTADOS_CORRESPONDENCIA[c.estado]?.etiqueta}</Insignia>
              {c.autorizado ? <Insignia color="verde">Autorizado para RPA</Insignia> : <Insignia>Sin autorizar</Insignia>}
              {tienePermiso("GESTIONAR_CORRESPONDENCIA") && !archivada && (
                <>
                  <button className="btn-secundario px-2 py-1 text-xs" onClick={() => autorizar.mutate(!c.autorizado)} disabled={autorizar.isPending}>
                    <ShieldCheck className="h-3.5 w-3.5" /> {c.autorizado ? "Quitar autorización" : "Autorizar"}
                  </button>
                  <button className="btn-secundario px-2 py-1 text-xs" onClick={() => setEditando(true)}><Pencil className="h-3.5 w-3.5" /> Editar</button>
                </>
              )}
            </div>
          </div>
          <ErrorApiAviso error={autorizar.error} />

          <section>
            <div className="mb-2 flex items-center justify-between">
              <h3 className="font-semibold">Adjuntos</h3>
              {tienePermiso("GESTIONAR_CORRESPONDENCIA") && !archivada && (
                <SubirArchivo compacto onSubir={async (f) => { await subirArchivo(`/correspondencia/${id}/adjuntos`, f); refrescar(); }} />
              )}
            </div>
            <ListaArchivos archivos={det.data.adjuntos} />
          </section>

          <section>
            <h3 className="mb-2 font-semibold">Seguimiento</h3>
            <ol className="relative ml-2 space-y-4 border-l border-slate-200 pl-5">
              {det.data.seguimiento.map((s) => (
                <li key={s.id}>
                  <span className="absolute -left-1.5 mt-1 h-3 w-3 rounded-full border-2 border-white bg-marca-500" />
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <Insignia color={ESTADOS_CORRESPONDENCIA[s.estado]?.color}>{ESTADOS_CORRESPONDENCIA[s.estado]?.etiqueta ?? s.estado}</Insignia>
                    <span className="text-xs text-slate-500">{formatoFechaHora(s.fecha)}</span>
                  </div>
                  {s.observacion && <p className="mt-1 text-sm text-slate-700">{s.observacion}</p>}
                </li>
              ))}
            </ol>
            {tienePermiso("SEGUIMIENTO_CORRESPONDENCIA") && !archivada && (
              <form className="mt-4 grid gap-3 rounded-md border border-slate-200 p-3 sm:grid-cols-4" onSubmit={(e) => { e.preventDefault(); seguimiento.mutate(); }}>
                <div className="sm:col-span-4"><ErrorApiAviso error={seguimiento.error} /></div>
                <select className="campo" value={seg.estado} onChange={(e) => setSeg({ ...seg, estado: e.target.value as EstadoCorrespondencia })} aria-label="Nuevo estado">
                  <option value="">Nuevo estado…</option>
                  {Object.entries(ESTADOS_CORRESPONDENCIA).map(([k, v]) => <option key={k} value={k}>{v.etiqueta}</option>)}
                </select>
                <input className="campo sm:col-span-2" placeholder="Observación (obligatoria)" maxLength={2000} value={seg.observacion}
                  onChange={(e) => setSeg({ ...seg, observacion: e.target.value })} aria-label="Observación" />
                <button className="btn-primario" disabled={!seg.estado || !seg.observacion.trim() || seguimiento.isPending}>Registrar</button>
              </form>
            )}
          </section>
        </div>
      )}
      {editando && c && <FormularioOficio oficio={c} destinatarios={destinatarios} onCerrar={() => { setEditando(false); refrescar(); }} />}
    </Modal>
  );
}
