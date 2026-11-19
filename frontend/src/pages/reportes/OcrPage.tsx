import { useEffect, useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, ScanText } from "lucide-react";
import { ErrorApi, api, obtenerUrlBlob, type Pagina } from "../../api/cliente";
import type { DocumentoOcr, ExtraccionOcr } from "../../api/tipos";
import { useAuth } from "../../auth/AuthContext";
import { Aviso, Cargando, Encabezado, ErrorApiAviso, Insignia, Modal, Paginacion, formatoFechaHora } from "../../components/ui";

const ESTADOS = { PENDIENTE: "gris", EXTRAIDO: "ambar", VALIDADO: "verde" } as const;

/** CU-21 Procesar documentos mediante OCR · CU-22 Revisar y validar el texto extraído. */
export default function OcrPage() {
  const qc = useQueryClient();
  const { tienePermiso } = useAuth();
  const [texto, setTexto] = useState("");
  const [estado, setEstado] = useState("");
  const [pagina, setPagina] = useState(0);
  const [abierto, setAbierto] = useState<DocumentoOcr | null>(null);
  const lista = useQuery({
    queryKey: ["ocr", texto, estado, pagina],
    queryFn: () => api<Pagina<DocumentoOcr>>("/ocr/documentos", { params: { texto, estado, pagina, tamano: 15 } }),
    placeholderData: keepPreviousData,
  });
  const procesar = useMutation({
    mutationFn: (d: DocumentoOcr) => api<ExtraccionOcr>(`/ocr/documentos/${d.archivo.id}/procesamiento`, { metodo: "POST" }),
    onSuccess: (_, d) => { qc.invalidateQueries({ queryKey: ["ocr"] }); setAbierto({ ...d, estadoOcr: "EXTRAIDO" }); },
  });
  return (
    <>
      <Encabezado titulo="Digitalización con OCR" descripcion="Extracción de texto de documentos escaneados (PDF, JPG o PNG). El texto queda pendiente hasta que una persona lo revisa y valida." />
      <div className="tarjeta">
        <div className="grid gap-3 border-b border-slate-200 p-3 sm:grid-cols-3">
          <input className="campo sm:col-span-2" placeholder="Nombre o descripción" value={texto} onChange={(e) => { setTexto(e.target.value); setPagina(0); }} aria-label="Buscar" />
          <select className="campo" value={estado} onChange={(e) => { setEstado(e.target.value); setPagina(0); }} aria-label="Estado OCR">
            <option value="">Todos</option><option value="PENDIENTE">Pendientes de OCR</option><option value="EXTRAIDO">Por validar</option><option value="VALIDADO">Validados</option>
          </select>
        </div>
        <div className="px-3 pt-3"><ErrorApiAviso error={procesar.error} /></div>
        {lista.isLoading ? <Cargando /> : (
          <>
            <table className="tabla">
              <thead><tr><th>Documento</th><th>Tipo</th><th>Cargado</th><th>Estado OCR</th><th>Confianza</th><th /></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {lista.data?.contenido.map((d) => (
                  <tr key={d.archivo.id}>
                    <td className="max-w-xs"><p className="truncate font-medium">{d.archivo.nombreArchivo}</p>{d.archivo.descripcion && <p className="truncate text-xs text-slate-500">{d.archivo.descripcion}</p>}</td>
                    <td className="text-xs">{d.archivo.tipoMime === "application/pdf" ? "PDF" : "Imagen"}</td>
                    <td className="whitespace-nowrap text-xs">{formatoFechaHora(d.archivo.fechaCarga)}</td>
                    <td><Insignia color={ESTADOS[d.estadoOcr]}>{d.estadoOcr === "EXTRAIDO" ? "por validar" : d.estadoOcr.toLowerCase()}</Insignia></td>
                    <td className="text-xs">{d.confianza !== null ? `${Number(d.confianza).toFixed(0)} %` : "—"}</td>
                    <td className="whitespace-nowrap text-right">
                      {d.estadoOcr === "PENDIENTE" && tienePermiso("PROCESAR_OCR") && (
                        <button className="btn-primario px-2 py-1 text-xs" disabled={procesar.isPending} onClick={() => procesar.mutate(d)}><ScanText className="h-3.5 w-3.5" /> {procesar.isPending && procesar.variables?.archivo.id === d.archivo.id ? "Procesando…" : "Procesar"}</button>
                      )}
                      {d.estadoOcr !== "PENDIENTE" && <button className="btn-secundario px-2 py-1 text-xs" onClick={() => setAbierto(d)}>{d.estadoOcr === "EXTRAIDO" ? "Revisar y validar" : "Ver texto"}</button>}
                    </td>
                  </tr>
                ))}
                {lista.data?.contenido.length === 0 && <tr><td colSpan={6} className="py-8 text-center text-slate-500">No hay documentos.</td></tr>}
              </tbody>
            </table>
            {lista.data && <Paginacion pagina={lista.data.pagina} totalPaginas={lista.data.totalPaginas} total={lista.data.totalElementos} onCambiar={setPagina} />}
          </>
        )}
      </div>
      {abierto && <Revision documento={abierto} onCerrar={() => { setAbierto(null); qc.invalidateQueries({ queryKey: ["ocr"] }); }} />}
    </>
  );
}

function Revision({ documento, onCerrar }: { documento: DocumentoOcr; onCerrar: () => void }) {
  const qc = useQueryClient();
  const { tienePermiso } = useAuth();
  const id = documento.archivo.id;
  const ext = useQuery({ queryKey: ["ocr-extraccion", id], queryFn: () => api<ExtraccionOcr>(`/ocr/documentos/${id}`) });
  const [texto, setTexto] = useState("");
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => { if (ext.data) setTexto(ext.data.texto); }, [ext.data]);
  useEffect(() => {
    let u: string | null = null;
    obtenerUrlBlob(`/documentos/${id}/contenido?enLinea=true`).then((x) => { u = x; setUrl(x); }).catch(() => setUrl(null));
    return () => { if (u) URL.revokeObjectURL(u); };
  }, [id]);
  const validar = useMutation({
    mutationFn: () => api<ExtraccionOcr>(`/ocr/documentos/${id}/validacion`, { metodo: "PUT", cuerpo: { texto } }),
    onSuccess: (d) => qc.setQueryData(["ocr-extraccion", id], d),
  });
  const reprocesar = useMutation({
    mutationFn: () => api<ExtraccionOcr>(`/ocr/documentos/${id}/procesamiento`, { metodo: "POST", params: { reprocesar: "true" } }),
    onSuccess: (d) => qc.setQueryData(["ocr-extraccion", id], d),
  });
  const e = ext.data;
  const esImagen = documento.archivo.tipoMime.startsWith("image/");
  return (
    <Modal abierto titulo={`OCR · ${documento.archivo.nombreArchivo}`} onCerrar={onCerrar} ancho="max-w-6xl">
      {ext.isLoading || !e ? (ext.error instanceof ErrorApi ? <ErrorApiAviso error={ext.error} /> : <Cargando />) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="min-h-[50vh] overflow-hidden rounded-md border border-slate-200 bg-slate-50">
            {url ? (esImagen ? <img src={url} alt="Documento original" className="h-full w-full object-contain" /> : <iframe title="Documento original" src={url} className="h-[60vh] w-full" />) : <Cargando texto="Cargando original…" />}
          </div>
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <Insignia color={e.validado ? "verde" : "ambar"}>{e.validado ? "Validado" : "Pendiente de validación"}</Insignia>
              <span className="text-slate-600">Confianza del motor: {Number(e.confianza).toFixed(0)} %</span>
              {e.validado && <span className="text-xs text-slate-500">· validado el {formatoFechaHora(e.fechaValidacion)}</span>}
            </div>
            {Number(e.confianza) < 70 && !e.validado && <Aviso tipo="info">Confianza baja: revise con atención el texto frente al original.</Aviso>}
            <ErrorApiAviso error={validar.error ?? reprocesar.error} />
            <textarea className="campo h-[48vh] font-mono text-xs" value={texto} onChange={(ev) => setTexto(ev.target.value)} readOnly={!tienePermiso("VALIDAR_OCR")} aria-label="Texto extraído" />
            <div className="flex flex-wrap justify-end gap-2">
              {tienePermiso("PROCESAR_OCR") && <button className="btn-secundario" disabled={reprocesar.isPending} onClick={() => reprocesar.mutate()}>Volver a procesar</button>}
              {tienePermiso("VALIDAR_OCR") && <button className="btn-primario" disabled={!texto.trim() || validar.isPending} onClick={() => validar.mutate()}><CheckCircle2 className="h-4 w-4" /> {e.validado ? "Guardar corrección" : "Validar texto"}</button>}
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
