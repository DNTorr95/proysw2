import { useState } from "react";
import { Link } from "react-router-dom";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Eraser } from "lucide-react";
import { api, type Pagina } from "../api/cliente";
import type { Correspondencia, Destinatario, TipoTramite, Tramite } from "../api/tipos";
import { Cargando, Encabezado, ErrorApiAviso, Insignia, Paginacion } from "../components/ui";
import { ESTADOS_CORRESPONDENCIA, ESTADOS_TRAMITE, formatoFecha } from "../lib/estados";

type Entidad = "correspondencia" | "tramites";
const VACIO = { texto: "", tipo: "", estado: "", idDestinatario: "", autorizado: "", idTipoTramite: "", prioridad: "", vencidos: "", desde: "", hasta: "" };

/** CU-33 Consultar registros mediante filtros convencionales. */
export default function ConsultasPage() {
  const [entidad, setEntidad] = useState<Entidad>("correspondencia");
  const [f, setF] = useState(VACIO);
  const [pagina, setPagina] = useState(0);
  const destinatarios = useQuery({ queryKey: ["destinatarios"], queryFn: () => api<Destinatario[]>("/destinatarios"), enabled: entidad === "correspondencia" });
  const tipos = useQuery({ queryKey: ["tipos-tramite"], queryFn: () => api<TipoTramite[]>("/tipos-tramite"), enabled: entidad === "tramites" });
  const lista = useQuery({
    queryKey: ["consulta", entidad, f, pagina],
    queryFn: () => api<Pagina<Correspondencia | Tramite>>(`/consultas/${entidad}`, { params: { ...f, pagina, tamano: 20 } }),
    placeholderData: keepPreviousData,
  });
  const fijar = (k: keyof typeof VACIO) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => { setF({ ...f, [k]: e.target.value }); setPagina(0); };
  const cambiarEntidad = (e: Entidad) => { setEntidad(e); setF(VACIO); setPagina(0); };

  return (
    <>
      <Encabezado titulo="Consultas con filtros" descripcion="Consulta de solo lectura con filtros combinables" />
      <div className="mb-4 flex gap-2" role="tablist">
        <button role="tab" aria-selected={entidad === "correspondencia"} className={entidad === "correspondencia" ? "btn-primario" : "btn-secundario"} onClick={() => cambiarEntidad("correspondencia")}>Correspondencia</button>
        <button role="tab" aria-selected={entidad === "tramites"} className={entidad === "tramites" ? "btn-primario" : "btn-secundario"} onClick={() => cambiarEntidad("tramites")}>Trámites</button>
      </div>
      <div className="tarjeta">
        <div className="grid items-center gap-3 border-b border-slate-200 p-3 sm:grid-cols-2 lg:grid-cols-7">
          <input className="campo lg:col-span-2" placeholder="Texto libre" value={f.texto} onChange={fijar("texto")} aria-label="Texto" />
          {entidad === "correspondencia" ? (
            <>
              <select className="campo" value={f.tipo} onChange={fijar("tipo")} aria-label="Tipo"><option value="">Tipo</option><option value="RECIBIDA">Recibida</option><option value="EMITIDA">Emitida</option></select>
              <select className="campo" value={f.estado} onChange={fijar("estado")} aria-label="Estado"><option value="">Estado</option>{Object.entries(ESTADOS_CORRESPONDENCIA).map(([k, v]) => <option key={k} value={k}>{v.etiqueta}</option>)}</select>
              <select className="campo" value={f.idDestinatario} onChange={fijar("idDestinatario")} aria-label="Dependencia"><option value="">Dependencia</option>{destinatarios.data?.map((d) => <option key={d.id} value={d.id}>{d.unidad}</option>)}</select>
              <select className="campo" value={f.autorizado} onChange={fijar("autorizado")} aria-label="Autorización"><option value="">Autorización</option><option value="true">Autorizados</option><option value="false">Sin autorizar</option></select>
            </>
          ) : (
            <>
              <select className="campo" value={f.estado} onChange={fijar("estado")} aria-label="Estado"><option value="">Estado</option>{Object.entries(ESTADOS_TRAMITE).map(([k, v]) => <option key={k} value={k}>{v.etiqueta}</option>)}</select>
              <select className="campo" value={f.idTipoTramite} onChange={fijar("idTipoTramite")} aria-label="Tipo de trámite"><option value="">Tipo</option>{tipos.data?.map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}</select>
              <select className="campo" value={f.prioridad} onChange={fijar("prioridad")} aria-label="Prioridad"><option value="">Prioridad</option><option>ALTA</option><option>MEDIA</option><option>BAJA</option></select>
              <select className="campo" value={f.vencidos} onChange={fijar("vencidos")} aria-label="Vencimiento"><option value="">Vencimiento</option><option value="true">Solo vencidos</option></select>
            </>
          )}
          <div className="flex gap-2 lg:col-span-2">
            <input className="campo" type="date" value={f.desde} onChange={fijar("desde")} aria-label="Desde" />
            <input className="campo" type="date" value={f.hasta} onChange={fijar("hasta")} aria-label="Hasta" />
          </div>
          <button className="btn-secundario" onClick={() => { setF(VACIO); setPagina(0); }}><Eraser className="h-4 w-4" /> Limpiar</button>
        </div>
        {lista.isLoading ? <Cargando /> : lista.error ? <div className="p-4"><ErrorApiAviso error={lista.error} /></div> : (
          <>
            <div className="overflow-x-auto">
              {entidad === "correspondencia" ? (
                <table className="tabla">
                  <thead><tr><th>Código</th><th>Fecha</th><th>Tipo</th><th>Asunto</th><th>Dependencia</th><th>Estado</th><th>Autorizado</th></tr></thead>
                  <tbody className="divide-y divide-slate-100">
                    {(lista.data?.contenido as Correspondencia[]).map((c) => (
                      <tr key={c.id}><td className="font-medium">{c.codigo}</td><td>{formatoFecha(c.fechaDocumento)}</td><td>{c.tipo}</td><td>{c.asunto}</td><td className="text-xs">{c.unidad}</td>
                        <td><Insignia color={ESTADOS_CORRESPONDENCIA[c.estado]?.color}>{ESTADOS_CORRESPONDENCIA[c.estado]?.etiqueta}</Insignia></td><td>{c.autorizado ? "Sí" : "No"}</td></tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <table className="tabla">
                  <thead><tr><th>Número</th><th>Tipo</th><th>Estudiante</th><th>Recepción</th><th>Plazo</th><th>Estado</th></tr></thead>
                  <tbody className="divide-y divide-slate-100">
                    {(lista.data?.contenido as Tramite[]).map((t) => (
                      <tr key={t.id}><td><Link className="font-medium text-marca-700 hover:underline" to={`/tramites/${t.id}`}>{t.numero}</Link></td><td>{t.tipo}</td><td>{t.estudiante}</td>
                        <td>{formatoFecha(t.fechaRecepcion)}</td><td>{formatoFecha(t.fechaLimite)}{t.vencido && <span className="ml-1 text-xs text-red-600">vencido</span>}</td>
                        <td><Insignia color={ESTADOS_TRAMITE[t.estadoActual]?.color}>{ESTADOS_TRAMITE[t.estadoActual]?.etiqueta}</Insignia></td></tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            {lista.data && <Paginacion pagina={lista.data.pagina} totalPaginas={lista.data.totalPaginas} total={lista.data.totalElementos} onCambiar={setPagina} />}
          </>
        )}
      </div>
    </>
  );
}
