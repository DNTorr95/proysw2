# Imagen oficial de Playwright para Python: incluye Chromium y sus dependencias del sistema.
FROM mcr.microsoft.com/playwright/python:v1.49.1-jammy
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
RUN groupadd -g 1001 rpa && useradd -r -u 1001 -g 1001 -m rpa && mkdir -p /var/storage/documentos && chown rpa /var/storage/documentos
USER rpa
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
    CMD python -c "import urllib.request;urllib.request.urlopen('http://localhost:8000/salud')" || exit 1
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
