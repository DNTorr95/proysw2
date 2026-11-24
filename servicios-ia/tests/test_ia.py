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


def test_modelo_se_guarda_y_se_recupera_al_reiniciar(tmp_path):
    m = ModeloDemora(tmp_path)
    metricas = m.entrenar(historico(), version=3)
    assert metricas.version == 3 and metricas.fecha and len(metricas.sha256) == 64
    assert (tmp_path / "demora-v3.joblib").is_file() and (tmp_path / "vigente.json").is_file()
    tramite = {"tipo": "Traspaso", "prioridad": "MEDIA", "plazoDias": 15, "requisitosPendientes": 1, "observaciones": 2,
               "mesRecepcion": 10}
    reiniciado = ModeloDemora(tmp_path)  # simula el reinicio del servicio
    assert reiniciado.entrenado and reiniciado.metricas == metricas
    assert reiniciado.predecir([tramite]) == m.predecir([tramite])


def test_modelo_conserva_las_versiones_y_numera_solo(tmp_path):
    m = ModeloDemora(tmp_path)
    assert m.entrenar(historico()).version == 1
    assert m.entrenar(historico(semilla=7)).version == 2
    assert m.versiones() == [1, 2]
    assert ModeloDemora(tmp_path).metricas.version == 2


def test_modelo_alterado_no_se_carga(tmp_path):
    ModeloDemora(tmp_path).entrenar(historico(), version=1)
    with open(tmp_path / "demora-v1.joblib", "ab") as f:
        f.write(b"alterado")
    assert not ModeloDemora(tmp_path).entrenado


def test_sin_directorio_el_modelo_no_se_guarda(tmp_path):
    m = ModeloDemora()
    assert m.entrenar(historico()).sha256 is None
    assert m.versiones() == []


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


def test_api_informa_el_modelo_vigente(cliente):
    h = {"X-Service-Key": os.environ["SERVICE_KEY"]}
    assert cliente.get("/interno/prediccion/modelo", headers=h).status_code == 404
    r = cliente.post("/interno/prediccion/entrenamiento", json={"historico": historico(), "version": 5}, headers=h)
    assert r.status_code == 200 and r.json()["version"] == 5
    v = cliente.get("/interno/prediccion/modelo", headers=h)
    assert v.status_code == 200 and v.json()["version"] == 5 and v.json()["modelo"] == "RandomForest"


def test_servicio_no_tiene_acceso_a_base_de_datos():
    raiz = Path(__file__).parents[1]
    codigo = "\n".join(p.read_text(encoding="utf-8") for p in (raiz / "app").rglob("*.py")).lower()
    for prohibido in ("psycopg", "sqlalchemy", "asyncpg", "jdbc:", "db_password", "db_url"):
        assert prohibido not in codigo, prohibido
