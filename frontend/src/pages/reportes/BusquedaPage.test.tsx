// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import BusquedaPage from "./BusquedaPage";

const api = vi.hoisted(() => vi.fn());
const permisos = vi.hoisted(() => ({ lista: [] as string[] }));
vi.mock("../../api/cliente", async (original) => ({ ...(await original<typeof import("../../api/cliente")>()), api }));
vi.mock("../../auth/AuthContext", () => ({
  useAuth: () => ({ tienePermiso: (...c: string[]) => c.some((x) => permisos.lista.includes(x)) }),
}));

function abrir() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter><BusquedaPage /></MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("BusquedaPage", () => {
  beforeEach(() => { api.mockReset(); });

  it("solo ofrece las modalidades que permite el rol", () => {
    permisos.lista = ["CONSULTAR_FILTROS"];
    abrir();
    expect(screen.getByRole("tab", { name: /Avanzada/ })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: /Semántica/ })).not.toBeInTheDocument();
    expect(screen.queryByText("Asistente")).not.toBeInTheDocument();
  });

  it("con todos los permisos muestra avanzada, semántica y el asistente", () => {
    permisos.lista = ["BUSQUEDA_AVANZADA", "BUSQUEDA_SEMANTICA", "USAR_ASISTENTE"];
    abrir();
    expect(screen.getByRole("tab", { name: /Avanzada/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Semántica/ })).toBeInTheDocument();
    expect(screen.getByText("Asistente")).toBeInTheDocument();
  });

  it("la búsqueda avanzada envía texto y tipos, y lista los resultados", async () => {
    permisos.lista = ["BUSQUEDA_AVANZADA"];
    api.mockResolvedValue({
      resultados: [{ tipo: "TRABAJO_GRADO", id: 1, titulo: "Ansiedad académica en estudiantes", detalle: "gestión 2025", fecha: "2025-01-01", ruta: "/institucional", relevancia: null }],
      tiposConsultados: ["TRABAJO_GRADO"], motor: "SQL",
    });
    abrir();
    await userEvent.click(screen.getByLabelText("Trabajo de grado"));
    await userEvent.type(screen.getByLabelText("Texto de búsqueda"), "ansiedad");
    await userEvent.click(screen.getByRole("button", { name: /Buscar/ }));
    expect(api).toHaveBeenCalledWith("/busqueda", { params: { texto: "ansiedad", tipos: "TRABAJO_GRADO", desde: "", hasta: "" } });
    expect(await screen.findByText("1 resultado(s) · fuentes consultadas: Trabajo de grado")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ansiedad académica en estudiantes" })).toHaveAttribute("href", "/institucional");
  });

  it("no busca con menos de 2 caracteres", async () => {
    permisos.lista = ["BUSQUEDA_AVANZADA"];
    abrir();
    await userEvent.type(screen.getByLabelText("Texto de búsqueda"), "a");
    expect(screen.getByRole("button", { name: /Buscar/ })).toBeDisabled();
    expect(api).not.toHaveBeenCalled();
  });
});
