// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ErrorApi } from "../api/cliente";
import { SubirArchivo } from "./documentos";

function archivo(nombre: string, bytes: number, tipo = "application/pdf") {
  const f = new File(["x"], nombre, { type: tipo });
  Object.defineProperty(f, "size", { value: bytes });
  return f;
}

function seleccionar(f: File) {
  const input = document.querySelector('input[type="file"]') as HTMLInputElement;
  return userEvent.upload(input, f, { applyAccept: false });
}

describe("SubirArchivo (validación local antes de enviar)", () => {
  it("rechaza extensiones no admitidas sin llamar al servidor", async () => {
    const subir = vi.fn();
    render(<SubirArchivo onSubir={subir} />);
    await seleccionar(archivo("notas.txt", 100, "text/plain"));
    expect(screen.getByRole("alert")).toHaveTextContent("Solo se admiten archivos PDF, JPG, PNG o DOCX.");
    expect(subir).not.toHaveBeenCalled();
  });

  it("rechaza archivos de más de 10 MB", async () => {
    const subir = vi.fn();
    render(<SubirArchivo onSubir={subir} />);
    await seleccionar(archivo("grande.pdf", 10 * 1024 * 1024 + 1));
    expect(screen.getByRole("alert")).toHaveTextContent("El archivo supera el tamaño máximo de 10 MB.");
    expect(subir).not.toHaveBeenCalled();
  });

  it("envía un PDF válido", async () => {
    const subir = vi.fn().mockResolvedValue(undefined);
    render(<SubirArchivo onSubir={subir} etiqueta="Subir documento" />);
    const f = archivo("acta.pdf", 2048);
    await seleccionar(f);
    expect(subir).toHaveBeenCalledWith(f);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("muestra el mensaje del servidor si el envío falla", async () => {
    const subir = vi.fn().mockRejectedValue(new ErrorApi(415, "El contenido del archivo no corresponde a la extensión .pdf"));
    render(<SubirArchivo onSubir={subir} />);
    await seleccionar(archivo("falso.pdf", 64));
    expect(await screen.findByRole("alert")).toHaveTextContent("El contenido del archivo no corresponde a la extensión .pdf");
  });
});
