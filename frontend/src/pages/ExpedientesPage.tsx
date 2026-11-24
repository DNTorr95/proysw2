import { useState } from "react";
import { Link } from "react-router-dom";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FolderLock, FolderOpen, Link2, Pencil, Plus } from "lucide-react";
import { api, subirArchivo, type Pagina } from "../api/cliente";
import type { Estudiante, Expediente, ExpedienteDetalle, Tramite } from "../api/tipos";
import { useAuth } from "../auth/AuthContext";
import { BuscadorEstudiante, ListaArchivos, SubirArchivo } from "../components/documentos";
import { Cargando, Encabezado, ErrorApiAviso, Insignia, Modal, Paginacion } from "../components/ui";
import { ESTADOS_TRAMITE, formatoFecha } from "../lib/estados";

/** CU-08 Gestionar expedientes administrativos. */
export default function ExpedientesPage() {
  const { tienePermiso } = useAuth();
  const gestiona = tienePermiso("GESTIONAR_EXPEDIENTES");
  const [texto, setTexto] = useState("");
  const [estado, setEstado] = useState("");
  const [pagina, setPagina] = useState(0);
  const [creando, setCreando] = useState(false);
  const [abierto, setAbierto] = useState<number | null>(null);
  const lista = useQuery({
    queryKey: ["expedientes", texto, estado, pagina],
    queryFn: () => api<Pagina<Expediente>>("/expedientes", { params: { texto, estado, pagina, tamano: 15 } }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <Encabezado titulo="Expedientes administrativos" descripcion="Agrupan los trámites y documentos de un estudiante o asunto"
        acciones={gestiona && <button className="btn-primario" onClick={() => setCreando(true)}><Plus className="h-4 w-4" /> Abrir expediente</button>} />
      <div className="tarjeta">
        <div className="grid gap-3 border-b border-slate-200 p-3 sm:grid-cols-3">
          <input className="campo sm:col-span-2" placeholder="Código, título, registro o estudiante" value={texto} onChange={(e) => { setTexto(e.target.value); setPagina(0); }} aria-label="Buscar" />
          <select className="campo" value={estado} onChange={(e) => { setEstado(e.target.value); setPagina(0); }} aria-label="Estado">
            <option value="">Abiertos y cerrados</option><option value="ABIERTO">Abiertos</option><option value="CERRADO">Cerrados</option>
          </select>
        </div>
        {lista.isLoading ? <Cargando /> : lista.error ? <div className="p-4"><ErrorApiAviso error={lista.error} /></div> : (
          <>
            <div className="overflow-x-auto">
              <table className="tabla">
                <thead><tr><th>Código</th><th>Título</th><th>Estudiante</th><th>Apertura</th><th>Estado</th></tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {lista.data?.contenido.map((x) => (
                    <tr key={x.id} className="cursor-pointer hover:bg-slate-50" onClick={() => setAbierto(x.id)}>
                      <td className="whitespace-nowrap font-medium text-marca-700">{x.codigo}</td>
                      <td>{x.titulo}</td>
                      <td>{x.estudiante ?? "—"}{x.registro && <div className="text-xs text-slate-500">{x.registro}</div>}</td>
                      <td>{formatoFecha(x.fechaApertura)}</td>
                      <td><Insignia color={x.estado === "ABIERTO" ? "verde" : "gris"}>{x.estado}</Insignia></td>
                    </tr>
                  ))}
                  {lista.data?.contenido.length === 0 && <tr><td colSpan={5} className="py-8 text-center text-slate-500">Sin expedientes.</td></tr>}
                </tbody>
              </table>
            </div>
            {lista.data && <Paginacion pagina={lista.data.pagina} totalPaginas={lista.data.totalPaginas} total={lista.data.totalElementos} onCambiar={setPagina} />}
          </>
        )}
      </div>
      {creando && <NuevoExpediente onCerrar={(id) => { setCreando(false); if (id) setAbierto(id); }} />}
      {abierto !== null && <DetalleExpediente id={abierto} onCerrar={() => setAbierto(null)} />}
    </>
  );
}

function NuevoExpediente({ onCerrar }: { onCerrar: (id?: number) => void }) {
  const qc = useQueryClient();
  const [estudiante, setEstudiante] = useState<Estudiante | null>(null);
  const [titulo, setTitulo] = useState("");
  const [descripcion, setDescripcion] = useState("");
  const m = useMutation({
    mutationFn: () => api<Expediente>("/expedientes", { metodo: "POST", cuerpo: { idEstudiante: estudiante?.id, titulo, descripcion } }),
    onSuccess: (x) => { qc.invalidateQueries({ queryKey: ["expedientes"] }); onCerrar(x.id); },
  });
  return (
    <Modal abierto titulo="Abrir expediente" onCerrar={() => onCerrar()} ancho="max-w-xl">
      <div className="space-y-4">
        <ErrorApiAviso error={m.error} />
        <div>
          <span className="etiqueta">Estudiante (opcional)</span>
          <BuscadorEstudiante seleccionado={estudiante} onSeleccionar={(e) => { setEstudiante(e); if (e && !titulo) setTitulo(`Expediente de ${e.nombreCompleto}`); }} />
        </div>
        <div>
          <label className="etiqueta" htmlFor="tit">Título</label>
          <input id="tit" className="campo" maxLength={200} value={titulo} onChange={(e) => setTitulo(e.target.value)} />
        </div>
        <div>
          <label className="etiqueta" htmlFor="des">Descripción</label>
          <textarea id="des" rows={3} className="campo" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
        </div>
        <div className="flex justify-end gap-2">
          <button className="btn-secundario" onClick={() => onCerrar()}>Cancelar</button>
          <button className="btn-primario" disabled={!titulo.trim() || m.isPending} onClick={() => m.mutate()}>Abrir</button>
        </div>
      </div>
    </Modal>
  );
}

function DetalleExpediente({ id, onCerrar }: { id: number; onCerrar: () => void }) {
  const qc = useQueryClient();
  const { tienePermiso } = useAuth();
  const det = useQuery({ queryKey: ["expediente", id], queryFn: () => api<ExpedienteDetalle>(`/expedientes/${id}`) });
  const [idTramite, setIdTramite] = useState("");
  const [motivo, setMotivo] = useState("");
  const [edicion, setEdicion] = useState<{ titulo: string; descripcion: string } | null>(null);
  const refrescar = () => { qc.invalidateQueries({ queryKey: ["expediente", id] }); qc.invalidateQueries({ queryKey: ["expedientes"] }); };
  const x = det.data?.expediente;

  const candidatos = useQuery({
    queryKey: ["tramites-estudiante", x?.idEstudiante],
    queryFn: () => api<Pagina<Tramite>>("/tramites", { params: { idEstudiante: x?.idEstudiante, tamano: 50 } }),
    enabled: !!x?.idEstudiante,
  });
  const vincular = useMutation({
    mutationFn: () => api(`/expedientes/${id}/tramites`, { metodo: "POST", cuerpo: { idTramite: Number(idTramite) } }),
    onSuccess: () => { setIdTramite(""); refrescar(); },
  });
  const editar = useMutation({
    mutationFn: (d: { titulo: string; descripcion: string }) =>
      api(`/expedientes/${id}`, { metodo: "PUT", cuerpo: { titulo: d.titulo.trim(), descripcion: d.descripcion.trim() || null } }),
    onSuccess: () => { setEdicion(null); refrescar(); },
  });
  const estado = useMutation({
    mutationFn: (nuevo: string) => api(`/expedientes/${id}/estado`, { metodo: "PATCH", cuerpo: { estado: nuevo, motivo } }),
    onSuccess: () => { setMotivo(""); refrescar(); },
  });

  const gestiona = tienePermiso("GESTIONAR_EXPEDIENTES");
  const abierto = x?.estado === "ABIERTO";
  const vinculados = new Set(det.data?.tramites.map((t) => t.id));
  return (
    <Modal abierto titulo={x ? `${x.codigo} · ${x.titulo}` : "Expediente"} onCerrar={onCerrar} ancho="max-w-4xl">
      {det.isLoading || !x || !det.data ? <Cargando /> : (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-slate-600">
            <span>{x.estudiante ? `${x.estudiante} · ${x.registro}` : "Sin estudiante asociado"} · Abierto el {formatoFecha(x.fechaApertura)}</span>
            <span className="flex items-center gap-2">
              {gestiona && abierto && !edicion && (
                <button className="btn-secundario px-2 py-1 text-xs" onClick={() => { editar.reset(); setEdicion({ titulo: x.titulo, descripcion: x.descripcion ?? "" }); }}>
                  <Pencil className="h-3.5 w-3.5" /> Editar datos
                </button>
              )}
              <Insignia color={abierto ? "verde" : "gris"}>{x.estado}</Insignia>
            </span>
          </div>
          {edicion ? (
            <form className="space-y-3 rounded-md border border-slate-200 p-3" onSubmit={(e) => { e.preventDefault(); editar.mutate(edicion); }}>
              <ErrorApiAviso error={editar.error} />
              <div><label className="etiqueta" htmlFor="etit">Título</label><input id="etit" className="campo" required maxLength={200} value={edicion.titulo} onChange={(e) => setEdicion({ ...edicion, titulo: e.target.value })} /></div>
              <div><label className="etiqueta" htmlFor="edes">Descripción</label><textarea id="edes" className="campo" rows={3} maxLength={4000} value={edicion.descripcion} onChange={(e) => setEdicion({ ...edicion, descripcion: e.target.value })} /></div>
              <div className="flex justify-end gap-2">
                <button type="button" className="btn-secundario" onClick={() => setEdicion(null)}>Cancelar</button>
                <button className="btn-primario" disabled={!edicion.titulo.trim() || editar.isPending}>Guardar</button>
              </div>
            </form>
          ) : x.descripcion && <p className="text-sm">{x.descripcion}</p>}

          <section>
            <h3 className="mb-2 font-semibold">Trámites</h3>
            {det.data.tramites.length === 0 ? <p className="text-sm text-slate-500">Sin trámites vinculados.</p> : (
              <ul className="divide-y divide-slate-100 rounded-md border border-slate-200">
                {det.data.tramites.map((t) => (
                  <li key={t.id} className="flex items-center justify-between px-3 py-2 text-sm">
                    <Link to={`/tramites/${t.id}`} className="font-medium text-marca-700 hover:underline">{t.numero}</Link>
                    <span className="flex-1 px-3">{t.tipo}</span>
                    <Insignia color={ESTADOS_TRAMITE[t.estadoActual]?.color}>{ESTADOS_TRAMITE[t.estadoActual]?.etiqueta}</Insignia>
                  </li>
                ))}
              </ul>
            )}
            {gestiona && abierto && x.idEstudiante && (
              <div className="mt-3 flex flex-wrap gap-2">
                <select className="campo max-w-sm" value={idTramite} onChange={(e) => setIdTramite(e.target.value)} aria-label="Trámite a vincular">
                  <option value="">Vincular trámite del estudiante…</option>
                  {candidatos.data?.contenido.filter((t) => !vinculados.has(t.id)).map((t) => <option key={t.id} value={t.id}>{t.numero} · {t.tipo}</option>)}
                </select>
                <button className="btn-secundario" disabled={!idTramite || vincular.isPending} onClick={() => vincular.mutate()}><Link2 className="h-4 w-4" /> Vincular</button>
              </div>
            )}
            <div className="mt-2"><ErrorApiAviso error={vincular.error} /></div>
          </section>

          <section>
            <div className="mb-2 flex items-center justify-between">
              <h3 className="font-semibold">Documentos</h3>
              {gestiona && abierto && <SubirArchivo compacto onSubir={async (f) => { await subirArchivo(`/expedientes/${id}/documentos`, f); refrescar(); }} />}
            </div>
            <ListaArchivos archivos={det.data.documentos} />
          </section>

          {gestiona && (
            <section className="flex flex-wrap items-center gap-2 border-t border-slate-200 pt-4">
              <input className="campo max-w-md" placeholder="Motivo (obligatorio)" value={motivo} onChange={(e) => setMotivo(e.target.value)} aria-label="Motivo" />
              {abierto
                ? <button className="btn-secundario" disabled={!motivo.trim()} onClick={() => estado.mutate("CERRADO")}><FolderLock className="h-4 w-4" /> Cerrar expediente</button>
                : <button className="btn-secundario" disabled={!motivo.trim()} onClick={() => estado.mutate("ABIERTO")}><FolderOpen className="h-4 w-4" /> Reabrir</button>}
              <ErrorApiAviso error={estado.error} />
            </section>
          )}
        </div>
      )}
    </Modal>
  );
}
