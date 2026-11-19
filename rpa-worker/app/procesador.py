"""Orquestación de la ejecución RPA paso a paso, con evidencias y límite de tiempo."""

from __future__ import annotations

import hashlib
import logging
import os
import time
import uuid
from contextlib import contextmanager
from datetime import date, datetime
from pathlib import Path

from .consolidacion import validar
from .informe import FallaPlantilla, RenderizadorPdf, renderizar_html
from .modelos import (BorradorCorreo, Incidencia, InformeGenerado, Paso, ResultadoEjecucion, Resumen,
                      SolicitudEjecucion)

log = logging.getLogger("rpa")

MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
         "septiembre", "octubre", "noviembre", "diciembre"]


class TiempoExcedido(Exception):
    pass


class Bitacora:
    """Registro de pasos (evidencias) que se devuelve al backend."""

    def __init__(self, limite_segundos: int):
        self.pasos: list[Paso] = []
        self.inicio = time.monotonic()
        self.limite = limite_segundos

    def verificar_tiempo(self):
        if time.monotonic() - self.inicio > self.limite:
            raise TiempoExcedido(f"Se superó el tiempo máximo de ejecución ({self.limite} s)")

    @contextmanager
    def paso(self, nombre: str, descripcion: str):
        self.verificar_tiempo()
        t0, inicio = time.monotonic(), datetime.now()
        registro = {"estado": "OK", "detalle": None}
        try:
            yield registro
        except Exception as e:
            registro["estado"], registro["detalle"] = "ERROR", str(e)
            raise
        finally:
            self.pasos.append(Paso(paso=nombre, descripcion=descripcion, estado=registro["estado"],
                                   inicio=inicio.isoformat(timespec="milliseconds"),
                                   fin=datetime.now().isoformat(timespec="milliseconds"),
                                   duracionMs=int((time.monotonic() - t0) * 1000), detalle=registro["detalle"]))
            log.info("ejecucion paso=%s estado=%s detalle=%s", nombre, registro["estado"], registro["detalle"])


def nombre_periodo(desde: date, hasta: date) -> str:
    if desde.day == 1 and desde.year == hasta.year and desde.month == hasta.month:
        return f"{MESES[desde.month - 1]} de {desde.year}"
    return f"{desde.strftime('%d/%m/%Y')} al {hasta.strftime('%d/%m/%Y')}"


def ejecutar(s: SolicitudEjecucion, renderizador: RenderizadorPdf, directorio: Path, hoy: date | None = None) -> ResultadoEjecucion:
    b = Bitacora(s.tiempoMaximoSegundos)
    resumen = Resumen(registrosProcesados=len(s.registros))
    incidencias: list[Incidencia] = []
    try:
        with b.paso("RECEPCION", "Recepción y validación de la solicitud") as p:
            desde, hasta = date.fromisoformat(s.periodoDesde), date.fromisoformat(s.periodoHasta)
            if hasta < desde:
                raise ValueError("El periodo es inválido: la fecha final es anterior a la inicial")
            p["detalle"] = f"{len(s.registros)} registro(s) recibidos del periodo {desde} a {hasta} ({s.tipoCorrespondencia})"

        with b.paso("VALIDACION", "Validación de campos obligatorios, duplicados y fechas") as p:
            c = validar(s.registros, desde, hasta, s.tipoCorrespondencia, hoy)
            incidencias.extend(c.incidencias)
            resumen.registrosValidos = len(c.validos)
            resumen.erroresDetectados = len(c.incidencias)
            resumen.totalRecibidos, resumen.totalEmitidos = c.total_recibidos, c.total_emitidos
            p["detalle"] = f"{len(c.validos)} válidos, {len(c.incidencias)} incidencia(s)"
            if c.incidencias:
                p["estado"] = "ADVERTENCIA"

        with b.paso("CONSOLIDACION", "Consolidación y clasificación por fecha, tipo, estado y dependencia") as p:
            p["detalle"] = ", ".join(f"{cat}: {sum(1 for d in c.detalle if d.categoria == cat)} grupo(s)"
                                     for cat in ("FECHA", "TIPO", "ESTADO", "DEPENDENCIA"))

        titulo = f"Informe de correspondencia - {nombre_periodo(desde, hasta)}"
        with b.paso("PLANTILLA", "Aplicación de la plantilla institucional") as p:
            html = renderizar_html(s.plantillaHtml, c, desde, hasta, s.idEjecucion, titulo)
            p["detalle"] = f"Plantilla '{s.plantillaNombre}' aplicada ({len(html)} caracteres)"

        with b.paso("VERIFICACION_NAVEGADOR", "Verificación del informe en el navegador (Playwright)") as p:
            esperadas = len({d.categoria for d in c.detalle})
            p["detalle"] = renderizador.verificar(html, esperadas)

        with b.paso("PDF", "Generación del informe PDF (CU-34)") as p:
            pdf = renderizador.renderizar(html)
            if not pdf.startswith(b"%PDF"):
                raise FallaPlantilla("El renderizador no produjo un PDF válido")
            b.verificar_tiempo()
            relativa = f"rpa/{hasta.year}/{hasta.month:02d}/informe-{s.idEjecucion}-{uuid.uuid4().hex[:8]}.pdf"
            destino = (directorio / relativa).resolve()
            if not str(destino).startswith(str(directorio.resolve())):
                raise ValueError("Ruta de salida inválida")
            destino.parent.mkdir(parents=True, exist_ok=True)
            temporal = destino.with_suffix(".tmp")
            temporal.write_bytes(pdf)
            os.replace(temporal, destino)
            informe = InformeGenerado(ruta=relativa, hashSha256=hashlib.sha256(pdf).hexdigest(), tamanoBytes=len(pdf))
            p["detalle"] = f"{relativa} ({len(pdf)} bytes)"

        with b.paso("BORRADOR_CORREO", "Preparación del borrador de correo con el PDF adjunto (CU-35)") as p:
            borrador = BorradorCorreo(
                asunto=f"{titulo} - Dirección de Carrera de Psicología",
                cuerpo=(
                    "Estimados:\n\n"
                    f"Se remite el informe consolidado de correspondencia del periodo {nombre_periodo(desde, hasta)}.\n\n"
                    f"Resumen: {resumen.totalRecibidos} oficio(s) recibido(s) y {resumen.totalEmitidos} emitido(s), "
                    f"{resumen.registrosValidos} registro(s) consolidados.\n\n"
                    "Se adjunta el informe en formato PDF.\n\n"
                    "Atentamente,\nDirección de Carrera de Psicología - UAGRM"),
                destinatarios=[d.strip() for d in s.destinatariosCorreo if d.strip()],
                adjunto=informe.ruta)
            p["detalle"] = f"Borrador para {len(borrador.destinatarios)} destinatario(s); queda pendiente de autorización humana"

        return ResultadoEjecucion(idEjecucion=s.idEjecucion, estado="COMPLETADA", resumen=resumen, detalle=c.detalle,
                                  informe=informe, borrador=borrador, incidencias=incidencias, pasos=b.pasos)

    except TiempoExcedido as e:
        incidencias.append(Incidencia(tipo="TIEMPO_EXCEDIDO", descripcion=str(e)))
        return _fallida(s, resumen, incidencias, b, str(e))
    except FallaPlantilla as e:
        incidencias.append(Incidencia(tipo="FALLA_PLANTILLA", descripcion=str(e)))
        return _fallida(s, resumen, incidencias, b, str(e))
    except Exception as e:  # noqa: BLE001 - cualquier otro error se informa como incidencia
        log.exception("Error en la ejecución %s", s.idEjecucion)
        incidencias.append(Incidencia(tipo="ERROR_INTERNO", descripcion=f"{type(e).__name__}: {e}"))
        return _fallida(s, resumen, incidencias, b, str(e))


def _fallida(s, resumen, incidencias, b, mensaje) -> ResultadoEjecucion:
    resumen.erroresDetectados = len(incidencias)
    return ResultadoEjecucion(idEjecucion=s.idEjecucion, estado="FALLIDA", resumen=resumen,
                              incidencias=incidencias, pasos=b.pasos, mensajeError=mensaje)

