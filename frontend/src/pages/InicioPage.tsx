import { Link } from "react-router-dom";
import { Clock, ShieldCheck, UserCircle2 } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { NOMBRES_ROL } from "../api/tipos";
import { Encabezado, formatoFechaHora } from "../components/ui";
import { menuPara } from "../lib/menu";

/** Inicio / dashboard. En la iteración 1 muestra el perfil y los accesos disponibles; los indicadores llegan con CU-26. */
export default function InicioPage() {
  const { perfil } = useAuth();
  if (!perfil) return null;
  const accesos = menuPara(perfil.permisos).flatMap((s) => s.opciones).filter((o) => o.ruta !== "/");

  return (
    <>
      <Encabezado titulo={`Bienvenido(a), ${perfil.nombreCompleto.split(" ")[0]}`}
        descripcion="Panel institucional de la Dirección de Carrera de Psicología" />

      <div className="grid gap-4 md:grid-cols-3">
        <div className="tarjeta flex items-center gap-4 p-5">
          <UserCircle2 className="h-10 w-10 text-marca-600" />
          <div>
            <p className="text-xs uppercase text-slate-500">Rol</p>
            <p className="font-medium">{NOMBRES_ROL[perfil.rol] ?? perfil.rol}</p>
            {perfil.cargo && <p className="text-sm text-slate-500">{perfil.cargo}</p>}
          </div>
        </div>
        <div className="tarjeta flex items-center gap-4 p-5">
          <ShieldCheck className="h-10 w-10 text-marca-600" />
          <div>
            <p className="text-xs uppercase text-slate-500">Permisos asignados</p>
            <p className="text-2xl font-semibold">{perfil.permisos.length}</p>
          </div>
        </div>
        <div className="tarjeta flex items-center gap-4 p-5">
          <Clock className="h-10 w-10 text-marca-600" />
          <div>
            <p className="text-xs uppercase text-slate-500">Último acceso</p>
            <p className="font-medium">{formatoFechaHora(perfil.ultimoAcceso)}</p>
          </div>
        </div>
      </div>

      <div className="tarjeta mt-6 p-5">
        <h2 className="font-semibold">Indicadores institucionales</h2>
        <p className="mt-1 text-sm text-slate-500">
          El dashboard con trámites, correspondencia, ejecuciones RPA y alertas estará disponible en la iteración 5 (CU-26).
        </p>
      </div>

      <div className="tarjeta mt-6 p-5">
        <h2 className="font-semibold">Accesos de su rol</h2>
        <div className="mt-3 flex flex-wrap gap-2">
          {accesos.map((o) =>
            o.disponible ? (
              <Link key={o.ruta} to={o.ruta} className="btn-secundario">{o.etiqueta}</Link>
            ) : (
              <span key={o.ruta} className="btn-secundario cursor-default opacity-50" title="Próxima iteración">{o.etiqueta}</span>
            ),
          )}
        </div>
      </div>
    </>
  );
}
