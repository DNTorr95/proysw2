#!/usr/bin/env bash
# =====================================================================
# Proyecto 25 - Restauración de un respaldo (base de datos + volumen documental).
#
# Uso:   ./scripts/restaurar.sh 2026-11-16_0130 [--si]
# Detiene el backend, verifica las sumas SHA-256, restaura la base y los documentos
# y vuelve a levantar el backend. SOBRESCRIBE los datos actuales.
# =====================================================================
set -euo pipefail
# En Git Bash (Windows) evita que las rutas internas del contenedor se conviertan en rutas de Windows
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*"

cd "$(dirname "$0")/.."
set -a; [ -f .env ] && . ./.env; set +a

FECHA="${1:?Indique la fecha del respaldo, p. ej. 2026-11-16_0130}"
DESTINO="$(cd "${RESPALDO_DIR:-./respaldos}" && pwd)"
PROYECTO="$(docker compose config --format json 2>/dev/null | python3 -c 'import json,sys;print(json.load(sys.stdin)["name"])' 2>/dev/null || echo proyecto25)"
VOLUMEN="${PROYECTO}_documentos"

for f in "bd_${FECHA}.dump" "documentos_${FECHA}.tar.gz" "manifiesto_${FECHA}.sha256"; do
    [ -f "$DESTINO/$f" ] || { echo "No existe $DESTINO/$f"; exit 1; }
done
(cd "$DESTINO" && sha256sum -c "manifiesto_${FECHA}.sha256")

if [ "${2:-}" != "--si" ]; then
    read -r -p "Se reemplazarán la base de datos y los documentos actuales por el respaldo ${FECHA}. ¿Continuar? (escriba SI) " r
    [ "$r" = "SI" ] || { echo "Cancelado"; exit 1; }
fi

docker compose stop backend
docker compose exec -T postgres pg_restore -U "$DB_USER" -d "$DB_NAME" --clean --if-exists --no-owner < "$DESTINO/bd_${FECHA}.dump"
docker run --rm -i -v "${VOLUMEN}:/datos" alpine:3.20 \
    sh -c "find /datos -mindepth 1 -delete && tar xzf - -C /datos" < "$DESTINO/documentos_${FECHA}.tar.gz"
docker compose start backend
echo "Restauración de ${FECHA} completada"
