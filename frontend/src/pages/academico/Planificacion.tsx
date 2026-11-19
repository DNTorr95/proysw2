import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarPlus, Copy, Lock, Unlock } from "lucide-react";
import { api } from "../../api/cliente";
import type { Periodo, ResumenPlanificacion } from "../../api/tipos";
import { Cargando, ErrorApiAviso, Insignia, Modal } from "../../components/ui";
import { formatoFecha } from "../../lib/estados";
import { usePuede } from "./AcademicoPage";

const COLOR = { PLANIFICADO: "azul", ABIERTO: "verde", CERRADO: "gris" } as const;

/** CU-13 Planificación académica semestral. */
export function PestanaPlanificacion({ periodos, idPeriodo, onSeleccionar }: {
  periodos: Periodo[]; idPeriodo: number | null; onSeleccionar: (id: number) => void;
}) {
  const qc = useQueryClient();
  const gestiona = usePuede("GESTIONAR_PLANIFICACION");
  const [creando, setCreando] = useState(false);
  const [copiando, setCopiando] = useState(false);
  const resumen = useQuery({
    queryKey: ["periodo-resumen", idPeriodo],
    queryFn: () => api<ResumenPlanificacion>(`/periodos/${idPeriodo}/resumen`),
    enabled: idPeriodo !== null,
  });
  const refrescar = () => { qc.invalidateQueries({ queryKey: ["periodos"] }); qc.invalidateQueries({ queryKey: ["periodo-resumen"] }); };
  const estado = useMutation({
    mutationFn: (e: string) => api(`/periodos/${idPeriodo}/estado`, { metodo: "PATCH", cuerpo: { estado: e } }),
    onSuccess: refrescar,
  });
  const r = resumen.data;
  const p = periodos.find((x) => x.id === idPeriodo);

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <section className="tarjeta lg:col-span-1">
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2">
          <h2 className="font-semibold">Periodos</h2>
          {gestiona && <button className="btn-secundario px-2 py-1 text-xs" onClick={() => setCreando(true)}><CalendarPlus className="h-3.5 w-3.5" /> Nuevo</button>}
        </div>
        <ul className="divide-y divide-slate-100">
          {periodos.map((x) => (
            <li key={x.id}>
              <button className={`flex w-full items-center justify-between px-4 py-2 text-left text-sm hover:bg-slate-50 ${x.id === idPeriodo ? "bg-marca-50" : ""}`} onClick={() => onSeleccionar(x.id)}>
                <span><strong>{x.nombre}</strong><span className="block text-xs text-slate-500">{formatoFecha(x.fechaInicio)} – {formatoFecha(x.fechaFin)} · {x.grupos} grupos</span></span>
                <Insignia color={COLOR[x.estado]}>{x.estado}</Insignia>
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section className="tarjeta p-5 lg:col-span-2">
        {!p || resumen.isLoading ? <Cargando /> : (
          <>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold">Periodo {p.nombre}</h2>
                <p className="text-sm text-slate-500">Del {formatoFecha(p.fechaInicio)} al {formatoFecha(p.fechaFin)} · cierre de inscripciones {formatoFecha(p.fechaCierreInscripcion)}</p>
              </div>
              {gestiona && (
                <div className="flex flex-wrap gap-2">
                  {p.estado === "PLANIFICADO" && p.grupos === 0 && <button className="btn-secundario" onClick={() => setCopiando(true)}><Copy className="h-4 w-4" /> Copiar oferta</button>}
                  {p.estado === "PLANIFICADO" && <button className="btn-primario" disabled={estado.isPending} onClick={() => estado.mutate("ABIERTO")}><Unlock className="h-4 w-4" /> Abrir periodo</button>}
                  {p.estado === "ABIERTO" && <button className="btn-peligro" disabled={estado.isPending} onClick={() => estado.mutate("CERRADO")}><Lock className="h-4 w-4" /> Cerrar periodo</button>}
                </div>
              )}
            </div>
            <div className="mt-3"><ErrorApiAviso error={estado.error} /></div>
            {r && (
              <div className="mt-4 grid gap-3 sm:grid-cols-3">
                <Dato titulo="Materias ofertadas" valor={r.materias} />
                <Dato titulo="Grupos" valor={r.grupos} />
                <Dato titulo="Cupo total" valor={r.cupoTotal} />
                <Dato titulo="Inscritos" valor={`${r.inscritos} (${r.ocupacion} %)`} />
                <Dato titulo="Grupos sin docente" valor={r.gruposSinDocente} alerta={r.gruposSinDocente > 0} />
                <Dato titulo="Grupos llenos" valor={r.gruposLlenos} />
              </div>
            )}
            {r && (
              <div className="mt-4">
                <p className="mb-1 text-xs text-slate-500">Ocupación de cupos</p>
                <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100"><div className="h-full bg-marca-600" style={{ width: `${Math.min(100, r.ocupacion)}%` }} /></div>
              </div>
            )}
          </>
        )}
      </section>

      {creando && <NuevoPeriodo onCerrar={(id) => { setCreando(false); refrescar(); if (id) onSeleccionar(id); }} />}
      {copiando && p && <CopiarOferta destino={p} periodos={periodos.filter((x) => x.id !== p.id && x.grupos > 0)} onCerrar={() => { setCopiando(false); refrescar(); }} />}
    </div>
  );
}

function Dato({ titulo, valor, alerta }: { titulo: string; valor: string | number; alerta?: boolean }) {
  return (
    <div className={`rounded-md border p-3 ${alerta ? "border-amber-300 bg-amber-50" : "border-slate-200"}`}>
      <p className="text-xs text-slate-500">{titulo}</p>
      <p className="text-lg font-semibold">{valor}</p>
    </div>
  );
}

function NuevoPeriodo({ onCerrar }: { onCerrar: (id?: number) => void }) {
  const anio = new Date().getFullYear() + 1;
  const [f, setF] = useState({ gestion: String(anio), semestre: "1", fechaInicio: `${anio}-02-15`, fechaFin: `${anio}-07-03`, fechaCierreInscripcion: `${anio}-03-06` });
  const m = useMutation({
    mutationFn: () => api<Periodo>("/periodos", { metodo: "POST", cuerpo: { ...f, gestion: Number(f.gestion), semestre: Number(f.semestre), fechaCierreInscripcion: f.fechaCierreInscripcion || null } }),
    onSuccess: (p) => onCerrar(p.id),
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Modal abierto titulo="Planificar nuevo periodo" onCerrar={() => onCerrar()}>
      <div className="space-y-4">
        <ErrorApiAviso error={m.error} />
        <div className="grid grid-cols-2 gap-3">
          <div><label className="etiqueta" htmlFor="ges">Gestión</label><input id="ges" type="number" className="campo" value={f.gestion} onChange={set("gestion")} /></div>
          <div><label className="etiqueta" htmlFor="sem">Semestre</label><select id="sem" className="campo" value={f.semestre} onChange={set("semestre")}><option value="1">1</option><option value="2">2</option></select></div>
          <div><label className="etiqueta" htmlFor="ini">Inicio</label><input id="ini" type="date" className="campo" value={f.fechaInicio} onChange={set("fechaInicio")} /></div>
          <div><label className="etiqueta" htmlFor="fin">Fin</label><input id="fin" type="date" className="campo" value={f.fechaFin} onChange={set("fechaFin")} /></div>
          <div className="col-span-2"><label className="etiqueta" htmlFor="cie">Cierre de inscripciones</label><input id="cie" type="date" className="campo" value={f.fechaCierreInscripcion} onChange={set("fechaCierreInscripcion")} /></div>
        </div>
        <div className="flex justify-end gap-2"><button className="btn-secundario" onClick={() => onCerrar()}>Cancelar</button><button className="btn-primario" disabled={m.isPending} onClick={() => m.mutate()}>Crear</button></div>
      </div>
    </Modal>
  );
}

function CopiarOferta({ destino, periodos, onCerrar }: { destino: Periodo; periodos: Periodo[]; onCerrar: () => void }) {
  const [origen, setOrigen] = useState(periodos[0]?.id ? String(periodos[0].id) : "");
  const m = useMutation({
    mutationFn: () => api(`/periodos/${destino.id}/copia-grupos`, { metodo: "POST", cuerpo: { idPeriodoOrigen: Number(origen) } }),
    onSuccess: onCerrar,
  });
  return (
    <Modal abierto titulo={`Copiar oferta de grupos a ${destino.nombre}`} onCerrar={onCerrar}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">Se copiarán los grupos (cupo, turno, horario y aula) del periodo elegido. Los docentes se asignan después en "Carga docente".</p>
        <ErrorApiAviso error={m.error} />
        <select className="campo" value={origen} onChange={(e) => setOrigen(e.target.value)} aria-label="Periodo de origen">
          {periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre} ({p.grupos} grupos)</option>)}
        </select>
        <div className="flex justify-end gap-2"><button className="btn-secundario" onClick={onCerrar}>Cancelar</button><button className="btn-primario" disabled={!origen || m.isPending} onClick={() => m.mutate()}>Copiar</button></div>
      </div>
    </Modal>
  );
}
