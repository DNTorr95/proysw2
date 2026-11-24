"""
Servicios inteligentes - Proyecto 25 (componentes de los otros grupos): OCR, búsqueda semántica,
predicción de demoras y asistente. Cada componente está detrás de una interfaz reemplazable.

Regla de arquitectura: este servicio NO tiene credenciales de base de datos ni accede a ella.
Todo dato llega desde el backend (que filtra por permisos) y el volumen documental se monta en solo lectura.
"""

import logging

from fastapi import Depends, FastAPI, HTTPException
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel, Field

from .asistente import InterpreteReglas
from .busqueda import IndiceSemantico
from .ocr import ErrorOcr, MotorTesseract, resolver_ruta
from .prediccion import ErrorPrediccion, ModeloDemora
from .seguridad import verificar_clave

logging.basicConfig(level=logging.INFO, format='{"nivel":"%(levelname)s","logger":"%(name)s","mensaje":"%(message)s"}')

app = FastAPI(title="Proyecto 25 - Servicios inteligentes", version="1.0.0", docs_url=None, redoc_url=None)
motor_ocr = MotorTesseract()
indice = IndiceSemantico()
modelo = ModeloDemora()
interprete = InterpreteReglas()
seguro = [Depends(verificar_clave)]


@app.get("/salud")
def salud() -> dict:
    return {"estado": "UP", "servicio": "servicios-ia", "indice": indice.tamano, "modeloEntrenado": modelo.entrenado}


# ---------------- OCR (CU-21) ----------------

class SolicitudOcr(BaseModel):
    ruta: str = Field(..., max_length=500)


@app.post("/interno/ocr", dependencies=seguro)
async def ocr(s: SolicitudOcr) -> dict:
    try:
        ruta = resolver_ruta(s.ruta)
        texto, confianza = await run_in_threadpool(motor_ocr.extraer, ruta.read_bytes(), ruta.suffix.lower())
    except ErrorOcr as e:
        raise HTTPException(422, str(e)) from e
    return {"texto": texto, "confianza": confianza, "motor": "tesseract-spa"}


# ---------------- Búsqueda semántica (CU-24) ----------------

class Documento(BaseModel):
    id: str = Field(..., max_length=50)
    texto: str = ""


class SolicitudIndice(BaseModel):
    documentos: list[Documento]


class SolicitudBusqueda(BaseModel):
    consulta: str = Field(..., min_length=2, max_length=500)
    limite: int = Field(20, ge=1, le=100)


@app.post("/interno/busqueda/indice", dependencies=seguro)
async def indexar(s: SolicitudIndice) -> dict:
    n = await run_in_threadpool(indice.indexar, [(d.id, d.texto) for d in s.documentos])
    return {"indexados": n, "motor": indice.codificador.nombre}


class SolicitudVectores(BaseModel):
    textos: list[str] = Field(..., min_length=1, max_length=256)


@app.post("/interno/embeddings", dependencies=seguro)
async def embeddings(s: SolicitudVectores) -> dict:
    """Vectores para pgvector. 409 si el motor activo es TF-IDF (el backend usa entonces el índice en memoria)."""
    try:
        m = await run_in_threadpool(indice.vectores, s.textos)
    except ValueError as e:
        raise HTTPException(409, str(e)) from e
    return {"vectores": [[round(float(x), 6) for x in fila] for fila in m], "dimension": int(m.shape[1]),
            "motor": indice.codificador.nombre}


@app.post("/interno/busqueda", dependencies=seguro)
async def buscar(s: SolicitudBusqueda) -> dict:
    if indice.tamano == 0:
        raise HTTPException(409, "El índice está vacío")
    resultados = await run_in_threadpool(indice.buscar, s.consulta, s.limite)
    return {"resultados": [{"id": i, "relevancia": r} for i, r in resultados], "motor": indice.codificador.nombre}


# ---------------- Predicción (CU-29) ----------------

class SolicitudEntrenamiento(BaseModel):
    historico: list[dict]


class SolicitudPrediccion(BaseModel):
    tramites: list[dict]


@app.post("/interno/prediccion/entrenamiento", dependencies=seguro)
async def entrenar(s: SolicitudEntrenamiento) -> dict:
    try:
        m = await run_in_threadpool(modelo.entrenar, s.historico)
    except ErrorPrediccion as e:
        raise HTTPException(422, str(e)) from e
    return m.__dict__


@app.post("/interno/prediccion", dependencies=seguro)
async def predecir(s: SolicitudPrediccion) -> dict:
    if not modelo.entrenado:
        raise HTTPException(409, "El modelo no está entrenado")
    probabilidades = await run_in_threadpool(modelo.predecir, s.tramites)
    return {"probabilidades": probabilidades, "metricas": modelo.metricas.__dict__}


# ---------------- Asistente (CU-25) ----------------

class SolicitudAsistente(BaseModel):
    pregunta: str = Field(..., min_length=2, max_length=500)


@app.post("/interno/asistente", dependencies=seguro)
def asistente(s: SolicitudAsistente) -> dict:
    r = interprete.interpretar(s.pregunta)
    return {"intencion": r.intencion, "confianza": r.confianza, "entidades": r.entidades}
