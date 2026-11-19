import { describe, expect, it } from "vitest";
import { PERMISOS_BUSQUEDA, menuPara } from "./menu";

const rutas = (permisos: string[]) => menuPara(permisos).flatMap((s) => s.opciones.map((o) => o.ruta));

// Permisos reales del rol APOYO_PROFESIONAL en el seed (V3): no tiene BUSQUEDA_* ni USAR_ASISTENTE.
const APOYO = [
  "CONSULTAR_ACADEMICO", "CONSULTAR_CORRESPONDENCIA", "CONSULTAR_EXPEDIENTES", "CONSULTAR_FILTROS", "CONSULTAR_INSTITUCIONAL",
  "CONSULTAR_RPA", "CONSULTAR_TRAMITES", "EJECUTAR_RPA", "GESTIONAR_CORRESPONDENCIA", "GESTIONAR_DOCUMENTOS", "GESTIONAR_REQUISITOS",
  "GESTIONAR_TITULADOS", "GESTIONAR_TRABAJOS_GRADO", "PROCESAR_OCR", "REGISTRAR_TRAMITE", "SEGUIMIENTO_CORRESPONDENCIA", "VALIDAR_OCR",
];

describe("acceso a la búsqueda (HU-22)", () => {
  it("el Apoyo Profesional ve «Búsqueda y asistente» por CONSULTAR_FILTROS, como en el backend", () => {
    expect(PERMISOS_BUSQUEDA).toContain("CONSULTAR_FILTROS");
    expect(rutas(APOYO)).toContain("/busqueda");
  });

  it("sin ningún permiso de búsqueda ni de consulta con filtros no aparece", () => {
    expect(rutas(["EJECUTAR_RPA", "GESTIONAR_DOCUMENTOS", "PROCESAR_OCR"])).not.toContain("/busqueda");
  });
});
