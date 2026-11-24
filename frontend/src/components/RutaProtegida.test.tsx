// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RutaProtegida from "./RutaProtegida";

// Estado de sesión simulado (lo que devolvería useAuth)
const sesion = vi.hoisted(() => ({ perfil: null as null | { id: number }, cargando: false, permisos: [] as string[] }));
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({
    perfil: sesion.perfil,
    cargando: sesion.cargando,
    tienePermiso: (...c: string[]) => c.some((x) => sesion.permisos.includes(x)),
  }),
}));

function abrir(permisos?: string[]) {
  render(
    <MemoryRouter initialEntries={["/usuarios"]}>
      <Routes>
        <Route path="/login" element={<p>Pantalla de login</p>} />
        <Route path="/usuarios" element={<RutaProtegida permisos={permisos}><p>Contenido protegido</p></RutaProtegida>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("RutaProtegida", () => {
  beforeEach(() => {
    sesion.perfil = { id: 1 };
    sesion.cargando = false;
    sesion.permisos = [];
  });

  it("sin sesión redirige al login", () => {
    sesion.perfil = null;
    abrir(["GESTIONAR_USUARIOS"]);
    expect(screen.getByText("Pantalla de login")).toBeInTheDocument();
    expect(screen.queryByText("Contenido protegido")).not.toBeInTheDocument();
  });

  it("mientras verifica la sesión muestra el indicador de carga", () => {
    sesion.cargando = true;
    abrir(["GESTIONAR_USUARIOS"]);
    expect(screen.getByText("Verificando sesión…")).toBeInTheDocument();
  });

  it("sin el permiso requerido muestra «Acceso no autorizado»", () => {
    sesion.permisos = ["CONSULTAR_TRAMITES"];
    abrir(["GESTIONAR_USUARIOS"]);
    expect(screen.getByRole("heading", { name: "Acceso no autorizado" })).toBeInTheDocument();
    expect(screen.queryByText("Contenido protegido")).not.toBeInTheDocument();
  });

  it("con al menos uno de los permisos muestra el contenido", () => {
    sesion.permisos = ["CONSULTAR_AUDITORIA"];
    abrir(["GESTIONAR_USUARIOS", "CONSULTAR_AUDITORIA"]);
    expect(screen.getByText("Contenido protegido")).toBeInTheDocument();
  });

  it("sin lista de permisos basta con tener sesión", () => {
    abrir();
    expect(screen.getByText("Contenido protegido")).toBeInTheDocument();
  });
});
