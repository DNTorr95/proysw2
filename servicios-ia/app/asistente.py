"""CU-25 Asistente: interpreta la pregunta (intención + entidades) con reglas.

No accede a datos: el backend ejecuta la consulta autorizada para el usuario y redacta la respuesta.

Las reglas toleran variaciones del lenguaje:
- sinónimos para cada intención y para los tipos de trámite (p. ej. «reingreso» = reincorporación);
- errores de escritura en palabras clave (p. ej. «venzidos», «requisitoz»), comparando el comienzo de cada
  palabra con la clave mediante difflib (similitud >= 0,8, palabras de 5 letras o más y misma primera letra);
- entidades: número de trámite (TRM-…), código de oficio (DCP-…), registro del estudiante (8 dígitos),
  mes, año, tipo y estado de trámite.

La tecnología de interpretación puede reemplazarse (p. ej. por un modelo de lenguaje) sin cambiar el
contrato `interpretar(pregunta) -> Interpretacion`.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from difflib import SequenceMatcher

from .busqueda import normalizar

MESES = {"enero": 1, "febrero": 2, "marzo": 3, "abril": 4, "mayo": 5, "junio": 6, "julio": 7, "agosto": 8,
         "septiembre": 9, "setiembre": 9, "octubre": 10, "noviembre": 11, "diciembre": 12}

# Sinónimo (normalizado) -> clave del tipo de trámite que reconoce el backend
TIPOS_TRAMITE = {
    "adicion": "adicion", "adicionar materia": "adicion", "agregar materia": "adicion", "materia fuera de fecha": "adicion",
    "cambio de grupo": "cambio de grupo", "cambio de paralelo": "cambio de grupo", "cambiarme de grupo": "cambio de grupo",
    "reincorporacion": "reincorporacion", "reingreso": "reincorporacion", "readmision": "reincorporacion", "volver a la carrera": "reincorporacion",
    "traspaso": "traspaso", "traslado": "traspaso", "cambio de carrera": "traspaso", "transferencia de carrera": "traspaso",
    "record": "record", "certificado de notas": "record", "historial academico": "record", "kardex": "record",
    "licencia": "licencia", "permiso temporal": "licencia", "suspension temporal": "licencia", "congelar": "licencia",
}

ESTADOS_TRAMITE = ("recepcionado", "revision", "observado", "aprobado", "rechazado", "archivado")

UMBRAL_SIMILITUD = 0.8
LARGO_MINIMO_DIFUSO = 5


@dataclass
class Interpretacion:
    intencion: str
    confianza: float
    entidades: dict = field(default_factory=dict)


# (intención, palabras clave normalizadas; cada coincidencia suma 1)
REGLAS: list[tuple[str, list[str]]] = [
    ("ESTADO_TRAMITE", ["estado", "tramite", "trm-", "como va", "seguimiento", "en que va", "situacion", "mi solicitud"]),
    ("TRAMITES_VENCIDOS", ["vencid", "atrasad", "fuera de plazo", "demorad", "retrasad", "pasados de plazo", "expirad"]),
    ("TRAMITES_POR_ESTADO", ["cuantos tramites", "tramites pendientes", "tramites en revision", "tramites observados", "tramites",
                             "resumen de tramites"]),
    ("REQUISITOS_TRAMITE", ["requisito", "que necesito", "documentos para", "que se necesita", "plazo de", "que piden",
                            "que debo presentar", "documentacion para"]),
    ("CORRESPONDENCIA_PERIODO", ["correspondencia", "oficios", "recibid", "emitid", "cartas", "notas recibidas"]),
    ("ESTADO_CORRESPONDENCIA", ["oficio", "dcp-", "estado del oficio", "seguimiento del oficio", "la nota", "el oficio"]),
    ("TRAMITES_ESTUDIANTE", ["tramites del estudiante", "tramites de la estudiante", "estudiante", "registro", "alumno", "universitario"]),
    ("INFORMES_RPA", ["informe", "rpa", "robot", "pendiente de revision", "consolidacion", "consolidado"]),
    ("AVANCE_POA", ["poa", "plan operativo", "actividades", "avance", "plan anual", "metas"]),
    ("RESUMEN_ACADEMICO", ["sin docente", "grupos", "cupos", "cupo", "inscritos", "ocupacion", "periodo academico", "carga docente",
                           "grupos llenos"]),
    ("EVENTOS_PROXIMOS", ["evento", "taller", "seminario", "proximos eventos", "agenda", "actividades programadas"]),
    ("ALERTAS", ["alerta", "riesgo", "prediccion", "avisos"]),
    ("AYUDA", ["ayuda", "que puedes", "que sabes", "hola", "buenos dias", "buenas", "que haces", "como funciona"]),
]


def _palabras(texto: str) -> list[str]:
    return re.findall(r"[a-z0-9ñ\-]+", texto)


def contiene(texto: str, palabras: list[str], clave: str) -> bool:
    """¿La clave aparece en el texto? Las claves de una palabra (>= 5 letras) toleran errores de escritura."""
    if clave in texto:
        return True
    if " " in clave or "-" in clave or len(clave) < LARGO_MINIMO_DIFUSO:
        return False
    # Se compara el comienzo de cada palabra con la clave (las claves son raíces: "vencid", "requisito"...)
    # y se exige la misma primera letra: los errores de escritura rara vez están en la primera.
    return any(len(p) >= LARGO_MINIMO_DIFUSO and p[0] == clave[0]
               and SequenceMatcher(None, p[:len(clave)], clave).ratio() >= UMBRAL_SIMILITUD
               for p in palabras)


class InterpreteReglas:
    def interpretar(self, pregunta: str) -> Interpretacion:
        texto = normalizar(pregunta)
        palabras = _palabras(texto)
        entidades: dict = {}
        if m := re.search(r"trm-\d{4}-\d{1,4}", texto):
            entidades["numeroTramite"] = m.group(0).upper()
        if m := re.search(r"dcp-(rec|emi)-\d{4}-\d{1,4}", texto):
            entidades["codigoCorrespondencia"] = m.group(0).upper()
        if m := re.search(r"(?<![\d-])\d{8}(?![\d-])", texto):
            entidades["registro"] = m.group(0)
        for nombre, numero in MESES.items():
            if nombre in texto:
                entidades["mes"] = numero
                break
        if m := re.search(r"\b(20\d{2})\b", texto):
            entidades["anio"] = int(m.group(1))
        for sinonimo, clave in TIPOS_TRAMITE.items():
            if sinonimo in texto:
                entidades["tipoTramite"] = clave
        for e in ESTADOS_TRAMITE:
            if e in texto:
                entidades["estado"] = e

        puntajes = {intencion: sum(1 for c in claves if contiene(texto, palabras, c)) for intencion, claves in REGLAS}
        # Las entidades concretas definen la consulta
        if "numeroTramite" in entidades:
            puntajes["ESTADO_TRAMITE"] += 3
        if "codigoCorrespondencia" in entidades:
            puntajes["ESTADO_CORRESPONDENCIA"] += 3
        if "registro" in entidades:
            puntajes["TRAMITES_ESTUDIANTE"] += 3
        # Las palabras específicas pesan más que las genéricas ("trámites", "oficio")
        if puntajes["TRAMITES_VENCIDOS"]:
            puntajes["TRAMITES_VENCIDOS"] += 2
        if "tipoTramite" in entidades and puntajes["REQUISITOS_TRAMITE"]:
            puntajes["REQUISITOS_TRAMITE"] += 2
        if "mes" in entidades and puntajes["CORRESPONDENCIA_PERIODO"]:
            puntajes["CORRESPONDENCIA_PERIODO"] += 2
        mejor = max(puntajes, key=lambda k: puntajes[k])
        if puntajes[mejor] == 0:
            return Interpretacion("DESCONOCIDA", 0.0, entidades)
        total = sum(puntajes.values())
        return Interpretacion(mejor, round(puntajes[mejor] / total, 2), entidades)
