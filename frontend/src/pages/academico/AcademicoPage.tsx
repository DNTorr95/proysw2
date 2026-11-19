import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../../api/cliente";
import type { Periodo } from "../../api/tipos";
import { useAuth } from "../../auth/AuthContext";
import { Encabezado } from "../../components/ui";
import { PestanaPlanificacion } from "./Planificacion";
import { PestanaMaterias, PestanaGrupos } from "./MateriasGrupos";
import { PestanaCargaDocente, PestanaMigraciones } from "./MigracionesCarga";

const PESTANAS = [
  { id: "planificacion", titulo: "Planificación", permiso: "GESTIONAR_PLANIFICACION" },
  { id: "grupos", titulo: "Grupos y cupos", permiso: "GESTIONAR_MATERIAS_GRUPOS" },
  { id: "materias", titulo: "Materias", permiso: "GESTIONAR_MATERIAS_GRUPOS" },
  { id: "carga", titulo: "Carga docente", permiso: "GESTIONAR_CARGA_DOCENTE" },
  { id: "migraciones", titulo: "Migraciones", permiso: "GESTIONAR_MIGRACIONES" },
] as const;

/** CU-13 a CU-16 Gestión académica. Quien solo consulta ve las mismas pestañas en modo lectura. */
export default function AcademicoPage() {
  const [pestana, setPestana] = useState<(typeof PESTANAS)[number]["id"]>("planificacion");
  const periodos = useQuery({ queryKey: ["periodos"], queryFn: () => api<Periodo[]>("/periodos") });
  const [idPeriodo, setIdPeriodo] = useState<number | null>(null);
  useEffect(() => {
    if (idPeriodo === null && periodos.data?.length) {
      setIdPeriodo((periodos.data.find((p) => p.estado === "ABIERTO") ?? periodos.data[0]).id);
    }
  }, [periodos.data, idPeriodo]);

  return (
    <>
      <Encabezado titulo="Gestión académica" descripcion="Planificación semestral, materias, grupos y cupos, carga docente y migraciones entre planes 148-1 y 148-2"
        acciones={pestana !== "materias" && pestana !== "migraciones" && (
          <select className="campo" value={idPeriodo ?? ""} onChange={(e) => setIdPeriodo(Number(e.target.value))} aria-label="Periodo">
            {periodos.data?.map((p) => <option key={p.id} value={p.id}>Periodo {p.nombre} · {p.estado.toLowerCase()}</option>)}
          </select>
        )} />
      <div className="mb-4 flex flex-wrap gap-2" role="tablist">
        {PESTANAS.map((p) => (
          <button key={p.id} role="tab" aria-selected={pestana === p.id} className={pestana === p.id ? "btn-primario" : "btn-secundario"} onClick={() => setPestana(p.id)}>{p.titulo}</button>
        ))}
      </div>
      {pestana === "planificacion" && <PestanaPlanificacion periodos={periodos.data ?? []} idPeriodo={idPeriodo} onSeleccionar={setIdPeriodo} />}
      {pestana === "grupos" && idPeriodo && <PestanaGrupos idPeriodo={idPeriodo} periodo={periodos.data?.find((p) => p.id === idPeriodo)} />}
      {pestana === "materias" && <PestanaMaterias />}
      {pestana === "carga" && idPeriodo && <PestanaCargaDocente idPeriodo={idPeriodo} />}
      {pestana === "migraciones" && <PestanaMigraciones />}
    </>
  );
}

/** ¿El usuario puede gestionar (no solo consultar) en esta pestaña? */
export function usePuede(permiso: string) {
  return useAuth().tienePermiso(permiso);
}
