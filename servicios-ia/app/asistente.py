"""CU-25 Asistente: interpreta la pregunta (intención + entidades) con reglas simples.

No accede a datos: el backend ejecuta la consulta autorizada para el usuario y redacta la respuesta.
La tecnología definitiva (p. ej. un LLM) puede reemplazar a `InterpreteReglas` sin cambiar el contrato.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

from .busqueda import normalizar

MESES = {"enero": 1, "febrero": 2, "marzo": 3, "abril": 4, "mayo": 5, "junio": 6, "julio": 7, "agosto": 8,
         "septiembre": 9, "setiembre": 9, "octubre": 10, "noviembre": 11, "diciembre": 12}


@dataclass
class Interpretacion:
    intencion: str
    confianza: float
    entidades: dict = field(default_factory=dict)


# (intención, palabras clave normalizadas; cada coincidencia suma)
REGLAS: list[tuple[str, list[str]]] = [
    ("ESTADO_TRAMITE", ["estado", "tramite", "trm-", "como va", "seguimiento"]),
    ("TRAMITES_VENCIDOS", ["vencid", "atrasad", "fuera de plazo", "demorad", "retrasad"]),
    ("TRAMITES_POR_ESTADO", ["cuantos tramites", "tramites pendientes", "tramites en revision", "tramites observados", "tramites"]),
    ("REQUISITOS_TRAMITE", ["requisito", "que necesito", "documentos para", "que se necesita", "plazo de"]),
    ("CORRESPONDENCIA_PERIODO", ["correspondencia", "oficios", "oficio", "recibid", "emitid"]),
    ("INFORMES_RPA", ["informe", "rpa", "robot", "pendiente de revision", "consolidacion"]),
    ("AVANCE_POA", ["poa", "plan operativo", "actividades", "avance"]),
    ("ALERTAS", ["alerta", "riesgo", "prediccion"]),
    ("AYUDA", ["ayuda", "que puedes", "que sabes", "hola", "buenos dias", "buenas"]),
]


class InterpreteReglas:
    def interpretar(self, pregunta: str) -> Interpretacion:
        texto = normalizar(pregunta)
        entidades: dict = {}
        if m := re.search(r"trm-\d{4}-\d{1,4}", texto):
            entidades["numeroTramite"] = m.group(0).upper()
        if m := re.search(r"dcp-(rec|emi)-\d{4}-\d{1,4}", texto):
            entidades["codigoCorrespondencia"] = m.group(0).upper()
        for nombre, numero in MESES.items():
            if nombre in texto:
                entidades["mes"] = numero
                break
        if m := re.search(r"\b(20\d{2})\b", texto):
            entidades["anio"] = int(m.group(1))
        for t in ("adicion", "cambio de grupo", "reincorporacion", "traspaso", "record", "licencia"):
            if t in texto:
                entidades["tipoTramite"] = t
        for e in ("recepcionado", "revision", "observado", "aprobado", "rechazado", "archivado"):
            if e in texto:
                entidades["estado"] = e

        puntajes = {intencion: sum(1 for c in claves if c in texto) for intencion, claves in REGLAS}
        if "numeroTramite" in entidades:
            puntajes["ESTADO_TRAMITE"] += 3
        # Las palabras específicas pesan más que las genéricas ("trámites")
        if puntajes["TRAMITES_VENCIDOS"]:
            puntajes["TRAMITES_VENCIDOS"] += 2
        if "tipoTramite" in entidades and puntajes["REQUISITOS_TRAMITE"]:
            puntajes["REQUISITOS_TRAMITE"] += 2
        if "mes" in entidades and puntajes["CORRESPONDENCIA_PERIODO"]:
            puntajes["CORRESPONDENCIA_PERIODO"] += 1
        mejor = max(puntajes, key=lambda k: puntajes[k])
        if puntajes[mejor] == 0:
            return Interpretacion("DESCONOCIDA", 0.0, entidades)
        total = sum(puntajes.values())
        return Interpretacion(mejor, round(puntajes[mejor] / total, 2), entidades)
