// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ErrorApi } from "../api/cliente";
import { Aviso, ErrorApiAviso, Modal, Paginacion } from "./ui";

describe("Paginacion", () => {
  it("muestra el total y deshabilita «anterior» en la primera página", async () => {
    const cambiar = vi.fn();
    render(<Paginacion pagina={0} totalPaginas={3} total={42} onCambiar={cambiar} />);
    expect(screen.getByText("42 registro(s)")).toBeInTheDocument();
    expect(screen.getByText("Página 1 de 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Página anterior" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Página siguiente" }));
    expect(cambiar).toHaveBeenCalledWith(1);
  });

  it("deshabilita «siguiente» en la última página", () => {
    render(<Paginacion pagina={2} totalPaginas={3} total={42} onCambiar={() => {}} />);
    expect(screen.getByRole("button", { name: "Página siguiente" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Página anterior" })).toBeEnabled();
  });

  it("sin resultados indica «Página 0 de 0»", () => {
    render(<Paginacion pagina={0} totalPaginas={0} total={0} onCambiar={() => {}} />);
    expect(screen.getByText("Página 0 de 0")).toBeInTheDocument();
  });
});

describe("Avisos", () => {
  it("un aviso de error usa role=alert y uno de éxito role=status", () => {
    render(<><Aviso>Falló</Aviso><Aviso tipo="exito">Listo</Aviso></>);
    expect(screen.getByRole("alert")).toHaveTextContent("Falló");
    expect(screen.getByRole("status")).toHaveTextContent("Listo");
  });

  it("ErrorApiAviso muestra el mensaje de la API y el detalle por campo", () => {
    render(<ErrorApiAviso error={new ErrorApi(400, "Datos inválidos", { asunto: "El asunto es obligatorio" })} />);
    const alerta = screen.getByRole("alert");
    expect(alerta).toHaveTextContent("Datos inválidos");
    expect(alerta).toHaveTextContent("El asunto es obligatorio");
  });

  it("ErrorApiAviso con un error desconocido muestra un mensaje genérico", () => {
    render(<ErrorApiAviso error={new Error("x")} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Ocurrió un error inesperado");
  });

  it("ErrorApiAviso sin error no muestra nada", () => {
    const { container } = render(<ErrorApiAviso error={null} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe("Modal", () => {
  it("se cierra con Escape y con el botón Cerrar", async () => {
    const cerrar = vi.fn();
    render(<Modal abierto titulo="Editar oficio" onCerrar={cerrar}><p>Formulario</p></Modal>);
    expect(screen.getByRole("dialog", { name: "Editar oficio" })).toHaveTextContent("Formulario");
    await userEvent.keyboard("{Escape}");
    await userEvent.click(screen.getByRole("button", { name: "Cerrar" }));
    expect(cerrar).toHaveBeenCalledTimes(2);
  });

  it("cerrado no se dibuja", () => {
    render(<Modal abierto={false} titulo="Oculto" onCerrar={() => {}}><p>x</p></Modal>);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
