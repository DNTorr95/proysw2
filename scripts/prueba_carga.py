"""
Prueba de carga (requisitos no funcionales): 20 usuarios concurrentes durante N segundos
ejecutando consultas con filtros; verifica que el percentil 95 de cada consulta sea < 3 s.

Uso:  python scripts/prueba_carga.py http://localhost [usuarios=20] [segundos=60]
Solo usa la biblioteca estándar de Python.
"""

import json
import random
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost").rstrip("/") + "/api"
USUARIOS = int(sys.argv[2]) if len(sys.argv) > 2 else 20
SEGUNDOS = int(sys.argv[3]) if len(sys.argv) > 3 else 60
LIMITE_P95 = 3.0

CUENTAS = [("director", "Director2026*"), ("coordinador", "Coordinador2026*"), ("apoyo", "Apoyo2026*")]
CONSULTAS = {
    "director": ["/dashboard", "/tramites?estado=EN_REVISION", "/alertas?estado=ABIERTAS", "/auditoria?accion=LOGIN",
                 "/rpa/ejecuciones", "/correspondencia?tipo=RECIBIDA&desde=2026-08-01&hasta=2026-10-31"],
    "coordinador": ["/tramites?vencidos=true", "/grupos?idPeriodo=2", "/busqueda?texto=solicitud", "/correspondencia?texto=aulas",
                    "/rpa/metricas", "/docentes?idPeriodo=2"],
    "apoyo": ["/correspondencia?autorizado=true&desde=2026-09-01&hasta=2026-09-30", "/tramites?texto=TRM", "/documentos",
              "/consultas/tramites?prioridad=ALTA", "/estudiantes?texto=ana", "/rpa/ejecuciones"],
}

tiempos = defaultdict(list)
errores = defaultdict(int)
candado = threading.Lock()


def llamar(ruta, token=None, cuerpo=None):
    req = urllib.request.Request(BASE + ruta, data=None if cuerpo is None else json.dumps(cuerpo).encode(),
                                 method="POST" if cuerpo is not None else "GET")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read() or b"null")


def usuario_virtual(n):
    nombre, clave = CUENTAS[n % len(CUENTAS)]
    token = llamar("/auth/login", cuerpo={"nombreUsuario": nombre, "contrasena": clave})["token"]
    fin = time.monotonic() + SEGUNDOS
    while time.monotonic() < fin:
        ruta = random.choice(CONSULTAS[nombre])
        clave_ruta = ruta.split("?")[0]
        t0 = time.monotonic()
        try:
            llamar(ruta, token)
            with candado:
                tiempos[clave_ruta].append(time.monotonic() - t0)
        except (urllib.error.URLError, TimeoutError):
            with candado:
                errores[clave_ruta] += 1
        time.sleep(random.uniform(0.2, 0.8))   # tiempo de "lectura" del usuario


def percentil(valores, p):
    v = sorted(valores)
    return v[min(len(v) - 1, int(round(p / 100 * (len(v) - 1))))]


def main():
    print(f"Prueba de carga: {USUARIOS} usuarios concurrentes durante {SEGUNDOS} s contra {BASE}")
    inicio = time.monotonic()
    with ThreadPoolExecutor(max_workers=USUARIOS) as ex:
        list(ex.map(usuario_virtual, range(USUARIOS)))
    total = sum(len(v) for v in tiempos.values())
    duracion = time.monotonic() - inicio
    print(f"\n{total} peticiones en {duracion:.0f} s ({total / duracion:.1f} por segundo), {sum(errores.values())} errores\n")
    print(f"{'Consulta':<28}{'n':>6}{'p50 (s)':>10}{'p95 (s)':>10}{'máx (s)':>10}  resultado")
    ok = True
    for ruta in sorted(tiempos):
        v = tiempos[ruta]
        p95 = percentil(v, 95)
        cumple = p95 < LIMITE_P95 and errores[ruta] == 0
        ok &= cumple
        print(f"{ruta:<28}{len(v):>6}{statistics.median(v):>10.3f}{p95:>10.3f}{max(v):>10.3f}  {'OK' if cumple else 'NO CUMPLE'}")
    print("\nResultado:", "CUMPLE (p95 < 3 s con 20 usuarios concurrentes)" if ok else "NO CUMPLE")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
