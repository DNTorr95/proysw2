import { useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, ArrowLeft, Check, CheckCircle2, Circle, Gavel, X } from "lucide-react";
import { api, subirArchivo } from "../../api/cliente";
import type { Accion, DocumentoTramite, TramiteDetalle } from "../../api/tipos";
import { useAuth } from "../../auth/AuthContext";
import { ListaArchivos, SubirArchivo } from "../../components/documentos";
import { Aviso, Cargando, ErrorApiAviso, Insignia, Modal, formatoFechaHora } from "../../components/ui";
import { ESTADOS_TRAMITE, PRIORIDADES, formatoFecha } from "../../lib/estados";

const FLUJO = ["RECEPCIONADO", "EN_REVISION", "APROBADO", "ARCHIVADO"];
const EDITABLES = ["RECEPCIONADO", "EN_REVISION", "OBSERVADO"];

/** CU-11 Ejecutar actuaciones · CU-12 Seguimiento e historial (línea de tiempo de estados). */
export default function TramiteDetallePage() {
  const { id } = useParams();
  const ubicacion = useLocation();
  const qc = useQueryClient();
  const { tienePermiso } = useAuth();
  const [accion, setAccion] = useState<Accion | null>(null);
  const [dictamenAbierto, setDictamenAbierto] = useState(false);
  const clave = ["tramite", id];
  const det = useQuery({ queryKey: clave, queryFn: () => api<TramiteDetalle>(`/tramites/${id}`) });
  const actualizar = (d?: TramiteDetalle) => {
    if (d) qc.setQueryData(clave, d); else qc.invalidateQueries({ queryKey: clave });
    qc.invalidateQueries({ queryKey: ["tramites"] });
  };

  if (det.isLoading) return <Cargando />;
  if (det.error || !det.data) return <ErrorApiAviso error={det.error} />;
  const { tramite: t, documentos, historial, acciones, requisitosPendientes, dictamen } = det.data;
  const editable = EDITABLES.includes(t.estadoActual);
  const archivos = documentos.flatMap((d) => (d.archivo ? [d.archivo] : []));
  const ruta = t.estadoActual === "RECHAZADO" ? ["RECEPCIONADO", "EN_REVISION", "RECHAZADO", "ARCHIVADO"] : FLUJO;
  const alcanzados = new Set(historial.map((h) => h.estadoDestino));

  return (
    <>
      <Link to="/tramites" className="mb-4 inline-flex items-center gap-1 text-sm text-marca-700 hover:underline"><ArrowLeft className="h-4 w-4" /> Volver a trámites</Link>
      {(ubicacion.state as { registrado?: boolean } | null)?.registrado && historial.length === 1 && (
        <div className="mb-4"><Aviso tipo="exito">Trámite {t.numero} registrado correctamente.</Aviso></div>
      )}

      <div className="tarjeta mb-6 p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold">{t.numero}</h1>
              <Insignia color={ESTADOS_TRAMITE[t.estadoActual]?.color}>{ESTADOS_TRAMITE[t.estadoActual]?.etiqueta ?? t.estadoActual}</Insignia>
              <Insignia color={PRIORIDADES[t.prioridad]}>Prioridad {t.prioridad.toLowerCase()}</Insignia>
              {t.vencido && <Insignia color="rojo">Plazo vencido</Insignia>}
            </div>
            <p className="mt-1 text-slate-700">{t.tipo}</p>
            <p className="text-sm text-slate-500">
              {t.estudiante} · Reg. {t.registro} · Recibido {formatoFechaHora(t.fechaRecepcion)} por {t.registradoPor} · Fecha límite {formatoFecha(t.fechaLimite)}
              {!t.fechaCierre && !t.vencido && ` (${t.diasRestantes} día(s) restantes)`}
              {t.expediente && ` · Expediente ${t.expediente}`}
            </p>
            {t.observaciones && <p className="mt-2 text-sm">{t.observaciones}</p>}
          </div>
          <div className="flex flex-wrap gap-2">
            {acciones.map((a) => (
              <button key={a.evento} onClick={() => setAccion(a)}
                className={a.evento === "rechazar" ? "btn-peligro" : a.evento === "observar" ? "btn-secundario" : "btn-primario"}>
                {a.etiqueta}
              </button>
            ))}
            {det.data.requiereDictamen && t.estadoActual === "EN_REVISION" &&
              (tienePermiso("EJECUTAR_ACTUACION_TRAMITE") || tienePermiso("APROBAR_RECHAZAR_TRAMITE")) && (
                <button className="btn-secundario" onClick={() => setDictamenAbierto(true)}><Gavel className="h-4 w-4" /> Dictamen</button>
              )}
          </div>
        </div>

        {/* Ruta de estados */}
        <ol className="mt-6 flex flex-wrap items-center gap-2 text-xs">
          {ruta.map((e, i) => {
            const actual = e === t.estadoActual;
            const hecho = alcanzados.has(e) && !actual;
            return (
              <li key={e} className="flex items-center gap-2">
                <span className={`inline-flex items-center gap-1 rounded-full px-3 py-1 font-medium ${
                  actual ? "bg-marca-700 text-white" : hecho ? "bg-marca-100 text-marca-800" : "bg-slate-100 text-slate-500"}`}>
                  {hecho ? <Check className="h-3 w-3" /> : <Circle className="h-3 w-3" />} {ESTADOS_TRAMITE[e].etiqueta}
                </span>
                {i < ruta.length - 1 && <span className="h-px w-6 bg-slate-300" />}
              </li>
            );
          })}
          {t.estadoActual === "OBSERVADO" && <li><Insignia color="ambar">Observado: pendiente de subsanación</Insignia></li>}
        </ol>
      </div>

      <div className="grid gap-6 lg:grid-cols-5">
        <div className="space-y-6 lg:col-span-3">
          <section className="tarjeta p-5">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-semibold">Requisitos y documentación</h2>
              {requisitosPendientes > 0
                ? <span className="inline-flex items-center gap-1 text-xs font-medium text-amber-700"><AlertTriangle className="h-3.5 w-3.5" /> {requisitosPendientes} obligatorio(s) pendiente(s)</span>
                : <span className="inline-flex items-center gap-1 text-xs font-medium text-green-700"><CheckCircle2 className="h-3.5 w-3.5" /> Requisitos obligatorios completos</span>}
            </div>
            <ul className="space-y-2">
              {documentos.map((d) => <FilaRequisito key={d.id} idTramite={t.id} doc={d} editable={editable} onCambio={() => actualizar()} />)}
            </ul>
            {editable && tienePermiso("GESTIONAR_REQUISITOS") && (
              <div className="mt-4">
                <SubirArchivo etiqueta="Agregar documento adicional" onSubir={async (f) => { await subirArchivo(`/tramites/${t.id}/documentos`, f); actualizar(); }} />
              </div>
            )}
          </section>

          <section className="tarjeta p-5">
            <h2 className="mb-3 font-semibold">Documentos adjuntos</h2>
            <ListaArchivos archivos={archivos} />
          </section>

          {dictamen && (
            <section className="tarjeta p-5">
              <h2 className="mb-2 font-semibold">Dictamen técnico</h2>
              <p className="text-sm"><Insignia color={dictamen.resultado === "FAVORABLE" ? "verde" : "rojo"}>{dictamen.resultado}</Insignia> <span className="text-slate-500">{formatoFecha(dictamen.fecha)}</span></p>
              <p className="mt-2 whitespace-pre-line text-sm">{dictamen.fundamento}</p>
            </section>
          )}
        </div>

        <section className="tarjeta p-5 lg:col-span-2">
          <h2 className="mb-4 font-semibold">Historial de estados</h2>
          <ol className="relative ml-2 space-y-5 border-l border-slate-200 pl-5">
            {[...historial].reverse().map((h) => (
              <li key={h.id}>
                <span className="absolute -left-1.5 mt-1 h-3 w-3 rounded-full border-2 border-white bg-marca-500" />
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  {h.estadoOrigen && <><Insignia color={ESTADOS_TRAMITE[h.estadoOrigen]?.color}>{ESTADOS_TRAMITE[h.estadoOrigen]?.etiqueta}</Insignia><span>→</span></>}
                  <Insignia color={ESTADOS_TRAMITE[h.estadoDestino]?.color}>{ESTADOS_TRAMITE[h.estadoDestino]?.etiqueta}</Insignia>
                </div>
                <p className="mt-1 text-xs text-slate-500">{formatoFechaHora(h.fechaHora)} · {h.usuario ?? `usuario ${h.idUsuario}`} · {h.evento}</p>
                {h.observacion && <p className="mt-1 text-sm text-slate-700">{h.observacion}</p>}
              </li>
            ))}
          </ol>
        </section>
      </div>

      {accion && <DialogoAccion idTramite={t.id} accion={accion} pendientes={requisitosPendientes} onCerrar={(d) => { setAccion(null); if (d) actualizar(d); }} />}
      {dictamenAbierto && <DialogoDictamen idTramite={t.id} actual={dictamen} onCerrar={() => { setDictamenAbierto(false); actualizar(); }} />}
    </>
  );
}

function FilaRequisito({ idTramite, doc, editable, onCambio }: { idTramite: number; doc: DocumentoTramite; editable: boolean; onCambio: () => void }) {
  const { tienePermiso } = useAuth();
  const m = useMutation({
    mutationFn: (cuerpo: Record<string, unknown>) => api(`/tramites/${idTramite}/documentos/${doc.id}`, { metodo: "PATCH", cuerpo }),
    onSuccess: onCambio,
  });
  const puedeRegistrar = editable && tienePermiso("GESTIONAR_REQUISITOS");
  const puedeVerificar = editable && (tienePermiso("GESTIONAR_REQUISITOS") || tienePermiso("EJECUTAR_ACTUACION_TRAMITE"));
  return (
    <li className="rounded-md border border-slate-200 px-3 py-2">
      <div className="flex flex-wrap items-center gap-3">
        {doc.presentado ? <CheckCircle2 className="h-4 w-4 text-green-600" /> : <Circle className="h-4 w-4 text-slate-300" />}
        <div className="min-w-0 flex-1 text-sm">
          <p>{doc.descripcion} {doc.esRequisito && (doc.obligatorio ? <span className="text-xs text-red-600">(obligatorio)</span> : <span className="text-xs text-slate-400">(opcional)</span>)}</p>
          <p className="text-xs text-slate-500">
            {doc.presentado ? "Presentado" : "No presentado"}{doc.verificado && " · verificado"}{doc.archivo && ` · ${doc.archivo.nombreArchivo}`}
            {doc.observacion && ` · ${doc.observacion}`}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-1">
          {puedeRegistrar && doc.esRequisito && (
            <button className="btn-secundario px-2 py-1 text-xs" disabled={m.isPending} onClick={() => m.mutate({ presentado: !doc.presentado })}>
              {doc.presentado ? "Marcar no presentado" : "Marcar presentado"}
            </button>
          )}
          {puedeRegistrar && doc.esRequisito && (
            <SubirArchivo compacto etiqueta={doc.archivo ? "Reemplazar" : "Adjuntar"}
              onSubir={async (f) => { await subirArchivo(`/tramites/${idTramite}/documentos/${doc.id}/archivo`, f); onCambio(); }} />
          )}
          {puedeVerificar && doc.presentado && (
            <button className="btn-secundario px-2 py-1 text-xs" disabled={m.isPending} onClick={() => m.mutate({ verificado: !doc.verificado })}>
              {doc.verificado ? <><X className="h-3 w-3" /> Quitar verificación</> : <><Check className="h-3 w-3" /> Verificar</>}
            </button>
          )}
        </div>
      </div>
      {m.error && <div className="mt-2"><ErrorApiAviso error={m.error} /></div>}
    </li>
  );
}

function DialogoAccion({ idTramite, accion, pendientes, onCerrar }: {
  idTramite: number; accion: Accion; pendientes: number; onCerrar: (d?: TramiteDetalle) => void;
}) {
  const [justificacion, setJustificacion] = useState("");
  const m = useMutation({
    mutationFn: () => api<TramiteDetalle>(`/tramites/${idTramite}/eventos/${accion.evento}`, { metodo: "POST", cuerpo: { justificacion } }),
    onSuccess: (d) => onCerrar(d),
  });
  const avisoRequisitos = (accion.evento === "derivar" || accion.evento === "subsanar") && pendientes > 0;
  return (
    <Modal abierto titulo={accion.etiqueta} onCerrar={() => onCerrar()}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          El trámite pasará al estado <strong>{ESTADOS_TRAMITE[accion.destino]?.etiqueta ?? accion.destino}</strong>. La actuación queda registrada en el historial y en la auditoría.
        </p>
        {avisoRequisitos && <Aviso>Hay {pendientes} requisito(s) obligatorio(s) pendiente(s): la máquina de estados rechazará esta acción.</Aviso>}
        <ErrorApiAviso error={m.error} />
        <div>
          <label className="etiqueta" htmlFor="just">{accion.requiereJustificacion ? "Justificación (obligatoria, mín. 10 caracteres)" : "Observación (opcional)"}</label>
          <textarea id="just" rows={4} className="campo" maxLength={2000} value={justificacion} onChange={(e) => setJustificacion(e.target.value)} />
        </div>
        <div className="flex justify-end gap-2">
          <button className="btn-secundario" onClick={() => onCerrar()}>Cancelar</button>
          <button className={accion.evento === "rechazar" ? "btn-peligro" : "btn-primario"} disabled={m.isPending || (accion.requiereJustificacion && justificacion.trim().length < 10)}
            onClick={() => m.mutate()}>Confirmar</button>
        </div>
      </div>
    </Modal>
  );
}

function DialogoDictamen({ idTramite, actual, onCerrar }: { idTramite: number; actual: TramiteDetalle["dictamen"]; onCerrar: () => void }) {
  const [resultado, setResultado] = useState(actual?.resultado ?? "FAVORABLE");
  const [fundamento, setFundamento] = useState(actual?.fundamento ?? "");
  const m = useMutation({
    mutationFn: () => api(`/tramites/${idTramite}/dictamen`, { metodo: "PUT", cuerpo: { resultado, fundamento } }),
    onSuccess: onCerrar,
  });
  return (
    <Modal abierto titulo="Dictamen técnico" onCerrar={onCerrar}>
      <div className="space-y-4">
        <ErrorApiAviso error={m.error} />
        <div>
          <label className="etiqueta" htmlFor="res">Resultado</label>
          <select id="res" className="campo" value={resultado} onChange={(e) => setResultado(e.target.value)}>
            <option value="FAVORABLE">Favorable</option><option value="DESFAVORABLE">Desfavorable</option>
          </select>
        </div>
        <div>
          <label className="etiqueta" htmlFor="fund">Fundamento</label>
          <textarea id="fund" rows={5} className="campo" maxLength={4000} value={fundamento} onChange={(e) => setFundamento(e.target.value)} />
        </div>
        <div className="flex justify-end gap-2">
          <button className="btn-secundario" onClick={onCerrar}>Cancelar</button>
          <button className="btn-primario" disabled={!fundamento.trim() || m.isPending} onClick={() => m.mutate()}>Guardar dictamen</button>
        </div>
      </div>
    </Modal>
  );
}
