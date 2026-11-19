import { useState } from "react";
import { Link } from "react-router-dom";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, BrainCircuit, CircleAlert, Info, RefreshCw } from "lucide-react";
import { api, type Pagina } from "../../api/cliente";
import type { Alerta, AnalisisPredictivo, ResumenAlertas } from "../../api/tipos";
import { useAuth } from "../../auth/AuthContext";
import { Aviso, Cargando, Encabezado, ErrorApiAviso, Insignia, Modal, Paginacion, formatoFechaHora } from "../../components/ui";
import { ESTADOS_TRAMITE, formatoFecha } from "../../lib/estados";

/** Nivel con ícono + texto (nunca solo color). */
function Nivel({ nivel }: { nivel: string }) {
  if (nivel === "ALTA") return <span className="inline-flex items-center gap-1 text-xs font-medium text-red-700"><CircleAlert className="h-3.5 w-3.5" /> Alta</span>;
  if (nivel === "MEDIA") return <span className="inline-flex items-center gap-1 text-xs font-medium text-amber-700"><AlertTriangle className="h-3.5 w-3.5" /> Media</span>;
  return <span className="inline-flex items-center gap-1 text-xs text-slate-600"><Info className="h-3.5 w-3.5" /> Baja</span>;
}

/** CU-28 Alertas administrativas y predictivas · CU-29 Análisis predictivo · CU-30 Atención de alertas. */
export default function AlertasPage() {
  const qc = useQueryClient();
  const { tienePermiso } = useAuth();
  const [f, setF] = useState({ estado: "ABIERTAS", origen: "", nivel: "" });
  const [pagina, setPagina] = useState(0);
  const [atender, setAtender] = useState<Alerta | null>(null);
  const [analisis, setAnalisis] = useState<AnalisisPredictivo | null>(null);
  const resumen = useQuery({ queryKey: ["alertas-resumen"], queryFn: () => api<ResumenAlertas>("/alertas/resumen") });
  const lista = useQuery({
    queryKey: ["alertas", f, pagina],
    queryFn: () => api<Pagina<Alerta>>("/alertas", { params: { ...f, pagina, tamano: 15 } }),
    placeholderData: keepPreviousData,
  });
  const refrescar = () => { qc.invalidateQueries({ queryKey: ["alertas"] }); qc.invalidateQueries({ queryKey: ["alertas-resumen"] }); };
  const generar = useMutation({ mutationFn: () => api<{ creadas: number; actualizadas: number; cerradasAutomaticamente: number }>("/alertas/generacion", { metodo: "POST" }), onSuccess: refrescar });
  const predecir = useMutation({ mutationFn: () => api<AnalisisPredictivo>("/prediccion/analisis", { metodo: "POST" }), onSuccess: (a) => { setAnalisis(a); refrescar(); } });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLSelectElement>) => { setF({ ...f, [k]: e.target.value }); setPagina(0); };
  const r = resumen.data;

  return (
    <>
      <Encabezado titulo="Alertas" descripcion="Plazos vencidos o por vencer y riesgo de demora estimado por el modelo predictivo"
        acciones={<>
          {(tienePermiso("ATENDER_ALERTAS") || tienePermiso("ANALISIS_PREDICTIVO")) && <button className="btn-secundario" disabled={generar.isPending} onClick={() => generar.mutate()}><RefreshCw className="h-4 w-4" /> Revisar plazos ahora</button>}
          {tienePermiso("ANALISIS_PREDICTIVO") && <button className="btn-primario" disabled={predecir.isPending} onClick={() => predecir.mutate()}><BrainCircuit className="h-4 w-4" /> {predecir.isPending ? "Analizando…" : "Análisis predictivo"}</button>}
        </>} />
      <div className="mb-4 space-y-2">
        <ErrorApiAviso error={generar.error ?? predecir.error} />
        {generar.data && <Aviso tipo="exito">Revisión de plazos: {generar.data.creadas} alerta(s) nueva(s), {generar.data.actualizadas} actualizada(s), {generar.data.cerradasAutomaticamente} cerrada(s) por trámites resueltos.</Aviso>}
      </div>
      {r && (
        <div className="mb-6 grid gap-4 sm:grid-cols-4">
          <div className="tarjeta p-4"><p className="text-xs uppercase text-slate-500">Abiertas</p><p className="text-2xl font-semibold">{r.abiertas}</p></div>
          <div className="tarjeta p-4"><Nivel nivel="ALTA" /><p className="text-2xl font-semibold">{r.altas}</p></div>
          <div className="tarjeta p-4"><Nivel nivel="MEDIA" /><p className="text-2xl font-semibold">{r.medias}</p></div>
          <div className="tarjeta p-4 text-sm"><p className="text-xs uppercase text-slate-500">Origen</p><p>{r.porPlazo} por plazo</p><p>{r.porPrediccion} predictiva(s)</p></div>
        </div>
      )}

      {analisis && (
        <section className="tarjeta mb-6 p-5">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div>
              <h2 className="font-semibold">Resultado del análisis predictivo</h2>
              <p className="text-sm text-slate-600">Modelo {analisis.modelo} entrenado con {analisis.historicos} trámites cerrados ({analisis.demoradosHistoricos} demorados)
                {analisis.exactitud !== null && ` · exactitud por validación cruzada ${Math.round(analisis.exactitud * 100)} %`} · {analisis.evaluados} trámites en curso evaluados · {analisis.alertasCreadas} alerta(s) nueva(s).</p>
            </div>
            <button className="text-xs text-marca-700 underline" onClick={() => setAnalisis(null)}>Ocultar</button>
          </div>
          <table className="tabla mt-3">
            <thead><tr><th>Trámite</th><th>Tipo</th><th>Estado</th><th>Límite</th><th className="w-56">Probabilidad de demora</th><th>Nivel</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {analisis.riesgos.slice(0, 10).map((x) => (
                <tr key={x.idTramite}>
                  <td><Link to={`/tramites/${x.idTramite}`} className="font-medium text-marca-700 hover:underline">{x.numero}</Link></td>
                  <td className="text-xs">{x.tipo}</td><td className="text-xs">{ESTADOS_TRAMITE[x.estado]?.etiqueta}</td><td className="text-xs">{formatoFecha(x.fechaLimite)}</td>
                  <td><div className="flex items-center gap-2"><div className="h-2 flex-1 overflow-hidden rounded bg-slate-100"><div className="h-full bg-marca-600" style={{ width: `${x.probabilidad * 100}%` }} /></div><span className="w-10 text-right text-xs">{Math.round(x.probabilidad * 100)} %</span></div></td>
                  <td><Nivel nivel={x.nivel} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <div className="tarjeta">
        <div className="grid gap-3 border-b border-slate-200 p-3 sm:grid-cols-3">
          <select className="campo" value={f.estado} onChange={set("estado")} aria-label="Estado"><option value="ABIERTAS">Abiertas y en atención</option><option value="ABIERTA">Abiertas</option><option value="EN_ATENCION">En atención</option><option value="ATENDIDA">Atendidas</option><option value="">Todas</option></select>
          <select className="campo" value={f.origen} onChange={set("origen")} aria-label="Origen"><option value="">Todos los orígenes</option><option value="PLAZO">Por plazo</option><option value="PREDICCION">Predictivas</option></select>
          <select className="campo" value={f.nivel} onChange={set("nivel")} aria-label="Nivel"><option value="">Todos los niveles</option><option value="ALTA">Alta</option><option value="MEDIA">Media</option><option value="BAJA">Baja</option></select>
        </div>
        {lista.isLoading ? <Cargando /> : (
          <>
            <table className="tabla">
              <thead><tr><th>Nivel</th><th>Trámite</th><th>Motivo</th><th>Origen</th><th>Generada</th><th>Estado</th><th /></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {lista.data?.contenido.map((a) => (
                  <tr key={a.id}>
                    <td><Nivel nivel={a.nivel} /></td>
                    <td><Link to={`/tramites/${a.idTramite}`} className="font-medium text-marca-700 hover:underline">{a.numeroTramite}</Link><div className="text-xs text-slate-500">{a.tipoTramite} · {a.estudiante}</div></td>
                    <td className="max-w-sm text-xs">{a.motivo}</td>
                    <td><Insignia color={a.origen === "PLAZO" ? "gris" : "azul"}>{a.origen === "PLAZO" ? "Plazo" : "Predicción"}</Insignia></td>
                    <td className="whitespace-nowrap text-xs">{formatoFechaHora(a.fechaGeneracion)}</td>
                    <td className="text-xs">{a.estado.replace("_", " ").toLowerCase()}{a.atendidaPor && <div className="text-slate-500">{a.atendidaPor}</div>}</td>
                    <td className="text-right">{tienePermiso("ATENDER_ALERTAS") && a.estado !== "ATENDIDA" && <button className="btn-secundario px-2 py-1 text-xs" onClick={() => setAtender(a)}>Atender</button>}</td>
                  </tr>
                ))}
                {lista.data?.contenido.length === 0 && <tr><td colSpan={7} className="py-8 text-center text-slate-500">Sin alertas.</td></tr>}
              </tbody>
            </table>
            {lista.data && <Paginacion pagina={lista.data.pagina} totalPaginas={lista.data.totalPaginas} total={lista.data.totalElementos} onCambiar={setPagina} />}
          </>
        )}
      </div>
      {atender && <DialogoAtencion alerta={atender} onCerrar={() => { setAtender(null); refrescar(); }} />}
    </>
  );
}

function DialogoAtencion({ alerta, onCerrar }: { alerta: Alerta; onCerrar: () => void }) {
  const [comentario, setComentario] = useState("");
  const m = useMutation({
    mutationFn: (estado: string) => api(`/alertas/${alerta.id}/atencion`, { metodo: "PATCH", cuerpo: { estado, comentario } }),
    onSuccess: onCerrar,
  });
  return (
    <Modal abierto titulo={`Atender alerta de ${alerta.numeroTramite}`} onCerrar={onCerrar}>
      <div className="space-y-3">
        <p className="text-sm text-slate-600">{alerta.motivo}</p>
        <ErrorApiAviso error={m.error} />
        <div><label className="etiqueta" htmlFor="com">Acción realizada (queda en la auditoría)</label><textarea id="com" rows={3} className="campo" value={comentario} onChange={(e) => setComentario(e.target.value)} /></div>
        <div className="flex justify-end gap-2">
          {alerta.estado === "ABIERTA" && <button className="btn-secundario" disabled={!comentario.trim() || m.isPending} onClick={() => m.mutate("EN_ATENCION")}>Marcar en atención</button>}
          <button className="btn-primario" disabled={!comentario.trim() || m.isPending} onClick={() => m.mutate("ATENDIDA")}>Marcar atendida</button>
        </div>
      </div>
    </Modal>
  );
}
