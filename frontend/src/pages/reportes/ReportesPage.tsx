import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { FileSpreadsheet, FileText } from "lucide-react";
import { api, obtenerUrlBlob } from "../../api/cliente";
import type { Periodo, PoaResumen } from "../../api/tipos";
import { Encabezado, ErrorApiAviso } from "../../components/ui";
import { hoyLocal } from "../../lib/estados";

const REPORTES = [
  { tipo: "tramites", titulo: "Trámites estudiantiles", descripcion: "Trámites recibidos en el periodo, con estado, días de atención y si se cerraron fuera de plazo.", filtro: "fechas" },
  { tipo: "correspondencia", titulo: "Correspondencia", descripcion: "Oficios recibidos y emitidos con dependencia, estado y autorización.", filtro: "fechas" },
  { tipo: "rpa", titulo: "Ejecuciones RPA", descripcion: "Ejecuciones del robot con duración, registros, errores y tasa de éxito.", filtro: "fechas" },
  { tipo: "carga-docente", titulo: "Carga docente", descripcion: "Asignaciones del periodo académico con horas por docente y antecedentes.", filtro: "periodo" },
  { tipo: "poa", titulo: "Seguimiento del POA", descripcion: "Actividades del POA con su avance y estado.", filtro: "gestion" },
] as const;

/** CU-27 Generar y exportar reportes (PDF y XLSX). */
export default function ReportesPage() {
  const periodos = useQuery({ queryKey: ["periodos"], queryFn: () => api<Periodo[]>("/periodos") });
  const poas = useQuery({ queryKey: ["poas"], queryFn: () => api<PoaResumen[]>("/poa"), retry: false });
  const [desde, setDesde] = useState(`${hoyLocal().slice(0, 4)}-01-01`);
  const [hasta, setHasta] = useState(hoyLocal());
  const [idPeriodo, setIdPeriodo] = useState("");
  const [gestion, setGestion] = useState("");
  const [generando, setGenerando] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  const descargar = async (tipo: string, formato: "pdf" | "xlsx", filtro: string) => {
    setError(null);
    setGenerando(`${tipo}-${formato}`);
    const p = new URLSearchParams({ formato });
    if (filtro === "fechas") { p.set("desde", desde); p.set("hasta", hasta); }
    if (filtro === "periodo" && idPeriodo) p.set("idPeriodo", idPeriodo);
    if (filtro === "gestion" && gestion) p.set("gestion", gestion);
    try {
      const url = await obtenerUrlBlob(`/reportes/${tipo}?${p}`);
      const a = document.createElement("a");
      a.href = url;
      a.download = `reporte-${tipo}-${hoyLocal()}.${formato}`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 30_000);
    } catch (e) {
      setError(e);
    } finally {
      setGenerando(null);
    }
  };

  return (
    <>
      <Encabezado titulo="Reportes" descripcion="Exportación en PDF (con formato institucional) o en Excel (XLSX) para análisis" />
      <div className="tarjeta mb-6 grid gap-3 p-4 sm:grid-cols-4">
        <div><label className="etiqueta" htmlFor="rd">Desde</label><input id="rd" type="date" className="campo" value={desde} onChange={(e) => setDesde(e.target.value)} /></div>
        <div><label className="etiqueta" htmlFor="rh">Hasta</label><input id="rh" type="date" className="campo" value={hasta} onChange={(e) => setHasta(e.target.value)} /></div>
        <div><label className="etiqueta" htmlFor="rp">Periodo académico</label>
          <select id="rp" className="campo" value={idPeriodo} onChange={(e) => setIdPeriodo(e.target.value)}><option value="">Periodo abierto</option>{periodos.data?.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}</select></div>
        <div><label className="etiqueta" htmlFor="rg">Gestión del POA</label>
          <select id="rg" className="campo" value={gestion} onChange={(e) => setGestion(e.target.value)}><option value="">Última</option>{poas.data?.map((p) => <option key={p.id} value={p.gestion}>{p.gestion}</option>)}</select></div>
      </div>
      <div className="mb-4"><ErrorApiAviso error={error} /></div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {REPORTES.map((r) => (
          <section key={r.tipo} className="tarjeta flex flex-col p-5">
            <h2 className="font-semibold">{r.titulo}</h2>
            <p className="mt-1 flex-1 text-sm text-slate-600">{r.descripcion}</p>
            <p className="mt-2 text-xs text-slate-500">Filtro: {r.filtro === "fechas" ? "rango de fechas" : r.filtro === "periodo" ? "periodo académico" : "gestión del POA"}</p>
            <div className="mt-3 flex gap-2">
              <button className="btn-secundario" disabled={generando !== null} onClick={() => descargar(r.tipo, "pdf", r.filtro)}><FileText className="h-4 w-4" /> {generando === `${r.tipo}-pdf` ? "Generando…" : "PDF"}</button>
              <button className="btn-secundario" disabled={generando !== null} onClick={() => descargar(r.tipo, "xlsx", r.filtro)}><FileSpreadsheet className="h-4 w-4" /> {generando === `${r.tipo}-xlsx` ? "Generando…" : "Excel"}</button>
            </div>
          </section>
        ))}
      </div>
    </>
  );
}
