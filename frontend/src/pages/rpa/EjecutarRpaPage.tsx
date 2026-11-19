import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { AlertTriangle, Bot, CheckCircle2, Info, Play, X } from "lucide-react";
import { api } from "../../api/cliente";
import type { Destinatario, EjecucionDetalle, EjecucionRpa, Previsualizacion, ProcesoRpa, Revisor } from "../../api/tipos";
import { NOMBRES_ROL } from "../../api/tipos";
import { Aviso, Cargando, Encabezado, ErrorApiAviso, Insignia } from "../../components/ui";
import { SITUACION_RPA, formatoDuracion, hoyLocal } from "../../lib/estados";
import { PasosEjecucion } from "./componentes";

function mesAnterior(): { desde: string; hasta: string } {
  const hoy = new Date();
  const inicio = new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1);
  const fin = new Date(hoy.getFullYear(), hoy.getMonth(), 0);
  const f = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  return { desde: f(inicio), hasta: f(fin) };
}

const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** CU-31 Ejecutar consolidación de correspondencia mediante RPA (incluye CU-34 y CU-35). */
export default function EjecutarRpaPage() {
  const procesos = useQuery({ queryKey: ["rpa-procesos"], queryFn: () => api<ProcesoRpa[]>("/rpa/procesos") });
  const revisores = useQuery({ queryKey: ["rpa-revisores"], queryFn: () => api<Revisor[]>("/rpa/revisores") });
  const destinatarios = useQuery({ queryKey: ["destinatarios"], queryFn: () => api<Destinatario[]>("/destinatarios") });

  const periodo = useMemo(mesAnterior, []);
  const [idProceso, setIdProceso] = useState("");
  const [desde, setDesde] = useState(periodo.desde);
  const [hasta, setHasta] = useState(periodo.hasta);
  const [tipo, setTipo] = useState("TODAS");
  const [idRevisor, setIdRevisor] = useState("");
  const [correos, setCorreos] = useState<string[]>([]);
  const [nuevoCorreo, setNuevoCorreo] = useState("");
  const [idEjecucion, setIdEjecucion] = useState<number | null>(null);

  useEffect(() => {
    if (!idProceso && procesos.data?.length) setIdProceso(String(procesos.data[0].id));
  }, [procesos.data, idProceso]);

  const previa = useQuery({
    queryKey: ["rpa-previa", idProceso, desde, hasta, tipo],
    queryFn: () => api<Previsualizacion>("/rpa/previsualizacion", { params: { idProceso, desde, hasta, tipo } }),
    enabled: !!idProceso && !!desde && !!hasta && desde <= hasta,
  });

  const ejecutar = useMutation({
    mutationFn: () => api<EjecucionRpa>("/rpa/ejecuciones", {
      metodo: "POST",
      cuerpo: { idProceso: Number(idProceso), periodoDesde: desde, periodoHasta: hasta, tipoCorrespondencia: tipo, idRevisor: Number(idRevisor), destinatariosCorreo: correos },
    }),
    onSuccess: (e) => setIdEjecucion(e.id),
  });

  const agregarCorreo = (c: string) => {
    const v = c.trim().toLowerCase();
    if (CORREO.test(v) && !correos.includes(v) && correos.length < 10) setCorreos([...correos, v]);
    setNuevoCorreo("");
  };

  const proceso = procesos.data?.find((p) => String(p.id) === idProceso);
  const bloqueado = !!previa.data?.idInformeVigente || !!previa.data?.ejecucionEnCurso;
  const listo = !!proceso && !!idRevisor && correos.length > 0 && desde <= hasta && !bloqueado;

  if (procesos.isLoading) return <Cargando />;
  if (idEjecucion !== null) return <ProgresoEjecucion id={idEjecucion} onNueva={() => { setIdEjecucion(null); ejecutar.reset(); previa.refetch(); }} />;

  return (
    <>
      <Encabezado titulo="Ejecutar RPA" descripcion="Consolidación de la correspondencia de un periodo: informe PDF y borrador de correo para revisión humana" />
      <div className="grid gap-6 lg:grid-cols-3">
        <form className="tarjeta space-y-5 p-5 lg:col-span-2" onSubmit={(e) => { e.preventDefault(); ejecutar.mutate(); }} noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label className="etiqueta" htmlFor="proc">Procedimiento</label>
              <select id="proc" className="campo" value={idProceso} onChange={(e) => setIdProceso(e.target.value)}>
                {procesos.data?.map((p) => <option key={p.id} value={p.id} disabled={p.estado !== "HABILITADO"}>{p.nombre} (v{p.version}){p.estado !== "HABILITADO" && " — deshabilitado"}</option>)}
              </select>
              {proceso?.descripcion && <p className="mt-1 text-xs text-slate-500">{proceso.descripcion}</p>}
            </div>
            <div>
              <label className="etiqueta" htmlFor="desde">Desde</label>
              <input id="desde" type="date" className="campo" value={desde} max={hasta} onChange={(e) => setDesde(e.target.value)} />
            </div>
            <div>
              <label className="etiqueta" htmlFor="hasta">Hasta</label>
              <input id="hasta" type="date" className="campo" value={hasta} min={desde} max={hoyLocal()} onChange={(e) => setHasta(e.target.value)} />
            </div>
            <div>
              <label className="etiqueta" htmlFor="tipo">Tipo de correspondencia</label>
              <select id="tipo" className="campo" value={tipo} onChange={(e) => setTipo(e.target.value)}>
                <option value="TODAS">Recibida y emitida</option><option value="RECIBIDA">Solo recibida</option><option value="EMITIDA">Solo emitida</option>
              </select>
            </div>
            <div>
              <label className="etiqueta" htmlFor="rev">Revisor</label>
              <select id="rev" className="campo" value={idRevisor} onChange={(e) => setIdRevisor(e.target.value)}>
                <option value="">Seleccione…</option>
                {revisores.data?.map((r) => <option key={r.id} value={r.id}>{r.nombreCompleto} — {NOMBRES_ROL[r.rol] ?? r.rol}</option>)}
              </select>
            </div>
          </div>

          <div>
            <span className="etiqueta">Destinatarios del correo (borrador)</span>
            <div className="flex flex-wrap gap-2">
              {correos.map((c) => (
                <span key={c} className="inline-flex items-center gap-1 rounded-full bg-marca-50 px-2.5 py-1 text-xs text-marca-800">
                  {c}<button type="button" onClick={() => setCorreos(correos.filter((x) => x !== c))} aria-label={`Quitar ${c}`}><X className="h-3 w-3" /></button>
                </span>
              ))}
            </div>
            <div className="mt-2 flex flex-wrap gap-2">
              <select className="campo max-w-xs" value="" onChange={(e) => e.target.value && agregarCorreo(e.target.value)} aria-label="Agregar desde el catálogo">
                <option value="">Agregar dependencia…</option>
                {destinatarios.data?.filter((d) => d.correo && d.activo).map((d) => <option key={d.id} value={d.correo!}>{d.unidad} ({d.correo})</option>)}
              </select>
              <input className="campo max-w-xs" type="email" placeholder="u otro correo y Enter" value={nuevoCorreo}
                onChange={(e) => setNuevoCorreo(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); agregarCorreo(nuevoCorreo); } }} aria-label="Otro correo" />
            </div>
            <p className="mt-1 text-xs text-slate-500">El RPA solo prepara el borrador; el correo se envía únicamente cuando el Director lo aprueba.</p>
          </div>

          <ErrorApiAviso error={ejecutar.error} />
          <button className="btn-primario" disabled={!listo || ejecutar.isPending}><Play className="h-4 w-4" /> Ejecutar consolidación</button>
        </form>

        <div className="space-y-4">
          <div className="tarjeta p-5">
            <h2 className="mb-3 flex items-center gap-2 font-semibold"><Info className="h-4 w-4 text-marca-600" /> Datos del periodo</h2>
            {previa.isLoading ? <Cargando /> : previa.error ? <ErrorApiAviso error={previa.error} /> : previa.data && (
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between"><dt>Oficios autorizados (se consolidan)</dt><dd className="font-semibold">{previa.data.autorizados}</dd></div>
                <div className="flex justify-between text-slate-500"><dt>Oficios sin autorizar (se excluyen)</dt><dd>{previa.data.noAutorizados}</dd></div>
              </dl>
            )}
            {previa.data?.idInformeVigente && (
              <div className="mt-3"><Aviso>Ya existe el informe #{previa.data.idInformeVigente} ({previa.data.estadoInformeVigente}) para este periodo. No se generan duplicados; si fue devuelto, use la reejecución desde el historial.</Aviso></div>
            )}
            {previa.data?.ejecucionEnCurso && <div className="mt-3"><Aviso>Hay una ejecución en curso para este periodo.</Aviso></div>}
          </div>
          <div className="tarjeta p-5 text-sm text-slate-600">
            <h2 className="mb-2 flex items-center gap-2 font-semibold text-slate-800"><Bot className="h-4 w-4 text-marca-600" /> ¿Qué hace el robot?</h2>
            <ol className="list-inside list-decimal space-y-1">
              <li>Valida campos, duplicados y fechas.</li>
              <li>Consolida y clasifica por fecha, tipo, estado y dependencia.</li>
              <li>Genera el PDF con la plantilla institucional.</li>
              <li>Prepara el borrador del correo con el PDF adjunto.</li>
            </ol>
            {proceso && <p className="mt-2 text-xs">Tiempo máximo: {proceso.tiempoMaxSegundos / 60} min · tiempo manual de referencia: {proceso.tiempoManualMinutos} min.</p>}
          </div>
        </div>
      </div>
    </>
  );
}

function ProgresoEjecucion({ id, onNueva }: { id: number; onNueva: () => void }) {
  const det = useQuery({
    queryKey: ["rpa-ejecucion", id],
    queryFn: () => api<EjecucionDetalle>(`/rpa/ejecuciones/${id}`),
    refetchInterval: (q) => (q.state.data && ["COMPLETADA", "FALLIDA"].includes(q.state.data.ejecucion.estado) ? false : 1200),
  });
  if (!det.data) return <Cargando texto="Iniciando ejecución…" />;
  const e = det.data.ejecucion;
  const terminada = e.estado === "COMPLETADA" || e.estado === "FALLIDA";
  return (
    <>
      <Encabezado titulo={`Ejecución RPA #${e.id}`} descripcion={`${e.proceso} · ${e.periodoDesde} al ${e.periodoHasta}`}
        acciones={terminada && <button className="btn-secundario" onClick={onNueva}>Nueva ejecución</button>} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="tarjeta p-5 lg:col-span-2">
          <div className="mb-4 flex items-center gap-2">
            <Insignia color={SITUACION_RPA[e.situacion]?.color}>{SITUACION_RPA[e.situacion]?.etiqueta ?? e.situacion}</Insignia>
            {!terminada && <span className="text-sm text-slate-500">Procesando… la página se actualiza sola.</span>}
          </div>
          <PasosEjecucion pasos={det.data.pasos} enCurso={!terminada} />
        </div>
        <div className="space-y-4">
          {terminada && e.estado === "COMPLETADA" && (
            <div className="tarjeta space-y-3 p-5">
              <p className="flex items-center gap-2 font-semibold text-green-700"><CheckCircle2 className="h-5 w-5" /> Informe generado</p>
              <p className="text-sm text-slate-600">El informe quedó <strong>pendiente de revisión</strong> y el correo en <strong>borrador</strong>. Se notificó a {e.revisor}.</p>
              <dl className="grid grid-cols-2 gap-2 text-sm">
                <dt className="text-slate-500">Duración</dt><dd>{formatoDuracion(e.duracionMs)}</dd>
                <dt className="text-slate-500">Procesados</dt><dd>{e.registrosProcesados}</dd>
                <dt className="text-slate-500">Válidos</dt><dd>{e.registrosValidos}</dd>
                <dt className="text-slate-500">Incidencias</dt><dd>{e.erroresDetectados}</dd>
              </dl>
              {e.idInforme && <Link to={`/rpa/revision/${e.idInforme}`} className="btn-secundario w-full">Ver informe</Link>}
            </div>
          )}
          {e.estado === "FALLIDA" && (
            <div className="tarjeta space-y-2 p-5">
              <p className="flex items-center gap-2 font-semibold text-red-700"><AlertTriangle className="h-5 w-5" /> La ejecución falló</p>
              <p className="text-sm">{det.data.mensajeError}</p>
              <p className="text-xs text-slate-500">No se generó informe ni correo. El Coordinador puede gestionar la reejecución desde el historial.</p>
            </div>
          )}
          {det.data.incidencias.length > 0 && (
            <div className="tarjeta p-5">
              <h3 className="mb-2 font-semibold">Incidencias ({det.data.incidencias.length})</h3>
              <ul className="space-y-1 text-sm">
                {det.data.incidencias.map((i) => <li key={i.id}><Insignia color={i.tipo === "DUPLICADO" || i.tipo === "CAMPO_OBLIGATORIO" || i.tipo === "FECHA_INVALIDA" ? "ambar" : "rojo"}>{i.tipo}</Insignia> <span className="text-slate-600">{i.descripcion}</span></li>)}
              </ul>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
