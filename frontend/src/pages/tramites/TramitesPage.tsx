import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { AlertTriangle, Eraser, Plus } from "lucide-react";
import { api, type Pagina } from "../../api/cliente";
import type { TipoTramite, Tramite } from "../../api/tipos";
import { useAuth } from "../../auth/AuthContext";
import { Cargando, Encabezado, ErrorApiAviso, Insignia, Paginacion } from "../../components/ui";
import { ESTADOS_TRAMITE, PRIORIDADES, formatoFecha } from "../../lib/estados";

const FILTROS = { texto: "", estado: "", idTipoTramite: "", prioridad: "", desde: "", hasta: "", vencidos: "" };

/** CU-12 Consultar seguimiento e historial de trámites (listado con filtros). */
export default function TramitesPage() {
  const { tienePermiso } = useAuth();
  const navigate = useNavigate();
  const [f, setF] = useState(FILTROS);
  const [pagina, setPagina] = useState(0);
  const tipos = useQuery({ queryKey: ["tipos-tramite"], queryFn: () => api<TipoTramite[]>("/tipos-tramite") });
  const lista = useQuery({
    queryKey: ["tramites", f, pagina],
    queryFn: () => api<Pagina<Tramite>>("/tramites", { params: { ...f, pagina, tamano: 15 } }),
    placeholderData: keepPreviousData,
  });
  const fijar = (k: keyof typeof FILTROS) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setF({ ...f, [k]: e.target.type === "checkbox" ? ((e.target as HTMLInputElement).checked ? "true" : "") : e.target.value });
    setPagina(0);
  };

  return (
    <>
      <Encabezado titulo="Trámites estudiantiles" descripcion="Seguimiento de solicitudes, plazos y estados"
        acciones={tienePermiso("REGISTRAR_TRAMITE") && <Link to="/tramites/nuevo" className="btn-primario"><Plus className="h-4 w-4" /> Registrar trámite</Link>} />

      <div className="tarjeta">
        <div className="grid items-center gap-3 border-b border-slate-200 p-3 sm:grid-cols-2 lg:grid-cols-8">
          <input className="campo lg:col-span-2" placeholder="Número, registro o estudiante" value={f.texto} onChange={fijar("texto")} aria-label="Buscar" />
          <select className="campo" value={f.estado} onChange={fijar("estado")} aria-label="Estado">
            <option value="">Todos los estados</option>
            {Object.entries(ESTADOS_TRAMITE).map(([k, v]) => <option key={k} value={k}>{v.etiqueta}</option>)}
          </select>
          <select className="campo" value={f.idTipoTramite} onChange={fijar("idTipoTramite")} aria-label="Tipo">
            <option value="">Todos los tipos</option>
            {tipos.data?.map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
          </select>
          <input className="campo" type="date" value={f.desde} onChange={fijar("desde")} aria-label="Recibido desde" />
          <input className="campo" type="date" value={f.hasta} onChange={fijar("hasta")} aria-label="Recibido hasta" />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="h-4 w-4 accent-marca-700" checked={f.vencidos === "true"} onChange={fijar("vencidos")} /> Solo vencidos
          </label>
          <button className="btn-secundario" onClick={() => { setF(FILTROS); setPagina(0); }}><Eraser className="h-4 w-4" /> Limpiar</button>
        </div>

        {lista.isLoading ? <Cargando /> : lista.error ? <div className="p-4"><ErrorApiAviso error={lista.error} /></div> : (
          <>
            <div className="overflow-x-auto">
              <table className="tabla">
                <thead><tr><th>Número</th><th>Tipo</th><th>Estudiante</th><th>Recepción</th><th>Plazo</th><th>Prioridad</th><th>Estado</th></tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {lista.data?.contenido.map((t) => (
                    <tr key={t.id} className="cursor-pointer hover:bg-slate-50" onClick={() => navigate(`/tramites/${t.id}`)}>
                      <td className="whitespace-nowrap font-medium text-marca-700">{t.numero}</td>
                      <td>{t.tipo}</td>
                      <td>{t.estudiante}<div className="text-xs text-slate-500">{t.registro}</div></td>
                      <td className="whitespace-nowrap">{formatoFecha(t.fechaRecepcion)}</td>
                      <td className="whitespace-nowrap">
                        {formatoFecha(t.fechaLimite)}
                        {t.vencido && <span className="ml-1 inline-flex items-center gap-1 text-xs font-medium text-red-600"><AlertTriangle className="h-3 w-3" /> vencido</span>}
                      </td>
                      <td><Insignia color={PRIORIDADES[t.prioridad]}>{t.prioridad}</Insignia></td>
                      <td><Insignia color={ESTADOS_TRAMITE[t.estadoActual]?.color}>{ESTADOS_TRAMITE[t.estadoActual]?.etiqueta ?? t.estadoActual}</Insignia></td>
                    </tr>
                  ))}
                  {lista.data?.contenido.length === 0 && <tr><td colSpan={7} className="py-8 text-center text-slate-500">No hay trámites para los filtros indicados.</td></tr>}
                </tbody>
              </table>
            </div>
            {lista.data && <Paginacion pagina={lista.data.pagina} totalPaginas={lista.data.totalPaginas} total={lista.data.totalElementos} onCambiar={setPagina} />}
          </>
        )}
      </div>
    </>
  );
}
