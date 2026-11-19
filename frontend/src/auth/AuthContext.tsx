import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, guardarToken, obtenerToken, registrarAlExpirarSesion } from "../api/cliente";
import type { LoginResponse, Perfil } from "../api/tipos";

interface EstadoAuth {
  perfil: Perfil | null;
  cargando: boolean;
  iniciarSesion: (usuario: string, contrasena: string) => Promise<void>;
  cerrarSesion: () => Promise<void>;
  tienePermiso: (...codigos: string[]) => boolean;
  recargarPerfil: () => Promise<void>;
}

const AuthCtx = createContext<EstadoAuth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [perfil, setPerfil] = useState<Perfil | null>(null);
  const [cargando, setCargando] = useState(true);
  const queryClient = useQueryClient();

  const limpiar = useCallback(() => {
    guardarToken(null);
    setPerfil(null);
    queryClient.clear();
  }, [queryClient]);

  const recargarPerfil = useCallback(async () => {
    if (!obtenerToken()) {
      setPerfil(null);
      return;
    }
    try {
      setPerfil(await api<Perfil>("/auth/perfil"));
    } catch {
      limpiar();
    }
  }, [limpiar]);

  useEffect(() => {
    registrarAlExpirarSesion(limpiar);
    recargarPerfil().finally(() => setCargando(false));
  }, [limpiar, recargarPerfil]);

  // Refresca los permisos periódicamente: si el Director los cambia, el menú se actualiza.
  useEffect(() => {
    if (!perfil) return;
    const id = window.setInterval(recargarPerfil, 5 * 60 * 1000);
    return () => window.clearInterval(id);
  }, [perfil, recargarPerfil]);

  const iniciarSesion = useCallback(async (usuario: string, contrasena: string) => {
    const r = await api<LoginResponse>("/auth/login", { metodo: "POST", cuerpo: { nombreUsuario: usuario, contrasena } });
    guardarToken(r.token);
    setPerfil(r.perfil);
  }, []);

  const cerrarSesion = useCallback(async () => {
    try {
      await api("/auth/logout", { metodo: "POST" });
    } catch {
      /* se cierra la sesión local aunque falle el registro */
    }
    limpiar();
  }, [limpiar]);

  const tienePermiso = useCallback(
    (...codigos: string[]) => !!perfil && codigos.some((c) => perfil.permisos.includes(c)),
    [perfil],
  );

  const valor = useMemo(
    () => ({ perfil, cargando, iniciarSesion, cerrarSesion, tienePermiso, recargarPerfil }),
    [perfil, cargando, iniciarSesion, cerrarSesion, tienePermiso, recargarPerfil],
  );
  return <AuthCtx.Provider value={valor}>{children}</AuthCtx.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error("useAuth debe usarse dentro de AuthProvider");
  return ctx;
}
