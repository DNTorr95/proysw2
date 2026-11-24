import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { GraduationCap, Pencil, Plus, Search } from "lucide-react";
import { api, subirArchivo } from "../../api/cliente";
import type { Estudiante, Titulado, TrabajoGrado } from "../../api/tipos";
import { useAuth } from "../../auth/AuthContext";
import { BuscadorEstudiante, ListaArchivos, SubirArchivo } from "../../components/documentos";
import { Cargando, Encabezado, ErrorApiAviso, Insignia, Modal } from "../../components/ui";
import { formatoFecha } from "../../lib/estados";

const MODALIDADES: Record<string, string> = { TESIS: "Tesis", PROYECTO_GRADO: "Proyecto de grado", TRABAJO_DIRIGIDO: "Trabajo dirigido", EXCELENCIA: "Excelencia académica" };

/** CU-17 Titulados · CU-18 Investigaciones y trabajos de grado. */
export default function InstitucionalPage() {
  const [pestana, setPestana] = useState<"trabajos" | "titulados">("trabajos");
  return (
    <>
      <Encabezado titulo="Titulados y trabajos de grado" descripcion="Repositorio de investigaciones y registro de titulación" />
      <div className="mb-4 flex gap-2" role="tablist">
        <button role="tab" aria-selected={pestana === "trabajos"} className={pestana === "trabajos" ? "btn-primario" : "btn-secundario"} onClick={() => setPestana("trabajos")}>Trabajos de grado</button>
        <button role="tab" aria-selected={pestana === "titulados"} className={pestana === "titulados" ? "btn-primario" : "btn-secundario"} onClick={() => setPestana("titulados")}>Titulados</button>
      </div>
      {pestana === "trabajos" ? <Trabajos /> : <Titulados />}
    </>
  );
}

function Trabajos() {
  const qc = useQueryClient();
  const gestiona = useAuth().tienePermiso("GESTIONAR_TRABAJOS_GRADO");
  const [texto, setTexto] = useState("");
  const [editar, setEditar] = useState<TrabajoGrado | "nuevo" | null>(null);
  const lista = useQuery({ queryKey: ["trabajos", texto], queryFn: () => api<TrabajoGrado[]>("/trabajos-grado", { params: { texto } }) });
  return (
    <div className="tarjeta">
      <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 p-3">
        <div className="relative max-w-md flex-1"><Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input className="campo pl-9" placeholder="Título, palabras clave, tutor o autor" value={texto} onChange={(e) => setTexto(e.target.value)} aria-label="Buscar" /></div>
        <div className="flex-1" />
        {gestiona && <button className="btn-primario" onClick={() => setEditar("nuevo")}><Plus className="h-4 w-4" /> Registrar trabajo</button>}
      </div>
      {lista.isLoading ? <Cargando /> : (
        <ul className="divide-y divide-slate-100">
          {lista.data?.map((t) => (
            <li key={t.id} className="flex flex-wrap items-start gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="font-medium">{t.titulo}</p>
                <p className="text-sm text-slate-600">{t.estudiante} ({t.registro}) · gestión {t.gestion}{t.tutor && ` · tutor: ${t.tutor}`}</p>
                {t.palabrasClave && <div className="mt-1 flex flex-wrap gap-1">{t.palabrasClave.split(", ").map((k) => <Insignia key={k} color="azul">{k}</Insignia>)}</div>}
                {t.archivo && <div className="mt-2 max-w-lg"><ListaArchivos archivos={[t.archivo]} /></div>}
              </div>
              {gestiona && <button className="rounded p-1.5 hover:bg-slate-100" aria-label={`Editar ${t.titulo}`} onClick={() => setEditar(t)}><Pencil className="h-4 w-4" /></button>}
            </li>
          ))}
          {lista.data?.length === 0 && <li className="py-8 text-center text-slate-500">Sin resultados.</li>}
        </ul>
      )}
      {editar && <FormTrabajo trabajo={editar === "nuevo" ? undefined : editar} onCerrar={() => { setEditar(null); qc.invalidateQueries({ queryKey: ["trabajos"] }); }} />}
    </div>
  );
}

function FormTrabajo({ trabajo, onCerrar }: { trabajo?: TrabajoGrado; onCerrar: () => void }) {
  const [estudiante, setEstudiante] = useState<Estudiante | null>(trabajo ? { id: trabajo.idEstudiante, registro: trabajo.registro, nombreCompleto: trabajo.estudiante, ci: "", plan: "", estado: "" } : null);
  const [f, setF] = useState({ titulo: trabajo?.titulo ?? "", tutor: trabajo?.tutor ?? "", gestion: String(trabajo?.gestion ?? new Date().getFullYear()), palabrasClave: trabajo?.palabrasClave ?? "" });
  const [guardado, setGuardado] = useState<TrabajoGrado | undefined>(trabajo);
  const m = useMutation({
    mutationFn: () => api<TrabajoGrado>(guardado ? `/trabajos-grado/${guardado.id}` : "/trabajos-grado", { metodo: guardado ? "PUT" : "POST", cuerpo: { ...f, idEstudiante: estudiante?.id, gestion: Number(f.gestion) } }),
    onSuccess: setGuardado,
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Modal abierto titulo={guardado ? "Trabajo de grado" : "Registrar trabajo de grado"} onCerrar={onCerrar} ancho="max-w-2xl">
      <div className="space-y-3">
        <ErrorApiAviso error={m.error} />
        {guardado ? <p className="text-sm">Autor: <strong>{estudiante?.nombreCompleto}</strong></p> : <BuscadorEstudiante seleccionado={estudiante} onSeleccionar={setEstudiante} />}
        <div><label className="etiqueta" htmlFor="tit">Título</label><input id="tit" className="campo" maxLength={300} value={f.titulo} onChange={set("titulo")} /></div>
        <div className="grid grid-cols-3 gap-3">
          <div className="col-span-2"><label className="etiqueta" htmlFor="tut">Tutor</label><input id="tut" className="campo" value={f.tutor} onChange={set("tutor")} /></div>
          <div><label className="etiqueta" htmlFor="gest">Gestión</label><input id="gest" type="number" className="campo" value={f.gestion} onChange={set("gestion")} /></div>
        </div>
        <div><label className="etiqueta" htmlFor="pcl">Palabras clave (separadas por coma)</label><input id="pcl" className="campo" value={f.palabrasClave} onChange={set("palabrasClave")} /></div>
        <div className="flex justify-end gap-2"><button className="btn-primario" disabled={!estudiante || !f.titulo || m.isPending} onClick={() => m.mutate()}>Guardar</button></div>
        {guardado && (
          <div className="border-t border-slate-200 pt-3">
            <p className="etiqueta">Documento del trabajo (PDF o DOCX)</p>
            {guardado.archivo && <div className="mb-2"><ListaArchivos archivos={[guardado.archivo]} /></div>}
            <SubirArchivo etiqueta={guardado.archivo ? "Reemplazar documento" : "Adjuntar documento"} onSubir={async (file) => setGuardado(await subirArchivo<TrabajoGrado>(`/trabajos-grado/${guardado.id}/archivo`, file))} />
          </div>
        )}
      </div>
    </Modal>
  );
}

function Titulados() {
  const qc = useQueryClient();
  const gestiona = useAuth().tienePermiso("GESTIONAR_TITULADOS");
  const [nuevo, setNuevo] = useState(false);
  const [editando, setEditando] = useState<Titulado | null>(null);
  const lista = useQuery({ queryKey: ["titulados"], queryFn: () => api<Titulado[]>("/titulados") });
  return (
    <div className="tarjeta">
      <div className="flex items-center justify-between border-b border-slate-200 p-3">
        <p className="text-sm text-slate-600">{lista.data?.length ?? 0} titulado(s)</p>
        {gestiona && <button className="btn-primario" onClick={() => setNuevo(true)}><GraduationCap className="h-4 w-4" /> Registrar titulación</button>}
      </div>
      {lista.isLoading ? <Cargando /> : (
        <table className="tabla">
          <thead><tr><th>Estudiante</th><th>Modalidad</th><th>Trabajo</th><th>Defensa</th><th>Gestión</th><th>Diploma</th>{gestiona && <th><span className="sr-only">Acciones</span></th>}</tr></thead>
          <tbody className="divide-y divide-slate-100">
            {lista.data?.map((t) => (
              <tr key={t.id}><td>{t.estudiante}<div className="text-xs text-slate-500">{t.registro}</div></td><td>{MODALIDADES[t.modalidad] ?? t.modalidad}</td>
                <td className="max-w-xs text-xs">{t.tituloTrabajo ?? "—"}</td><td>{formatoFecha(t.fechaDefensa)}</td><td>{t.gestion}</td><td className="text-xs">{t.nroDiploma ?? "—"}</td>
                {gestiona && <td><button className="btn-secundario px-2 py-1 text-xs" onClick={() => setEditando(t)} aria-label={`Editar titulación de ${t.estudiante}`}><Pencil className="h-3.5 w-3.5" /> Editar</button></td>}</tr>
            ))}
          </tbody>
        </table>
      )}
      {(nuevo || editando) && <FormTitulado titulado={editando ?? undefined} onCerrar={() => { setNuevo(false); setEditando(null); qc.invalidateQueries({ queryKey: ["titulados"] }); }} />}
    </div>
  );
}

/** Registrar (POST) o corregir (PUT /titulados/{id}) una titulación; al corregir, el estudiante no cambia. */
function FormTitulado({ titulado, onCerrar }: { titulado?: Titulado; onCerrar: () => void }) {
  const [estudiante, setEstudiante] = useState<Estudiante | null>(null);
  const idEstudiante = titulado?.idEstudiante ?? estudiante?.id;
  const nombre = titulado?.estudiante ?? estudiante?.nombreCompleto;
  const trabajos = useQuery({ queryKey: ["trabajos-de", idEstudiante], queryFn: () => api<TrabajoGrado[]>("/trabajos-grado", { params: { texto: nombre } }), enabled: !!idEstudiante });
  const propios = trabajos.data?.filter((t) => t.idEstudiante === idEstudiante) ?? [];
  const [f, setF] = useState({
    idTrabajo: titulado?.idTrabajo ? String(titulado.idTrabajo) : "", modalidad: titulado?.modalidad ?? "TESIS",
    gestion: String(titulado?.gestion ?? new Date().getFullYear()), fechaDefensa: titulado?.fechaDefensa ?? "", nroDiploma: titulado?.nroDiploma ?? "",
  });
  const m = useMutation({
    mutationFn: () => {
      const cuerpo = { ...f, idEstudiante, idTrabajo: f.idTrabajo ? Number(f.idTrabajo) : null, gestion: Number(f.gestion) };
      return titulado ? api(`/titulados/${titulado.id}`, { metodo: "PUT", cuerpo }) : api("/titulados", { metodo: "POST", cuerpo });
    },
    onSuccess: onCerrar,
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Modal abierto titulo={titulado ? `Editar titulación de ${titulado.estudiante}` : "Registrar titulación"} onCerrar={onCerrar} ancho="max-w-xl">
      <div className="space-y-3">
        <ErrorApiAviso error={m.error} />
        {titulado
          ? <p className="text-sm"><strong>{titulado.estudiante}</strong> · {titulado.registro}</p>
          : <BuscadorEstudiante seleccionado={estudiante} onSeleccionar={setEstudiante} />}
        <div className="grid grid-cols-2 gap-3">
          <div><label className="etiqueta" htmlFor="mod">Modalidad</label><select id="mod" className="campo" value={f.modalidad} onChange={set("modalidad")}>{Object.entries(MODALIDADES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
          <div><label className="etiqueta" htmlFor="tra">Trabajo de grado</label><select id="tra" className="campo" value={f.idTrabajo} onChange={set("idTrabajo")}><option value="">Ninguno</option>{propios.map((t) => <option key={t.id} value={t.id}>{t.titulo}</option>)}</select></div>
          <div><label className="etiqueta" htmlFor="fd">Fecha de defensa</label><input id="fd" type="date" className="campo" value={f.fechaDefensa} onChange={set("fechaDefensa")} /></div>
          <div><label className="etiqueta" htmlFor="gt">Gestión</label><input id="gt" type="number" className="campo" value={f.gestion} onChange={set("gestion")} /></div>
          <div className="col-span-2"><label className="etiqueta" htmlFor="dip">N.º de diploma</label><input id="dip" className="campo" value={f.nroDiploma} onChange={set("nroDiploma")} /></div>
        </div>
        {!titulado && <p className="text-xs text-slate-500">Al registrar, el estudiante pasa al estado TITULADO.</p>}
        <div className="flex justify-end gap-2"><button className="btn-secundario" onClick={onCerrar}>Cancelar</button><button className="btn-primario" disabled={!idEstudiante || !f.fechaDefensa || m.isPending} onClick={() => m.mutate()}>{titulado ? "Guardar" : "Registrar"}</button></div>
      </div>
    </Modal>
  );
}
