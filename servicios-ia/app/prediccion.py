"""CU-29 Análisis predictivo: probabilidad de demora de un trámite (Scikit-learn).

El backend envía los trámites históricos cerrados (características + si se demoró) para entrenar, y los
trámites abiertos para estimar. No se guardan datos personales.

Persistencia y versiones: si se indica un directorio (variable MODELOS_DIR en Docker, volumen "modelos-ia"),
cada entrenamiento se guarda como demora-v<versión>.joblib junto con sus metadatos (fecha, ejemplos,
exactitud, SHA-256) en vigente.json. Al reiniciar el servicio se carga la versión vigente y se comprueba su
SHA-256. Las versiones anteriores se conservan. Sin directorio (pruebas locales) el modelo vive en memoria.
"""

from __future__ import annotations

import hashlib
import json
import logging
import threading
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path

import joblib

import numpy as np
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import StratifiedKFold, cross_val_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

CATEGORICAS = ["tipo", "prioridad"]
# Sin "días transcurridos": en el histórico filtraría la respuesta (duración real) y sobreestimaría la exactitud
NUMERICAS = ["plazoDias", "requisitosPendientes", "observaciones", "mesRecepcion"]
MINIMO_EJEMPLOS = 12


class ErrorPrediccion(Exception):
    pass


log = logging.getLogger("servicios-ia.prediccion")


@dataclass
class Metricas:
    ejemplos: int
    positivos: int
    exactitud: float | None
    modelo: str
    version: int | None = None
    fecha: str | None = None
    sha256: str | None = None


class ModeloDemora:
    def __init__(self, directorio: Path | str | None = None):
        self._pipeline: Pipeline | None = None
        self._metricas: Metricas | None = None
        self._lock = threading.Lock()
        self._dir = Path(directorio) if directorio else None
        if self._dir:
            self._dir.mkdir(parents=True, exist_ok=True)
            self._cargar_vigente()

    # ---------------- persistencia ----------------

    def _cargar_vigente(self) -> None:
        meta = self._dir / "vigente.json"
        if not meta.is_file():
            return
        try:
            m = Metricas(**json.loads(meta.read_text(encoding="utf-8")))
            archivo = self._dir / f"demora-v{m.version}.joblib"
            if hashlib.sha256(archivo.read_bytes()).hexdigest() != m.sha256:
                log.error("El modelo %s no coincide con su SHA-256; se ignora", archivo.name)
                return
            self._pipeline, self._metricas = joblib.load(archivo), m
            log.info("Modelo predictivo v%s cargado (%s)", m.version, m.fecha)
        except Exception as e:  # noqa: BLE001 - un archivo dañado no debe impedir que el servicio arranque
            log.error("No se pudo cargar el modelo vigente: %s", e)

    def _guardar(self, pipeline: Pipeline, m: Metricas) -> Metricas:
        archivo = self._dir / f"demora-v{m.version}.joblib"
        temporal = archivo.with_suffix(".tmp")
        joblib.dump(pipeline, temporal)
        temporal.replace(archivo)
        m.sha256 = hashlib.sha256(archivo.read_bytes()).hexdigest()
        (self._dir / "vigente.json").write_text(json.dumps(asdict(m), ensure_ascii=False), encoding="utf-8")
        return m

    def versiones(self) -> list[int]:
        if not self._dir:
            return []
        return sorted(int(p.stem.split("-v")[1]) for p in self._dir.glob("demora-v*.joblib"))

    @staticmethod
    def _matriz(filas: list[dict]):
        cat = np.array([[str(f.get(c, "")) for c in CATEGORICAS] for f in filas], dtype=object)
        num = np.array([[float(f.get(c) or 0) for c in NUMERICAS] for f in filas], dtype=float)
        return np.hstack([cat, num])

    def entrenar(self, historico: list[dict], version: int | None = None) -> Metricas:
        if len(historico) < MINIMO_EJEMPLOS:
            raise ErrorPrediccion(f"Se necesitan al menos {MINIMO_EJEMPLOS} trámites históricos (hay {len(historico)})")
        y = np.array([1 if h["demorado"] else 0 for h in historico])
        if len(set(y)) < 2:
            raise ErrorPrediccion("El histórico debe tener trámites demorados y a tiempo")
        x = self._matriz(historico)
        n_cat = len(CATEGORICAS)
        pipeline = Pipeline([
            ("prep", ColumnTransformer([
                ("cat", OneHotEncoder(handle_unknown="ignore"), list(range(n_cat))),
                ("num", "passthrough", list(range(n_cat, n_cat + len(NUMERICAS)))),
            ])),
            ("rf", RandomForestClassifier(n_estimators=150, max_depth=6, min_samples_leaf=2,
                                          class_weight="balanced", random_state=25)),
        ])
        pliegues = min(5, int(min(np.bincount(y))))
        exactitud = None
        if pliegues >= 2:
            exactitud = float(np.mean(cross_val_score(pipeline, x, y, cv=StratifiedKFold(pliegues, shuffle=True, random_state=25))))
        pipeline.fit(x, y)
        if version is None:
            version = (self._metricas.version or 0) + 1 if self._metricas else 1
        m = Metricas(len(y), int(y.sum()), None if exactitud is None else round(exactitud, 3), "RandomForest",
                     version, datetime.now(timezone.utc).isoformat(timespec="seconds"))
        with self._lock:
            if self._dir:
                m = self._guardar(pipeline, m)
            self._pipeline = pipeline
            self._metricas = m
        return self._metricas

    @property
    def entrenado(self) -> bool:
        return self._pipeline is not None

    @property
    def metricas(self) -> Metricas | None:
        return self._metricas

    def predecir(self, filas: list[dict]) -> list[float]:
        with self._lock:
            if self._pipeline is None:
                raise ErrorPrediccion("El modelo no está entrenado")
            if not filas:
                return []
            return [round(float(p), 4) for p in self._pipeline.predict_proba(self._matriz(filas))[:, 1]]
