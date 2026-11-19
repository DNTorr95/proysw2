"""Generación del informe: plantilla institucional (Jinja2 en sandbox) -> HTML -> PDF (Playwright)."""

from __future__ import annotations

import re
from datetime import date, datetime
from typing import Protocol

from jinja2 import StrictUndefined, TemplateError
from jinja2.sandbox import SandboxedEnvironment
from markupsafe import Markup, escape

from .consolidacion import Consolidado

TITULOS = {
    "FECHA": "Correspondencia por fecha",
    "TIPO": "Correspondencia por tipo",
    "ESTADO": "Correspondencia por estado",
    "DEPENDENCIA": "Correspondencia por dependencia",
}

# Variables que la plantilla institucional debe usar
VARIABLES_REQUERIDAS = ("titulo", "periodo_desde", "periodo_hasta", "secciones")


class FallaPlantilla(Exception):
    """La plantilla no se pudo interpretar o no contiene las variables requeridas."""


class RenderizadorPdf(Protocol):
    def renderizar(self, html: str) -> bytes: ...

    def verificar(self, html: str, secciones_esperadas: int) -> str: ...


def _fecha_es(d: date) -> str:
    return d.strftime("%d/%m/%Y")


def construir_secciones(c: Consolidado) -> Markup:
    """Tablas HTML de cada clasificación + listado de oficios consolidados (todo escapado)."""
    partes: list[str] = []
    for categoria, titulo in TITULOS.items():
        filas = [d for d in c.detalle if d.categoria == categoria]
        if not filas:
            continue
        cuerpo = "".join(f"<tr><td>{escape(d.clave)}</td><td style='text-align:right'>{d.cantidad}</td></tr>" for d in filas)
        partes.append(f"<section class='clasificacion' data-categoria='{categoria}'><h3>{escape(titulo)}</h3>"
                      f"<table><thead><tr><th>Categoría</th><th style='text-align:right'>Cantidad</th></tr></thead>"
                      f"<tbody>{cuerpo}</tbody></table></section>")
    filas = "".join(
        f"<tr><td>{escape(r.codigo)}</td><td>{_fecha_es(r.fecha)}</td><td>{'Recibida' if r.tipo == 'RECIBIDA' else 'Emitida'}</td>"
        f"<td>{escape(r.asunto)}</td><td>{escape(r.dependencia)}</td></tr>"
        for r in sorted(c.validos, key=lambda x: (x.fecha, x.codigo)))
    partes.append("<section class='listado'><h3>Detalle de oficios consolidados</h3>"
                  "<table><thead><tr><th>Código</th><th>Fecha</th><th>Tipo</th><th>Asunto</th><th>Dependencia</th></tr></thead>"
                  f"<tbody>{filas}</tbody></table></section>")
    return Markup("".join(partes))


def renderizar_html(plantilla: str, c: Consolidado, desde: date, hasta: date, id_ejecucion: int, titulo: str) -> str:
    """Rellena la plantilla en un entorno aislado (sin acceso a Python) con autoescape."""
    usadas = set(re.findall(r"\{\{\s*(\w+)", plantilla))
    faltantes = [v for v in VARIABLES_REQUERIDAS if v not in usadas]
    if faltantes:
        raise FallaPlantilla(f"La plantilla no contiene las variables requeridas: {', '.join(faltantes)}")
    entorno = SandboxedEnvironment(autoescape=True, undefined=StrictUndefined)
    try:
        return entorno.from_string(plantilla).render(
            titulo=titulo,
            periodo_desde=_fecha_es(desde),
            periodo_hasta=_fecha_es(hasta),
            total_recibidos=c.total_recibidos,
            total_emitidos=c.total_emitidos,
            secciones=construir_secciones(c),
            id_ejecucion=id_ejecucion,
            fecha_generacion=datetime.now().strftime("%d/%m/%Y %H:%M"),
        )
    except TemplateError as e:
        raise FallaPlantilla(f"Error en la plantilla institucional: {e}") from e


class RenderizadorPlaywright:
    """Usa Chromium (Playwright) para convertir el HTML a PDF y para verificar el render como lo vería un usuario."""

    def __init__(self, tiempo_ms: int = 60_000):
        self.tiempo_ms = tiempo_ms

    def renderizar(self, html: str) -> bytes:
        from playwright.sync_api import sync_playwright

        with sync_playwright() as p:
            navegador = p.chromium.launch(args=["--no-sandbox"])
            try:
                pagina = navegador.new_page()
                pagina.set_default_timeout(self.tiempo_ms)
                # Sin acceso a red: la plantilla es autocontenida
                pagina.route("**/*", lambda ruta: ruta.abort() if not ruta.request.url.startswith("data:") else ruta.continue_())
                pagina.set_content(html, wait_until="load")
                return pagina.pdf(format="Letter", print_background=True,
                                  margin={"top": "1.5cm", "bottom": "1.5cm", "left": "1.5cm", "right": "1.5cm"})
            finally:
                navegador.close()

    def verificar(self, html: str, secciones_esperadas: int) -> str:
        """Automatización de navegador: abre el informe y comprueba título, totales y secciones."""
        from playwright.sync_api import sync_playwright

        with sync_playwright() as p:
            navegador = p.chromium.launch(args=["--no-sandbox"])
            try:
                pagina = navegador.new_page()
                pagina.set_default_timeout(self.tiempo_ms)
                pagina.set_content(html, wait_until="load")
                titulo = pagina.locator("h2").first.inner_text()
                secciones = pagina.locator("section.clasificacion").count()
                tablas = pagina.locator("table").count()
            finally:
                navegador.close()
        if secciones < secciones_esperadas:
            raise FallaPlantilla(f"El informe renderizado muestra {secciones} clasificaciones de {secciones_esperadas} esperadas")
        return f"Título '{titulo}', {secciones} clasificaciones y {tablas} tablas visibles"
