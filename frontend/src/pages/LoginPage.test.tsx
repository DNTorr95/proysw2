// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ErrorApi } from "../api/cliente";
import LoginPage from "./LoginPage";

const iniciarSesion = vi.hoisted(() => vi.fn());
vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ perfil: null, iniciarSesion }) }));

function abrir() {
  render(
    <MemoryRouter initialEntries={["/login"]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<p>Inicio del sistema</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("LoginPage", () => {
  beforeEach(() => { iniciarSesion.mockReset(); });

  it("el botón Ingresar se habilita solo con usuario y contraseña", async () => {
    abrir();
    const boton = screen.getByRole("button", { name: /Ingresar/ });
    expect(boton).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Usuario"), "apoyo");
    expect(boton).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Contraseña"), "secreta1");
    expect(boton).toBeEnabled();
  });

  it("con credenciales correctas entra al inicio (usuario recortado)", async () => {
    iniciarSesion.mockResolvedValue(undefined);
    abrir();
    await userEvent.type(screen.getByLabelText("Usuario"), "  director ");
    await userEvent.type(screen.getByLabelText("Contraseña"), "clave2026");
    await userEvent.click(screen.getByRole("button", { name: /Ingresar/ }));
    expect(iniciarSesion).toHaveBeenCalledWith("director", "clave2026");
    expect(await screen.findByText("Inicio del sistema")).toBeInTheDocument();
  });

  it("con error muestra el mensaje del servidor y borra la contraseña", async () => {
    iniciarSesion.mockRejectedValue(new ErrorApi(401, "Usuario o contraseña incorrectos. Intentos restantes antes del bloqueo: 4"));
    abrir();
    await userEvent.type(screen.getByLabelText("Usuario"), "coordinador");
    await userEvent.type(screen.getByLabelText("Contraseña"), "equivocada");
    await userEvent.click(screen.getByRole("button", { name: /Ingresar/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Intentos restantes antes del bloqueo: 4");
    expect(screen.getByLabelText("Contraseña")).toHaveValue("");
    expect(screen.queryByText("Inicio del sistema")).not.toBeInTheDocument();
  });
});
