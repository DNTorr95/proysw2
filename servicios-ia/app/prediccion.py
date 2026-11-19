"""CU-29 Análisis predictivo: probabilidad de demora de un trámite (Scikit-learn).

El backend envía los trámites históricos cerrados (características + si se demoró) para entrenar, y los
trámites abiertos para estimar. El modelo vive en memoria; no se guardan datos personales.
"""

from __future__ import annotations

import threading
from dataclasses import dataclass

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


@dataclass
class Metricas:
    ejemplos: int
    positivos: int
    exactitud: float | None
    modelo: str


class ModeloDemora:
    def __init__(self):
        self._pipeline: Pipeline | None = None
        self._metricas: Metricas | None = None
        self._lock = threading.Lock()

    @staticmethod
    def _matriz(filas: list[dict]):
        cat = np.array([[str(f.get(c, "")) for c in CATEGORICAS] for f in filas], dtype=object)
        num = np.array([[float(f.get(c) or 0) for c in NUMERICAS] for f in filas], dtype=float)
        return np.hstack([cat, num])

    def entrenar(self, historico: list[dict]) -> Metricas:
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
        with self._lock:
            self._pipeline = pipeline
            self._metricas = Metricas(len(y), int(y.sum()), None if exactitud is None else round(exactitud, 3), "RandomForest")
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
