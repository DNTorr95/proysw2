import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Activity, Clock, Gauge, RotateCcw, Timer, TrendingUp, TriangleAlert } from "lucide-react";
import { api, type Pagina } from "../../api/cliente";
import type { EjecucionDetalle, EjecucionRpa, MetricasRpa } from "../../api/tipos";
import { useAuth } from "../../auth/AuthContext";
import { Cargando, Encabezado, ErrorApiAviso, Insignia, Modal, Paginacion, formatoFechaHora } from "../../components/ui";
import { SITUACION_RPA, formatoDuracion, formatoFecha } from "../../lib/estados";
import { PasosEjecucion } from "./componentes";

/** CU-36 Consultar resultados e incidencias RPA · CU-37 Gestionar incidencias y reejecuciones (con métricas). */
export default function HistorialRpaPage() {
  const [params, setParams] = useSearchParams();
  const [estado, setEstado] = useState("");
  const [pagina, setPagina] = useState(0);
  const abierta = params.get("ejecucion");
  const metricas = useQuery({ queryKey: ["rpa-metricas"], queryFn: () => api<MetricasRpa>("/rpa/metricas") });
  const lista = useQuery({
    queryKey: ["rpa-ejecuciones", estado, pagina],
    queryFn: () => api<Pagina<EjecucionRpa>>("/rpa/ejecuciones", { params: { estado, pagina, tamano: 15 } }),
    placeholderData: keepPreviousData,
    refetchInterval: (q) => (q.state.data?.contenido.some((e) => e.estado === "PENDIENTE" || e.estado === "EN_PROCESO") ? 2000 : false),
  });
  const m = metricas.data;

  return (
    <>
      <Encabezado titulo="Historial RPA" descripcion="Resultados, incidencias, reejecuciones y métricas de evaluación del robot" />

      {m && (
        <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Metrica icono={<Activity className="h-5 w-5" />} titulo="Ejecuciones" valor={String(m.totalEjecuciones)}
            detalle={`${m.completadas} completadas · ${m.fallidas} fallidas · ${m.reejecuciones} reejecuciones`} />
          <Metrica icono={<TrendingUp className="h-5 w-5" />} titulo="Tasa de éxito" valor={`${m.tasaExito} %`}
            detalle={`${m.registrosProcesados} registros procesados · ${m.erroresDetectados} errores detectados`} />
          <Metrica icono={<Timer className="h-5 w-5" />} titulo="Duración promedio" valor={formatoDuracion(m.duracionPromedioMs)}
            detalle={`Máxima ${formatoDuracion(m.duracionMaximaMs)}`} />
          <Metrica icono={<Clock className="h-5 w-5" />} titulo="Frente al proceso manual" valor={`${m.tiempoManualMinutos} min → ${m.tiempoAutomatizadoPromedioMin} min`}
            detalle={`Ahorro estimado: ${m.ahorroEstimadoHoras} h · ${m.correosEnviados} informe(s) enviados`} />
        </div>
      )}

      <div className="tarjeta">
        <div className="flex flex-wrap gap-3 border-b border-slate-200 p-3">
          <select className="campo max-w-xs" value={estado} onChange={(e) => { setEstado(e.target.value); setPagina(0); }} aria-label="Estado">
            <option value="">Todas las ejecuciones</option>
            <option value="COMPLETADA">Completadas</option><option value="FALLIDA">Fallidas</option>
            <option value="EN_PROCESO">En proceso</option><option value="PENDIENTE">Pendientes</option>
          </select>
        </div>
        {lista.isLoading ? <Cargando /> : lista.error ? <div className="p-4"><ErrorApiAviso error={lista.error} /></div> : (
          <>
            <div className="overflow-x-auto">
              <table className="tabla">
                <thead><tr><th>#</th><th>Periodo</th><th>Inicio</th><th>Duración</th><th>Registros</th><th>Errores</th><th>Situación</th><th>Iniciada por</th></tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {lista.data?.contenido.map((e) => (
                    <tr key={e.id} className="cursor-pointer hover:bg-slate-50" onClick={() => setParams({ ejecucion: String(e.id) })}>
                      <td className="font-medium text-marca-700">
                        {e.id}
                        {e.idEjecucionOrigen && <span className="ml-1 text-xs text-slate-500" title={`Reejecución de #${e.idEjecucionOrigen}`}>↻{e.idEjecucionOrigen}</span>}
                      </td>
                      <td className="whitespace-nowrap">{formatoFecha(e.periodoDesde)} – {formatoFecha(e.periodoHasta)}</td>
                      <td className="whitespace-nowrap text-xs">{formatoFechaHora(e.fechaInicio)}</td>
                      <td>{formatoDuracion(e.duracionMs)}</td>
                      <td>{e.registrosValidos}/{e.registrosProcesados}</td>
                      <td>{e.erroresDetectados > 0 ? <span className="text-amber-700">{e.erroresDetectados}</span> : 0}</td>
                      <td>
                        <Insignia color={SITUACION_RPA[e.situacion]?.color}>{SITUACION_RPA[e.situacion]?.etiqueta ?? e.situacion}</Insignia>
                        {e.idReejecucion && <span className="ml-1 text-xs text-slate-500">→ #{e.idReejecucion}</span>}
                      </td>
                      <td className="text-xs">{e.iniciadaPor}</td>
                    </tr>
                  ))}
                  {lista.data?.contenido.length === 0 && <tr><td colSpan={8} className="py-8 text-center text-slate-500">Sin ejecuciones.</td></tr>}
                </tbody>
              </table>
            </div>
            {lista.data && <Paginacion pagina={lista.data.pagina} totalPaginas={lista.data.totalPaginas} total={lista.data.totalElementos} onCambiar={setPagina} />}
          </>
        )}
      </div>
      {abierta && <DetalleEjecucion id={Number(abierta)} onCerrar={() => setParams({})} onAbrir={(id) => setParams({ ejecucion: String(id) })} />}
    </>
  );
}

function Metrica({ icono, titulo, valor, detalle }: { icono: React.ReactNode; titulo: string; valor: string; detalle: string }) {
  return (
    <div className="tarjeta p-4">
      <p className="flex items-center gap-2 text-xs font-medium uppercase text-slate-500"><span className="text-marca-600">{icono}</span>{titulo}</p>
      <p className="mt-1 text-xl font-semibold">{valor}</p>
      <p className="mt-1 text-xs text-slate-500">{detalle}</p>
    </div>
  );
}

function DetalleEjecucion({ id, onCerrar, onAbrir }: { id: number; onCerrar: () => void; onAbrir: (id: number) => void }) {
  const qc = useQueryClient();
  const { tienePermiso } = useAuth();
  const [reejecutando, setReejecutando] = useState(false);
  const [motivo, setMotivo] = useState("");
  const det = useQuery({
    queryKey: ["rpa-ejecucion", id],
    queryFn: () => api<EjecucionDetalle>(`/rpa/ejecuciones/${id}`),
    refetchInterval: (q) => (q.state.data && ["PENDIENTE", "EN_PROCESO"].includes(q.state.data.ejecucion.estado) ? 1200 : false),
  });
  const refrescar = () => {
    qc.invalidateQueries({ queryKey: ["rpa-ejecucion"] });
    qc.invalidateQueries({ queryKey: ["rpa-ejecuciones"] });
    qc.invalidateQueries({ queryKey: ["rpa-metricas"] });
  };
  const reejecutar = useMutation({
    mutationFn: () => api<EjecucionRpa>(`/rpa/ejecuciones/${id}/reejecucion`, { metodo: "POST", cuerpo: { motivo } }),
    onSuccess: (n) => { setReejecutando(false); refrescar(); onAbrir(n.id); },
  });
  const cerrar = useMutation({
    mutationFn: (idInc: number) => api(`/rpa/incidencias/${idInc}/cierre`, { metodo: "PATCH", cuerpo: { comentario: "Revisada y cerrada por el Coordinador" } }),
    onSuccess: refrescar,
  });

  const e = det.data?.ejecucion;
  return (
    <Modal abierto titulo={`Ejecución RPA #${id}`} onCerrar={onCerrar} ancho="max-w-4xl">
      {!det.data || !e ? <Cargando /> : (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <Insignia color={SITUACION_RPA[e.situacion]?.color}>{SITUACION_RPA[e.situacion]?.etiqueta ?? e.situacion}</Insignia>
            <span className="text-slate-600">{e.proceso} · {formatoFecha(e.periodoDesde)} – {formatoFecha(e.periodoHasta)} · {e.tipoCorrespondencia}</span>
          </div>
          <dl className="grid gap-3 text-sm sm:grid-cols-4">
            <div><dt className="text-xs text-slate-500">Iniciada por</dt><dd>{e.iniciadaPor}</dd></div>
            <div><dt className="text-xs text-slate-500">Revisor</dt><dd>{e.revisor}</dd></div>
            <div><dt className="text-xs text-slate-500">Duración</dt><dd>{formatoDuracion(e.duracionMs)}</dd></div>
            <div><dt className="text-xs text-slate-500">Registros válidos</dt><dd>{e.registrosValidos} de {e.registrosProcesados}</dd></div>
          </dl>
          <div className="flex flex-wrap gap-2 text-sm">
            {e.idEjecucionOrigen && <button className="btn-secundario px-2 py-1 text-xs" onClick={() => onAbrir(e.idEjecucionOrigen!)}>Ver ejecución anterior #{e.idEjecucionOrigen}</button>}
            {e.idReejecucion && <button className="btn-secundario px-2 py-1 text-xs" onClick={() => onAbrir(e.idReejecucion!)}>Ver reejecución #{e.idReejecucion}</button>}
            {e.idInforme && <Link className="btn-secundario px-2 py-1 text-xs" to={`/rpa/revision/${e.idInforme}`}>Ver informe #{e.idInforme}</Link>}
          </div>
          {det.data.mensajeError && <p className="rounded-md bg-red-50 p-3 text-sm text-red-800">{det.data.mensajeError}</p>}

          <section>
            <h3 className="mb-2 font-semibold">Registro de pasos (evidencias)</h3>
            <PasosEjecucion pasos={det.data.pasos} enCurso={e.estado === "PENDIENTE" || e.estado === "EN_PROCESO"} />
          </section>

          <section>
            <h3 className="mb-2 font-semibold">Incidencias</h3>
            {det.data.incidencias.length === 0 ? <p className="text-sm text-slate-500">Sin incidencias.</p> : (
              <ul className="divide-y divide-slate-100 rounded-md border border-slate-200">
                {det.data.incidencias.map((i) => (
                  <li key={i.id} className="flex flex-wrap items-center gap-2 px-3 py-2 text-sm">
                    <TriangleAlert className="h-4 w-4 text-amber-500" />
                    <Insignia color="ambar">{i.tipo}</Insignia>
                    <span className="flex-1">{i.descripcion}</span>
                    <Insignia color={i.estado === "CERRADA" ? "gris" : i.estado === "EN_REEJECUCION" ? "azul" : "rojo"}>{i.estado}</Insignia>
                    {i.estado === "ABIERTA" && tienePermiso("GESTIONAR_REEJECUCION_RPA") && (
                      <button className="btn-secundario px-2 py-1 text-xs" disabled={cerrar.isPending} onClick={() => cerrar.mutate(i.id)}>Cerrar</button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          {tienePermiso("GESTIONAR_REEJECUCION_RPA") && (
            <section className="flex flex-wrap items-center gap-3 border-t border-slate-200 pt-4">
              {det.data.puedeReejecutar ? (
                <button className="btn-primario" onClick={() => setReejecutando(true)}><RotateCcw className="h-4 w-4" /> Reejecutar</button>
              ) : (
                <p className="flex items-center gap-2 text-sm text-slate-500"><Gauge className="h-4 w-4" /> {det.data.motivoNoReejecutable}</p>
              )}
            </section>
          )}
        </div>
      )}
      {reejecutando && (
        <Modal abierto titulo={`Reejecutar la ejecución #${id}`} onCerrar={() => setReejecutando(false)}>
          <div className="space-y-4 text-sm">
            <p>Se creará una nueva ejecución que referencia a la #{id}, con el mismo periodo, revisor y destinatarios. Las incidencias abiertas pasan a "en reejecución". No se duplican informes ni correos.</p>
            <ErrorApiAviso error={reejecutar.error} />
            <div><label className="etiqueta" htmlFor="mot">Motivo</label><input id="mot" className="campo" maxLength={300} value={motivo} onChange={(ev) => setMotivo(ev.target.value)} /></div>
            <div className="flex justify-end gap-2">
              <button className="btn-secundario" onClick={() => setReejecutando(false)}>Cancelar</button>
              <button className="btn-primario" disabled={reejecutar.isPending} onClick={() => reejecutar.mutate()}>Reejecutar</button>
            </div>
          </div>
        </Modal>
      )}
    </Modal>
  );
}
