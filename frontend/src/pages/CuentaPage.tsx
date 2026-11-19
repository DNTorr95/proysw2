import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { api } from "../api/cliente";
import { useAuth } from "../auth/AuthContext";
import { NOMBRES_ROL } from "../api/tipos";
import { Aviso, Encabezado, ErrorApiAviso } from "../components/ui";

/** CU-01: datos de la cuenta y cambio de contraseña. */
export default function CuentaPage() {
  const { perfil } = useAuth();
  const [actual, setActual] = useState("");
  const [nueva, setNueva] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [errorLocal, setErrorLocal] = useState<string | null>(null);

  const cambiar = useMutation({
    mutationFn: () => api("/auth/cambiar-contrasena", { metodo: "POST", cuerpo: { contrasenaActual: actual, contrasenaNueva: nueva } }),
    onSuccess: () => {
      setActual("");
      setNueva("");
      setConfirmacion("");
    },
  });

  const enviar = (e: FormEvent) => {
    e.preventDefault();
    setErrorLocal(null);
    if (nueva !== confirmacion) return setErrorLocal("La confirmación no coincide con la nueva contraseña");
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(nueva))
      return setErrorLocal("La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número");
    cambiar.mutate();
  };

  if (!perfil) return null;
  return (
    <>
      <Encabezado titulo="Mi cuenta" descripcion="Datos de acceso y cambio de contraseña" />
      <div className="grid gap-6 lg:grid-cols-2">
        <div className="tarjeta p-5">
          <h2 className="font-semibold">Datos de la cuenta</h2>
          <dl className="mt-4 grid grid-cols-3 gap-y-3 text-sm">
            <dt className="text-slate-500">Usuario</dt><dd className="col-span-2">{perfil.nombreUsuario}</dd>
            <dt className="text-slate-500">Nombre</dt><dd className="col-span-2">{perfil.nombreCompleto}</dd>
            <dt className="text-slate-500">Correo</dt><dd className="col-span-2">{perfil.correo ?? "—"}</dd>
            <dt className="text-slate-500">Rol</dt><dd className="col-span-2">{NOMBRES_ROL[perfil.rol] ?? perfil.rol}</dd>
            <dt className="text-slate-500">Cargo</dt><dd className="col-span-2">{perfil.cargo ?? "—"}</dd>
          </dl>
        </div>

        <form onSubmit={enviar} className="tarjeta space-y-4 p-5" noValidate>
          <h2 className="font-semibold">Cambiar contraseña</h2>
          {errorLocal && <Aviso>{errorLocal}</Aviso>}
          <ErrorApiAviso error={cambiar.error} />
          {cambiar.isSuccess && <Aviso tipo="exito">La contraseña se cambió correctamente.</Aviso>}
          <div>
            <label className="etiqueta" htmlFor="actual">Contraseña actual</label>
            <input id="actual" type="password" className="campo" autoComplete="current-password" value={actual} onChange={(e) => setActual(e.target.value)} />
          </div>
          <div>
            <label className="etiqueta" htmlFor="nueva">Nueva contraseña</label>
            <input id="nueva" type="password" className="campo" autoComplete="new-password" value={nueva} onChange={(e) => setNueva(e.target.value)} />
            <p className="mt-1 text-xs text-slate-500">Mínimo 8 caracteres, con al menos una letra y un número.</p>
          </div>
          <div>
            <label className="etiqueta" htmlFor="confirmacion">Confirmar nueva contraseña</label>
            <input id="confirmacion" type="password" className="campo" autoComplete="new-password" value={confirmacion} onChange={(e) => setConfirmacion(e.target.value)} />
          </div>
          <button className="btn-primario" disabled={cambiar.isPending || !actual || !nueva}>Guardar</button>
        </form>
      </div>
    </>
  );
}
