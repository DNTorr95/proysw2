import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarX, History, Plus } from "lucide-react";
import { api } from "../../api/cliente";
import type { Asignacion, Docente, Estudiante, Grupo, Migracion } from "../../api/tipos";
import { BuscadorEstudiante } from "../../components/documentos";
import { Cargando, ErrorApiAviso, Insignia, Modal } from "../../components/ui";
import { formatoFecha, hoyLocal } from "../../lib/estados";
import { usePuede } from "./AcademicoPage";

/** CU-15 Migraciones entre planes 148-1 y 148-2. */
export function PestanaMigraciones() {
  const qc = useQueryClient();
  const gestiona = usePuede("GESTIONAR_MIGRACIONES");
  const lista = useQuery({ queryKey: ["migraciones"], queryFn: () => api<Migracion[]>("/migraciones") });
  const [estudiante, setEstudiante] = useState<Estudiante | null>(null);
  const [fecha, setFecha] = useState(hoyLocal());
  const [observacion, setObservacion] = useState("");
  const destino = estudiante?.plan === "148-1" ? "148-2" : "148-1";
  const m = useMutation({
    mutationFn: () => api("/migraciones", { metodo: "POST", cuerpo: { idEstudiante: estudiante?.id, planDestino: destino, fecha, observacion } }),
    onSuccess: () => { setEstudiante(null); setObservacion(""); qc.invalidateQueries({ queryKey: ["migraciones"] }); },
  });
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      {gestiona && (
        <section className="tarjeta space-y-3 p-5">
          <h2 className="font-semibold">Registrar migración</h2>
          <BuscadorEstudiante seleccionado={estudiante} onSeleccionar={setEstudiante} />
          {estudiante && <p className="text-sm">Plan actual <strong>{estudiante.plan}</strong> → nuevo plan <strong>{destino}</strong></p>}
          <div><label className="etiqueta" htmlFor="fm">Fecha</label><input id="fm" type="date" className="campo" value={fecha} onChange={(e) => setFecha(e.target.value)} /></div>
          <div><label className="etiqueta" htmlFor="om">Convalidaciones y motivo</label><textarea id="om" rows={3} className="campo" value={observacion} onChange={(e) => setObservacion(e.target.value)} /></div>
          <ErrorApiAviso error={m.error} />
          <button className="btn-primario w-full" disabled={!estudiante || !observacion.trim() || m.isPending} onClick={() => m.mutate()}>Registrar migración</button>
        </section>
      )}
      <section className={`tarjeta ${gestiona ? "lg:col-span-2" : "lg:col-span-3"}`}>
        {lista.isLoading ? <Cargando /> : (
          <table className="tabla">
            <thead><tr><th>Fecha</th><th>Estudiante</th><th>Migración</th><th>Observación</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {lista.data?.map((x) => (
                <tr key={x.id}><td className="whitespace-nowrap">{formatoFecha(x.fecha)}</td><td>{x.estudiante}<div className="text-xs text-slate-500">{x.registro}</div></td>
                  <td className="whitespace-nowrap">{x.planOrigen} → {x.planDestino}</td><td className="text-xs">{x.observacion}</td></tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

/** CU-16 Carga docente y antecedentes de cobertura. */
export function PestanaCargaDocente({ idPeriodo }: { idPeriodo: number }) {
  const qc = useQueryClient();
  const gestiona = usePuede("GESTIONAR_CARGA_DOCENTE");
  const docentes = useQuery({ queryKey: ["docentes", idPeriodo], queryFn: () => api<Docente[]>("/docentes", { params: { idPeriodo } }) });
  const asignaciones = useQuery({ queryKey: ["asignaciones", idPeriodo], queryFn: () => api<Asignacion[]>("/asignaciones", { params: { idPeriodo } }) });
  const [nueva, setNueva] = useState(false);
  const [antecedentes, setAntecedentes] = useState<Docente | null>(null);
  const [finalizar, setFinalizar] = useState<Asignacion | null>(null);
  const refrescar = () => { ["docentes", "asignaciones", "grupos", "periodo-resumen"].forEach((k) => qc.invalidateQueries({ queryKey: [k] })); };

  return (
    <div className="space-y-6">
      <section className="tarjeta">
        <div className="border-b border-slate-200 px-4 py-2 font-semibold">Docentes y carga horaria del periodo</div>
        {docentes.isLoading ? <Cargando /> : (
          <table className="tabla">
            <thead><tr><th>Docente</th><th>Dedicación</th><th>Especialidad</th><th>Carga</th><th /></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {docentes.data?.map((d) => {
                const pct = Math.min(100, (d.horasAsignadas / d.limiteHoras) * 100);
                return (
                  <tr key={d.id}>
                    <td>{d.nombreCompleto}<div className="text-xs text-slate-500">{d.codigo} · cat. {d.categoria}</div></td>
                    <td className="text-xs">{d.dedicacion?.replace("_", " ").toLowerCase()}</td><td className="text-xs">{d.especialidad}</td>
                    <td className="min-w-[10rem]"><div className="text-xs">{d.horasAsignadas} / {d.limiteHoras} h</div>
                      <div className="h-2 overflow-hidden rounded bg-slate-100"><div className={pct >= 90 ? "h-full bg-amber-500" : "h-full bg-marca-600"} style={{ width: `${pct}%` }} /></div></td>
                    <td className="text-right"><button className="btn-secundario px-2 py-1 text-xs" onClick={() => setAntecedentes(d)}><History className="h-3.5 w-3.5" /> Antecedentes</button></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>

      <section className="tarjeta">
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2">
          <span className="font-semibold">Asignaciones del periodo</span>
          {gestiona && <button className="btn-primario px-3 py-1.5" onClick={() => setNueva(true)}><Plus className="h-4 w-4" /> Asignar</button>}
        </div>
        {asignaciones.isLoading ? <Cargando /> : <TablaAsignaciones lista={asignaciones.data ?? []} onFinalizar={gestiona ? setFinalizar : undefined} />}
      </section>

      {nueva && <NuevaAsignacion idPeriodo={idPeriodo} docentes={docentes.data ?? []} onCerrar={() => { setNueva(false); refrescar(); }} />}
      {antecedentes && <Antecedentes docente={antecedentes} onCerrar={() => setAntecedentes(null)} />}
      {finalizar && <FinalizarAsignacion asignacion={finalizar} onCerrar={() => { setFinalizar(null); refrescar(); }} />}
    </div>
  );
}

function TablaAsignaciones({ lista, onFinalizar }: { lista: Asignacion[]; onFinalizar?: (a: Asignacion) => void }) {
  return (
    <div className="overflow-x-auto">
      <table className="tabla">
        <thead><tr><th>Docente</th><th>Grupo</th><th>Tipo</th><th>Horas</th><th>Vigencia</th><th>Antecedente</th><th /></tr></thead>
        <tbody className="divide-y divide-slate-100">
          {lista.map((a) => (
            <tr key={a.id}>
              <td>{a.docente}</td><td>{a.grupo}<div className="text-xs text-slate-500">{a.materia} · {a.periodo}</div></td>
              <td><Insignia color={a.tipo === "TITULAR" ? "azul" : a.tipo === "COBERTURA" ? "ambar" : "gris"}>{a.tipo}</Insignia></td>
              <td>{a.horas}</td>
              <td className="whitespace-nowrap text-xs">{formatoFecha(a.fechaInicio)} – {a.fechaFin ? formatoFecha(a.fechaFin) : "…"} {!a.vigente && <Insignia>finalizada</Insignia>}</td>
              <td className="max-w-xs text-xs">{a.observacion}</td>
              <td className="text-right">{onFinalizar && a.vigente && <button className="btn-secundario px-2 py-1 text-xs" onClick={() => onFinalizar(a)}><CalendarX className="h-3.5 w-3.5" /> Finalizar</button>}</td>
            </tr>
          ))}
          {lista.length === 0 && <tr><td colSpan={7} className="py-6 text-center text-slate-500">Sin asignaciones.</td></tr>}
        </tbody>
      </table>
    </div>
  );
}

function NuevaAsignacion({ idPeriodo, docentes, onCerrar }: { idPeriodo: number; docentes: Docente[]; onCerrar: () => void }) {
  const grupos = useQuery({ queryKey: ["grupos", idPeriodo], queryFn: () => api<Grupo[]>("/grupos", { params: { idPeriodo } }) });
  const [f, setF] = useState({ idDocente: "", idGrupo: "", tipo: "TITULAR", horas: "80", fechaInicio: hoyLocal(), fechaFin: "", observacion: "" });
  const m = useMutation({
    mutationFn: () => api("/asignaciones", { metodo: "POST", cuerpo: { ...f, idDocente: Number(f.idDocente), idGrupo: Number(f.idGrupo), horas: Number(f.horas), fechaFin: f.fechaFin || null } }),
    onSuccess: onCerrar,
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value });
  const d = docentes.find((x) => String(x.id) === f.idDocente);
  return (
    <Modal abierto titulo="Asignar carga docente" onCerrar={onCerrar} ancho="max-w-xl">
      <div className="space-y-3">
        <ErrorApiAviso error={m.error} />
        <div className="grid grid-cols-2 gap-3">
          <div className="col-span-2"><label className="etiqueta" htmlFor="doc">Docente</label>
            <select id="doc" className="campo" value={f.idDocente} onChange={set("idDocente")}><option value="">Seleccione…</option>{docentes.map((x) => <option key={x.id} value={x.id}>{x.nombreCompleto} ({x.horasAsignadas}/{x.limiteHoras} h)</option>)}</select>
            {d && <p className="mt-1 text-xs text-slate-500">Disponible: {d.limiteHoras - d.horasAsignadas} h</p>}</div>
          <div className="col-span-2"><label className="etiqueta" htmlFor="gru">Grupo</label>
            <select id="gru" className="campo" value={f.idGrupo} onChange={set("idGrupo")}><option value="">Seleccione…</option>{grupos.data?.map((g) => <option key={g.id} value={g.id}>{g.sigla} {g.codigo} · {g.materia}{g.docentes.length === 0 ? " · sin docente" : ""}</option>)}</select></div>
          <div><label className="etiqueta" htmlFor="tip">Tipo</label><select id="tip" className="campo" value={f.tipo} onChange={set("tipo")}><option>TITULAR</option><option>INTERINO</option><option>COBERTURA</option></select></div>
          <div><label className="etiqueta" htmlFor="hrs">Horas</label><input id="hrs" type="number" min={1} className="campo" value={f.horas} onChange={set("horas")} /></div>
          <div><label className="etiqueta" htmlFor="fi">Desde</label><input id="fi" type="date" className="campo" value={f.fechaInicio} onChange={set("fechaInicio")} /></div>
          <div><label className="etiqueta" htmlFor="ff">Hasta (opcional)</label><input id="ff" type="date" className="campo" value={f.fechaFin} onChange={set("fechaFin")} /></div>
          <div className="col-span-2"><label className="etiqueta" htmlFor="obs">Antecedente / observación {f.tipo === "COBERTURA" && "(obligatorio)"}</label><textarea id="obs" rows={2} className="campo" value={f.observacion} onChange={set("observacion")} /></div>
        </div>
        <div className="flex justify-end gap-2"><button className="btn-secundario" onClick={onCerrar}>Cancelar</button><button className="btn-primario" disabled={!f.idDocente || !f.idGrupo || m.isPending} onClick={() => m.mutate()}>Asignar</button></div>
      </div>
    </Modal>
  );
}

function Antecedentes({ docente, onCerrar }: { docente: Docente; onCerrar: () => void }) {
  const lista = useQuery({ queryKey: ["antecedentes", docente.id], queryFn: () => api<Asignacion[]>(`/docentes/${docente.id}/antecedentes`) });
  return (
    <Modal abierto titulo={`Antecedentes de ${docente.nombreCompleto}`} onCerrar={onCerrar} ancho="max-w-4xl">
      {lista.isLoading ? <Cargando /> : <TablaAsignaciones lista={lista.data ?? []} />}
    </Modal>
  );
}

function FinalizarAsignacion({ asignacion, onCerrar }: { asignacion: Asignacion; onCerrar: () => void }) {
  const [fecha, setFecha] = useState(hoyLocal());
  const [observacion, setObservacion] = useState("");
  const m = useMutation({
    mutationFn: () => api(`/asignaciones/${asignacion.id}/finalizacion`, { metodo: "POST", cuerpo: { fecha, observacion } }),
    onSuccess: onCerrar,
  });
  return (
    <Modal abierto titulo={`Finalizar asignación de ${asignacion.docente}`} onCerrar={onCerrar}>
      <div className="space-y-3">
        <ErrorApiAviso error={m.error} />
        <div><label className="etiqueta" htmlFor="ffin">Fecha de fin</label><input id="ffin" type="date" className="campo" value={fecha} onChange={(e) => setFecha(e.target.value)} /></div>
        <div><label className="etiqueta" htmlFor="ofin">Motivo (queda como antecedente)</label><textarea id="ofin" rows={3} className="campo" value={observacion} onChange={(e) => setObservacion(e.target.value)} /></div>
        <div className="flex justify-end gap-2"><button className="btn-secundario" onClick={onCerrar}>Cancelar</button><button className="btn-primario" disabled={!observacion.trim() || m.isPending} onClick={() => m.mutate()}>Finalizar</button></div>
      </div>
    </Modal>
  );
}
