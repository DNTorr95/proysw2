"""CU-24 Búsqueda semántica.

El backend envía el corpus autorizado a indexar (id + texto: asunto, descripción y texto OCR) y luego consulta.
El servicio devuelve ids y relevancia; el filtrado por permisos lo hace siempre el backend.

Motores intercambiables (interfaz `Codificador`):
- SentenceTransformers multilingüe (por defecto: paraphrase-multilingual-MiniLM-L12-v2).
- TF-IDF de scikit-learn como respaldo si el modelo no está disponible (o SEMANTICA_MOTOR=tfidf).

Con SentenceTransformers los vectores tienen dimensión fija (384) y el backend los guarda en PostgreSQL con
pgvector (POST /interno/embeddings). TF-IDF no tiene dimensión fija: en ese caso se usa el índice en memoria.
"""

from __future__ import annotations

import logging
import os
import threading
import unicodedata
from typing import Protocol

import numpy as np

log = logging.getLogger("busqueda")
MODELO = os.environ.get("SEMANTICA_MODELO", "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2")


def normalizar(texto: str) -> str:
    texto = unicodedata.normalize("NFD", texto.lower())
    return "".join(c for c in texto if unicodedata.category(c) != "Mn")


class Codificador(Protocol):
    nombre: str

    def ajustar(self, textos: list[str]) -> None: ...

    def codificar(self, textos: list[str]) -> np.ndarray: ...


class CodificadorSentence:
    vectores_fijos = True

    def __init__(self, modelo: str = MODELO):
        from sentence_transformers import SentenceTransformer

        self._modelo = SentenceTransformer(modelo, device="cpu")
        self.nombre = f"sentence-transformers:{modelo.split('/')[-1]}"

    def ajustar(self, textos: list[str]) -> None:
        return None

    def codificar(self, textos: list[str]) -> np.ndarray:
        return np.asarray(self._modelo.encode(textos, normalize_embeddings=True, batch_size=32, show_progress_bar=False))


class CodificadorTfidf:
    nombre = "tfidf"
    vectores_fijos = False  # el vocabulario depende del corpus: no sirve para guardar vectores

    def __init__(self):
        from sklearn.feature_extraction.text import TfidfVectorizer

        # n-gramas de caracteres: tolera variaciones morfológicas y errores de OCR
        self._vec = TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), preprocessor=normalizar, sublinear_tf=True)
        self._ajustado = False

    def ajustar(self, textos: list[str]) -> None:
        self._vec.fit(textos or ["vacio"])
        self._ajustado = True

    def codificar(self, textos: list[str]) -> np.ndarray:
        if not self._ajustado:
            raise RuntimeError("El índice TF-IDF no está ajustado")
        m = self._vec.transform(textos).toarray()
        normas = np.linalg.norm(m, axis=1, keepdims=True)
        normas[normas == 0] = 1
        return m / normas


def crear_codificador() -> Codificador:
    if os.environ.get("SEMANTICA_MOTOR", "sentence").lower() == "tfidf":
        return CodificadorTfidf()
    try:
        return CodificadorSentence()
    except Exception as e:  # noqa: BLE001 - sin modelo disponible se degrada a TF-IDF
        log.warning("Modelo semántico no disponible (%s); se usa TF-IDF", e)
        return CodificadorTfidf()


class IndiceSemantico:
    """Índice en memoria (el corpus es pequeño: miles de documentos). Seguro para hilos."""

    def __init__(self, codificador: Codificador | None = None):
        self._codificador = codificador
        self._ids: list[str] = []
        self._matriz: np.ndarray | None = None
        self._lock = threading.Lock()

    @property
    def codificador(self) -> Codificador:
        if self._codificador is None:
            self._codificador = crear_codificador()
        return self._codificador

    def indexar(self, documentos: list[tuple[str, str]]) -> int:
        ids = [d[0] for d in documentos if d[1] and d[1].strip()]
        textos = [d[1].strip()[:4000] for d in documentos if d[1] and d[1].strip()]
        with self._lock:
            self.codificador.ajustar(textos)
            self._matriz = self.codificador.codificar(textos) if textos else None
            self._ids = ids
        return len(ids)

    def vectores(self, textos: list[str]) -> np.ndarray:
        """Vectores normalizados (L2) para guardarlos fuera del servicio. Exige un motor de dimensión fija."""
        if not getattr(self.codificador, "vectores_fijos", False):
            raise ValueError(f"El motor {self.codificador.nombre} no produce vectores de dimensión fija")
        return self.codificador.codificar([(t or "").strip()[:4000] or "vacio" for t in textos])

    @property
    def tamano(self) -> int:
        return len(self._ids)

    def buscar(self, consulta: str, limite: int = 20, minimo: float = 0.15) -> list[tuple[str, float]]:
        with self._lock:
            if self._matriz is None or not self._ids:
                return []
            q = self.codificador.codificar([consulta])[0]
            puntajes = self._matriz @ q
        orden = np.argsort(-puntajes)[:limite]
        return [(self._ids[i], round(float(puntajes[i]), 4)) for i in orden if puntajes[i] >= minimo]
