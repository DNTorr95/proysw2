import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, Eye, Info, Trash2 } from "lucide-react";
import { abrirDocumento, api, subirArchivo, type Pagina } from "../api/cliente";
import type { Archivo } from "../api/tipos";
import { useAuth } from "../auth/AuthContext";
import { SubirArchivo } from "../components/documentos";
import { Cargando, Encabezado, ErrorApiAviso, Modal, Paginacion, formatoFechaHora } from "../components/ui";
import { formatoTamano } from "../lib/estados";

const TIPOS: Record<string, string> = {
  "application/pdf": "PDF",
  "image/jpeg": "JPG",
  "image/png": "PNG",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "DOCX",
};

/** CU-07 Gestionar documentos digitales. */
export default function DocumentosPage() {
  const qc = useQueryClient();
  const [texto, setTexto] = useState("");
  const [tipoMime, setTipoMime] = useState("");
  const [pagina, setPagina] = useState(0);
  const [descripcion, setDescripcion] = useState("");
  const [eliminar, setEliminar] = useState<Archivo | null>(null);
  const [ficha, setFicha] = useState<number | null>(null);
  const [errorDescarga, setErrorDescarga] = useState<unknown>(null);
  const lista = useQuery({
    queryKey: ["documentos", texto, tipoMime, pagina],
    queryFn: () => api<Pagina<Archivo>>("/documentos", { params: { texto, tipoMime, pagina, tamano: 15 } }),
    placeholderData: keepPreviousData,
  });
  const borrar = useMutation({
    mutationFn: (id: number) => api(`/documentos/${id}`, { metodo: "DELETE" }),
    onSuccess: () => { setEliminar(null); qc.invalidateQueries({ queryKey: ["documentos"] }); },
  });
  const abrir = (a: Archivo, enLinea: boolean) => { setErrorDescarga(null); abrirDocumento(a.id, a.nombreArchivo, enLinea).catch(setErrorDescarga); };

  return (
    <>
      <Encabezado titulo="Documentos digitales" descripcion="PDF, JPG, PNG o DOCX de hasta 10 MB. Cada archivo se valida por su contenido y se registra con su hash SHA-256." />

      <div className="tarjeta mb-6 flex flex-wrap items-end gap-3 p-4">
        <div className="min-w-[16rem] flex-1">
          <label className="etiqueta" htmlFor="desc">Descripción del nuevo documento</label>
          <input id="desc" className="campo" maxLength={300} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} placeholder="Ej.: Resolución de Consejo de Carrera 015/2026" />
        </div>
        <SubirArchivo etiqueta="Subir documento" onSubir={async (f) => {
          await subirArchivo("/documentos", f, { descripcion: descripcion || undefined });
          setDescripcion("");
          qc.invalidateQueries({ queryKey: ["documentos"] });
        }} />
      </div>

      <div className="tarjeta">
        <div className="grid gap-3 border-b border-slate-200 p-3 sm:grid-cols-3">
          <input className="campo sm:col-span-2" placeholder="Nombre, descripción o inicio del hash" value={texto} onChange={(e) => { setTexto(e.target.value); setPagina(0); }} aria-label="Buscar" />
          <select className="campo" value={tipoMime} onChange={(e) => { setTipoMime(e.target.value); setPagina(0); }} aria-label="Tipo">
            <option value="">Todos los tipos</option>
            {Object.entries(TIPOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div className="px-3 pt-3"><ErrorApiAviso error={errorDescarga} /></div>
        {lista.isLoading ? <Cargando /> : lista.error ? <div className="p-4"><ErrorApiAviso error={lista.error} /></div> : (
          <>
            <div className="overflow-x-auto">
              <table className="tabla">
                <thead><tr><th>Nombre</th><th>Tipo</th><th>Tamaño</th><th>Contexto</th><th>Cargado</th><th>SHA-256</th><th className="text-right">Acciones</th></tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {lista.data?.contenido.map((a) => (
                    <tr key={a.id} className="hover:bg-slate-50">
                      <td className="max-w-xs"><p className="truncate font-medium">{a.nombreArchivo}</p>{a.descripcion && <p className="truncate text-xs text-slate-500">{a.descripcion}</p>}</td>
                      <td>{TIPOS[a.tipoMime] ?? a.tipoMime}</td>
                      <td className="whitespace-nowrap">{formatoTamano(a.tamanoBytes)}</td>
                      <td className="text-xs">{a.idCorrespondencia ? `Correspondencia #${a.idCorrespondencia}` : a.idExpediente ? `Expediente #${a.idExpediente}` : "—"}</td>
                      <td className="whitespace-nowrap text-xs">{formatoFechaHora(a.fechaCarga)}</td>
                      <td className="font-mono text-[11px]" title={a.hashSha256}>{a.hashSha256.substring(0, 16)}…</td>
                      <td>
                        <div className="flex justify-end gap-1">
                          <button className="rounded p-1.5 hover:bg-slate-100" title="Ficha y descripción" aria-label={`Ficha de ${a.nombreArchivo}`} onClick={() => setFicha(a.id)}><Info className="h-4 w-4" /></button>
                          <button className="rounded p-1.5 hover:bg-slate-100" title="Ver" aria-label="Ver" onClick={() => abrir(a, true)}><Eye className="h-4 w-4" /></button>
                          <button className="rounded p-1.5 hover:bg-slate-100" title="Descargar" aria-label="Descargar" onClick={() => abrir(a, false)}><Download className="h-4 w-4" /></button>
                          <button className="rounded p-1.5 text-red-600 hover:bg-red-50" title="Eliminar" aria-label="Eliminar" onClick={() => setEliminar(a)}><Trash2 className="h-4 w-4" /></button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {lista.data?.contenido.length === 0 && <tr><td colSpan={7} className="py-8 text-center text-slate-500">No hay documentos.</td></tr>}
                </tbody>
              </table>
            </div>
            {lista.data && <Paginacion pagina={lista.data.pagina} totalPaginas={lista.data.totalPaginas} total={lista.data.totalElementos} onCambiar={setPagina} />}
          </>
        )}
      </div>

      {ficha !== null && <FichaDocumento id={ficha} onCerrar={() => setFicha(null)} />}
      {eliminar && (
        <Modal abierto titulo="Eliminar documento" onCerrar={() => setEliminar(null)}>
          <div className="space-y-4">
            <p className="text-sm">¿Eliminar <strong>{eliminar.nombreArchivo}</strong>? La acción queda registrada en la auditoría. No se puede eliminar si es requisito de un trámite.</p>
            <ErrorApiAviso error={borrar.error} />
            <div className="flex justify-end gap-2">
              <button className="btn-secundario" onClick={() => setEliminar(null)}>Cancelar</button>
              <button className="btn-peligro" disabled={borrar.isPending} onClick={() => borrar.mutate(eliminar.id)}>Eliminar</button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}

/** Ficha de un documento (GET /documentos/{id}) con la descripción editable para quien gestiona documentos. */
function FichaDocumento({ id, onCerrar }: { id: number; onCerrar: () => void }) {
  const qc = useQueryClient();
  const { tienePermiso } = useAuth();
  const puedeEditar = tienePermiso("GESTIONAR_DOCUMENTOS");
  const doc = useQuery({ queryKey: ["documento", id], queryFn: () => api<Archivo>(`/documentos/${id}`) });
  const [descripcion, setDescripcion] = useState<string | null>(null);
  const guardar = useMutation({
    mutationFn: (d: string) => api<Archivo>(`/documentos/${id}`, { metodo: "PATCH", cuerpo: { descripcion: d.trim() || null } }),
    onSuccess: (a) => {
      qc.setQueryData(["documento", id], a);
      qc.invalidateQueries({ queryKey: ["documentos"] });
      setDescripcion(null);
    },
  });
  const a = doc.data;
  return (
    <Modal abierto titulo="Ficha del documento" onCerrar={onCerrar}>
      {doc.isLoading ? <Cargando /> : doc.error ? <ErrorApiAviso error={doc.error} /> : a && (
        <div className="space-y-4">
          <dl className="grid grid-cols-3 gap-x-3 gap-y-2 text-sm">
            <dt className="text-slate-500">Nombre</dt><dd className="col-span-2 break-all font-medium">{a.nombreArchivo}</dd>
            <dt className="text-slate-500">Tipo</dt><dd className="col-span-2">{TIPOS[a.tipoMime] ?? a.tipoMime} · {formatoTamano(a.tamanoBytes)}</dd>
            <dt className="text-slate-500">Cargado</dt><dd className="col-span-2">{formatoFechaHora(a.fechaCarga)} (usuario #{a.idUsuarioCarga})</dd>
            <dt className="text-slate-500">Contexto</dt><dd className="col-span-2">{a.idCorrespondencia ? `Correspondencia #${a.idCorrespondencia}` : a.idExpediente ? `Expediente #${a.idExpediente}` : "Documento suelto"}</dd>
            <dt className="text-slate-500">SHA-256</dt><dd className="col-span-2 break-all font-mono text-[11px]">{a.hashSha256}</dd>
          </dl>
          <div>
            <label className="etiqueta" htmlFor="fdesc">Descripción</label>
            {puedeEditar ? (
              <textarea id="fdesc" className="campo" rows={3} maxLength={300} value={descripcion ?? a.descripcion ?? ""} onChange={(e) => setDescripcion(e.target.value)} />
            ) : <p id="fdesc" className="text-sm">{a.descripcion || "—"}</p>}
          </div>
          <ErrorApiAviso error={guardar.error} />
          {guardar.isSuccess && descripcion === null && <p className="text-sm text-green-700" role="status">Descripción guardada.</p>}
          <div className="flex justify-end gap-2">
            <button className="btn-secundario" onClick={onCerrar}>Cerrar</button>
            {puedeEditar && <button className="btn-primario" disabled={descripcion === null || guardar.isPending} onClick={() => guardar.mutate(descripcion ?? "")}>Guardar descripción</button>}
          </div>
        </div>
      )}
    </Modal>
  );
}
