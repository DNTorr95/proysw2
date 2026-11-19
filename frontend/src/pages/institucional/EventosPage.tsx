import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarPlus, Pencil } from "lucide-react";
import { api } from "../../api/cliente";
import type { ActividadPoa, Evento, PoaResumen } from "../../api/tipos";
import { useAuth } from "../../auth/AuthContext";
import { Cargando, Encabezado, ErrorApiAviso, Insignia, Modal } from "../../components/ui";
import { formatoFecha, hoyLocal } from "../../lib/estados";

const TIPOS = { TALLER: "Taller", SEMINARIO: "Seminario", EVENTO: "Evento" } as const;

/** CU-20 Gestionar eventos y talleres institucionales. */
export default function EventosPage() {
  const qc = useQueryClient();
  const gestiona = useAuth().tienePermiso("GESTIONAR_EVENTOS");
  const [editar, setEditar] = useState<Evento | "nuevo" | null>(null);
  const lista = useQuery({ queryKey: ["eventos"], queryFn: () => api<Evento[]>("/eventos") });
  const hoy = hoyLocal();
  const proximos = lista.data?.filter((e) => e.fecha >= hoy).sort((a, b) => a.fecha.localeCompare(b.fecha)) ?? [];
  const pasados = lista.data?.filter((e) => e.fecha < hoy) ?? [];

  const Tabla = ({ eventos }: { eventos: Evento[] }) => (
    <table className="tabla">
      <thead><tr><th>Fecha</th><th>Evento</th><th>Tipo</th><th>Responsable</th><th>Actividad POA</th><th /></tr></thead>
      <tbody className="divide-y divide-slate-100">
        {eventos.map((e) => (
          <tr key={e.id}><td className="whitespace-nowrap">{formatoFecha(e.fecha)}</td><td className="font-medium">{e.nombre}</td>
            <td><Insignia color={e.tipo === "TALLER" ? "azul" : e.tipo === "SEMINARIO" ? "verde" : "gris"}>{TIPOS[e.tipo as keyof typeof TIPOS]}</Insignia></td>
            <td className="text-xs">{e.responsable}</td><td className="max-w-xs text-xs">{e.actividad ?? "—"}</td>
            <td className="text-right">{gestiona && <button className="rounded p-1.5 hover:bg-slate-100" aria-label={`Editar ${e.nombre}`} onClick={() => setEditar(e)}><Pencil className="h-4 w-4" /></button>}</td></tr>
        ))}
        {eventos.length === 0 && <tr><td colSpan={6} className="py-6 text-center text-slate-500">Sin eventos.</td></tr>}
      </tbody>
    </table>
  );

  return (
    <>
      <Encabezado titulo="Eventos y talleres" descripcion="Actividades institucionales vinculadas al POA"
        acciones={gestiona && <button className="btn-primario" onClick={() => setEditar("nuevo")}><CalendarPlus className="h-4 w-4" /> Nuevo evento</button>} />
      {lista.isLoading ? <Cargando /> : (
        <div className="space-y-6">
          <section className="tarjeta"><div className="border-b border-slate-200 px-4 py-2 font-semibold">Próximos</div><Tabla eventos={proximos} /></section>
          <section className="tarjeta"><div className="border-b border-slate-200 px-4 py-2 font-semibold">Realizados</div><Tabla eventos={pasados} /></section>
        </div>
      )}
      {editar && <FormEvento evento={editar === "nuevo" ? undefined : editar} onCerrar={() => { setEditar(null); qc.invalidateQueries({ queryKey: ["eventos"] }); }} />}
    </>
  );
}

function FormEvento({ evento, onCerrar }: { evento?: Evento; onCerrar: () => void }) {
  const poas = useQuery({ queryKey: ["poas"], queryFn: () => api<PoaResumen[]>("/poa") });
  const gestion = poas.data?.[0]?.gestion;
  const poa = useQuery({ queryKey: ["poa", gestion], queryFn: () => api<{ actividades: ActividadPoa[] }>(`/poa/${gestion}`), enabled: !!gestion });
  const [f, setF] = useState({ nombre: evento?.nombre ?? "", tipo: evento?.tipo ?? "TALLER", fecha: evento?.fecha ?? "", responsable: evento?.responsable ?? "", idActividad: evento?.idActividad ? String(evento.idActividad) : "" });
  const m = useMutation({
    mutationFn: () => api(evento ? `/eventos/${evento.id}` : "/eventos", { metodo: evento ? "PUT" : "POST", cuerpo: { ...f, idActividad: f.idActividad ? Number(f.idActividad) : null } }),
    onSuccess: onCerrar,
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  return (
    <Modal abierto titulo={evento ? "Editar evento" : "Nuevo evento"} onCerrar={onCerrar}>
      <div className="space-y-3">
        <ErrorApiAviso error={m.error} />
        <div><label className="etiqueta" htmlFor="en">Nombre</label><input id="en" className="campo" value={f.nombre} onChange={set("nombre")} /></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="etiqueta" htmlFor="et">Tipo</label><select id="et" className="campo" value={f.tipo} onChange={set("tipo")}>{Object.entries(TIPOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
          <div><label className="etiqueta" htmlFor="ef">Fecha</label><input id="ef" type="date" className="campo" value={f.fecha} onChange={set("fecha")} /></div>
        </div>
        <div><label className="etiqueta" htmlFor="er">Responsable</label><input id="er" className="campo" value={f.responsable} onChange={set("responsable")} /></div>
        <div><label className="etiqueta" htmlFor="ea">Actividad del POA {gestion}</label><select id="ea" className="campo" value={f.idActividad} onChange={set("idActividad")}><option value="">Ninguna</option>{poa.data?.actividades.map((a) => <option key={a.id} value={a.id}>{a.descripcion}</option>)}</select></div>
        <div className="flex justify-end gap-2"><button className="btn-secundario" onClick={onCerrar}>Cancelar</button><button className="btn-primario" disabled={!f.nombre || !f.fecha || m.isPending} onClick={() => m.mutate()}>Guardar</button></div>
      </div>
    </Modal>
  );
}
