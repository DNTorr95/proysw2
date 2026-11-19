import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CheckCircle2, Mail, RefreshCw, Save, Send, Undo2 } from "lucide-react";
import { api, obtenerUrlBlob, type Pagina } from "../../api/cliente";
import type { InformeDetalle, InformeRpa } from "../../api/tipos";
import { Aviso, Cargando, Encabezado, ErrorApiAviso, Insignia, Modal, Paginacion, formatoFechaHora } from "../../components/ui";
import { ESTADOS_CORREO, ESTADOS_INFORME, formatoFecha } from "../../lib/estados";

/** CU-32 Revisar y validar resultados de ejecución RPA: listado de informes. */
export function ListaRevisionRpaPage() {
  const navigate = useNavigate();
  const [estado, setEstado] = useState("PENDIENTE_REVISION");
  const [pagina, setPagina] = useState(0);
  const lista = useQuery({
    queryKey: ["rpa-informes", estado, pagina],
    queryFn: () => api<Pagina<InformeRpa>>("/rpa/informes", { params: { estado, pagina, tamano: 15 } }),
    placeholderData: keepPreviousData,
  });
  return (
    <>
      <Encabezado titulo="Revisión de resultados RPA" descripcion="Informes generados por el robot, pendientes de revisión y autorización" />
      <div className="tarjeta">
        <div className="flex flex-wrap gap-2 border-b border-slate-200 p-3" role="tablist">
          {[["PENDIENTE_REVISION", "Pendientes"], ["APROBADO", "Aprobados"], ["DEVUELTO", "Devueltos"], ["", "Todos"]].map(([v, t]) => (
            <button key={v} role="tab" aria-selected={estado === v} className={estado === v ? "btn-primario" : "btn-secundario"} onClick={() => { setEstado(v); setPagina(0); }}>{t}</button>
          ))}
        </div>
        {lista.isLoading ? <Cargando /> : lista.error ? <div className="p-4"><ErrorApiAviso error={lista.error} /></div> : (
          <>
            <div className="overflow-x-auto">
              <table className="tabla">
                <thead><tr><th>Informe</th><th>Periodo</th><th>Recibidos / emitidos</th><th>Generado</th><th>Revisor</th><th>Revisión</th><th>Correo</th></tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {lista.data?.contenido.map((i) => (
                    <tr key={i.id} className="cursor-pointer hover:bg-slate-50" onClick={() => navigate(`/rpa/revision/${i.id}`)}>
                      <td className="font-medium text-marca-700">#{i.id}</td>
                      <td className="whitespace-nowrap">{formatoFecha(i.periodoDesde)} – {formatoFecha(i.periodoHasta)}</td>
                      <td>{i.totalRecibidos} / {i.totalEmitidos}</td>
                      <td className="whitespace-nowrap text-xs">{formatoFechaHora(i.fechaGeneracion)}</td>
                      <td>{i.revisor}</td>
                      <td><Insignia color={ESTADOS_INFORME[i.estadoRevision]?.color}>{ESTADOS_INFORME[i.estadoRevision]?.etiqueta}</Insignia></td>
                      <td>{i.estadoCorreo && <Insignia color={ESTADOS_CORREO[i.estadoCorreo]?.color}>{ESTADOS_CORREO[i.estadoCorreo]?.etiqueta}</Insignia>}</td>
                    </tr>
                  ))}
                  {lista.data?.contenido.length === 0 && <tr><td colSpan={7} className="py-8 text-center text-slate-500">No hay informes en este estado.</td></tr>}
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

const CATEGORIAS: Record<string, string> = { FECHA: "Por fecha", TIPO: "Por tipo", ESTADO: "Por estado", DEPENDENCIA: "Por dependencia" };

/** CU-32: detalle con vista previa del PDF, borrador y decisión (aprobar y autorizar envío, devolver). */
export default function RevisionRpaPage() {
  const { id } = useParams();
  const qc = useQueryClient();
  const clave = ["rpa-informe", id];
  const det = useQuery({ queryKey: clave, queryFn: () => api<InformeDetalle>(`/rpa/informes/${id}`) });
  const [urlPdf, setUrlPdf] = useState<string | null>(null);
  const [errorPdf, setErrorPdf] = useState<unknown>(null);
  const [confirmar, setConfirmar] = useState(false);
  const [devolviendo, setDevolviendo] = useState(false);

  useEffect(() => {
    let url: string | null = null;
    obtenerUrlBlob(`/rpa/informes/${id}/pdf`).then((u) => { url = u; setUrlPdf(u); }).catch(setErrorPdf);
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [id]);

  const actualizar = (d: InformeDetalle) => {
    qc.setQueryData(clave, d);
    qc.invalidateQueries({ queryKey: ["rpa-informes"] });
    qc.invalidateQueries({ queryKey: ["notificaciones"] });
  };
  const aprobar = useMutation({
    mutationFn: () => api<InformeDetalle>(`/rpa/informes/${id}/aprobacion`, { metodo: "POST" }),
    onSuccess: (d) => { setConfirmar(false); actualizar(d); },
  });
  const reintentar = useMutation({
    mutationFn: () => api<InformeDetalle>(`/rpa/informes/${id}/reintento-envio`, { metodo: "POST" }),
    onSuccess: actualizar,
  });

  if (det.isLoading) return <Cargando />;
  if (det.error || !det.data) return <ErrorApiAviso error={det.error} />;
  const { informe: i, correo, detalles, incidencias } = det.data;

  return (
    <>
      <Link to="/rpa/revision" className="mb-4 inline-flex items-center gap-1 text-sm text-marca-700 hover:underline"><ArrowLeft className="h-4 w-4" /> Volver a la revisión</Link>
      <Encabezado titulo={`Informe #${i.id}`}
        descripcion={`Correspondencia del ${formatoFecha(i.periodoDesde)} al ${formatoFecha(i.periodoHasta)} · ejecución #${i.idEjecucion} · revisor ${i.revisor ?? "—"}`}
        acciones={
          <>
            {det.data.puedeDevolver && <button className="btn-secundario" onClick={() => setDevolviendo(true)}><Undo2 className="h-4 w-4" /> Devolver</button>}
            {det.data.puedeAprobar && <button className="btn-primario" onClick={() => setConfirmar(true)}><Send className="h-4 w-4" /> Aprobar y autorizar envío</button>}
            {det.data.puedeReintentarEnvio && <button className="btn-primario" disabled={reintentar.isPending} onClick={() => reintentar.mutate()}><RefreshCw className="h-4 w-4" /> Reintentar envío</button>}
          </>
        } />

      <div className="mb-4 flex flex-wrap gap-2">
        <Insignia color={ESTADOS_INFORME[i.estadoRevision]?.color}>Informe: {ESTADOS_INFORME[i.estadoRevision]?.etiqueta}</Insignia>
        {correo && <Insignia color={ESTADOS_CORREO[correo.estado]?.color}>Correo: {ESTADOS_CORREO[correo.estado]?.etiqueta}</Insignia>}
        <span className="text-xs text-slate-500" title={i.hashSha256 ?? ""}>SHA-256 {i.hashSha256?.substring(0, 16)}…</span>
      </div>
      <div className="space-y-2">
        <ErrorApiAviso error={aprobar.error ?? reintentar.error} />
        {i.estadoRevision === "DEVUELTO" && <Aviso>Devuelto el {formatoFechaHora(i.fechaRevision)}: {i.observacionesRevision}. El Coordinador puede reejecutar desde el historial RPA.</Aviso>}
        {correo?.estado === "ENVIADO" && <Aviso tipo="exito">Enviado el {formatoFechaHora(correo.fechaEnvio)} · autorizado por {correo.autorizadoPor} el {formatoFechaHora(correo.fechaAutorizacion)}.</Aviso>}
        {correo?.estado === "ERROR" && <Aviso>El envío falló: {correo.mensajeError}</Aviso>}
      </div>

      <div className="mt-4 grid gap-6 xl:grid-cols-5">
        <section className="tarjeta overflow-hidden xl:col-span-3">
          <div className="border-b border-slate-200 px-4 py-2 text-sm font-semibold">Vista previa del PDF</div>
          {errorPdf ? <div className="p-4"><ErrorApiAviso error={errorPdf} /></div>
            : urlPdf ? <iframe title="Vista previa del informe" src={urlPdf} className="h-[75vh] w-full" />
              : <Cargando texto="Cargando PDF…" />}
        </section>

        <div className="space-y-6 xl:col-span-2">
          {correo && <Borrador idInforme={i.id} correo={correo} editable={det.data.puedeEditarBorrador} onGuardado={actualizar} />}

          <section className="tarjeta p-5">
            <h2 className="mb-3 font-semibold">Resumen consolidado</h2>
            <p className="mb-3 text-sm">Recibidos: <strong>{i.totalRecibidos}</strong> · Emitidos: <strong>{i.totalEmitidos}</strong></p>
            {Object.entries(CATEGORIAS).map(([cat, titulo]) => {
              const filas = detalles.filter((d) => d.categoria === cat);
              if (!filas.length) return null;
              return (
                <div key={cat} className="mb-3">
                  <p className="text-xs font-semibold uppercase text-slate-500">{titulo}</p>
                  <ul className="text-sm">{filas.map((d) => <li key={d.clave} className="flex justify-between border-b border-slate-100 py-0.5"><span>{d.clave}</span><span>{d.cantidad}</span></li>)}</ul>
                </div>
              );
            })}
          </section>

          {incidencias.length > 0 && (
            <section className="tarjeta p-5">
              <h2 className="mb-2 font-semibold">Incidencias detectadas ({incidencias.length})</h2>
              <ul className="space-y-1 text-sm">{incidencias.map((x) => <li key={x.id}><Insignia color="ambar">{x.tipo}</Insignia> {x.descripcion}</li>)}</ul>
            </section>
          )}
        </div>
      </div>

      {confirmar && correo && (
        <Modal abierto titulo="Aprobar y autorizar envío" onCerrar={() => setConfirmar(false)}>
          <div className="space-y-4 text-sm">
            <p>Se aprobará el informe #{i.id} y se enviará el correo con el PDF adjunto a:</p>
            <ul className="list-inside list-disc">{correo.destinatarios.map((d) => <li key={d}>{d}</li>)}</ul>
            <p className="text-slate-500">Su autorización queda registrada (usuario, fecha y hora) en el correo y en la auditoría. El envío no se puede deshacer.</p>
            <ErrorApiAviso error={aprobar.error} />
            <div className="flex justify-end gap-2">
              <button className="btn-secundario" onClick={() => setConfirmar(false)}>Cancelar</button>
              <button className="btn-primario" disabled={aprobar.isPending} onClick={() => aprobar.mutate()}><CheckCircle2 className="h-4 w-4" /> Aprobar y enviar</button>
            </div>
          </div>
        </Modal>
      )}
      {devolviendo && <DialogoDevolver idInforme={i.id} onCerrar={(d) => { setDevolviendo(false); if (d) actualizar(d); }} />}
    </>
  );
}

function Borrador({ idInforme, correo, editable, onGuardado }: {
  idInforme: number; correo: NonNullable<InformeDetalle["correo"]>; editable: boolean; onGuardado: (d: InformeDetalle) => void;
}) {
  const [editando, setEditando] = useState(false);
  const [destinatarios, setDestinatarios] = useState(correo.destinatarios.join(", "));
  const [asunto, setAsunto] = useState(correo.asunto);
  const [cuerpo, setCuerpo] = useState(correo.cuerpo);
  const guardar = useMutation({
    mutationFn: () => api<InformeDetalle>(`/rpa/informes/${idInforme}/borrador`, {
      metodo: "PUT", cuerpo: { destinatarios: destinatarios.split(/[,;\s]+/).filter(Boolean), asunto, cuerpo },
    }),
    onSuccess: (d) => { setEditando(false); onGuardado(d); },
  });
  return (
    <section className="tarjeta p-5">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="flex items-center gap-2 font-semibold"><Mail className="h-4 w-4" /> Borrador de correo</h2>
        {editable && !editando && <button className="btn-secundario px-2 py-1 text-xs" onClick={() => setEditando(true)}>Editar</button>}
      </div>
      {editando ? (
        <div className="space-y-3">
          <ErrorApiAviso error={guardar.error} />
          <div><label className="etiqueta" htmlFor="para">Para (separados por coma)</label><input id="para" className="campo" value={destinatarios} onChange={(e) => setDestinatarios(e.target.value)} /></div>
          <div><label className="etiqueta" htmlFor="asu">Asunto</label><input id="asu" className="campo" maxLength={300} value={asunto} onChange={(e) => setAsunto(e.target.value)} /></div>
          <div><label className="etiqueta" htmlFor="cue">Mensaje</label><textarea id="cue" rows={8} className="campo" value={cuerpo} onChange={(e) => setCuerpo(e.target.value)} /></div>
          <div className="flex justify-end gap-2">
            <button className="btn-secundario" onClick={() => setEditando(false)}>Cancelar</button>
            <button className="btn-primario" disabled={guardar.isPending} onClick={() => guardar.mutate()}><Save className="h-4 w-4" /> Guardar borrador</button>
          </div>
        </div>
      ) : (
        <dl className="space-y-2 text-sm">
          <div><dt className="text-xs text-slate-500">Para</dt><dd>{correo.destinatarios.join(", ")}</dd></div>
          <div><dt className="text-xs text-slate-500">Asunto</dt><dd>{correo.asunto}</dd></div>
          <div><dt className="text-xs text-slate-500">Adjunto</dt><dd>{correo.adjunto}</dd></div>
          <div><dt className="text-xs text-slate-500">Mensaje</dt><dd className="whitespace-pre-line rounded bg-slate-50 p-2">{correo.cuerpo}</dd></div>
        </dl>
      )}
    </section>
  );
}

function DialogoDevolver({ idInforme, onCerrar }: { idInforme: number; onCerrar: (d?: InformeDetalle) => void }) {
  const [obs, setObs] = useState("");
  const m = useMutation({
    mutationFn: () => api<InformeDetalle>(`/rpa/informes/${idInforme}/devolucion`, { metodo: "POST", cuerpo: { observaciones: obs } }),
    onSuccess: (d) => onCerrar(d),
  });
  return (
    <Modal abierto titulo="Devolver informe" onCerrar={() => onCerrar()}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">El informe quedará DEVUELTO y el borrador de correo se anulará. Luego podrá reejecutarse sin duplicar informes.</p>
        <ErrorApiAviso error={m.error} />
        <div><label className="etiqueta" htmlFor="obs">Observaciones (mín. 10 caracteres)</label><textarea id="obs" rows={4} className="campo" value={obs} onChange={(e) => setObs(e.target.value)} /></div>
        <div className="flex justify-end gap-2">
          <button className="btn-secundario" onClick={() => onCerrar()}>Cancelar</button>
          <button className="btn-peligro" disabled={obs.trim().length < 10 || m.isPending} onClick={() => m.mutate()}>Devolver</button>
        </div>
      </div>
    </Modal>
  );
}
