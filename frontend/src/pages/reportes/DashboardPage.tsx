import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CircleAlert, Info } from "lucide-react";
import { api } from "../../api/cliente";
import type { Dashboard } from "../../api/tipos";
import { BarrasHorizontales, ColumnasAgrupadas, Indicador, TarjetaGrafico } from "../../components/graficos";
import { Cargando, Encabezado, ErrorApiAviso } from "../../components/ui";
import { ESTADOS_TRAMITE, formatoDuracion, formatoFecha } from "../../lib/estados";

const MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
const mesCorto = (aaaamm: string) => `${MESES[Number(aaaamm.slice(5, 7)) - 1]} ${aaaamm.slice(2, 4)}`;

/** CU-26 Consultar dashboard institucional. */
export default function DashboardPage() {
  const q = useQuery({ queryKey: ["dashboard"], queryFn: () => api<Dashboard>("/dashboard"), refetchInterval: 120_000 });
  if (q.isLoading) return <Cargando />;
  if (q.error || !q.data) return <ErrorApiAviso error={q.error} />;
  const d = q.data;
  const t = d.tramites;
  const estados = t.porEstado.map((e) => ({ etiqueta: ESTADOS_TRAMITE[e.clave]?.etiqueta ?? e.clave, valor: e.valor }));
  const tipos = t.porTipo.filter((x) => x.cerrados > 0).map((x) => ({
    etiqueta: x.tipo, valor: Math.round((x.aTiempo * 1000) / x.cerrados) / 10,
    detalle: `${x.aTiempo} de ${x.cerrados} cerrados a tiempo · promedio ${x.promedioDias} días`,
  }));

  return (
    <>
      <Encabezado titulo="Dashboard institucional" descripcion={`Indicadores al ${formatoFecha(d.fecha)} · Dirección de Carrera de Psicología`} />

      <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Indicador titulo="Trámites en curso" valor={String(t.enCurso)}
          detalle={t.vencidos > 0 ? <span className="inline-flex items-center gap-1 text-red-700"><AlertTriangle className="h-3 w-3" /> {t.vencidos} con plazo vencido</span> : "Ninguno vencido"}
          tono={t.vencidos > 0 ? "alerta" : undefined} />
        <Indicador titulo="Atención a tiempo" valor={`${t.porcentajeATiempo} %`} detalle={`Promedio de atención: ${t.promedioDiasAtencion} días`} />
        <Indicador titulo="Correspondencia del último mes" valor={String(d.correspondencia.ultimoMes)} detalle={`${d.correspondencia.sinAutorizar} oficio(s) sin autorizar`} />
        <Indicador titulo="Robot RPA" valor={`${d.rpa.tasaExito} % de éxito`}
          detalle={`${d.rpa.completadas} ejecución(es) completada(s) · ${formatoDuracion(d.rpa.duracionPromedioMs)} en promedio frente a ${d.rpa.tiempoManualMinutos} min manuales`} />
      </div>

      <div className="mb-6 grid gap-6 xl:grid-cols-2">
        <TarjetaGrafico titulo="Correspondencia por mes" subtitulo="Oficios recibidos y emitidos en los últimos 6 meses"
          tabla={{ columnas: ["Mes", "Recibidas", "Emitidas"], filas: d.correspondencia.porMes.map((m) => [mesCorto(m.mes), m.recibidas, m.emitidas]) }}>
          <ColumnasAgrupadas categorias={d.correspondencia.porMes.map((m) => mesCorto(m.mes))}
            series={[{ nombre: "Recibidas", valores: d.correspondencia.porMes.map((m) => m.recibidas) }, { nombre: "Emitidas", valores: d.correspondencia.porMes.map((m) => m.emitidas) }]} />
        </TarjetaGrafico>
        <TarjetaGrafico titulo="Trámites por estado" subtitulo={`${t.total} trámites registrados`}
          tabla={{ columnas: ["Estado", "Trámites"], filas: estados.map((e) => [e.etiqueta, e.valor]) }}>
          <BarrasHorizontales datos={estados} etiquetaAncho={110} />
        </TarjetaGrafico>
      </div>

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <TarjetaGrafico titulo="Porcentaje de trámites atendidos a tiempo, por tipo" subtitulo="Trámites cerrados dentro del plazo de referencia"
            tabla={{ columnas: ["Tipo", "% a tiempo", "Cerrados", "Promedio (días)"], filas: t.porTipo.map((x) => [x.tipo, x.cerrados ? Math.round((x.aTiempo * 1000) / x.cerrados) / 10 : 0, x.cerrados, x.promedioDias]) }}>
            <BarrasHorizontales datos={tipos} formato={(v) => `${v} %`} etiquetaAncho={190} />
          </TarjetaGrafico>
        </div>
        <div className="space-y-4">
          <section className="tarjeta p-5">
            <h2 className="mb-3 font-semibold">Alertas abiertas</h2>
            <ul className="space-y-2 text-sm">
              <li className="flex items-center justify-between"><span className="inline-flex items-center gap-2"><CircleAlert className="h-4 w-4 text-red-600" /> Nivel alto</span><strong>{d.alertas.altas}</strong></li>
              <li className="flex items-center justify-between"><span className="inline-flex items-center gap-2"><AlertTriangle className="h-4 w-4 text-amber-500" /> Nivel medio</span><strong>{d.alertas.medias}</strong></li>
              <li className="flex items-center justify-between"><span className="inline-flex items-center gap-2"><Info className="h-4 w-4 text-slate-500" /> Por plazo / predictivas</span><strong>{d.alertas.porPlazo} / {d.alertas.porPrediccion}</strong></li>
            </ul>
            <Link to="/alertas" className="mt-3 inline-block text-sm text-marca-700 hover:underline">Ver alertas →</Link>
          </section>
          <section className="tarjeta p-5 text-sm">
            <h2 className="mb-2 font-semibold">POA {d.poa.gestion}</h2>
            <p className="text-2xl font-semibold">{Number(d.poa.avance).toFixed(1)} %</p>
            <div className="my-2 h-2 overflow-hidden rounded bg-slate-100"><div className="h-full bg-marca-600" style={{ width: `${Math.min(100, Number(d.poa.avance))}%` }} /></div>
            <p className="text-slate-600">{d.poa.concluidas} de {d.poa.actividades} actividades concluidas · {d.poa.retrasadas} retrasada(s)</p>
          </section>
          <section className="tarjeta p-5 text-sm">
            <h2 className="mb-2 font-semibold">Periodo {d.academico.periodo ?? "—"}</h2>
            <p className="text-slate-600">{d.academico.inscritos} inscritos en {d.academico.grupos} grupos · ocupación {d.academico.ocupacion} %</p>
            {d.academico.gruposSinDocente > 0 && <p className="mt-1 text-amber-700">{d.academico.gruposSinDocente} grupo(s) sin docente</p>}
          </section>
        </div>
      </div>
    </>
  );
}
