import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { CalendarClock, Save } from "lucide-react";
import { api } from "../../api/cliente";
import type { Estudiante, TipoTramite, TramiteDetalle } from "../../api/tipos";
import { BuscadorEstudiante } from "../../components/documentos";
import { Aviso, Cargando, Encabezado, ErrorApiAviso } from "../../components/ui";

/** CU-09 Registrar trámite o solicitud estudiantil (incluye CU-10: requisitos presentados). */
export default function RegistrarTramitePage() {
  const navigate = useNavigate();
  const tipos = useQuery({ queryKey: ["tipos-tramite"], queryFn: () => api<TipoTramite[]>("/tipos-tramite") });
  const [estudiante, setEstudiante] = useState<Estudiante | null>(null);
  const [idTipo, setIdTipo] = useState("");
  const [prioridad, setPrioridad] = useState("MEDIA");
  const [observaciones, setObservaciones] = useState("");
  const [presentados, setPresentados] = useState<Set<number>>(new Set());

  const tipo = tipos.data?.find((t) => String(t.id) === idTipo);
  const limite = tipo ? new Date(Date.now() + tipo.plazoReferenciaDias * 86_400_000).toLocaleDateString("es-BO") : null;
  const faltantes = tipo?.requisitos.filter((r) => r.obligatorio && !presentados.has(r.id)).length ?? 0;

  const registrar = useMutation({
    mutationFn: () => api<TramiteDetalle>("/tramites", {
      metodo: "POST",
      cuerpo: { idEstudiante: estudiante?.id, idTipoTramite: Number(idTipo), prioridad, observaciones, requisitosPresentados: [...presentados] },
    }),
    onSuccess: (d) => navigate(`/tramites/${d.tramite.id}`, { state: { registrado: true } }),
  });

  const alternar = (id: number) => {
    const s = new Set(presentados);
    if (s.has(id)) s.delete(id); else s.add(id);
    setPresentados(s);
  };
  const enviar = (e: FormEvent) => { e.preventDefault(); registrar.mutate(); };

  if (tipos.isLoading) return <Cargando />;
  return (
    <>
      <Encabezado titulo="Registrar trámite" descripcion="Recepción de una solicitud estudiantil y de sus requisitos" />
      <form onSubmit={enviar} className="grid gap-6 lg:grid-cols-3" noValidate>
        <div className="space-y-6 lg:col-span-2">
          <div className="tarjeta space-y-4 p-5">
            <h2 className="font-semibold">1. Estudiante</h2>
            <BuscadorEstudiante seleccionado={estudiante} onSeleccionar={setEstudiante} />
          </div>

          <div className="tarjeta space-y-4 p-5">
            <h2 className="font-semibold">2. Tipo de trámite</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="etiqueta" htmlFor="tipo">Tipo</label>
                <select id="tipo" className="campo" value={idTipo} onChange={(e) => { setIdTipo(e.target.value); setPresentados(new Set()); }}>
                  <option value="">Seleccione…</option>
                  {tipos.data?.map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
                </select>
              </div>
              <div>
                <label className="etiqueta" htmlFor="prioridad">Prioridad</label>
                <select id="prioridad" className="campo" value={prioridad} onChange={(e) => setPrioridad(e.target.value)}>
                  <option value="ALTA">Alta</option><option value="MEDIA">Media</option><option value="BAJA">Baja</option>
                </select>
              </div>
            </div>
            {tipo && (
              <p className="flex items-center gap-2 text-sm text-slate-600">
                <CalendarClock className="h-4 w-4" /> Plazo de referencia: {tipo.plazoReferenciaDias} día(s) · fecha límite estimada {limite}
                {tipo.requiereDictamen && " · requiere dictamen técnico"}
              </p>
            )}
            <div>
              <label className="etiqueta" htmlFor="obs">Observaciones</label>
              <textarea id="obs" rows={3} className="campo" maxLength={2000} value={observaciones} onChange={(e) => setObservaciones(e.target.value)} />
            </div>
          </div>

          {tipo && (
            <div className="tarjeta space-y-3 p-5">
              <h2 className="font-semibold">3. Requisitos presentados</h2>
              <p className="text-sm text-slate-500">Marque los requisitos que el estudiante entregó. Los archivos pueden adjuntarse después, desde el seguimiento del trámite.</p>
              {tipo.requisitos.map((r) => (
                <label key={r.id} className="flex cursor-pointer items-start gap-3 rounded-md border border-slate-200 px-3 py-2 hover:bg-slate-50">
                  <input type="checkbox" className="mt-0.5 h-4 w-4 accent-marca-700" checked={presentados.has(r.id)} onChange={() => alternar(r.id)} />
                  <span className="text-sm">{r.descripcion} {r.obligatorio ? <span className="text-xs text-red-600">(obligatorio)</span> : <span className="text-xs text-slate-400">(opcional)</span>}</span>
                </label>
              ))}
            </div>
          )}
        </div>

        <div className="space-y-4">
          <div className="tarjeta sticky top-20 space-y-4 p-5">
            <h2 className="font-semibold">Resumen</h2>
            <dl className="space-y-2 text-sm">
              <div><dt className="text-slate-500">Estudiante</dt><dd>{estudiante?.nombreCompleto ?? "—"}</dd></div>
              <div><dt className="text-slate-500">Trámite</dt><dd>{tipo?.nombre ?? "—"}</dd></div>
              <div><dt className="text-slate-500">Requisitos</dt><dd>{tipo ? `${presentados.size} de ${tipo.requisitos.length} presentados` : "—"}</dd></div>
            </dl>
            {tipo && faltantes > 0 && (
              <Aviso tipo="info">Faltan {faltantes} requisito(s) obligatorio(s). Puede registrarse igual, pero no podrá derivarse a revisión hasta completarlos.</Aviso>
            )}
            <ErrorApiAviso error={registrar.error} />
            <button className="btn-primario w-full" disabled={!estudiante || !tipo || registrar.isPending}><Save className="h-4 w-4" /> Registrar trámite</button>
            <button type="button" className="btn-secundario w-full" onClick={() => navigate("/tramites")}>Cancelar</button>
          </div>
        </div>
      </form>
    </>
  );
}
