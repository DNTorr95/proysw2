"""
Trabajador RPA - Proyecto 25 (CU-31, CU-34, CU-35).

Regla de arquitectura: este servicio NO tiene credenciales de base de datos. Recibe del backend
los datos mínimos (oficios autorizados del periodo y la plantilla institucional), genera el PDF en el
volumen documental, prepara el borrador del correo y devuelve resumen, incidencias y evidencias.
Nunca envía correos: el envío lo hace el backend tras la autorización humana.
"""

import logging
import os
from pathlib import Path

from fastapi import Depends, FastAPI
from fastapi.concurrency import run_in_threadpool

from . import procesador
from .informe import RenderizadorPlaywright
from .modelos import ResultadoEjecucion, SolicitudEjecucion
from .seguridad import verificar_clave

logging.basicConfig(level=logging.INFO, format='{"nivel":"%(levelname)s","logger":"%(name)s","mensaje":"%(message)s"}')

DIRECTORIO = Path(os.environ.get("STORAGE_DIR", "./almacenamiento"))

app = FastAPI(title="Proyecto 25 - Trabajador RPA", version="1.0.0", docs_url=None, redoc_url=None)
renderizador = RenderizadorPlaywright()


@app.get("/salud")
def salud() -> dict:
    return {"estado": "UP", "servicio": "rpa-worker"}


@app.post("/interno/rpa/ejecuciones", response_model=ResultadoEjecucion, dependencies=[Depends(verificar_clave)])
async def ejecutar(solicitud: SolicitudEjecucion) -> ResultadoEjecucion:
    # Playwright síncrono se ejecuta fuera del bucle de eventos
    return await run_in_threadpool(procesador.ejecutar, solicitud, renderizador, DIRECTORIO)
