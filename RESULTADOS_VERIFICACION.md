# Resultados de verificación – Proyecto 25
Fecha y hora de ejecución: 19/11/2026, de 00:43 a 00:52 (hora de Bolivia, UTC-4)
Equipo (sistema operativo, CPU, RAM): Microsoft Windows 11 Pro 10.0.26300 · AMD Ryzen 7 5800X 8-Core Processor (16 hilos) · 31,9 GB RAM
Versiones: Java 21.0.10 (Oracle, el que usa Maven vía JAVA_HOME; el `java` del PATH es Temurin 17.0.21) · Maven 3.9.12 · Docker 29.9.2 (Docker Desktop, Compose v5.6.1) · Node v24.22.1 (npm 11.19.1) · Python 3.14.4

Salida de `java -version` (PATH):
```
openjdk version "17.0.21" 2026-10-20
OpenJDK Runtime Environment Temurin-17.0.21+9 (build 17.0.21+9)
```
Salida de `mvn -version`:
```
Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
Java version: 21.0.10, vendor: Oracle Corporation, runtime: C:\Program Files\Java\jdk-21.0.10
```

## 1. Backend (mvn test)
Comando: `cd backend && mvn -B test` (Testcontainers con `postgres:16-alpine` en Docker; no fue necesario `PRUEBAS_DB_URL`).

Línea final de Maven: `Tests run: 113, Failures: 0, Errors: 0, Skipped: 0` → `BUILD SUCCESS` (Total time: 44.485 s)

| Clase de prueba | Ejecutadas | Fallidas | Errores | Omitidas | Tiempo (s) |
|---|---|---|---|---|---|
| academico.AcademicoIntegracionTest | 4 | 0 | 0 | 0 | 10.647 |
| comun.AlmacenamientoServiceTest | 7 | 0 | 0 | 0 | 0.051 |
| comun.ArquitecturaTest | 3 | 0 | 0 | 0 | 0.040 |
| documental.DocumentalIntegracionTest | 4 | 0 | 0 | 0 | 1.171 |
| institucional.InstitucionalIntegracionTest | 3 | 0 | 0 | 0 | 0.978 |
| institucional.ReglasInstitucionalesTest | 3 | 0 | 0 | 0 | 0.004 |
| reportes.InteligenciaIntegracionTest | 5 | 0 | 0 | 0 | 7.096 |
| rpa.RpaIntegracionTest | 5 | 0 | 0 | 0 | 4.471 |
| seguridad.JwtServiceTest | 6 | 0 | 0 | 0 | 0.131 |
| seguridad.RolServiceTest | 3 | 0 | 0 | 0 | 0.370 |
| seguridad.SeguridadIntegracionTest | 42 | 0 | 0 | 0 | 8.769 |
| seguridad.UsuarioBloqueoTest | 4 | 0 | 0 | 0 | 0.005 |
| tramites.MaquinaEstadosTramiteTest | 19 | 0 | 0 | 0 | 0.045 |
| tramites.TramitesIntegracionTest | 5 | 0 | 0 | 0 | 1.025 |
| **Total (14 clases)** | **113** | **0** | **0** | **0** | |

(Paquete base de todas las clases: `bo.edu.uagrm.psicologia`.)

Fallas: ninguna. Omitidas: ninguna (Testcontainers detectó Docker: `Docker host IP address is localhost` · `Creating container for image: postgres:16-alpine`).

Nota: la solicitud esperaba 69 pruebas; el proyecto actual tiene y ejecutó **113**. La cifra 69 no aparece en el README ni en `docs/`.

## 2. Python y frontend
- rpa-worker: **14 passed**, 0 failed (`14 passed, 1 warning in 1.55s`) — ejecutado en la imagen `mcr.microsoft.com/playwright/python:v1.49.1-jammy` (Python 3.10.12), ver sección 7.
- servicios-ia: **16 passed**, 0 failed (`16 passed, 1 warning in 2.05s`) — ejecutado en la imagen del servicio `proyecto25-servicios-ia` (Python 3.11.18), ver sección 7.
- frontend vitest: **6 passed**, 0 failed (`Test Files 2 passed (2)` · `Tests 6 passed (6)`, vitest v2.1.9) · tsc: **sin errores** (`npx tsc --noEmit`, código de salida 0)

La única advertencia en ambos servicios Python es la misma: `DeprecationWarning: The anyio.abc.BlockingPortal alias is deprecated` (proviene de `starlette/testclient.py`, no del código del proyecto).

## 3. Sistema levantado
`docker compose build` → las 4 imágenes construidas (`proyecto25-nginx`, `proyecto25-rpa-worker`, `proyecto25-servicios-ia`, `proyecto25-backend`), sin necesidad de `docker build` manual. `.env` ya existía y no se modificó.

Salida de `docker compose ps` (tras reiniciar con base limpia, ver sección 7):
```
NAME                        IMAGE                     COMMAND                  SERVICE        CREATED          STATUS                    PORTS
proyecto25-backend-1        proyecto25-backend        "java -XX:MaxRAMPerc…"   backend        17 seconds ago   Up 11 seconds (healthy)   8080/tcp
proyecto25-mailhog-1        mailhog/mailhog:v1.0.1    "MailHog"                mailhog        17 seconds ago   Up 17 seconds             127.0.0.1:8025->8025/tcp
proyecto25-nginx-1          proyecto25-nginx          "/docker-entrypoint.…"   nginx          17 seconds ago   Up 11 seconds             0.0.0.0:80->80/tcp, [::]:80->80/tcp
proyecto25-postgres-1       postgres:16-alpine        "docker-entrypoint.s…"   postgres       17 seconds ago   Up 17 seconds (healthy)   5432/tcp
proyecto25-rpa-worker-1     proyecto25-rpa-worker     "uvicorn app.main:ap…"   rpa-worker     17 seconds ago   Up 16 seconds (healthy)   8000/tcp
proyecto25-servicios-ia-1   proyecto25-servicios-ia   "uvicorn app.main:ap…"   servicios-ia   17 seconds ago   Up 16 seconds (healthy)   8000/tcp
```
URLs verificadas: localhost (sí, HTTP 200) · /api/docs (sí, HTTP 200, redirige a `/api/swagger-ui/index.html`) · MailHog (sí, HTTP 200)

## 4. Punta a punta y rendimiento
- E2E RPA: resultado **SUPERADA** (código de salida 0) · oficios procesados **46** (46/46 válidos, octubre 2026) · tiempo de la ejecución RPA **1789 ms ≈ 1,8 s** (de PENDIENTE a COMPLETADA, según `duracionMs`) · duración total del script 5,2 s
- E2E por la interfaz: resultado **SUPERADA en 14 s** (septiembre 2026) · capturas generadas: **6** (`evidencias/01…06`)
- Carga (20 usuarios, 60 s): 2346 peticiones en 61 s (38,5 por segundo), 0 errores · **¿cumple p95 < 3 s? SÍ** (p95 máximo 0,038 s)

Ejecuciones RPA registradas durante la verificación (consulta a `/api/rpa/ejecuciones`):

| Ejecución | Periodo | Estado | Duración | Válidos/procesados | Situación | Correo |
|---|---|---|---|---|---|---|
| 1 | 01/10/2026 – 31/10/2026 (script E2E RPA) | COMPLETADA | 1789 ms | 46/46 | ENVIADA | ENVIADO |
| 2 | 01/09/2026 – 30/09/2026 (script E2E UI) | COMPLETADA | 815 ms | 47/47 | ENVIADA | ENVIADO |
| 3 | 01/08/2026 – 31/08/2026 (capturas del documento) | COMPLETADA | 812 ms | 49/49 | PENDIENTE_REVISION | BORRADOR |

### Salida de `python scripts/prueba_e2e_rpa.py http://localhost`
```
1) Login Apoyo
   oficios autorizados oct/2026: 46 · sin autorizar: 3
2) Ejecutar RPA (octubre 2026, revisor Director)
   202 ejecución 1 PENDIENTE
   COMPLETADA en 1789 ms · válidos 46/46 · situación PENDIENTE_REVISION
     - [BACK] SOLICITUD              OK          29 ms
     - [BACK] ENVIO_TRABAJADOR       OK          1671 ms
     - [TRAB] RECEPCION              OK          0 ms
     - [TRAB] VALIDACION             OK          0 ms
     - [TRAB] CONSOLIDACION          OK          0 ms
     - [TRAB] PLANTILLA              OK          2 ms
     - [TRAB] VERIFICACION_NAVEGADOR OK          1197 ms
     - [TRAB] PDF                    OK          379 ms
     - [TRAB] BORRADOR_CORREO        OK          0 ms
     - [BACK] REGISTRO               OK          27 ms
   correos en SMTP tras ejecutar: ver MailHog (debe ser 0: nada se envía sin aprobación)
3) Login Director y revisión
   notificaciones del Director: ['Informe asignado para su revisión']
   PDF vista previa: 200 61588 bytes, empieza con b'%PDF-'
4) Aprobar y autorizar envío -> 200 informe APROBADO · correo ENVIADO · autoriza Mariela Rosales Vaca
5) Correos recibidos por el SMTP: ver MailHog en http://localhost:8025
   segunda aprobación -> 409 · correos en SMTP: ver MailHog
6) Auditoría: ['CORREO_ENVIADO', 'AUTORIZAR_ENVIO', 'APROBAR_INFORME', 'NOTIFICAR_REVISOR', 'RPA_EJECUCION_COMPLETADA', 'RPA_INICIAR']
```
Comprobación en MailHog (`/api/v2/messages`) tras esta prueba: 1 correo, asunto "Informe de correspondencia - octubre de 2026 - Dirección de Carrera de Psicología", para `decanato.demo@ejemplo.edu.bo`.

### Salida de `python scripts/prueba_e2e_ui.py --url http://localhost --mailhog http://localhost:8025 --desde 2026-09-01 --hasta 2026-09-30`
```
1. Apoyo Profesional ejecuta la consolidación RPA
  [01] apoyo_formulario_rpa
  [02] apoyo_rpa_completado
   OK: informe en PENDIENTE_REVISION y ningún correo enviado
2. Director revisa y autoriza el envío
  [03] director_revision_informe
  [04] director_informe_enviado
3. Verificación del correo en el servidor SMTP
   OK: 'Informe de correspondencia - septiembre de 2026 - Dirección de Carrera de Psicología' para decanato.demo@ejemplo.edu.bo con adjunto(s) ['attachment; \tfilename=informe-correspondencia-2026-09-01_2026-09-30.pdf']
  [05] mailhog_correo_recibido
4. Auditoría registrada
  [06] auditoria_correo_enviado
   OK: RPA_INICIAR, RPA_EJECUCION_COMPLETADA, APROBAR_INFORME, AUTORIZAR_ENVIO y CORREO_ENVIADO registrados

PRUEBA DE PUNTA A PUNTA SUPERADA en 14 s · evidencias en C:\xampp\htdocs\taller\proyecto25\evidencias
```
Capturas generadas (19/11/2026 00:48): `evidencias/01_apoyo_formulario_rpa.png`, `02_apoyo_rpa_completado.png`, `03_director_revision_informe.png`, `04_director_informe_enviado.png`, `05_mailhog_correo_recibido.png`, `06_auditoria_correo_enviado.png` (sobrescribieron las del 16/11/2026).

### Salida de `python scripts/prueba_carga.py http://localhost 20 60`
```
Prueba de carga: 20 usuarios concurrentes durante 60 s contra http://localhost/api

2346 peticiones en 61 s (38.5 por segundo), 0 errores

Consulta                         n   p50 (s)   p95 (s)   máx (s)  resultado
/alertas                       145     0.007     0.029     0.063  OK
/auditoria                     140     0.007     0.029     0.033  OK
/busqueda                      118     0.010     0.032     0.090  OK
/consultas/tramites            121     0.013     0.036     0.081  OK
/correspondencia               381     0.010     0.031     0.052  OK
/dashboard                     110     0.018     0.038     0.116  OK
/docentes                      140     0.010     0.032     0.047  OK
/documentos                    129     0.007     0.027     0.030  OK
/estudiantes                   126     0.008     0.030     0.043  OK
/grupos                        135     0.017     0.035     0.046  OK
/rpa/ejecuciones               275     0.012     0.033     0.054  OK
/rpa/metricas                  147     0.009     0.031     0.056  OK
/tramites                      379     0.015     0.036     0.089  OK

Resultado: CUMPLE (p95 < 3 s con 20 usuarios concurrentes)
```

## 5. Capturas
Archivos en `evidencias/documento/` (tomadas con Playwright, viewport 1440 px de ancho, usuarios `director` y `apoyo` del README):

| Archivo | Contenido |
|---|---|
| `01_login.png` | Pantalla de inicio de sesión |
| `02_dashboard_director.png` | Dashboard institucional del Director (Mariela Rosales Vaca) |
| `03_tramites.png` | Lista de trámites |
| `04_detalle_tramite.png` | Trámite TRM-2026-0054 (Observado) con historial Recepcionado → En revisión → Observado |
| `05_rpa_ejecutar.png` | Formulario de consolidación RPA (Apoyo, agosto 2026) |
| `06_rpa_revision.png` | Informe #3 pendiente de revisión: vista previa del PDF (4 páginas), borrador de correo y resumen consolidado |
| `07_mailhog_correo.png` | Correo de septiembre 2026 en MailHog, pestaña MIME con `application/pdf; name=informe-correspondencia-2026-09-01_2026-09-30.pdf (84745 bytes)` |
| `08_swagger.png` | Documentación Swagger UI de la API |

Nota: la carpeta `evidencias/` está en `.gitignore`, por lo que estas imágenes no se suben al repositorio.

## 6. Datos del repositorio
- Primer commit: `2026-11-18 Faridh Torres Commit inicial del proyecto`
- Último commit: `2026-11-18`
- Commits por integrante (`git shortlog -sn HEAD`): `1	Faridh Torres`
- Nota: el repositorio se inicializó el 18/11/2026 con un único commit que contiene todo el proyecto; el historial no refleja el desarrollo previo ni la participación de cada integrante.

`cloc` **no está instalado** (`cloc: command not found`). Como sustituto (no es cloc: cuenta líneas físicas, incluidas las vacías y los comentarios), conteo sobre los archivos versionados con `git ls-files`:

| Extensión | Archivos | Líneas |
|---|---|---|
| .java | 177 | 14 681 |
| .tsx | 38 | 4 749 |
| .sql | 9 | 2 586 |
| .py | 21 | 1 690 |
| .ts | 7 | 981 |
| .md | 3 | 554 |
| .yml | 2 | 192 |
| .sh | 2 | 87 |
| .html | 2 | 43 |
| .css | 1 | 39 |

## 7. Problemas encontrados
1. **`pip install -r requirements-dev.txt` en rpa-worker falló con Python 3.14.4** (el único instalado):
   ```
   error: subprocess-exited-with-error
   Building wheel for greenlet (pyproject.toml) did not run successfully.
   Failed to build installable wheels for some pyproject.toml based projects
   greenlet
   C:\Python314\python.exe: No module named pytest
   ```
   Causa: las versiones fijadas (`playwright==1.49.1` → `greenlet`) no tienen paquetes precompilados para Python 3.14. Solución de entorno: las pruebas se ejecutaron en la imagen oficial que usa el propio `rpa-worker/Dockerfile` (`mcr.microsoft.com/playwright/python:v1.49.1-jammy`, Python 3.10.12) sin cambiar código ni pruebas.
2. **servicios-ia: mismo problema previsto con Python 3.14** (`numpy==2.1.3`, `scikit-learn==1.5.2` sin paquetes para 3.14). Las pruebas se ejecutaron dentro de la imagen construida del servicio (`proyecto25-servicios-ia`, Python 3.11.18, con Tesseract y el modelo ya incluidos), instalando solo `pytest` y `httpx`.
3. **Primer intento de `prueba_e2e_rpa.py` falló** porque la base de datos de Docker conservaba los datos de una ejecución anterior (16/11/2026), con informes ya aprobados para septiembre y octubre de 2026:
   ```
   Traceback (most recent call last):
     File "C:\xampp\htdocs\taller\proyecto25\scripts\prueba_e2e_rpa.py", line 24, in <module>
       print("  ", s, "ejecución", e["id"], e["estado"])
                                   ~^^^^^^
   KeyError: 'id'
   ```
   La API respondió (correctamente, por regla de negocio): `409 · "Ya existe el informe 2 (APROBADO) para ese periodo y tipo; no se generan informes duplicados. Si fue devuelto, use la reejecución."`
   Solución de entorno: respaldo de la base (`pg_dump`, `respaldos/antes_verificacion_20261119.dump`) y del volumen de documentos (`respaldos/documentos_antes_verificacion_20261119.tgz`), luego `docker compose down -v` y `docker compose up -d` para partir de la base inicial (migraciones + datos de desarrollo). Después de eso, todas las pruebas pasaron.
4. **Primera captura de `06_rpa_revision.png` sin PDF**: el Chromium sin interfaz (headless) de Playwright no incluye el visor de PDF. Se tomó de nuevo con el navegador visible.
5. `java -version` del PATH muestra JDK 17 (Temurin 17.0.21), aunque Maven usa el JDK 21 vía `JAVA_HOME`. No afectó al build.
6. `npm ci` (npm 11) bloqueó el script de instalación de `esbuild@0.21.5` (`npm warn install-scripts ... Run npm install-scripts ls to review`); vitest y tsc funcionaron igual.
7. Diferencia de cantidad de pruebas: se esperaban 69 y el backend tiene 113 (todas ejecutadas, ninguna omitida).
