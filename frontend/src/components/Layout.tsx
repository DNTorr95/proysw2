import { useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  ArrowRightLeft, Award, CalendarDays, BarChart3, Bell, BellRing, Bot, ClipboardCheck, FileCheck, Files, FolderOpen, GraduationCap, History, KeyRound,
  LayoutDashboard, ListFilter, LogOut, Mail, Menu as IconoMenu, ScanText, ScrollText, Search, ShieldCheck, Target, Users, X,
  type LucideIcon,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/cliente";
import type { Notificacion } from "../api/tipos";
import { menuPara } from "../lib/menu";
import { NOMBRES_ROL } from "../api/tipos";

const ICONOS: Record<string, LucideIcon> = {
  ArrowRightLeft, Award, CalendarDays, LayoutDashboard, ListFilter, FileCheck, Files, Mail, FolderOpen, Bot, ClipboardCheck, History, GraduationCap, Target, ScanText,
  Search, BellRing, BarChart3, Users, ShieldCheck, ScrollText,
};

export default function Layout() {
  const { perfil, cerrarSesion } = useAuth();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [notifAbiertas, setNotifAbiertas] = useState(false);
  const notificaciones = useQuery({
    queryKey: ["notificaciones"],
    queryFn: () => api<Notificacion[]>("/notificaciones"),
    enabled: !!perfil,
    refetchInterval: 60_000,
  });
  const navigate = useNavigate();
  if (!perfil) return null;
  const secciones = menuPara(perfil.permisos);

  const salir = async () => {
    await cerrarSesion();
    navigate("/login", { replace: true });
  };

  const lateral = (
    <nav className="flex h-full flex-col bg-marca-900 text-marca-100">
      <div className="flex items-center gap-3 border-b border-white/10 px-5 py-4">
        <div className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-lg font-bold text-marca-800">Ψ</div>
        <div className="leading-tight">
          <p className="text-sm font-semibold text-white">Carrera de Psicología</p>
          <p className="text-xs text-marca-200">UAGRM · Gestión administrativa</p>
        </div>
      </div>
      <div className="flex-1 overflow-y-auto px-3 py-4">
        {secciones.map((s) => (
          <div key={s.titulo} className="mb-5">
            <p className="mb-1 px-2 text-[11px] font-semibold uppercase tracking-wider text-marca-200/70">{s.titulo}</p>
            {s.opciones.map((o) => {
              const Icono = ICONOS[o.icono] ?? LayoutDashboard;
              if (!o.disponible) {
                return (
                  <span key={o.ruta} title="Disponible en una próxima iteración"
                    className="flex cursor-not-allowed items-center gap-3 rounded-md px-2 py-2 text-sm text-marca-200/40">
                    <Icono className="h-4 w-4" /> {o.etiqueta}
                    <span className="ml-auto rounded bg-white/5 px-1.5 text-[10px]">pronto</span>
                  </span>
                );
              }
              return (
                <NavLink key={o.ruta} to={o.ruta} end={o.ruta === "/"} onClick={() => setMenuAbierto(false)}
                  className={({ isActive }) =>
                    `flex items-center gap-3 rounded-md px-2 py-2 text-sm transition ${
                      isActive ? "bg-white/10 font-medium text-white" : "hover:bg-white/5 hover:text-white"
                    }`}>
                  <Icono className="h-4 w-4" /> {o.etiqueta}
                </NavLink>
              );
            })}
          </div>
        ))}
      </div>
    </nav>
  );

  return (
    <div className="flex min-h-screen">
      <aside className="fixed inset-y-0 left-0 hidden w-64 lg:block">{lateral}</aside>
      {menuAbierto && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/50" onClick={() => setMenuAbierto(false)} />
          <aside className="absolute inset-y-0 left-0 w-64">{lateral}</aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col lg:pl-64">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6">
          <button className="rounded p-2 text-slate-600 hover:bg-slate-100 lg:hidden" onClick={() => setMenuAbierto(!menuAbierto)} aria-label="Abrir menú">
            {menuAbierto ? <X className="h-5 w-5" /> : <IconoMenu className="h-5 w-5" />}
          </button>
          <div className="flex-1" />

          <div className="relative">
            <button className="relative rounded-full p-2 text-slate-600 hover:bg-slate-100" onClick={() => setNotifAbiertas(!notifAbiertas)}
              aria-label={`Notificaciones (${notificaciones.data?.length ?? 0})`}>
              <Bell className="h-5 w-5" />
              {!!notificaciones.data?.length && (
                <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
                  {notificaciones.data.length > 9 ? "9+" : notificaciones.data.length}
                </span>
              )}
            </button>
            {notifAbiertas && (
              <div className="tarjeta absolute right-0 mt-2 max-h-96 w-80 overflow-y-auto text-sm">
                <p className="border-b border-slate-200 px-4 py-2 font-medium text-slate-700">Notificaciones</p>
                {!notificaciones.data?.length && <p className="px-4 py-3 text-slate-500">No tiene notificaciones pendientes.</p>}
                <ul className="divide-y divide-slate-100">
                  {notificaciones.data?.map((n, i) => (
                    <li key={`${n.ruta}-${i}`}>
                      <Link to={n.ruta} onClick={() => setNotifAbiertas(false)} className="block px-4 py-2 hover:bg-slate-50">
                        <p className={`font-medium ${n.tipo === "REVISION_RPA" ? "text-marca-800" : "text-red-700"}`}>{n.titulo}</p>
                        {n.detalle && <p className="text-xs text-slate-500">{n.detalle}</p>}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          <div className="hidden text-right sm:block">
            <p className="text-sm font-medium text-slate-800">{perfil.nombreCompleto}</p>
            <p className="text-xs text-slate-500">
              {NOMBRES_ROL[perfil.rol] ?? perfil.rol}
              {perfil.autoridadDelegada && <span className="ml-1 rounded bg-amber-100 px-1 text-amber-800" title={perfil.autoridadDelegada}>por delegación</span>}
            </p>
          </div>
          <Link to="/cuenta" className="rounded-full p-2 text-slate-600 hover:bg-slate-100" title="Cambiar contraseña" aria-label="Mi cuenta">
            <KeyRound className="h-5 w-5" />
          </Link>
          <button onClick={salir} className="rounded-full p-2 text-slate-600 hover:bg-slate-100" title="Cerrar sesión" aria-label="Cerrar sesión">
            <LogOut className="h-5 w-5" />
          </button>
        </header>

        <main className="flex-1 p-4 sm:p-6 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
