import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FilePlus2, LineChart, Pencil, Plus } from "lucide-react";
import { api } from "../../api/cliente";
import type { ActividadPoa, Evento, PoaResumen, SeguimientoActividad } from "../../api/tipos";
import { useAuth } from "../../auth/AuthContext";
import { Cargando, Encabezado, ErrorApiAviso, Insignia, Modal } from "../../components/ui";
import { formatoFecha, hoyLocal } from "../../lib/estados";

const ESTADO_ACT = { PENDIENTE: "gris", EN_CURSO: "azul", CONCLUIDA: "verde", RETRASADA: "rojo" } as const;

function Barra({ valor }: { valor: number }) {
  return (
    <div className="flex items-center gap-2">
      <div className="h-2 flex-1 overflow-hidden rounded bg-slate-100"><div className={valor >= 100 ? "h-full bg-green-600" : "h-full bg-marca-600"} style={{ width: `${Math.min(100, valor)}%` }} /></div>
      <span className="w-12 text-right text-xs">{Number(valor).toFixed(0)} %</span>
    </div>
  );
}

/** CU-19 Gestionar seguimiento del POA. */
export default function PoaPage() {
  const qc = useQueryClient();
  const gestiona = useAuth().tienePermiso("GESTIONAR_POA");
  const poas = useQuery({ queryKey: ["poas"], queryFn: () => api<PoaResumen[]>("/poa") });
  const [gestion, setGestion] = useState<number | null>(null);
  useEffect(() => { if (gestion === null && poas.data?.length) setGestion(poas.data[0].gestion); }, [poas.data, gestion]);
  const poa = useQuery({ queryKey: ["poa", gestion], queryFn: () => api<{ poa: PoaResumen; actividades: ActividadPoa[] }>(`/poa/${gestion}`), enabled: gestion !== null });
  const [seguir, setSeguir] = useState<ActividadPoa | null>(null);
  const [nueva, setNueva] = useState(false);
  const [editarActividad, setEditarActividad] = useState<ActividadPoa | null>(null);
  const [formPoa, setFormPoa] = useState<"nuevo" | "editar" | null>(null);
  const refrescar = () => { qc.invalidateQueries({ queryKey: ["poa"] }); qc.invalidateQueries({ queryKey: ["poas"] }); };
  const p = poa.data?.poa;

  return (
    <>
      <Encabezado titulo="Plan Operativo Anual" descripcion="Seguimiento del avance de las actividades del POA"
        acciones={<>
          {poas.data && poas.data.length > 0 && <select className="campo" value={gestion ?? ""} onChange={(e) => setGestion(Number(e.target.value))} aria-label="Gestión">{poas.data.map((x) => <option key={x.id} value={x.gestion}>POA {x.gestion}</option>)}</select>}
          {gestiona && p && <button className="btn-secundario" onClick={() => setFormPoa("editar")}><Pencil className="h-4 w-4" /> Editar POA</button>}
          {gestiona && <button className="btn-primario" onClick={() => setFormPoa("nuevo")}><FilePlus2 className="h-4 w-4" /> Nuevo POA</button>}
        </>} />
      {poas.data?.length === 0 ? <div className="tarjeta p-6 text-sm text-slate-600">Todavía no hay un POA registrado.</div> : poa.isLoading || !p ? <Cargando /> : (
        <>
          <div className="mb-6 grid gap-4 md:grid-cols-4">
            <div className="tarjeta p-4 md:col-span-2"><p className="text-xs uppercase text-slate-500">Objetivo · {p.estado.replace("_", " ").toLowerCase()}</p><p className="mt-1 text-sm">{p.objetivo}</p></div>
            <div className="tarjeta p-4"><p className="text-xs uppercase text-slate-500">Avance global</p><p className="mt-1 text-2xl font-semibold">{Number(p.avance).toFixed(1)} %</p><Barra valor={Number(p.avance)} /></div>
            <div className="tarjeta p-4 text-sm"><p className="text-xs uppercase text-slate-500">Actividades</p><p className="mt-1">{p.actividades} en total</p><p className="text-green-700">{p.concluidas} concluidas</p><p className="text-red-700">{p.retrasadas} retrasadas</p></div>
          </div>
          <div className="tarjeta">
            <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2">
              <span className="font-semibold">Actividades</span>
              {gestiona && p.estado !== "CERRADO" && <button className="btn-primario px-3 py-1.5" onClick={() => setNueva(true)}><Plus className="h-4 w-4" /> Nueva actividad</button>}
            </div>
            <table className="tabla">
              <thead><tr><th>Actividad</th><th>Meta</th><th>Responsable</th><th>Plazo</th><th className="w-48">Avance</th><th>Estado</th><th /></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {poa.data!.actividades.map((a) => (
                  <tr key={a.id}>
                    <td>{a.descripcion}{a.eventos > 0 && <div className="text-xs text-slate-500">{a.eventos} evento(s) vinculado(s)</div>}</td>
                    <td className="text-xs">{a.meta}</td><td className="text-xs">{a.responsable}</td>
                    <td className="whitespace-nowrap text-xs">{formatoFecha(a.fechaInicio)} – {formatoFecha(a.fechaFin)}</td>
                    <td><Barra valor={Number(a.avance)} /></td>
                    <td><Insignia color={ESTADO_ACT[a.estado as keyof typeof ESTADO_ACT]}>{a.estado.replace("_", " ").toLowerCase()}</Insignia></td>
                    <td className="text-right"><div className="flex justify-end gap-1">
                      {gestiona && p.estado !== "CERRADO" && <button className="btn-secundario px-2 py-1 text-xs" onClick={() => setEditarActividad(a)} aria-label={`Editar ${a.descripcion}`}><Pencil className="h-3.5 w-3.5" /> Editar</button>}
                      <button className="btn-secundario px-2 py-1 text-xs" onClick={() => setSeguir(a)}><LineChart className="h-3.5 w-3.5" /> Seguimiento</button>
                    </div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      {seguir && <Seguimiento actividad={seguir} gestiona={gestiona} onCerrar={() => { setSeguir(null); refrescar(); }} />}
      {(nueva || editarActividad) && p && <FormActividad idPoa={p.id} gestion={p.gestion} actividad={editarActividad ?? undefined} onCerrar={() => { setNueva(false); setEditarActividad(null); refrescar(); }} />}
      {formPoa && <FormPoa poa={formPoa === "editar" ? p : undefined} onCerrar={(g) => { setFormPoa(null); refrescar(); if (g) setGestion(g); }} />}
    </>
  );
}

function Seguimiento({ actividad, gestiona, onCerrar }: { actividad: ActividadPoa; gestiona: boolean; onCerrar: () => void }) {
  const qc = useQueryClient();
  const lista = useQuery({ queryKey: ["seguimientos", actividad.id], queryFn: () => api<SeguimientoActividad[]>(`/actividades-poa/${actividad.id}/seguimientos`) });
  const eventos = useQuery({ queryKey: ["eventos"], queryFn: () => api<Evento[]>("/eventos") });
  const propios = eventos.data?.filter((e) => e.idActividad === actividad.id) ?? [];
  const [f, setF] = useState({ fecha: hoyLocal(), avance: String(actividad.avance), observacion: "", evidencia: "", idEvento: "" });
  const m = useMutation({
    mutationFn: () => api(`/actividades-poa/${actividad.id}/seguimientos`, { metodo: "POST", cuerpo: { ...f, avance: Number(f.avance), idEvento: f.idEvento ? Number(f.idEvento) : null } }),
    onSuccess: () => { setF({ ...f, observacion: "", evidencia: "" }); qc.invalidateQueries({ queryKey: ["seguimientos"] }); qc.invalidateQueries({ queryKey: ["poa"] }); },
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Modal abierto titulo={actividad.descripcion} onCerrar={onCerrar} ancho="max-w-3xl">
      <div className="space-y-4">
        <p className="text-sm text-slate-600">Meta: {actividad.meta} · plazo {formatoFecha(actividad.fechaInicio)} – {formatoFecha(actividad.fechaFin)}</p>
        {gestiona && (
          <div className="grid gap-3 rounded-md border border-slate-200 p-3 sm:grid-cols-4">
            <div><label className="etiqueta" htmlFor="sf">Fecha</label><input id="sf" type="date" className="campo" value={f.fecha} onChange={set("fecha")} /></div>
            <div><label className="etiqueta" htmlFor="sa">Avance (%)</label><input id="sa" type="number" min={0} max={100} className="campo" value={f.avance} onChange={set("avance")} /></div>
            <div className="sm:col-span-2"><label className="etiqueta" htmlFor="se">Evento vinculado</label><select id="se" className="campo" value={f.idEvento} onChange={set("idEvento")}><option value="">Ninguno</option>{propios.map((e) => <option key={e.id} value={e.id}>{e.nombre} ({formatoFecha(e.fecha)})</option>)}</select></div>
            <div className="sm:col-span-2"><label className="etiqueta" htmlFor="so">Observación</label><input id="so" className="campo" value={f.observacion} onChange={set("observacion")} /></div>
            <div className="sm:col-span-2"><label className="etiqueta" htmlFor="sv">Evidencia</label><input id="sv" className="campo" value={f.evidencia} onChange={set("evidencia")} placeholder="Acta, lista de asistencia, informe…" /></div>
            <div className="sm:col-span-4"><ErrorApiAviso error={m.error} /></div>
            <div className="sm:col-span-4"><button className="btn-primario" disabled={!f.observacion.trim() || m.isPending} onClick={() => m.mutate()}>Registrar avance</button></div>
          </div>
        )}
        {lista.isLoading ? <Cargando /> : (
          <ol className="relative ml-2 space-y-4 border-l border-slate-200 pl-5">
            {lista.data?.map((s) => (
              <li key={s.id}><span className="absolute -left-1.5 mt-1 h-3 w-3 rounded-full border-2 border-white bg-marca-500" />
                <p className="text-sm"><strong>{Number(s.avance).toFixed(0)} %</strong> · {formatoFecha(s.fecha)}</p>
                <p className="text-sm text-slate-700">{s.observacion}</p>{s.evidencia && <p className="text-xs text-slate-500">Evidencia: {s.evidencia}</p>}</li>
            ))}
            {lista.data?.length === 0 && <li className="text-sm text-slate-500">Sin seguimientos.</li>}
          </ol>
        )}
      </div>
    </Modal>
  );
}

/** Crear (POST /poa/{id}/actividades) o editar (PUT /actividades-poa/{id}) una actividad del POA. */
function FormActividad({ idPoa, gestion, actividad, onCerrar }: { idPoa: number; gestion: number; actividad?: ActividadPoa; onCerrar: () => void }) {
  const [f, setF] = useState({
    descripcion: actividad?.descripcion ?? "", meta: actividad?.meta ?? "", responsable: actividad?.responsable ?? "",
    fechaInicio: actividad?.fechaInicio ?? `${gestion}-01-02`, fechaFin: actividad?.fechaFin ?? `${gestion}-12-15`,
  });
  const m = useMutation({
    mutationFn: () => actividad
      ? api(`/actividades-poa/${actividad.id}`, { metodo: "PUT", cuerpo: f })
      : api(`/poa/${idPoa}/actividades`, { metodo: "POST", cuerpo: f }),
    onSuccess: onCerrar,
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Modal abierto titulo={actividad ? "Editar actividad del POA" : `Nueva actividad del POA ${gestion}`} onCerrar={onCerrar}>
      <div className="space-y-3">
        <ErrorApiAviso error={m.error} />
        <div><label className="etiqueta" htmlFor="ad">Descripción</label><input id="ad" className="campo" value={f.descripcion} onChange={set("descripcion")} /></div>
        <div><label className="etiqueta" htmlFor="am">Meta</label><input id="am" className="campo" value={f.meta} onChange={set("meta")} /></div>
        <div><label className="etiqueta" htmlFor="ar">Responsable</label><input id="ar" className="campo" value={f.responsable} onChange={set("responsable")} /></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="etiqueta" htmlFor="ai">Inicio</label><input id="ai" type="date" className="campo" value={f.fechaInicio} onChange={set("fechaInicio")} /></div>
          <div><label className="etiqueta" htmlFor="af">Fin</label><input id="af" type="date" className="campo" value={f.fechaFin} onChange={set("fechaFin")} /></div>
        </div>
        <div className="flex justify-end gap-2"><button className="btn-secundario" onClick={onCerrar}>Cancelar</button><button className="btn-primario" disabled={!f.descripcion || !f.meta || m.isPending} onClick={() => m.mutate()}>{actividad ? "Guardar" : "Crear"}</button></div>
      </div>
    </Modal>
  );
}

const ESTADOS_POA = { FORMULADO: "Formulado", EN_EJECUCION: "En ejecución", CERRADO: "Cerrado" };

/** Registrar (POST /poa) o editar (PUT /poa/{id}) el POA de una gestión; la gestión no cambia al editar. */
function FormPoa({ poa, onCerrar }: { poa?: PoaResumen; onCerrar: (gestion?: number) => void }) {
  const [f, setF] = useState({ gestion: String(poa?.gestion ?? new Date().getFullYear() + 1), objetivo: poa?.objetivo ?? "", estado: poa?.estado ?? "FORMULADO" });
  const m = useMutation({
    mutationFn: () => {
      const cuerpo = { gestion: Number(f.gestion), objetivo: f.objetivo.trim(), estado: f.estado };
      return poa ? api<PoaResumen>(`/poa/${poa.id}`, { metodo: "PUT", cuerpo }) : api<PoaResumen>("/poa", { metodo: "POST", cuerpo });
    },
    onSuccess: (r) => onCerrar(r.gestion),
  });
  return (
    <Modal abierto titulo={poa ? `Editar POA ${poa.gestion}` : "Nuevo POA"} onCerrar={() => onCerrar()}>
      <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); m.mutate(); }}>
        <ErrorApiAviso error={m.error} />
        <div className="grid grid-cols-2 gap-3">
          <div><label className="etiqueta" htmlFor="pg">Gestión</label><input id="pg" type="number" min={2000} max={2100} required className="campo" disabled={!!poa} value={f.gestion} onChange={(e) => setF({ ...f, gestion: e.target.value })} /></div>
          {poa && <div><label className="etiqueta" htmlFor="pe">Estado</label><select id="pe" className="campo" value={f.estado} onChange={(e) => setF({ ...f, estado: e.target.value })}>{Object.entries(ESTADOS_POA).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>}
        </div>
        <div><label className="etiqueta" htmlFor="po">Objetivo</label><textarea id="po" required rows={4} maxLength={4000} className="campo" value={f.objetivo} onChange={(e) => setF({ ...f, objetivo: e.target.value })} /></div>
        {poa && f.estado === "CERRADO" && poa.estado !== "CERRADO" && <p className="text-xs text-amber-700">Al cerrar el POA ya no se podrán agregar ni editar actividades.</p>}
        <div className="flex justify-end gap-2"><button type="button" className="btn-secundario" onClick={() => onCerrar()}>Cancelar</button><button className="btn-primario" disabled={!f.objetivo.trim() || m.isPending}>{poa ? "Guardar" : "Crear"}</button></div>
      </form>
    </Modal>
  );
}
