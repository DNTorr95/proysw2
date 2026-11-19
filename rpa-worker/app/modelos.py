"""Contrato REST interno entre el backend y el trabajador RPA (datos mínimos, sin acceso a BD)."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class RegistroCorrespondencia(BaseModel):
    """Oficio autorizado del periodo, tal como lo envía el backend. Los campos pueden venir incompletos:
    la validación es responsabilidad del trabajador y se informa como incidencia."""

    codigo: str | None = None
    tipo: str | None = None                  # RECIBIDA | EMITIDA
    asunto: str | None = None
    fechaDocumento: str | None = None        # ISO yyyy-mm-dd
    estado: str | None = None
    dependencia: str | None = None
    destinatario: str | None = None


class SolicitudEjecucion(BaseModel):
    idEjecucion: int
    proceso: str
    periodoDesde: str
    periodoHasta: str
    tipoCorrespondencia: Literal["TODAS", "RECIBIDA", "EMITIDA"] = "TODAS"
    plantillaHtml: str
    plantillaNombre: str = "Informe mensual de correspondencia"
    revisor: str | None = None
    destinatariosCorreo: list[str] = Field(default_factory=list)
    tiempoMaximoSegundos: int = 300
    registros: list[RegistroCorrespondencia] = Field(default_factory=list)


class Paso(BaseModel):
    paso: str
    descripcion: str
    estado: Literal["OK", "ADVERTENCIA", "ERROR"]
    inicio: str
    fin: str
    duracionMs: int
    detalle: str | None = None


class Incidencia(BaseModel):
    tipo: str            # CAMPO_OBLIGATORIO | DUPLICADO | FECHA_INVALIDA | FALLA_PLANTILLA | TIEMPO_EXCEDIDO | ERROR_INTERNO
    descripcion: str


class Detalle(BaseModel):
    categoria: Literal["FECHA", "TIPO", "ESTADO", "DEPENDENCIA"]
    clave: str
    cantidad: int


class Resumen(BaseModel):
    registrosProcesados: int = 0
    registrosValidos: int = 0
    erroresDetectados: int = 0
    totalRecibidos: int = 0
    totalEmitidos: int = 0


class InformeGenerado(BaseModel):
    ruta: str
    hashSha256: str
    tamanoBytes: int


class BorradorCorreo(BaseModel):
    asunto: str
    cuerpo: str
    destinatarios: list[str]
    adjunto: str


class ResultadoEjecucion(BaseModel):
    idEjecucion: int
    estado: Literal["COMPLETADA", "FALLIDA"]
    resumen: Resumen
    detalle: list[Detalle] = Field(default_factory=list)
    informe: InformeGenerado | None = None
    borrador: BorradorCorreo | None = None
    incidencias: list[Incidencia] = Field(default_factory=list)
    pasos: list[Paso] = Field(default_factory=list)
    mensajeError: str | None = None
