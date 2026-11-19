"""Pruebas del trabajador RPA con datos que incluyen duplicados, registros incompletos y falla de plantilla."""

import hashlib
import os
import time
from datetime import date
from pathlib import Path

import pytest

os.environ.setdefault("SERVICE_KEY", "clave-de-prueba")

from app import procesador  # noqa: E402
from app.consolidacion import validar  # noqa: E402
from app.modelos import RegistroCorrespondencia, SolicitudEjecucion  # noqa: E402

PLANTILLA = (Path(__file__).parent / "plantilla_seed.html").read_text(encoding="utf-8")
HOY = date(2026, 11, 16)


class RenderizadorFalso:
    """Sustituye a Playwright en las pruebas unitarias."""

    def __init__(self, demora: float = 0.0, pdf: bytes = b"%PDF-1.4 informe de prueba"):
        self.demora, self.pdf, self.html = demora, pdf, None

    def renderizar(self, html: str) -> bytes:
        time.sleep(self.demora)
        self.html = html
        return self.pdf

    def verificar(self, html: str, secciones_esperadas: int) -> str:
        return f"{secciones_esperadas} clasificaciones"


def reg(codigo, tipo="RECIBIDA", fecha="2026-10-05", asunto="Solicitud", dependencia="Decanato FHCS", estado="REGISTRADA"):
    return RegistroCorrespondencia(codigo=codigo, tipo=tipo, asunto=asunto, fechaDocumento=fecha,
                                   dependencia=dependencia, estado=estado, destinatario="X")


def solicitud(registros, plantilla=PLANTILLA, tiempo=300, tipo="TODAS"):
    return SolicitudEjecucion(idEjecucion=7, proceso="Informe mensual", periodoDesde="2026-10-01",
                              periodoHasta="2026-10-31", tipoCorrespondencia=tipo, plantillaHtml=plantilla,
                              destinatariosCorreo=["decanato@ejemplo.edu.bo"], tiempoMaximoSegundos=tiempo,
                              registros=registros)


# ---------------------------------------------------------------- validación

def test_detecta_duplicados_incompletos_y_fechas_invalidas():
    registros = [
        reg("DCP-REC-2026-0001"),
        reg("DCP-REC-2026-0001"),                              # duplicado
        reg("DCP-EMI-2026-0002", tipo="EMITIDA"),
        reg("DCP-REC-2026-0003", asunto=""),                    # incompleto
        RegistroCorrespondencia(codigo=None, tipo="RECIBIDA"),  # incompleto (varios campos)
        reg("DCP-REC-2026-0004", fecha="2026-13-45"),           # fecha no interpretable
        reg("DCP-REC-2026-0005", fecha="2026-09-30"),           # fuera del periodo
        reg("DCP-REC-2026-0006", fecha="2026-12-01"),           # futura
        reg("DCP-REC-2026-0007", tipo="INTERNA"),               # tipo inválido
    ]
    c = validar(registros, date(2026, 10, 1), date(2026, 10, 31), hoy=HOY)
    tipos = [i.tipo for i in c.incidencias]
    assert [r.codigo for r in c.validos] == ["DCP-REC-2026-0001", "DCP-EMI-2026-0002"]
    assert tipos.count("DUPLICADO") == 1
    assert tipos.count("CAMPO_OBLIGATORIO") == 3
    assert tipos.count("FECHA_INVALIDA") == 3
    assert c.total_recibidos == 1 and c.total_emitidos == 1


def test_clasifica_por_fecha_tipo_estado_y_dependencia():
    registros = [reg("A", fecha="2026-10-05"), reg("B", fecha="2026-10-06", tipo="EMITIDA", dependencia="Kardex"),
                 reg("C", fecha="2026-10-20", estado="ARCHIVADA")]
    c = validar(registros, date(2026, 10, 1), date(2026, 10, 31), hoy=HOY)
    por = {cat: {d.clave: d.cantidad for d in c.detalle if d.categoria == cat} for cat in ("FECHA", "TIPO", "ESTADO", "DEPENDENCIA")}
    assert por["TIPO"] == {"Recibida": 2, "Emitida": 1}
    assert por["DEPENDENCIA"] == {"Decanato FHCS": 2, "Kardex": 1}
    assert por["ESTADO"] == {"Registrada": 2, "Archivada": 1}
    assert sum(por["FECHA"].values()) == 3 and all(k.startswith("Semana del") for k in por["FECHA"])


def test_semanas_en_orden_cronologico():
    regs = [reg("A", fecha="2026-10-20"), reg("B", fecha="2026-10-01"), reg("C", fecha="2026-10-08")]
    c = validar(regs, date(2026, 10, 1), date(2026, 10, 31), hoy=HOY)
    claves = [d.clave for d in c.detalle if d.categoria == "FECHA"]
    assert claves == ["Semana del 28/09/2026", "Semana del 05/10/2026", "Semana del 19/10/2026"]


def test_filtra_por_tipo_solicitado():
    c = validar([reg("A"), reg("B", tipo="EMITIDA")], date(2026, 10, 1), date(2026, 10, 31), "EMITIDA", hoy=HOY)
    assert [r.codigo for r in c.validos] == ["B"]


# ---------------------------------------------------------------- ejecución completa

def test_ejecucion_completa_genera_pdf_y_borrador(tmp_path):
    r = RenderizadorFalso()
    res = procesador.ejecutar(solicitud([reg("A"), reg("A"), reg("B", tipo="EMITIDA"), reg("C", asunto=None)]),
                              r, tmp_path, hoy=HOY)
    assert res.estado == "COMPLETADA"
    assert res.resumen.registrosProcesados == 4
    assert res.resumen.registrosValidos == 2
    assert res.resumen.erroresDetectados == 2
    assert {i.tipo for i in res.incidencias} == {"DUPLICADO", "CAMPO_OBLIGATORIO"}
    archivo = tmp_path / res.informe.ruta
    assert archivo.read_bytes() == r.pdf
    assert res.informe.hashSha256 == hashlib.sha256(r.pdf).hexdigest()
    assert res.borrador.adjunto == res.informe.ruta
    assert res.borrador.destinatarios == ["decanato@ejemplo.edu.bo"]
    assert "octubre de 2026" in res.borrador.asunto
    assert [p.paso for p in res.pasos] == ["RECEPCION", "VALIDACION", "CONSOLIDACION", "PLANTILLA",
                                           "VERIFICACION_NAVEGADOR", "PDF", "BORRADOR_CORREO"]
    assert res.pasos[1].estado == "ADVERTENCIA"
    # La plantilla institucional quedó aplicada y los datos escapados
    assert "Universidad Autónoma Gabriel René Moreno" in r.html
    assert "data-categoria='DEPENDENCIA'" in r.html


def test_contenido_malicioso_se_escapa(tmp_path):
    r = RenderizadorFalso()
    procesador.ejecutar(solicitud([reg("A", asunto="<script>alert(1)</script>")]), r, tmp_path, hoy=HOY)
    assert "<script>alert(1)</script>" not in r.html
    assert "&lt;script&gt;" in r.html


def test_falla_de_plantilla_por_variables_faltantes(tmp_path):
    res = procesador.ejecutar(solicitud([reg("A")], plantilla="<html>{{ titulo }}</html>"), RenderizadorFalso(), tmp_path, hoy=HOY)
    assert res.estado == "FALLIDA"
    assert res.informe is None and res.borrador is None
    assert res.incidencias[-1].tipo == "FALLA_PLANTILLA"
    assert res.pasos[-1].paso == "PLANTILLA" and res.pasos[-1].estado == "ERROR"
    assert not any(tmp_path.rglob("*.pdf"))


def test_falla_de_plantilla_por_sintaxis(tmp_path):
    rota = PLANTILLA.replace("{{ secciones }}", "{{ secciones }} {% if %}")
    res = procesador.ejecutar(solicitud([reg("A")], plantilla=rota), RenderizadorFalso(), tmp_path, hoy=HOY)
    assert res.estado == "FALLIDA" and res.incidencias[-1].tipo == "FALLA_PLANTILLA"


def test_plantilla_no_puede_ejecutar_codigo(tmp_path):
    maliciosa = PLANTILLA.replace("{{ titulo }}", "{{ titulo.__class__.__mro__[1].__subclasses__() }}")
    res = procesador.ejecutar(solicitud([reg("A")], plantilla=maliciosa), RenderizadorFalso(), tmp_path, hoy=HOY)
    assert res.estado == "FALLIDA"


def test_renderizador_que_no_produce_pdf(tmp_path):
    res = procesador.ejecutar(solicitud([reg("A")]), RenderizadorFalso(pdf=b"<html>"), tmp_path, hoy=HOY)
    assert res.estado == "FALLIDA" and res.incidencias[-1].tipo == "FALLA_PLANTILLA"


def test_tiempo_excedido(tmp_path):
    res = procesador.ejecutar(solicitud([reg("A")], tiempo=1), RenderizadorFalso(demora=1.3), tmp_path, hoy=HOY)
    assert res.estado == "FALLIDA"
    assert res.incidencias[-1].tipo == "TIEMPO_EXCEDIDO"


def test_periodo_vacio_completa_con_informe_sin_registros(tmp_path):
    res = procesador.ejecutar(solicitud([]), RenderizadorFalso(), tmp_path, hoy=HOY)
    assert res.estado == "COMPLETADA" and res.resumen.registrosValidos == 0


# ---------------------------------------------------------------- API y seguridad

@pytest.fixture()
def cliente(tmp_path, monkeypatch):
    from fastapi.testclient import TestClient

    from app import main
    monkeypatch.setattr(main, "renderizador", RenderizadorFalso())
    monkeypatch.setattr(main, "DIRECTORIO", tmp_path)
    return TestClient(main.app)


def test_api_exige_clave_de_servicio(cliente):
    cuerpo = solicitud([reg("A")]).model_dump()
    assert cliente.post("/interno/rpa/ejecuciones", json=cuerpo).status_code == 401
    assert cliente.post("/interno/rpa/ejecuciones", json=cuerpo, headers={"X-Service-Key": "otra"}).status_code == 401
    r = cliente.post("/interno/rpa/ejecuciones", json=cuerpo, headers={"X-Service-Key": "clave-de-prueba"})
    assert r.status_code == 200 and r.json()["estado"] in ("COMPLETADA", "FALLIDA")


def test_trabajador_no_tiene_acceso_a_base_de_datos():
    """Regla de arquitectura: ni variables de BD ni drivers en el trabajador."""
    raiz = Path(__file__).parents[1]
    codigo = "\n".join(p.read_text(encoding="utf-8") for p in (raiz / "app").rglob("*.py")).lower()
    for prohibido in ("psycopg", "sqlalchemy", "asyncpg", "jdbc:", "db_password", "db_url"):
        assert prohibido not in codigo, prohibido
    requisitos = (raiz / "requirements.txt").read_text(encoding="utf-8").lower()
    assert "psycopg" not in requisitos and "sqlalchemy" not in requisitos
