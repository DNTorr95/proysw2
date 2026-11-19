FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 HF_HOME=/opt/modelos
# Tesseract con el idioma español para el OCR
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-spa \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /srv
# PyTorch solo CPU (imagen mucho más liviana) antes del resto de dependencias
RUN pip install --no-cache-dir torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
# Modelo multilingüe descargado en la construcción: el contenedor no necesita Internet en ejecución
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2')"
COPY app ./app
# Grupo 1001 = grupo del volumen documental (backend y rpa-worker). El backend guarda los archivos
# con 0640, así que el OCR los lee por pertenecer al grupo, sin abrirlos a otros usuarios (HU-21).
RUN groupadd -g 1001 documentos \
    && useradd -r -u 1002 -G documentos ia \
    && chmod -R a+rX /opt/modelos
USER ia
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
    CMD python -c "import urllib.request;urllib.request.urlopen('http://localhost:8000/salud')" || exit 1
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
