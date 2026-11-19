import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Eraser } from "lucide-react";
import { api, type Pagina } from "../api/cliente";
import type { RegistroAuditoria } from "../api/tipos";
import { Cargando, Encabezado, ErrorApiAviso, Insignia, Paginacion, formatoFechaHora } from "../components/ui";

const VACIO = { accion: "", entidad: "", idUsuario: "", desde: "", hasta: "" };

/** CU-03 Consultar bitácora de auditoría (solo lectura). */
export default function AuditoriaPage() {
  const [filtros, setFiltros] = useState(VACIO);
  const [pagina, setPagina] = useState(0);
  const catalogos = useQuery({ queryKey: ["auditoria-catalogos"], queryFn: () => api<{ acciones: string[]; entidades: string[] }>("/auditoria/catalogos") });
  const registros = useQuery({
    queryKey: ["auditoria", filtros, pagina],
    queryFn: () => api<Pagina<RegistroAuditoria>>("/auditoria", { params: { ...filtros, pagina, tamano: 25 } }),
    placeholderData: keepPreviousData,
  });
  const fijar = (k: keyof typeof VACIO) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setFiltros({ ...filtros, [k]: e.target.value });
    setPagina(0);
  };

  return (
    <>
      <Encabezado titulo="Bitácora de auditoría" descripcion="Registro inalterable de las operaciones sensibles del sistema (solo lectura)" />
      <div className="tarjeta">
        <div className="grid gap-3 border-b border-slate-200 p-3 sm:grid-cols-2 lg:grid-cols-6">
          <select className="campo" value={filtros.accion} onChange={fijar("accion")} aria-label="Acción">
            <option value="">Todas las acciones</option>
            {catalogos.data?.acciones.map((a) => <option key={a}>{a}</option>)}
          </select>
          <select className="campo" value={filtros.entidad} onChange={fijar("entidad")} aria-label="Entidad">
            <option value="">Todas las entidades</option>
            {catalogos.data?.entidades.map((a) => <option key={a}>{a}</option>)}
          </select>
          <input className="campo" type="number" min={1} placeholder="ID de usuario" value={filtros.idUsuario} onChange={fijar("idUsuario")} aria-label="ID de usuario" />
          <input className="campo" type="date" value={filtros.desde} onChange={fijar("desde")} aria-label="Desde" />
          <input className="campo" type="date" value={filtros.hasta} onChange={fijar("hasta")} aria-label="Hasta" />
          <button className="btn-secundario" onClick={() => { setFiltros(VACIO); setPagina(0); }}><Eraser className="h-4 w-4" /> Limpiar</button>
        </div>

        {registros.isLoading ? <Cargando /> : registros.error ? <div className="p-4"><ErrorApiAviso error={registros.error} /></div> : (
          <>
            <div className="overflow-x-auto">
              <table className="tabla">
                <thead><tr><th>Fecha y hora</th><th>Usuario</th><th>Acción</th><th>Entidad</th><th>ID</th><th>IP</th><th>Detalle</th></tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {registros.data?.contenido.map((r) => (
                    <tr key={r.id} className="hover:bg-slate-50">
                      <td className="whitespace-nowrap">{formatoFechaHora(r.fechaHora)}</td>
                      <td>{r.idUsuario ?? <span className="text-slate-400">sistema</span>}</td>
                      <td><Insignia color={r.accion.includes("FALLIDO") || r.accion.includes("BLOQUE") || r.accion.includes("RECHAZ") ? "rojo" : "azul"}>{r.accion}</Insignia></td>
                      <td>{r.entidad}</td>
                      <td>{r.idEntidad ?? "—"}</td>
                      <td className="whitespace-nowrap text-xs">{r.direccionIp}</td>
                      <td className="max-w-md text-xs text-slate-600">{r.detalle}</td>
                    </tr>
                  ))}
                  {registros.data?.contenido.length === 0 && <tr><td colSpan={7} className="py-8 text-center text-slate-500">Sin registros para los filtros indicados.</td></tr>}
                </tbody>
              </table>
            </div>
            {registros.data && <Paginacion pagina={registros.data.pagina} totalPaginas={registros.data.totalPaginas} total={registros.data.totalElementos} onCambiar={setPagina} />}
          </>
        )}
      </div>
    </>
  );
}
