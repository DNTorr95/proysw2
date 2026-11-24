import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Save, Undo2 } from "lucide-react";
import { api } from "../api/cliente";
import { NOMBRES_ROL, type Permiso, type Rol } from "../api/tipos";
import { useAuth } from "../auth/AuthContext";
import { Aviso, Cargando, Encabezado, ErrorApiAviso } from "../components/ui";

const PROTEGIDOS_DIRECTOR = ["GESTIONAR_ROLES_PERMISOS", "GESTIONAR_USUARIOS"];

/** CU-02 Gestionar roles y permisos institucionales. */
export default function RolesPage() {
  const qc = useQueryClient();
  const { recargarPerfil } = useAuth();
  const roles = useQuery({ queryKey: ["roles"], queryFn: () => api<Rol[]>("/roles") });
  const permisos = useQuery({ queryKey: ["permisos"], queryFn: () => api<Permiso[]>("/permisos") });
  const [idRol, setIdRol] = useState<number | null>(null);
  const [seleccion, setSeleccion] = useState<Set<string>>(new Set());
  const [descripcion, setDescripcion] = useState<string | null>(null);

  const rol = roles.data?.find((r) => r.id === idRol) ?? roles.data?.[0];
  useEffect(() => {
    if (rol) setSeleccion(new Set(rol.permisos));
  }, [rol]);

  const porModulo = useMemo(() => {
    const m = new Map<string, Permiso[]>();
    permisos.data?.forEach((p) => m.set(p.modulo, [...(m.get(p.modulo) ?? []), p]));
    return [...m.entries()];
  }, [permisos.data]);

  const guardar = useMutation({
    mutationFn: () => api<Rol>(`/roles/${rol!.id}/permisos`, { metodo: "PUT", cuerpo: { permisos: [...seleccion] } }),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["roles"] });
      await recargarPerfil();
    },
  });

  const guardarDescripcion = useMutation({
    mutationFn: (d: string) => api<Rol>(`/roles/${rol!.id}`, { metodo: "PUT", cuerpo: { descripcion: d.trim() } }),
    onSuccess: async () => {
      setDescripcion(null);
      await qc.invalidateQueries({ queryKey: ["roles"] });
    },
  });

  if (roles.isLoading || permisos.isLoading) return <Cargando />;
  if (roles.error || permisos.error) return <ErrorApiAviso error={roles.error ?? permisos.error} />;
  if (!rol) return null;

  const cambios = rol.permisos.length !== seleccion.size || rol.permisos.some((p) => !seleccion.has(p));
  const esDirector = rol.nombre === "DIRECTOR_CARRERA";
  const alternar = (codigo: string) => {
    const s = new Set(seleccion);
    if (s.has(codigo)) s.delete(codigo); else s.add(codigo);
    setSeleccion(s);
  };

  return (
    <>
      <Encabezado titulo="Roles y permisos" descripcion="Los tres roles son fijos; aquí se ajustan los permisos de cada uno. Los cambios rigen de inmediato." />

      <div className="mb-4 flex flex-wrap gap-2" role="tablist">
        {roles.data!.map((r) => (
          <button key={r.id} role="tab" aria-selected={r.id === rol.id}
            onClick={() => { setIdRol(r.id); guardar.reset(); setDescripcion(null); guardarDescripcion.reset(); }}
            className={r.id === rol.id ? "btn-primario" : "btn-secundario"}>
            {NOMBRES_ROL[r.nombre] ?? r.nombre}
            <span className="rounded bg-black/10 px-1.5 text-xs">{r.usuariosActivos} usuario(s)</span>
          </button>
        ))}
      </div>

      <div className="tarjeta p-5">
        {descripcion === null ? (
          <div className="flex items-start justify-between gap-3">
            <p className="text-sm text-slate-600">{rol.descripcion}</p>
            <button className="btn-secundario shrink-0 px-2 py-1 text-xs" onClick={() => { guardarDescripcion.reset(); setDescripcion(rol.descripcion ?? ""); }}>
              <Pencil className="h-3.5 w-3.5" /> Editar descripción
            </button>
          </div>
        ) : (
          <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); guardarDescripcion.mutate(descripcion); }}>
            <label className="etiqueta" htmlFor="rdesc">Descripción del rol</label>
            <input id="rdesc" className="campo" required maxLength={200} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
            <ErrorApiAviso error={guardarDescripcion.error} />
            <div className="flex justify-end gap-2">
              <button type="button" className="btn-secundario" onClick={() => setDescripcion(null)}>Cancelar</button>
              <button className="btn-primario" disabled={!descripcion.trim() || guardarDescripcion.isPending}>Guardar descripción</button>
            </div>
          </form>
        )}
        {guardarDescripcion.isSuccess && descripcion === null && <div className="mt-2"><Aviso tipo="exito">Descripción actualizada.</Aviso></div>}
        <div className="mt-3 space-y-2">
          <ErrorApiAviso error={guardar.error} />
          {guardar.isSuccess && !cambios && <Aviso tipo="exito">Permisos actualizados y registrados en la auditoría.</Aviso>}
        </div>

        <div className="mt-4 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {porModulo.map(([modulo, lista]) => (
            <fieldset key={modulo} className="rounded-md border border-slate-200 p-3">
              <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">{modulo}</legend>
              {lista.map((p) => {
                const bloqueado = esDirector && PROTEGIDOS_DIRECTOR.includes(p.codigo);
                return (
                  <label key={p.codigo} className="flex cursor-pointer items-start gap-2 rounded px-1 py-1.5 hover:bg-slate-50">
                    <input type="checkbox" className="mt-0.5 h-4 w-4 accent-marca-700" checked={seleccion.has(p.codigo)}
                      disabled={bloqueado} onChange={() => alternar(p.codigo)} />
                    <span className="text-sm">
                      {p.descripcion}
                      <span className="block text-[11px] text-slate-400">{p.codigo}{bloqueado && " · obligatorio para el Director"}</span>
                    </span>
                  </label>
                );
              })}
            </fieldset>
          ))}
        </div>

        <div className="mt-5 flex justify-end gap-2 border-t border-slate-200 pt-4">
          <button className="btn-secundario" disabled={!cambios} onClick={() => setSeleccion(new Set(rol.permisos))}><Undo2 className="h-4 w-4" /> Descartar</button>
          <button className="btn-primario" disabled={!cambios || guardar.isPending} onClick={() => guardar.mutate()}><Save className="h-4 w-4" /> Guardar cambios</button>
        </div>
      </div>
    </>
  );
}
