#!/usr/bin/env bash
# =====================================================================
# Proyecto 25 - Respaldo diario: base de datos (pg_dump) + volumen documental.
#
# Uso (en el servidor, desde la carpeta del proyecto):
#   ./scripts/respaldo.sh
# Programación diaria (crontab -e), por ejemplo a la 01:30:
#   30 1 * * * cd /opt/proyecto25 && ./scripts/respaldo.sh >> /var/log/proyecto25-respaldo.log 2>&1
#
# Variables opcionales:
#   RESPALDO_DIR      carpeta de destino (por defecto ./respaldos)
#   RETENCION_DIAS    días que se conservan los respaldos (por defecto 14)
# =====================================================================
set -euo pipefail
# En Git Bash (Windows) evita que las rutas internas del contenedor se conviertan en rutas de Windows
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*"

cd "$(dirname "$0")/.."
# Variables DB_NAME y DB_USER del .env del proyecto
set -a; [ -f .env ] && . ./.env; set +a

DESTINO="${RESPALDO_DIR:-./respaldos}"
RETENCION="${RETENCION_DIAS:-14}"
FECHA="$(date +%Y-%m-%d_%H%M)"
PROYECTO="$(docker compose config --format json 2>/dev/null | python3 -c 'import json,sys;print(json.load(sys.stdin)["name"])' 2>/dev/null || echo proyecto25)"
VOLUMEN="${PROYECTO}_documentos"

mkdir -p "$DESTINO"
DESTINO="$(cd "$DESTINO" && pwd)"
echo "[$(date -Is)] Inicio del respaldo $FECHA en $DESTINO"

# 1) Base de datos: formato personalizado (comprimido, restaurable por partes)
docker compose exec -T postgres pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc --no-owner > "$DESTINO/bd_${FECHA}.dump"
# Verificación: el volcado debe poder listarse
docker compose exec -T postgres pg_restore --list < "$DESTINO/bd_${FECHA}.dump" > /dev/null
echo "  base de datos: bd_${FECHA}.dump ($(du -h "$DESTINO/bd_${FECHA}.dump" | cut -f1))"

# 2) Volumen documental (PDF, imágenes, adjuntos, informes RPA), montado en solo lectura
#    (el tar sale por la salida estándar: no se monta ninguna carpeta del anfitrión)
docker run --rm -v "${VOLUMEN}:/datos:ro" alpine:3.20 \
    tar czf - -C /datos . > "$DESTINO/documentos_${FECHA}.tar.gz"
echo "  documentos: documentos_${FECHA}.tar.gz ($(du -h "$DESTINO/documentos_${FECHA}.tar.gz" | cut -f1))"

# 3) Manifiesto con sumas SHA-256 para comprobar integridad al restaurar
(cd "$DESTINO" && sha256sum "bd_${FECHA}.dump" "documentos_${FECHA}.tar.gz" > "manifiesto_${FECHA}.sha256")

# 4) Retención
find "$DESTINO" -maxdepth 1 -type f \( -name 'bd_*.dump' -o -name 'documentos_*.tar.gz' -o -name 'manifiesto_*.sha256' \) \
    -mtime +"$RETENCION" -print -delete | sed 's/^/  eliminado por retención: /'

echo "[$(date -Is)] Respaldo $FECHA completado"
