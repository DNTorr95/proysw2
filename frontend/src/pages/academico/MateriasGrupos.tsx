import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRightLeft, Pencil, Plus, UserMinus, UserPlus, Users } from "lucide-react";
import { api } from "../../api/cliente";
import type { Estudiante, Grupo, Inscripcion, Materia, Periodo } from "../../api/tipos";
import { BuscadorEstudiante } from "../../components/documentos";
import { Aviso, Cargando, ErrorApiAviso, Insignia, Modal } from "../../components/ui";
import { formatoFecha } from "../../lib/estados";
import { usePuede } from "./AcademicoPage";

const TURNOS: Record<string, string> = { MANANA: "Mañana", TARDE: "Tarde", NOCHE: "Noche" };

/** CU-14 Materias de los planes 148-1 y 148-2. */
export function PestanaMaterias() {
  const qc = useQueryClient();
  const gestiona = usePuede("GESTIONAR_MATERIAS_GRUPOS");
  const [plan, setPlan] = useState("");
  const [editar, setEditar] = useState<Materia | "nueva" | null>(null);
  const lista = useQuery({ queryKey: ["materias", plan], queryFn: () => api<Materia[]>("/materias", { params: { plan } }) });
  return (
    <div className="tarjeta">
      <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 p-3">
        <select className="campo max-w-xs" value={plan} onChange={(e) => setPlan(e.target.value)} aria-label="Plan"><option value="">Ambos planes</option><option>148-1</option><option>148-2</option></select>
        <div className="flex-1" />
        {gestiona && <button className="btn-primario" onClick={() => setEditar("nueva")}><Plus className="h-4 w-4" /> Nueva materia</button>}
      </div>
      {lista.isLoading ? <Cargando /> : (
        <table className="tabla">
          <thead><tr><th>Sigla</th><th>Materia</th><th>Plan</th><th>Nivel</th><th>Horas</th><th /></tr></thead>
          <tbody className="divide-y divide-slate-100">
            {lista.data?.map((m) => (
              <tr key={m.id}><td className="font-medium">{m.sigla}</td><td>{m.nombre}</td><td>{m.plan}</td><td>{m.nivel}</td><td>{m.horas}</td>
                <td className="text-right">{gestiona && <button className="rounded p-1.5 hover:bg-slate-100" aria-label={`Editar ${m.sigla}`} onClick={() => setEditar(m)}><Pencil className="h-4 w-4" /></button>}</td></tr>
            ))}
          </tbody>
        </table>
      )}
      {editar && <FormMateria materia={editar === "nueva" ? undefined : editar} onCerrar={() => { setEditar(null); qc.invalidateQueries({ queryKey: ["materias"] }); }} />}
    </div>
  );
}

function FormMateria({ materia, onCerrar }: { materia?: Materia; onCerrar: () => void }) {
  const [f, setF] = useState({ sigla: materia?.sigla ?? "PSI-", nombre: materia?.nombre ?? "", plan: materia?.plan ?? "148-2", nivel: String(materia?.nivel ?? 1), horas: String(materia?.horas ?? 80) });
  const m = useMutation({
    mutationFn: () => api(materia ? `/materias/${materia.id}` : "/materias", { metodo: materia ? "PUT" : "POST", cuerpo: { ...f, nivel: Number(f.nivel), horas: Number(f.horas) } }),
    onSuccess: onCerrar,
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Modal abierto titulo={materia ? `Editar ${materia.sigla}` : "Nueva materia"} onCerrar={onCerrar}>
      <div className="space-y-3">
        <ErrorApiAviso error={m.error} />
        <div className="grid grid-cols-2 gap-3">
          <div><label className="etiqueta" htmlFor="sig">Sigla</label><input id="sig" className="campo" value={f.sigla} disabled={!!materia} onChange={set("sigla")} /></div>
          <div><label className="etiqueta" htmlFor="pla">Plan</label><select id="pla" className="campo" value={f.plan} disabled={!!materia} onChange={set("plan")}><option>148-1</option><option>148-2</option></select></div>
          <div className="col-span-2"><label className="etiqueta" htmlFor="nom">Nombre</label><input id="nom" className="campo" value={f.nombre} onChange={set("nombre")} /></div>
          <div><label className="etiqueta" htmlFor="niv">Nivel</label><input id="niv" type="number" min={1} max={10} className="campo" value={f.nivel} onChange={set("nivel")} /></div>
          <div><label className="etiqueta" htmlFor="hor">Horas semestrales</label><input id="hor" type="number" min={16} max={400} className="campo" value={f.horas} onChange={set("horas")} /></div>
        </div>
        <div className="flex justify-end gap-2"><button className="btn-secundario" onClick={onCerrar}>Cancelar</button><button className="btn-primario" disabled={m.isPending} onClick={() => m.mutate()}>Guardar</button></div>
      </div>
    </Modal>
  );
}

/** CU-14 Grupos, cupos e inscripciones del periodo. */
export function PestanaGrupos({ idPeriodo, periodo }: { idPeriodo: number; periodo?: Periodo }) {
  const qc = useQueryClient();
  const gestiona = usePuede("GESTIONAR_MATERIAS_GRUPOS");
  const [dialogo, setDialogo] = useState<{ tipo: "nuevo" } | { tipo: "editar" | "inscritos"; grupo: Grupo } | null>(null);
  const lista = useQuery({ queryKey: ["grupos", idPeriodo], queryFn: () => api<Grupo[]>("/grupos", { params: { idPeriodo } }) });
  const refrescar = () => { qc.invalidateQueries({ queryKey: ["grupos"] }); qc.invalidateQueries({ queryKey: ["periodo-resumen"] }); };
  return (
    <div className="tarjeta">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-3">
        <p className="text-sm text-slate-600">{lista.data?.length ?? 0} grupos · periodo {periodo?.nombre} ({periodo?.estado.toLowerCase()})</p>
        {gestiona && periodo?.estado !== "CERRADO" && <button className="btn-primario" onClick={() => setDialogo({ tipo: "nuevo" })}><Plus className="h-4 w-4" /> Nuevo grupo</button>}
      </div>
      {lista.isLoading ? <Cargando /> : (
        <div className="overflow-x-auto">
          <table className="tabla">
            <thead><tr><th>Materia</th><th>Grupo</th><th>Plan</th><th>Turno / horario</th><th>Docentes</th><th>Cupo</th><th /></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {lista.data?.map((g) => (
                <tr key={g.id}>
                  <td><span className="font-medium">{g.sigla}</span> {g.materia}</td>
                  <td>{g.codigo}</td><td>{g.plan}</td>
                  <td className="text-xs">{TURNOS[g.turno]}{g.horario && ` · ${g.horario}`}{g.aula && ` · ${g.aula}`}</td>
                  <td className="text-xs">{g.docentes.length ? g.docentes.join(", ") : <span className="text-amber-700">Sin docente</span>}</td>
                  <td className="min-w-[8rem]">
                    <div className="flex items-center gap-2 text-xs"><span className={g.disponibles === 0 ? "font-semibold text-red-600" : ""}>{g.inscritos}/{g.cupo}</span>
                      <div className="h-2 flex-1 overflow-hidden rounded bg-slate-100"><div className={g.disponibles === 0 ? "h-full bg-red-500" : "h-full bg-marca-600"} style={{ width: `${(g.inscritos / g.cupo) * 100}%` }} /></div></div>
                  </td>
                  <td className="whitespace-nowrap text-right">
                    <button className="rounded p-1.5 hover:bg-slate-100" title="Inscritos" aria-label={`Inscritos de ${g.sigla} ${g.codigo}`} onClick={() => setDialogo({ tipo: "inscritos", grupo: g })}><Users className="h-4 w-4" /></button>
                    {gestiona && periodo?.estado !== "CERRADO" && <button className="rounded p-1.5 hover:bg-slate-100" title="Editar cupo" aria-label={`Editar ${g.sigla} ${g.codigo}`} onClick={() => setDialogo({ tipo: "editar", grupo: g })}><Pencil className="h-4 w-4" /></button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {dialogo?.tipo === "nuevo" && <FormGrupo idPeriodo={idPeriodo} onCerrar={() => { setDialogo(null); refrescar(); }} />}
      {dialogo?.tipo === "editar" && <FormGrupo idPeriodo={idPeriodo} grupo={dialogo.grupo} onCerrar={() => { setDialogo(null); refrescar(); }} />}
      {dialogo?.tipo === "inscritos" && <Inscritos grupo={dialogo.grupo} grupos={lista.data ?? []} gestiona={gestiona && periodo?.estado === "ABIERTO"} onCerrar={() => { setDialogo(null); refrescar(); }} />}
    </div>
  );
}

function FormGrupo({ idPeriodo, grupo, onCerrar }: { idPeriodo: number; grupo?: Grupo; onCerrar: () => void }) {
  const materias = useQuery({ queryKey: ["materias", ""], queryFn: () => api<Materia[]>("/materias"), enabled: !grupo });
  const [f, setF] = useState({ idMateria: "", codigo: "Z1", cupo: String(grupo?.cupo ?? 40), turno: grupo?.turno ?? "MANANA", horario: grupo?.horario ?? "", aula: grupo?.aula ?? "" });
  const m = useMutation({
    mutationFn: () => grupo
      ? api(`/grupos/${grupo.id}`, { metodo: "PUT", cuerpo: { cupo: Number(f.cupo), turno: f.turno, horario: f.horario, aula: f.aula } })
      : api("/grupos", { metodo: "POST", cuerpo: { ...f, idMateria: Number(f.idMateria), idPeriodo, cupo: Number(f.cupo) } }),
    onSuccess: onCerrar,
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Modal abierto titulo={grupo ? `Grupo ${grupo.sigla} ${grupo.codigo}` : "Nuevo grupo"} onCerrar={onCerrar}>
      <div className="space-y-3">
        <ErrorApiAviso error={m.error} />
        {grupo && <Aviso tipo="info">Inscritos actuales: {grupo.inscritos}. El cupo no puede ser menor.</Aviso>}
        <div className="grid grid-cols-2 gap-3">
          {!grupo && (
            <>
              <div className="col-span-2"><label className="etiqueta" htmlFor="mat">Materia</label>
                <select id="mat" className="campo" value={f.idMateria} onChange={set("idMateria")}><option value="">Seleccione…</option>{materias.data?.map((x) => <option key={x.id} value={x.id}>{x.sigla} {x.nombre} ({x.plan})</option>)}</select></div>
              <div><label className="etiqueta" htmlFor="cod">Código</label><input id="cod" className="campo" value={f.codigo} onChange={set("codigo")} /></div>
            </>
          )}
          <div><label className="etiqueta" htmlFor="cup">Cupo</label><input id="cup" type="number" min={1} className="campo" value={f.cupo} onChange={set("cupo")} /></div>
          <div><label className="etiqueta" htmlFor="tur">Turno</label><select id="tur" className="campo" value={f.turno} onChange={set("turno")}>{Object.entries(TURNOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
          <div><label className="etiqueta" htmlFor="aul">Aula</label><input id="aul" className="campo" value={f.aula} onChange={set("aula")} /></div>
          <div className="col-span-2"><label className="etiqueta" htmlFor="hora">Horario</label><input id="hora" className="campo" value={f.horario} onChange={set("horario")} placeholder="Lun-Mié 07:00-09:15" /></div>
        </div>
        <div className="flex justify-end gap-2"><button className="btn-secundario" onClick={onCerrar}>Cancelar</button><button className="btn-primario" disabled={m.isPending || (!grupo && !f.idMateria)} onClick={() => m.mutate()}>Guardar</button></div>
      </div>
    </Modal>
  );
}

function Inscritos({ grupo, grupos, gestiona, onCerrar }: { grupo: Grupo; grupos: Grupo[]; gestiona: boolean; onCerrar: () => void }) {
  const qc = useQueryClient();
  const lista = useQuery({ queryKey: ["inscripciones", grupo.id], queryFn: () => api<Inscripcion[]>(`/grupos/${grupo.id}/inscripciones`) });
  const [estudiante, setEstudiante] = useState<Estudiante | null>(null);
  const [casoEspecial, setCasoEspecial] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [cambio, setCambio] = useState<Inscripcion | null>(null);
  const [destino, setDestino] = useState("");
  const refrescar = () => { qc.invalidateQueries({ queryKey: ["inscripciones"] }); qc.invalidateQueries({ queryKey: ["grupos"] }); };
  const inscribir = useMutation({
    mutationFn: () => api(`/grupos/${grupo.id}/inscripciones`, { metodo: "POST", cuerpo: { idEstudiante: estudiante?.id, casoEspecial, motivo } }),
    onSuccess: () => { setEstudiante(null); setMotivo(""); refrescar(); },
  });
  const retirar = useMutation({
    mutationFn: (id: number) => api(`/inscripciones/${id}/retiro`, { metodo: "POST", cuerpo: { motivo: "Retiro registrado por Coordinación" } }),
    onSuccess: refrescar,
  });
  const cambiar = useMutation({
    mutationFn: () => api(`/inscripciones/${cambio!.id}/cambio-grupo`, { metodo: "POST", cuerpo: { idGrupoDestino: Number(destino), motivo: motivo || "Trámite de cambio de grupo" } }),
    onSuccess: () => { setCambio(null); setDestino(""); refrescar(); },
  });
  const alternativos = grupos.filter((g) => g.idMateria === grupo.idMateria && g.id !== grupo.id);

  return (
    <Modal abierto titulo={`Inscritos · ${grupo.sigla} ${grupo.materia} · grupo ${grupo.codigo}`} onCerrar={onCerrar} ancho="max-w-3xl">
      <div className="space-y-4">
        <p className="text-sm text-slate-600">Cupo {grupo.cupo} · inscritos {grupo.inscritos} · disponibles {grupo.disponibles}</p>
        {gestiona && (
          <div className="space-y-2 rounded-md border border-slate-200 p-3">
            <p className="text-sm font-medium">Inscribir estudiante</p>
            <BuscadorEstudiante seleccionado={estudiante} onSeleccionar={setEstudiante} />
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-marca-700" checked={casoEspecial} onChange={(e) => setCasoEspecial(e.target.checked)} /> Caso especial (adición fuera de fecha, respaldada por trámite)</label>
            {casoEspecial && <input className="campo" placeholder="Motivo o número de trámite" value={motivo} onChange={(e) => setMotivo(e.target.value)} />}
            <ErrorApiAviso error={inscribir.error} />
            <button className="btn-primario" disabled={!estudiante || inscribir.isPending} onClick={() => inscribir.mutate()}><UserPlus className="h-4 w-4" /> Inscribir</button>
          </div>
        )}
        <ErrorApiAviso error={retirar.error ?? cambiar.error} />
        {lista.isLoading ? <Cargando /> : (
          <table className="tabla">
            <thead><tr><th>Registro</th><th>Estudiante</th><th>Fecha</th><th>Estado</th><th /></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {lista.data?.map((i) => (
                <tr key={i.id}>
                  <td>{i.registro}</td><td>{i.estudiante}{i.casoEspecial && <Insignia color="ambar">caso especial</Insignia>}</td>
                  <td>{formatoFecha(i.fechaInscripcion)}</td><td><Insignia color={i.estado === "INSCRITO" ? "verde" : "gris"}>{i.estado}</Insignia></td>
                  <td className="whitespace-nowrap text-right">
                    {gestiona && i.estado === "INSCRITO" && (
                      <>
                        {alternativos.length > 0 && <button className="rounded p-1.5 hover:bg-slate-100" title="Cambio de grupo" aria-label={`Cambiar de grupo a ${i.registro}`} onClick={() => setCambio(i)}><ArrowRightLeft className="h-4 w-4" /></button>}
                        <button className="rounded p-1.5 text-red-600 hover:bg-red-50" title="Retirar" aria-label={`Retirar a ${i.registro}`} onClick={() => retirar.mutate(i.id)}><UserMinus className="h-4 w-4" /></button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
              {lista.data?.length === 0 && <tr><td colSpan={5} className="py-6 text-center text-slate-500">Sin inscritos.</td></tr>}
            </tbody>
          </table>
        )}
        {cambio && (
          <div className="flex flex-wrap items-center gap-2 rounded-md border border-marca-200 bg-marca-50 p-3 text-sm">
            <span>Cambiar a {cambio.estudiante} al grupo</span>
            <select className="campo max-w-[10rem]" value={destino} onChange={(e) => setDestino(e.target.value)} aria-label="Grupo de destino"><option value="">…</option>{alternativos.map((g) => <option key={g.id} value={g.id} disabled={g.disponibles === 0}>{g.codigo} ({g.disponibles} libres)</option>)}</select>
            <input className="campo max-w-xs" placeholder="Motivo / trámite" value={motivo} onChange={(e) => setMotivo(e.target.value)} />
            <button className="btn-primario" disabled={!destino || cambiar.isPending} onClick={() => cambiar.mutate()}>Confirmar</button>
            <button className="btn-secundario" onClick={() => setCambio(null)}>Cancelar</button>
          </div>
        )}
      </div>
    </Modal>
  );
}
