import { describe, expect, it } from "vitest";
import { menuPara } from "./menu";

const rutas = (permisos: string[]) => menuPara(permisos).flatMap((s) => s.opciones.map((o) => o.ruta));

describe("menú por permisos", () => {
  it("sin permisos solo muestra Inicio", () => {
    expect(rutas([])).toEqual(["/"]);
  });

  it("el Apoyo Profesional no ve administración", () => {
    const r = rutas(["REGISTRAR_TRAMITE", "EJECUTAR_RPA", "CONSULTAR_RPA", "GESTIONAR_CORRESPONDENCIA"]);
    expect(r).toContain("/rpa/ejecutar");
    expect(r).not.toContain("/usuarios");
    expect(r).not.toContain("/auditoria");
    expect(r).not.toContain("/rpa/revision");
  });

  it("el Director ve usuarios, roles y auditoría", () => {
    const r = rutas(["GESTIONAR_USUARIOS", "GESTIONAR_ROLES_PERMISOS", "CONSULTAR_AUDITORIA", "AUTORIZAR_ENVIO_RPA"]);
    expect(r).toEqual(expect.arrayContaining(["/usuarios", "/roles", "/auditoria", "/rpa/revision"]));
    expect(r).not.toContain("/rpa/ejecutar");
  });

  it("oculta las secciones sin opciones visibles", () => {
    expect(menuPara(["CONSULTAR_AUDITORIA"]).map((s) => s.titulo)).toEqual(["General", "Administración"]);
  });
});
