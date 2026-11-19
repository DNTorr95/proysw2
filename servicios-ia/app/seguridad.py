"""Verificación de la clave de servicio (cabecera X-Service-Key) para las rutas /interno/*."""

import hmac
import os

from fastapi import Header, HTTPException, status

CLAVE_SERVICIO = os.environ.get("SERVICE_KEY", "")


def verificar_clave(x_service_key: str | None = Header(default=None, alias="X-Service-Key")) -> None:
    """Dependencia de FastAPI: rechaza la petición si la clave falta o no coincide."""
    if not CLAVE_SERVICIO:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "SERVICE_KEY no configurada")
    if not x_service_key or not hmac.compare_digest(x_service_key, CLAVE_SERVICIO):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Clave de servicio inválida")
