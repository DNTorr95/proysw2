import { useState, type FormEvent } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, KeyRound, Lock, Pencil, Plus, Unlock, UserX } from "lucide-react";
import { api, type Pagina } from "../api/cliente";
import { NOMBRES_ROL, type EstadoCuenta, type Rol, type Usuario } from "../api/tipos";
import { useAuth } from "../auth/AuthContext";
import { Cargando, Encabezado, ErrorApiAviso, Insignia, Modal, Paginacion, formatoFechaHora } from "../components/ui";

const COLOR_ESTADO: Record<EstadoCuenta, "verde" | "rojo" | "gris"> = { ACTIVO: "verde", BLOQUEADO: "rojo", INACTIVO: "gris" };

type Dialogo =
  | { tipo: "crear" }
  | { tipo: "ficha"; id: number }
  | { tipo: "editar"; usuario: Usuario }
  | { tipo: "estado"; usuario: Usuario; estado: EstadoCuenta }
  | { tipo: "contrasena"; usuario: Usuario }
  | null;

/** CU-01 (administración de cuentas): usuarios del sistema. */
export default function UsuariosPage() {
  const { perfil } = useAuth();
  const [texto, setTexto] = useState("");
  const [idRol, setIdRol] = useState("");
  const [estado, setEstado] = useState("");
  const [pagina, setPagina] = useState(0);
  const [dialogo, setDialogo] = useState<Dialogo>(null);

  const roles = useQuery({ queryKey: ["roles"], queryFn: () => api<Rol[]>("/roles") });
  const usuarios = useQuery({
    queryKey: ["usuarios", texto, idRol, estado, pagina],
    queryFn: () => api<Pagina<Usuario>>("/usuarios", { params: { texto, idRol, estado, pagina, tamano: 15 } }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <Encabezado titulo="Usuarios" descripcion="Cuentas de acceso del personal de la Dirección de Carrera"
        acciones={<button className="btn-primario" onClick={() => setDialogo({ tipo: "crear" })}><Plus className="h-4 w-4" /> Nuevo usuario</button>} />

      <div className="tarjeta">
        <div className="grid gap-3 border-b border-slate-200 p-3 sm:grid-cols-4">
          <input className="campo sm:col-span-2" placeholder="Buscar por usuario, nombre o CI" value={texto}
            onChange={(e) => { setTexto(e.target.value); setPagina(0); }} aria-label="Buscar" />
          <select className="campo" value={idRol} onChange={(e) => { setIdRol(e.target.value); setPagina(0); }} aria-label="Rol">
            <option value="">Todos los roles</option>
            {roles.data?.map((r) => <option key={r.id} value={r.id}>{NOMBRES_ROL[r.nombre] ?? r.nombre}</option>)}
          </select>
          <select className="campo" value={estado} onChange={(e) => { setEstado(e.target.value); setPagina(0); }} aria-label="Estado">
            <option value="">Todos los estados</option>
            <option value="ACTIVO">Activo</option>
            <option value="BLOQUEADO">Bloqueado</option>
            <option value="INACTIVO">Inactivo</option>
          </select>
        </div>

        {usuarios.isLoading ? <Cargando /> : usuarios.error ? <div className="p-4"><ErrorApiAviso error={usuarios.error} /></div> : (
          <>
            <div className="overflow-x-auto">
              <table className="tabla">
                <thead>
                  <tr><th>Usuario</th><th>Nombre</th><th>Rol / cargo</th><th>Estado</th><th>Último acceso</th><th className="text-right">Acciones</th></tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {usuarios.data?.contenido.map((u) => {
                    const propio = u.id === perfil?.id;
                    return (
                      <tr key={u.id} className="hover:bg-slate-50">
                        <td className="font-medium">{u.nombreUsuario}</td>
                        <td>{u.nombres} {u.apellidos}<div className="text-xs text-slate-500">CI {u.ci}</div></td>
                        <td>{NOMBRES_ROL[u.rol] ?? u.rol}<div className="text-xs text-slate-500">{u.cargo}</div></td>
                        <td>
                          <Insignia color={COLOR_ESTADO[u.estado]}>{u.estado}</Insignia>
                          {u.intentosFallidos > 0 && u.estado === "ACTIVO" && <div className="text-xs text-amber-600">{u.intentosFallidos} intento(s) fallido(s)</div>}
                        </td>
                        <td className="whitespace-nowrap">{formatoFechaHora(u.ultimoAcceso)}</td>
                        <td>
                          <div className="flex justify-end gap-1">
                            <BotonIcono titulo="Ver ficha" onClick={() => setDialogo({ tipo: "ficha", id: u.id })}><Eye className="h-4 w-4" /></BotonIcono>
                            <BotonIcono titulo="Editar" onClick={() => setDialogo({ tipo: "editar", usuario: u })}><Pencil className="h-4 w-4" /></BotonIcono>
                            <BotonIcono titulo="Restablecer contraseña" onClick={() => setDialogo({ tipo: "contrasena", usuario: u })}><KeyRound className="h-4 w-4" /></BotonIcono>
                            {u.estado !== "ACTIVO" ? (
                              <BotonIcono titulo={u.estado === "BLOQUEADO" ? "Desbloquear" : "Activar"} onClick={() => setDialogo({ tipo: "estado", usuario: u, estado: "ACTIVO" })}><Unlock className="h-4 w-4" /></BotonIcono>
                            ) : !propio && (
                              <>
                                <BotonIcono titulo="Bloquear" onClick={() => setDialogo({ tipo: "estado", usuario: u, estado: "BLOQUEADO" })}><Lock className="h-4 w-4" /></BotonIcono>
                                <BotonIcono titulo="Desactivar" onClick={() => setDialogo({ tipo: "estado", usuario: u, estado: "INACTIVO" })}><UserX className="h-4 w-4" /></BotonIcono>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                  {usuarios.data?.contenido.length === 0 && <tr><td colSpan={6} className="py-8 text-center text-slate-500">No se encontraron usuarios.</td></tr>}
                </tbody>
              </table>
            </div>
            {usuarios.data && <Paginacion pagina={usuarios.data.pagina} totalPaginas={usuarios.data.totalPaginas} total={usuarios.data.totalElementos} onCambiar={setPagina} />}
          </>
        )}
      </div>

      {(dialogo?.tipo === "crear" || dialogo?.tipo === "editar") && (
        <FormularioUsuario usuario={dialogo.tipo === "editar" ? dialogo.usuario : undefined} roles={roles.data ?? []}
          esPropio={dialogo.tipo === "editar" && dialogo.usuario.id === perfil?.id} onCerrar={() => setDialogo(null)} />
      )}
      {dialogo?.tipo === "ficha" && <FichaUsuario id={dialogo.id} onEditar={(u) => setDialogo({ tipo: "editar", usuario: u })} onCerrar={() => setDialogo(null)} />}
      {dialogo?.tipo === "estado" && <DialogoEstado usuario={dialogo.usuario} estado={dialogo.estado} onCerrar={() => setDialogo(null)} />}
      {dialogo?.tipo === "contrasena" && <DialogoContrasena usuario={dialogo.usuario} onCerrar={() => setDialogo(null)} />}
    </>
  );
}

function BotonIcono({ titulo, onClick, children }: { titulo: string; onClick: () => void; children: React.ReactNode }) {
  return <button onClick={onClick} title={titulo} aria-label={titulo} className="rounded p-1.5 text-slate-600 hover:bg-slate-100 hover:text-marca-700">{children}</button>;
}

function FormularioUsuario({ usuario, roles, esPropio, onCerrar }: { usuario?: Usuario; roles: Rol[]; esPropio: boolean; onCerrar: () => void }) {
  const qc = useQueryClient();
  const [f, setF] = useState({
    ci: usuario?.ci ?? "", nombres: usuario?.nombres ?? "", apellidos: usuario?.apellidos ?? "", correo: usuario?.correo ?? "",
    telefono: usuario?.telefono ?? "", cargo: usuario?.cargo ?? "", area: usuario?.area ?? "",
    nombreUsuario: usuario?.nombreUsuario ?? "", contrasenaInicial: "", idRol: usuario ? String(usuario.idRol) : "",
  });
  const cambiar = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });

  const guardar = useMutation({
    mutationFn: () => usuario
      ? api(`/usuarios/${usuario.id}`, { metodo: "PUT", cuerpo: { nombres: f.nombres, apellidos: f.apellidos, correo: f.correo, telefono: f.telefono, cargo: f.cargo, area: f.area, idRol: Number(f.idRol) } })
      : api("/usuarios", { metodo: "POST", cuerpo: { ...f, idRol: Number(f.idRol) } }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["usuarios"] }); qc.invalidateQueries({ queryKey: ["roles"] }); onCerrar(); },
  });
  const enviar = (e: FormEvent) => { e.preventDefault(); guardar.mutate(); };

  return (
    <Modal abierto titulo={usuario ? `Editar usuario ${usuario.nombreUsuario}` : "Nuevo usuario"} onCerrar={onCerrar} ancho="max-w-2xl">
      <form onSubmit={enviar} className="space-y-4" noValidate>
        <ErrorApiAviso error={guardar.error} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo etiqueta="CI" valor={f.ci} onChange={cambiar("ci")} deshabilitado={!!usuario} />
          <Campo etiqueta="Correo" tipo="email" valor={f.correo} onChange={cambiar("correo")} />
          <Campo etiqueta="Nombres" valor={f.nombres} onChange={cambiar("nombres")} />
          <Campo etiqueta="Apellidos" valor={f.apellidos} onChange={cambiar("apellidos")} />
          <Campo etiqueta="Teléfono" valor={f.telefono} onChange={cambiar("telefono")} />
          <div>
            <label className="etiqueta" htmlFor="rol">Rol</label>
            <select id="rol" className="campo" value={f.idRol} onChange={cambiar("idRol")} disabled={esPropio}>
              <option value="">Seleccione…</option>
              {roles.map((r) => <option key={r.id} value={r.id}>{NOMBRES_ROL[r.nombre] ?? r.nombre}</option>)}
            </select>
            {esPropio && <p className="mt-1 text-xs text-slate-500">No puede cambiar su propio rol.</p>}
          </div>
          <Campo etiqueta="Cargo" valor={f.cargo} onChange={cambiar("cargo")} />
          <Campo etiqueta="Área" valor={f.area} onChange={cambiar("area")} />
          {!usuario && (
            <>
              <Campo etiqueta="Nombre de usuario" valor={f.nombreUsuario} onChange={cambiar("nombreUsuario")} ayuda="Minúsculas, números, punto o guion" />
              <Campo etiqueta="Contraseña inicial" tipo="password" valor={f.contrasenaInicial} onChange={cambiar("contrasenaInicial")} ayuda="Mín. 8 caracteres, con letra y número" />
            </>
          )}
        </div>
        <div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
          <button type="button" className="btn-secundario" onClick={onCerrar}>Cancelar</button>
          <button className="btn-primario" disabled={guardar.isPending}>Guardar</button>
        </div>
      </form>
    </Modal>
  );
}

function Campo({ etiqueta, valor, onChange, tipo = "text", deshabilitado, ayuda }: {
  etiqueta: string; valor: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void; tipo?: string; deshabilitado?: boolean; ayuda?: string;
}) {
  const id = etiqueta.toLowerCase().replace(/\s+/g, "-");
  return (
    <div>
      <label className="etiqueta" htmlFor={id}>{etiqueta}</label>
      <input id={id} type={tipo} className="campo" value={valor} onChange={onChange} disabled={deshabilitado} autoComplete="off" />
      {ayuda && <p className="mt-1 text-xs text-slate-500">{ayuda}</p>}
    </div>
  );
}

function DialogoEstado({ usuario, estado, onCerrar }: { usuario: Usuario; estado: EstadoCuenta; onCerrar: () => void }) {
  const qc = useQueryClient();
  const [motivo, setMotivo] = useState("");
  const accion = estado === "ACTIVO" ? (usuario.estado === "BLOQUEADO" ? "Desbloquear" : "Activar") : estado === "BLOQUEADO" ? "Bloquear" : "Desactivar";
  const m = useMutation({
    mutationFn: () => api(`/usuarios/${usuario.id}/estado`, { metodo: "PATCH", cuerpo: { estado, motivo } }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["usuarios"] }); onCerrar(); },
  });
  return (
    <Modal abierto titulo={`${accion} cuenta de ${usuario.nombreUsuario}`} onCerrar={onCerrar}>
      <div className="space-y-4">
        <ErrorApiAviso error={m.error} />
        <div>
          <label className="etiqueta" htmlFor="motivo">Motivo (queda registrado en la auditoría)</label>
          <textarea id="motivo" className="campo" rows={3} maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        </div>
        <div className="flex justify-end gap-2">
          <button className="btn-secundario" onClick={onCerrar}>Cancelar</button>
          <button className={estado === "ACTIVO" ? "btn-primario" : "btn-peligro"} disabled={!motivo.trim() || m.isPending} onClick={() => m.mutate()}>{accion}</button>
        </div>
      </div>
    </Modal>
  );
}

function DialogoContrasena({ usuario, onCerrar }: { usuario: Usuario; onCerrar: () => void }) {
  const [nueva, setNueva] = useState("");
  const m = useMutation({
    mutationFn: () => api(`/usuarios/${usuario.id}/restablecer-contrasena`, { metodo: "POST", cuerpo: { contrasenaNueva: nueva } }),
    onSuccess: onCerrar,
  });
  return (
    <Modal abierto titulo={`Restablecer contraseña de ${usuario.nombreUsuario}`} onCerrar={onCerrar}>
      <div className="space-y-4">
        <ErrorApiAviso error={m.error} />
        <div>
          <label className="etiqueta" htmlFor="nueva">Nueva contraseña</label>
          <input id="nueva" type="password" className="campo" autoComplete="new-password" value={nueva} onChange={(e) => setNueva(e.target.value)} />
          <p className="mt-1 text-xs text-slate-500">Comunique la contraseña al usuario por un medio seguro y pídale que la cambie.</p>
        </div>
        <div className="flex justify-end gap-2">
          <button className="btn-secundario" onClick={onCerrar}>Cancelar</button>
          <button className="btn-primario" disabled={!nueva || m.isPending} onClick={() => m.mutate()}>Restablecer</button>
        </div>
      </div>
    </Modal>
  );
}

/** Ficha de la cuenta con los datos actuales del servidor (GET /usuarios/{id}). */
function FichaUsuario({ id, onEditar, onCerrar }: { id: number; onEditar: (u: Usuario) => void; onCerrar: () => void }) {
  const u = useQuery({ queryKey: ["usuario", id], queryFn: () => api<Usuario>(`/usuarios/${id}`) });
  const d = u.data;
  const fila = (etiqueta: string, valor: React.ReactNode) => (
    <>
      <dt className="text-slate-500">{etiqueta}</dt>
      <dd className="col-span-2">{valor ?? "—"}</dd>
    </>
  );
  return (
    <Modal abierto titulo={d ? `Cuenta ${d.nombreUsuario}` : "Cuenta de usuario"} onCerrar={onCerrar}>
      {u.isLoading ? <Cargando /> : u.error ? <ErrorApiAviso error={u.error} /> : d && (
        <div className="space-y-4">
          <dl className="grid grid-cols-3 gap-x-3 gap-y-2 text-sm">
            {fila("Nombre", `${d.nombres} ${d.apellidos}`)}
            {fila("CI", d.ci)}
            {fila("Rol", NOMBRES_ROL[d.rol] ?? d.rol)}
            {fila("Cargo y área", [d.cargo, d.area].filter(Boolean).join(" · ") || null)}
            {fila("Correo", d.correo)}
            {fila("Teléfono", d.telefono)}
            {fila("Estado", <Insignia color={COLOR_ESTADO[d.estado]}>{d.estado}</Insignia>)}
            {fila("Intentos fallidos", d.intentosFallidos)}
            {fila("Último acceso", d.ultimoAcceso ? formatoFechaHora(d.ultimoAcceso) : "Nunca")}
            {fila("Creada", formatoFechaHora(d.fechaCreacion))}
          </dl>
          <div className="flex justify-end gap-2">
            <button className="btn-secundario" onClick={onCerrar}>Cerrar</button>
            <button className="btn-primario" onClick={() => onEditar(d)}><Pencil className="h-4 w-4" /> Editar</button>
          </div>
        </div>
      )}
    </Modal>
  );
}
