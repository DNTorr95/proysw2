import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Loader2, LogIn } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { ErrorApiAviso } from "../components/ui";

/** CU-01: inicio de sesión. */
export default function LoginPage() {
  const { perfil, iniciarSesion } = useAuth();
  const navigate = useNavigate();
  const ubicacion = useLocation();
  const [usuario, setUsuario] = useState("");
  const [contrasena, setContrasena] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [enviando, setEnviando] = useState(false);

  if (perfil) return <Navigate to="/" replace />;

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await iniciarSesion(usuario.trim(), contrasena);
      const destino = (ubicacion.state as { desde?: string } | null)?.desde ?? "/";
      navigate(destino, { replace: true });
    } catch (err) {
      setError(err);
      setContrasena("");
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="flex min-h-screen">
      <div className="hidden flex-1 flex-col justify-between bg-marca-900 p-12 text-white lg:flex">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-white text-2xl font-bold text-marca-800">Ψ</div>
          <div>
            <p className="font-semibold">Universidad Autónoma Gabriel René Moreno</p>
            <p className="text-sm text-marca-200">Facultad de Humanidades · Carrera de Psicología</p>
          </div>
        </div>
        <div>
          <h1 className="text-3xl font-semibold leading-snug">Sistema Web de Gestión Administrativa con Automatización Robótica de Procesos</h1>
          <p className="mt-4 max-w-lg text-marca-200">
            Trámites estudiantiles, correspondencia, documentos, información académica, POA y reportes en un solo lugar.
          </p>
        </div>
        <p className="text-xs text-marca-200/70">Dirección de Carrera de Psicología</p>
      </div>

      <div className="flex flex-1 items-center justify-center p-6">
        <form onSubmit={enviar} className="w-full max-w-sm space-y-5" noValidate>
          <div>
            <h2 className="text-2xl font-semibold text-slate-900">Iniciar sesión</h2>
            <p className="mt-1 text-sm text-slate-500">Ingrese con su cuenta institucional.</p>
          </div>
          <ErrorApiAviso error={error} />
          <div>
            <label htmlFor="usuario" className="etiqueta">Usuario</label>
            <input id="usuario" className="campo" autoComplete="username" autoFocus required maxLength={50}
              value={usuario} onChange={(e) => setUsuario(e.target.value)} />
          </div>
          <div>
            <label htmlFor="contrasena" className="etiqueta">Contraseña</label>
            <input id="contrasena" type="password" className="campo" autoComplete="current-password" required maxLength={72}
              value={contrasena} onChange={(e) => setContrasena(e.target.value)} />
          </div>
          <button type="submit" className="btn-primario w-full" disabled={enviando || !usuario || !contrasena}>
            {enviando ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogIn className="h-4 w-4" />} Ingresar
          </button>
          <p className="text-xs text-slate-500">
            Por seguridad, la cuenta se bloquea tras 5 intentos fallidos. Para desbloquearla, comuníquese con la Dirección de Carrera.
          </p>
        </form>
      </div>
    </div>
  );
}
