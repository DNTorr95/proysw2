import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRightLeft, Ban, Download } from "lucide-react";
import { abrirDocumento, api, subirArchivo, type Pagina } from "../api/cliente";
import { NOMBRES_ROL, type Transferencia, type Usuario } from "../api/tipos";
import { SubirArchivo } from "../components/documentos";
import { Aviso, Cargando, Encabezado, ErrorApiAviso, Insignia, Modal } from "../components/ui";
import { ACEPTA_ARCHIVOS, formatoFecha, hoyLocal } from "../lib/estados";

/** CU-04 Transferir autoridad de Dirección. */
export default function TransferenciaAutoridadPage() {
  const qc = useQueryClient();
  const lista = useQuery({ queryKey: ["transferencias"], queryFn: () => api<Transferencia[]>("/transferencias-autoridad") });
  const usuarios = useQuery({ queryKey: ["usuarios-activos"], queryFn: () => api<Pagina<Usuario>>("/usuarios", { params: { estado: "ACTIVO", tamano: 100 } }) });
  const hoy = hoyLocal();
  const [f, setF] = useState({ idUsuarioDestino: "", motivo: "", resolucion: "", fechaInicio: hoy, fechaFin: "" });
  const [revocar, setRevocar] = useState<Transferencia | null>(null);
  const vigente = lista.data?.find((t) => t.estado === "VIGENTE");
  const [respaldo, setRespaldo] = useState<File | null>(null);
  const [errorDescarga, setErrorDescarga] = useState<unknown>(null);
  const crear = useMutation({
    mutationFn: async () => {
      const t = await api<Transferencia>("/transferencias-autoridad", { metodo: "POST", cuerpo: { ...f, idUsuarioDestino: Number(f.idUsuarioDestino), fechaFin: f.fechaFin || null } });
      // El respaldo se adjunta después de registrar la transferencia (necesita su id)
      if (respaldo) await subirArchivo(`/transferencias-autoridad/${t.id}/respaldo`, respaldo);
      return t;
    },
    onSuccess: () => { setF({ ...f, motivo: "", resolucion: "" }); setRespaldo(null); },
    onSettled: () => qc.invalidateQueries({ queryKey: ["transferencias"] }),
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });

  return (
    <>
      <Encabezado titulo="Transferencia de autoridad" descripcion="Delegación de la autoridad de Dirección durante ausencias (viaje, licencia, comisión)" />
      <div className="grid gap-6 lg:grid-cols-3">
        <section className="tarjeta space-y-3 p-5">
          <h2 className="flex items-center gap-2 font-semibold"><ArrowRightLeft className="h-4 w-4" /> Nueva transferencia</h2>
          {vigente ? <Aviso tipo="info">Hay una transferencia vigente a {vigente.destino}. Revóquela para registrar otra.</Aviso> : (
            <>
              <p className="text-xs text-slate-500">Mientras esté vigente, la persona designada actúa con el rol y los permisos de Director de Carrera (por ejemplo, aprobar trámites y autorizar envíos RPA). No puede volver a delegarla. Todo queda en la auditoría.</p>
              <ErrorApiAviso error={crear.error} />
              <div><label className="etiqueta" htmlFor="dest">Delegar a</label>
                <select id="dest" className="campo" value={f.idUsuarioDestino} onChange={set("idUsuarioDestino")}><option value="">Seleccione…</option>
                  {usuarios.data?.contenido.filter((u) => u.rol !== "DIRECTOR_CARRERA").map((u) => <option key={u.id} value={u.id}>{u.nombres} {u.apellidos} — {NOMBRES_ROL[u.rol]}</option>)}</select></div>
              <div><label className="etiqueta" htmlFor="mot">Motivo</label><input id="mot" className="campo" value={f.motivo} onChange={set("motivo")} placeholder="Viaje oficial, licencia médica…" /></div>
              <div><label className="etiqueta" htmlFor="res">Resolución (opcional)</label><input id="res" className="campo" value={f.resolucion} onChange={set("resolucion")} /></div>
              <div>
                <label className="etiqueta" htmlFor="resp">Documento de respaldo (opcional)</label>
                <input id="resp" type="file" accept={ACEPTA_ARCHIVOS} className="campo"
                  onChange={(e) => setRespaldo(e.target.files?.[0] ?? null)} />
                <p className="mt-1 text-xs text-slate-500">Resolución o memorándum escaneado: PDF, JPG, PNG o DOCX de hasta 10 MB.</p>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="etiqueta" htmlFor="fi">Desde</label><input id="fi" type="date" min={hoy} className="campo" value={f.fechaInicio} onChange={set("fechaInicio")} /></div>
                <div><label className="etiqueta" htmlFor="ff">Hasta (vacío = indefinida)</label><input id="ff" type="date" min={f.fechaInicio} className="campo" value={f.fechaFin} onChange={set("fechaFin")} /></div>
              </div>
              <button className="btn-primario w-full" disabled={!f.idUsuarioDestino || f.motivo.trim().length < 10 || crear.isPending} onClick={() => crear.mutate()}>Transferir autoridad</button>
            </>
          )}
        </section>
        <section className="tarjeta lg:col-span-2">
          <div className="border-b border-slate-200 px-4 py-2 font-semibold">Historial</div>
          {!!errorDescarga && <div className="px-4 pt-3"><ErrorApiAviso error={errorDescarga} /></div>}
          {lista.isLoading ? <Cargando /> : (
            <table className="tabla">
              <thead><tr><th>Delegada a</th><th>Por</th><th>Vigencia</th><th>Motivo</th><th>Respaldo</th><th>Estado</th><th /></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {lista.data?.map((t) => (
                  <tr key={t.id}>
                    <td>{t.destino}<div className="text-xs text-slate-500">{NOMBRES_ROL[t.rolDestino]}</div></td><td className="text-xs">{t.origen}</td>
                    <td className="whitespace-nowrap text-xs">{formatoFecha(t.fechaInicio)} – {t.fechaFin ? formatoFecha(t.fechaFin) : "indefinida"}</td>
                    <td className="max-w-xs text-xs">{t.motivo}{t.resolucion && ` · ${t.resolucion}`}</td>
                    <td className="text-xs">
                      {t.respaldo ? (
                        <button className="inline-flex items-center gap-1 text-marca-700 hover:underline" title={`SHA-256 ${t.respaldo.hashSha256}`}
                          onClick={() => { setErrorDescarga(null); abrirDocumento(t.respaldo!.id, t.respaldo!.nombreArchivo).catch(setErrorDescarga); }}>
                          <Download className="h-3.5 w-3.5" /> {t.respaldo.nombreArchivo}
                        </button>
                      ) : t.estado === "VIGENTE" ? (
                        <SubirArchivo compacto etiqueta="Adjuntar" onSubir={async (file) => {
                          await subirArchivo(`/transferencias-autoridad/${t.id}/respaldo`, file);
                          qc.invalidateQueries({ queryKey: ["transferencias"] });
                        }} />
                      ) : <span className="text-slate-400">—</span>}
                    </td>
                    <td><Insignia color={t.activaHoy ? "verde" : t.estado === "VIGENTE" ? "azul" : "gris"}>{t.activaHoy ? "activa" : t.estado.toLowerCase()}</Insignia></td>
                    <td className="text-right">{t.estado === "VIGENTE" && <button className="btn-secundario px-2 py-1 text-xs" onClick={() => setRevocar(t)}><Ban className="h-3.5 w-3.5" /> Revocar</button>}</td>
                  </tr>
                ))}
                {lista.data?.length === 0 && <tr><td colSpan={7} className="py-6 text-center text-slate-500">Sin transferencias.</td></tr>}
              </tbody>
            </table>
          )}
        </section>
      </div>
      {revocar && <Revocar t={revocar} onCerrar={() => { setRevocar(null); qc.invalidateQueries({ queryKey: ["transferencias"] }); }} />}
    </>
  );
}

function Revocar({ t, onCerrar }: { t: Transferencia; onCerrar: () => void }) {
  const [motivo, setMotivo] = useState("");
  const m = useMutation({ mutationFn: () => api(`/transferencias-autoridad/${t.id}/revocacion`, { metodo: "POST", cuerpo: { motivo } }), onSuccess: onCerrar });
  return (
    <Modal abierto titulo={`Revocar transferencia a ${t.destino}`} onCerrar={onCerrar}>
      <div className="space-y-3">
        <ErrorApiAviso error={m.error} />
        <div><label className="etiqueta" htmlFor="rm">Motivo</label><input id="rm" className="campo" value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Retorno del Director" /></div>
        <div className="flex justify-end gap-2"><button className="btn-secundario" onClick={onCerrar}>Cancelar</button><button className="btn-peligro" disabled={!motivo.trim() || m.isPending} onClick={() => m.mutate()}>Revocar</button></div>
      </div>
    </Modal>
  );
}
