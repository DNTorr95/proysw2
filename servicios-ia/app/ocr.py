"""CU-21 OCR: extrae texto de una imagen o PDF del volumen documental (Tesseract, idioma spa).

Interfaz reemplazable: cualquier motor que implemente `MotorOcr.extraer(bytes, extension) -> (texto, confianza)`.
"""

from __future__ import annotations

import io
import os
from pathlib import Path
from typing import Protocol

DIRECTORIO = Path(os.environ.get("STORAGE_DIR", "./almacenamiento")).resolve()
EXTENSIONES = {".pdf", ".png", ".jpg", ".jpeg"}
MAX_PAGINAS_PDF = 10


class ErrorOcr(Exception):
    """Archivo inexistente, tipo no soportado o fallo del motor."""


class MotorOcr(Protocol):
    def extraer(self, contenido: bytes, extension: str) -> tuple[str, float]: ...


def resolver_ruta(relativa: str, base: Path = DIRECTORIO) -> Path:
    """Ruta dentro del volumen (impide salir con ../)."""
    p = (base / relativa).resolve()
    if not str(p).startswith(str(base)):
        raise ErrorOcr("Ruta de archivo inválida")
    if not p.is_file():
        raise ErrorOcr(f"El archivo no existe en el volumen documental: {relativa}")
    if p.suffix.lower() not in EXTENSIONES:
        raise ErrorOcr("Solo se procesan imágenes (JPG, PNG) y PDF")
    return p


class MotorTesseract:
    """Tesseract (spa). Los PDF se rasterizan a 200 ppp con pypdfium2 (sin dependencias del sistema)."""

    def __init__(self, idioma: str = "spa"):
        self.idioma = idioma

    def _imagenes(self, contenido: bytes, extension: str):
        from PIL import Image

        if extension == ".pdf":
            import pypdfium2 as pdfium

            pdf = pdfium.PdfDocument(contenido)
            for i in range(min(len(pdf), MAX_PAGINAS_PDF)):
                yield pdf[i].render(scale=200 / 72).to_pil()
        else:
            yield Image.open(io.BytesIO(contenido))

    def extraer(self, contenido: bytes, extension: str) -> tuple[str, float]:
        import pytesseract

        textos, confianzas = [], []
        try:
            for imagen in self._imagenes(contenido, extension):
                datos = pytesseract.image_to_data(imagen.convert("L"), lang=self.idioma, output_type=pytesseract.Output.DICT)
                palabras = [(t, float(c)) for t, c in zip(datos["text"], datos["conf"]) if t.strip() and float(c) >= 0]
                textos.append(" ".join(t for t, _ in palabras))
                confianzas.extend(c for _, c in palabras)
        except pytesseract.TesseractNotFoundError as e:
            raise ErrorOcr("Tesseract no está instalado en el servicio") from e
        except Exception as e:  # noqa: BLE001
            raise ErrorOcr(f"No se pudo procesar el archivo: {e}") from e
        texto = "\n\n".join(t for t in textos if t).strip()
        confianza = round(sum(confianzas) / len(confianzas), 2) if confianzas else 0.0
        return texto, confianza
