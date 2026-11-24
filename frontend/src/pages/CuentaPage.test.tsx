// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CuentaPage from "./CuentaPage";

const api = vi.hoisted(() => vi.fn());
vi.mock("../api/cliente", async (original) => ({ ...(await original<typeof import("../api/cliente")>()), api }));
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({
    perfil: { id: 3, nombreUsuario: "apoyo", nombreCompleto: "Daniela Suárez", correo: "apoyo@ejemplo.edu.bo", rol: "APOYO_PROFESIONAL", cargo: "Apoyo" },
  }),
}));

function abrir() {
  render(<QueryClientProvider client={new QueryClient()}><CuentaPage /></QueryClientProvider>);
}

async function completar(actual: string, nueva: string, confirmacion: string) {
  await userEvent.type(screen.getByLabelText("Contraseña actual"), actual);
  await userEvent.type(screen.getByLabelText("Nueva contraseña"), nueva);
  await userEvent.type(screen.getByLabelText("Confirmar nueva contraseña"), confirmacion);
  await userEvent.click(screen.getByRole("button", { name: "Guardar" }));
}

describe("CuentaPage (cambio de contraseña)", () => {
  beforeEach(() => { api.mockReset(); });

  it("muestra los datos de la cuenta", () => {
    abrir();
    expect(screen.getByText("Daniela Suárez")).toBeInTheDocument();
    expect(screen.getByText("Apoyo Profesional")).toBeInTheDocument();
  });

  it("rechaza una confirmación distinta sin llamar a la API", async () => {
    abrir();
    await completar("Actual2026", "Nueva2026x", "Otra2026x");
    expect(screen.getByRole("alert")).toHaveTextContent("La confirmación no coincide con la nueva contraseña");
    expect(api).not.toHaveBeenCalled();
  });

  it("rechaza una contraseña sin números", async () => {
    abrir();
    await completar("Actual2026", "solamenteletras", "solamenteletras");
    expect(screen.getByRole("alert")).toHaveTextContent("al menos una letra y un número");
    expect(api).not.toHaveBeenCalled();
  });

  it("con datos válidos llama a la API y confirma el cambio", async () => {
    api.mockResolvedValue(undefined);
    abrir();
    await completar("Actual2026", "Nueva2026x", "Nueva2026x");
    expect(api).toHaveBeenCalledWith("/auth/cambiar-contrasena", {
      metodo: "POST", cuerpo: { contrasenaActual: "Actual2026", contrasenaNueva: "Nueva2026x" },
    });
    expect(await screen.findByRole("status")).toHaveTextContent("La contraseña se cambió correctamente.");
  });
});
