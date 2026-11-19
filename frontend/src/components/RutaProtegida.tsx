import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { ShieldAlert } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { Cargando } from "./ui";

/** Exige sesión iniciada y, opcionalmente, al menos uno de los permisos indicados. */
export default function RutaProtegida({ permisos, children }: { permisos?: string[]; children: ReactNode }) {
  const { perfil, cargando, tienePermiso } = useAuth();
  const ubicacion = useLocation();
  if (cargando) return <Cargando texto="Verificando sesión…" />;
  if (!perfil) return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />;
  if (permisos && permisos.length > 0 && !tienePermiso(...permisos)) {
    return (
      <div className="mx-auto mt-16 max-w-md text-center">
        <ShieldAlert className="mx-auto h-12 w-12 text-amber-500" />
        <h1 className="mt-4 text-xl font-semibold">Acceso no autorizado</h1>
        <p className="mt-2 text-sm text-slate-500">Su rol no tiene permiso para acceder a esta sección.</p>
      </div>
    );
  }
  return <>{children}</>;
}
