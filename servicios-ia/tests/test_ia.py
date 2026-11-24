"""Pruebas de los servicios inteligentes (sin Tesseract ni modelo descargado: se usan dobles y TF-IDF)."""

import os
import random
from pathlib import Path

import pytest

os.environ["SEMANTICA_MOTOR"] = "tfidf"
os.environ.setdefault("SERVICE_KEY", "clave-de-prueba")

from app import ocr  # noqa: E402
from app.asistente import InterpreteReglas  # noqa: E402
from app.busqueda import CodificadorTfidf, IndiceSemantico  # noqa: E402
from app.prediccion import ErrorPrediccion, ModeloDemora  # noqa: E402

CLAVE = {"X-Service-Key": "clave-de-prueba"}


# ---------------------------------------------------------------- OCR

def test_ocr_impide_salir_del_volumen(tmp_path):
    (tmp_path / "a.png").write_bytes(b"\x89PNG")
    assert ocr.resolver_ruta("a.png", tmp_path).name == "a.png"
    with pytest.raises(ocr.ErrorOcr, match="inválida"):
        ocr.resolver_ruta("../../etc/passwd", tmp_path)
    with pytest.raises(ocr.ErrorOcr, match="no existe"):
        ocr.resolver_ruta("falta.pdf", tmp_path)
    (tmp_path / "x.docx").write_bytes(b"PK")
    with pytest.raises(ocr.ErrorOcr, match="Solo se procesan"):
        ocr.resolver_ruta("x.docx", tmp_path)


# ---------------------------------------------------------------- Búsqueda semántica

CORPUS = [
    ("CORR-1", "Solicitud de ambientes y aulas para el periodo académico"),
    ("CORR-2", "Invitación a taller de salud mental para estudiantes"),
    ("CORR-3", "Remisión de planillas de calificaciones del semestre"),
    ("DOC-4", "Resolución de designación de tribunal para defensa de tesis"),
    ("TRAB-5", "Ansiedad académica y estrés en universitarios de primer año"),
]


def test_busqueda_devuelve_lo_relevante_primero():
    indice = IndiceSemantico(CodificadorTfidf())
    assert indice.indexar(CORPUS + [("VACIO", "  ")]) == 5
    assert indice.buscar("talleres de salud mental")[0][0] == "CORR-2"
    assert indice.buscar("estres academico")[0][0] == "TRAB-5"        # sin tildes y con variación
    assert indice.buscar("tribunal de tesis")[0][0] == "DOC-4"
    assert indice.buscar("xyzw qqq") == []


def test_busqueda_con_indice_vacio():
    assert IndiceSemantico(CodificadorTfidf()).buscar("algo") == []


# ---------------------------------------------------------------- Predicción

def historico(n=60, semilla=25):
    rnd = random.Random(semilla)
    filas = []
    for i in range(n):
        tipo = rnd.choice(["Traspaso", "Récord académico", "Reincorporación", "Cambio de grupo"])
        obs = rnd.randint(0, 2)
        demorado = tipo in ("Traspaso", "Reincorporación") and obs > 0 or obs == 2
        filas.append({"tipo": tipo, "prioridad": rnd.choice(["ALTA", "MEDIA", "BAJA"]), "plazoDias": 5,
                      "requisitosPendientes": rnd.randint(0, 1), "observaciones": obs,
                      "mesRecepcion": rnd.randint(1, 12), "demorado": demorado})
    return filas


def test_prediccion_aprende_el_patron():
    m = ModeloDemora()
    metricas = m.entrenar(historico())
    assert metricas.ejemplos == 60 and metricas.exactitud is not None and metricas.exactitud > 0.6
    riesgoso = {"tipo": "Traspaso", "prioridad": "MEDIA", "plazoDias": 15, "requisitosPendientes": 1, "observaciones": 2,
                "mesRecepcion": 10}
    sencillo = dict(riesgoso, tipo="Récord académico", observaciones=0, requisitosPendientes=0)
    p_riesgo, p_simple = m.predecir([riesgoso, sencillo])
    assert 0 <= p_simple < p_riesgo <= 1


def test_prediccion_valida_el_historico():
    m = ModeloDemora()
    with pytest.raises(ErrorPrediccion, match="al menos"):
        m.entrenar(historico(5))
    with pytest.raises(ErrorPrediccion, match="demorados y a tiempo"):
        m.entrenar([dict(h, demorado=False) for h in historico()])
    with pytest.raises(ErrorPrediccion, match="no está entrenado"):
        m.predecir([{}])


# ---------------------------------------------------------------- Asistente

@pytest.mark.parametrize("pregunta,intencion", [
    ("¿Cuál es el estado del trámite TRM-2026-0005?", "ESTADO_TRAMITE"),
    ("¿Cuántos trámites están vencidos?", "TRAMITES_VENCIDOS"),
    ("¿Qué requisitos necesito para una reincorporación?", "REQUISITOS_TRAMITE"),
    ("¿Cuánta correspondencia recibimos en octubre?", "CORRESPONDENCIA_PERIODO"),
    ("¿Hay informes del robot pendientes de revisión?", "INFORMES_RPA"),
    ("¿Cómo va el avance del POA?", "AVANCE_POA"),
    ("Hola, ¿qué puedes hacer?", "AYUDA"),
    ("¿Cuál es la capital de Francia?", "DESCONOCIDA"),
])
def test_asistente_reconoce_intenciones(pregunta, intencion):
    assert InterpreteReglas().interpretar(pregunta).intencion == intencion


def test_asistente_extrae_entidades():
    r = InterpreteReglas().interpretar("estado del trámite trm-2026-0061 de octubre 2026")
    assert r.entidades == {"numeroTramite": "TRM-2026-0061", "mes": 10, "anio": 2026}


@pytest.mark.parametrize("pregunta,intencion", [
    ("¿En qué estado está el oficio DCP-REC-2026-0012?", "ESTADO_CORRESPONDENCIA"),
    ("trámites del estudiante 21901222", "TRAMITES_ESTUDIANTE"),
    ("¿Cuántos grupos sin docente hay en el periodo?", "RESUMEN_ACADEMICO"),
    ("¿Qué talleres o eventos vienen?", "EVENTOS_PROXIMOS"),
    ("¿Cuántos oficios recibimos en septiembre?", "CORRESPONDENCIA_PERIODO"),
])
def test_asistente_reconoce_intenciones_nuevas(pregunta, intencion):
    assert InterpreteReglas().interpretar(pregunta).intencion == intencion


@pytest.mark.parametrize("pregunta", [
    "resumen académico del periodo",
    "¿Cómo va el semestre?",
    "dame el resumen del periodo actual",
    "planificación académica",
])
def test_asistente_reconoce_el_resumen_academico_con_otras_frases(pregunta):
    assert InterpreteReglas().interpretar(pregunta).intencion == "RESUMEN_ACADEMICO"


@pytest.mark.parametrize("pregunta,intencion", [
    ("cuantos tramites estan venzidos", "TRAMITES_VENCIDOS"),
    ("que requisitoz piden para un reingreso", "REQUISITOS_TRAMITE"),
    ("como va el avanse del POA", "AVANCE_POA"),
    ("hay alertaz abiertas", "ALERTAS"),
])
def test_asistente_tolera_errores_de_escritura(pregunta, intencion):
    assert InterpreteReglas().interpretar(pregunta).intencion == intencion


@pytest.mark.parametrize("pregunta,tipo", [
    ("¿Qué necesito para un reingreso?", "reincorporacion"),
    ("documentos para el certificado de notas", "record"),
    ("requisitos para cambio de paralelo", "cambio de grupo"),
    ("¿qué piden para un traslado?", "traspaso"),
    ("requisitos para congelar el semestre", "licencia"),
])
def test_asistente_reconoce_sinonimos_de_tipos_de_tramite(pregunta, tipo):
    r = InterpreteReglas().interpretar(pregunta)
    assert r.intencion == "REQUISITOS_TRAMITE"
    assert r.entidades["tipoTramite"] == tipo


def test_asistente_extrae_registro_y_codigo_de_oficio():
    r = InterpreteReglas().interpretar("seguimiento del oficio dcp-emi-2026-7 y trámites del registro 22001481")
    assert r.entidades["codigoCorrespondencia"] == "DCP-EMI-2026-7"
    assert r.entidades["registro"] == "22001481"
    # un año o un número de trámite no se confunden con un registro
    assert "registro" not in InterpreteReglas().interpretar("estado del trámite TRM-2026-0061 de 2026").entidades


# ---------------------------------------------------------------- API y arquitectura

@pytest.fixture()
def cliente():
    from fastapi.testclient import TestClient

    from app import main
    main.indice = IndiceSemantico(CodificadorTfidf())
    main.modelo = ModeloDemora()
    return TestClient(main.app)


def test_api_exige_clave_y_flujo_completo(cliente):
    assert cliente.post("/interno/asistente", json={"pregunta": "hola"}).status_code == 401
    assert cliente.post("/interno/busqueda", json={"consulta": "aulas"}, headers=CLAVE).status_code == 409
    r = cliente.post("/interno/busqueda/indice", json={"documentos": [{"id": i, "texto": t} for i, t in CORPUS]}, headers=CLAVE)
    assert r.json()["indexados"] == 5
    r = cliente.post("/interno/busqueda", json={"consulta": "aulas para el periodo"}, headers=CLAVE)
    assert r.json()["resultados"][0]["id"] == "CORR-1"
    assert cliente.post("/interno/prediccion", json={"tramites": []}, headers=CLAVE).status_code == 409
    assert cliente.post("/interno/prediccion/entrenamiento", json={"historico": historico()}, headers=CLAVE).status_code == 200
    r = cliente.post("/interno/prediccion", json={"tramites": [historico(1)[0]]}, headers=CLAVE)
    assert len(r.json()["probabilidades"]) == 1
    r = cliente.post("/interno/ocr", json={"ruta": "../secreto.png"}, headers=CLAVE)
    assert r.status_code == 422


def test_servicio_no_tiene_acceso_a_base_de_datos():
    raiz = Path(__file__).parents[1]
    codigo = "\n".join(p.read_text(encoding="utf-8") for p in (raiz / "app").rglob("*.py")).lower()
    for prohibido in ("psycopg", "sqlalchemy", "asyncpg", "jdbc:", "db_password", "db_url"):
        assert prohibido not in codigo, prohibido
