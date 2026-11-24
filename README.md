# Proyecto 25: Sistema Web de Gestión Administrativa con RPA

Dirección de Carrera de Psicología, UAGRM.

El sistema centraliza los trámites estudiantiles, la correspondencia, los documentos, la información académica, el POA y los reportes. Su componente principal es un trabajador RPA que consolida la correspondencia de un periodo, genera un informe PDF y prepara un borrador de correo. Ningún envío ocurre sin la autorización de una persona.

> **Estado actual: Iteración 6 (transición) completada. Los 37 casos de uso están implementados.**
> - Iteración 1: CU-01 a CU-03, seguridad JWT con permisos por rol y auditoría.
> - Iteración 2: CU-05 a CU-12, con la máquina de estados del trámite en Spring State Machine.
> - Iteración 3: CU-31 a CU-37 y CU-33, con el trabajador RPA (Playwright), la revisión humana, el envío por SMTP y las métricas.
> - Iteración 4: CU-13 a CU-20 y CU-04 (planificación, grupos y cupos, migraciones, carga docente, titulados, trabajos de grado, POA, eventos y transferencia de autoridad).
> - Iteración 5: CU-21 a CU-30 (OCR, búsqueda avanzada y semántica, asistente, dashboard, reportes PDF/XLSX, alertas y análisis predictivo).
> - Iteración 6: despliegue con Docker Compose, respaldos diarios, prueba de carga, prueba de punta a punta por la interfaz, manual de usuario y matriz de trazabilidad.
>
> Documentación: [manual de usuario](docs/MANUAL_USUARIO.md) · [trazabilidad CU → endpoint, permiso, pantalla y prueba](docs/TRAZABILIDAD.md).

## Arquitectura

| Capa | Servicio (contenedor) | Tecnología |
|---|---|---|
| Presentación + proxy | `nginx` | React 18 + Vite + TS + Tailwind servido por Nginx; proxy inverso de `/api` |
| Lógica de negocio | `backend` | Java 21, Spring Boot 3.5, Spring Security (JWT), JPA, Flyway, springdoc |
| Datos | `postgres` | PostgreSQL 16 y volumen `documentos` en `/var/storage/documentos` |
| RPA | `rpa-worker` | Python 3.11 + FastAPI + Playwright (Chromium) + Jinja2 (sandbox) |
| Servicios inteligentes | `servicios-ia` | Python 3.11 + FastAPI: Tesseract (OCR, spa), Sentence-Transformers con respaldo TF-IDF (búsqueda), Scikit-learn (predicción) y asistente por reglas |
| Correo (desarrollo) | `mailhog` | Interfaz web en http://localhost:8025 |

Reglas que se cumplen desde esta iteración:
- `rpa-worker` y `servicios-ia` **no reciben credenciales de la base de datos** (revisar `docker-compose.yml`).
- Solo se publican al exterior `nginx` (80) y la interfaz de MailHog. Los servicios internos exigen la cabecera `X-Service-Key`.
- Toda operación sensible queda registrada en `bitacora_auditoria`: usuario, acción, entidad, id, fecha y hora, IP y detalle.

## Puesta en marcha con Docker

```bash
cp .env.example .env        # cambiar contraseñas, JWT_SECRET y SERVICE_KEY
docker compose up -d --build
```

- Aplicación: http://localhost
- Documentación OpenAPI (Swagger UI): http://localhost/api/docs
- MailHog: http://localhost:8025

Al primer arranque, Flyway crea las 44 tablas y carga los datos ficticios. La primera construcción descarga unos 3–4 GB (Playwright con Chromium, PyTorch en CPU y el modelo de Sentence-Transformers).

> **Windows con Docker Desktop y Compose v5:** si `docker compose up --build` falla al delegar en *bake* (falta de privilegios), construya las imágenes una por una y luego levante sin `--build`:
> ```bash
> docker build -t proyecto25-backend      -f backend/Dockerfile      backend
> docker build -t proyecto25-rpa-worker   -f rpa-worker/Dockerfile   rpa-worker
> docker build -t proyecto25-nginx        -f frontend/Dockerfile     .
> docker build -t proyecto25-servicios-ia -f servicios-ia/Dockerfile servicios-ia
> docker compose up -d
> ```

### Usuarios de prueba (datos ficticios)

| Usuario | Contraseña | Rol |
|---|---|---|
| `director` | `Director2026*` | Director de Carrera (`DIRECTOR_CARRERA`) |
| `coordinador` | `Coordinador2026*` | Coordinador de Carrera (`COORDINADOR_CARRERA`) |
| `apoyo` | `Apoyo2026*` | Apoyo Profesional (`APOYO_PROFESIONAL`) |

Una cuenta se bloquea tras 5 intentos fallidos. El Director la desbloquea desde **Usuarios**.

## Desarrollo local sin Docker

Requisitos: JDK 21, Maven 3.9, Node 20 o superior y PostgreSQL 16.

```bash
# Backend (crea el esquema con Flyway en la base indicada)
cd backend
DB_URL=jdbc:postgresql://localhost:5432/proyecto25 DB_USER=... DB_PASSWORD=... mvn spring-boot:run

# Frontend (el proxy de Vite envía /api a http://localhost:8080)
cd frontend
npm install
npm run dev     # http://localhost:5173
```

## Base de datos

Los scripts de referencia están en [`db/`](db/). Flyway los ejecuta desde `backend/src/main/resources/db/migration/`:

| Versión | Script | Contenido |
|---|---|---|
| V1 | `V1__esquema_34_tablas.sql` | 34 tablas base (seguridad, trámites, correspondencia, académico, POA, RPA) |
| V2 | `V2__complementario_10_tablas.sql` | 10 tablas del sistema integrado, 3 roles, estados y transiciones |
| V2_1 | `V2_1__ajustes_conexion.sql` | 6 claves foráneas de conexión, secuencias, guardas y restricciones |
| V3 | `V3__seed_desarrollo.sql` | Datos ficticios: permisos, usuarios, 40 estudiantes, 20 materias, 150 oficios, 60 trámites, POA… |
| V4 | `V4__secuencias_codigos.sql` | Secuencias de los códigos únicos (TRM-, DCP-REC/EMI-, EXP-). No agrega tablas ni columnas |
| V7 | `V7__vectores_busqueda_semantica.sql` | Extensión `vector` y tabla `embedding_documento` con índice HNSW para la búsqueda semántica (CU-24) |

**pgvector.** La base usa la imagen `pgvector/pgvector:pg16` (PostgreSQL 16 sobre Debian). servicios-ia calcula los
vectores (384 dimensiones) y el backend los guarda en `embedding_documento`; la búsqueda se resuelve en PostgreSQL con
distancia coseno. Al reindexar solo se recalculan los textos que cambiaron (SHA-256 del texto). Si servicios-ia trabaja
con TF-IDF (`SEMANTICA_MOTOR=tfidf` o sin el modelo), se usa su índice en memoria como antes.

> **Volumen existente creado con `postgres:16-alpine`.** Los datos son compatibles (misma versión mayor), pero Alpine
> (musl) y Debian (glibc) ordenan el texto distinto con la configuración regional `en_US.utf8`. Después de cambiar la
> imagen hay que reconstruir los índices de texto una sola vez, antes de usar el sistema:
>
> ```bash
> ./scripts/respaldo.sh                      # respaldo previo
> docker compose up -d postgres
> docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "REINDEX DATABASE \"$POSTGRES_DB\""'
> docker compose up -d
> ```

El modelo final tiene **44 tablas y 64 claves foráneas**.

## Seguridad y permisos

- JWT HS256 con expiración de 8 h, enviado solo en la cabecera `Authorization`.
- Contraseñas con BCrypt.
- Autorización **por permiso**, no por nombre de rol: `@PreAuthorize("hasAuthority('CODIGO')")`. El Director ajusta los permisos de cada rol en **Roles y permisos**. Los cambios rigen de inmediato, porque los permisos se leen de la base en cada petición.
- El rol Director siempre conserva `GESTIONAR_USUARIOS` y `GESTIONAR_ROLES_PERMISOS`. Además, siempre debe quedar al menos un Director activo.
- CORS restringido a `CORS_ORIGENES`.

## Endpoints de la iteración 1

| CU | Método y ruta | Permiso |
|---|---|---|
| CU-01 | `POST /api/auth/login` | público |
| CU-01 | `GET /api/auth/perfil` · `POST /api/auth/cambiar-contrasena` · `POST /api/auth/logout` | autenticado |
| CU-01 | `GET/POST /api/usuarios` · `GET/PUT /api/usuarios/{id}` · `PATCH /api/usuarios/{id}/estado` · `POST /api/usuarios/{id}/restablecer-contrasena` | `GESTIONAR_USUARIOS` |
| CU-02 | `GET /api/roles` | `GESTIONAR_ROLES_PERMISOS` o `GESTIONAR_USUARIOS` |
| CU-02 | `GET /api/permisos` · `PUT /api/roles/{id}` · `PUT /api/roles/{id}/permisos` | `GESTIONAR_ROLES_PERMISOS` |
| CU-03 | `GET /api/auditoria` · `GET /api/auditoria/catalogos` | `CONSULTAR_AUDITORIA` |

## Endpoints de la iteración 2

| CU | Método y ruta | Permiso |
|---|---|---|
| CU-05 | `GET /api/correspondencia` · `GET /api/correspondencia/{id}` · `GET /api/destinatarios` | `GESTIONAR_`, `CONSULTAR_` o `SEGUIMIENTO_CORRESPONDENCIA` |
| CU-05 | `POST/PUT /api/correspondencia` · `PATCH …/{id}/autorizacion` · `POST …/{id}/adjuntos` · `POST/PUT /api/destinatarios` | `GESTIONAR_CORRESPONDENCIA` |
| CU-06 | `POST /api/correspondencia/{id}/seguimiento` | `SEGUIMIENTO_CORRESPONDENCIA` |
| CU-07 | `GET/POST /api/documentos` · `PATCH/DELETE /api/documentos/{id}` | `GESTIONAR_DOCUMENTOS` |
| CU-07 | `GET /api/documentos/{id}` · `GET /api/documentos/{id}/contenido` | según el contexto del archivo (correspondencia, expediente o trámite) |
| CU-08 | `GET /api/expedientes` · `GET /api/expedientes/{id}` | `GESTIONAR_` o `CONSULTAR_EXPEDIENTES` |
| CU-08 | `POST/PUT /api/expedientes` · `PATCH …/{id}/estado` · `POST …/{id}/tramites` · `POST …/{id}/documentos` | `GESTIONAR_EXPEDIENTES` |
| CU-09 | `POST /api/tramites` · `GET /api/tipos-tramite` · `GET /api/estudiantes` | `REGISTRAR_TRAMITE` |
| CU-10 | `PATCH /api/tramites/{id}/documentos/{idDoc}` · `POST …/{idDoc}/archivo` · `POST /api/tramites/{id}/documentos` | `GESTIONAR_REQUISITOS` (verificar también con `EJECUTAR_ACTUACION_TRAMITE`) |
| CU-11 | `POST /api/tramites/{id}/eventos/{derivar\|observar\|subsanar\|archivar}` · `PUT …/{id}/dictamen` | `EJECUTAR_ACTUACION_TRAMITE` |
| CU-11 | `POST /api/tramites/{id}/eventos/{aprobar\|rechazar}` | `APROBAR_RECHAZAR_TRAMITE` y rol `DIRECTOR_CARRERA` |
| CU-12 | `GET /api/tramites` (filtros: estado, tipo, prioridad, fechas, vencidos) · `GET /api/tramites/{id}` | `CONSULTAR_TRAMITES` |

### Máquina de estados del trámite

Se implementa con Spring State Machine en `tramites/maquina/MaquinaEstadosTramite`.
- Las transiciones se cargan desde `transicion_estado`.
- Las guardas están en `GuardasTramite`: requisitos registrados, justificación obligatoria, documentación completa y rol `DIRECTOR_CARRERA`.
- El estado del trámite solo cambia cuando la máquina acepta el evento. En la misma transacción se escriben `historial_estado` y `bitacora_auditoria`.

### Documentos

- Tipos admitidos: PDF, JPG, PNG y DOCX, de hasta 10 MB.
- El tipo se valida por la firma binaria del archivo, no por la extensión.
- Cada archivo guarda su hash SHA-256 y se comprueba al descargarlo.
- En el volumen, el archivo se guarda con un nombre aleatorio.
- La descarga envía el token en la cabecera, nunca en la URL.

## Endpoints de la iteración 3 (RPA)

| CU | Método y ruta | Permiso |
|---|---|---|
| CU-31 | `GET /api/rpa/procesos` · `GET /api/rpa/revisores` · `GET /api/rpa/previsualizacion` · `POST /api/rpa/ejecuciones` (asíncrono, 202) | `EJECUTAR_RPA` |
| CU-36 | `GET /api/rpa/ejecuciones` · `GET /api/rpa/ejecuciones/{id}` (pasos e incidencias) · `GET /api/rpa/metricas` | `CONSULTAR_RPA` |
| CU-37 | `POST /api/rpa/ejecuciones/{id}/reejecucion` · `PATCH /api/rpa/incidencias/{id}/cierre` | `GESTIONAR_REEJECUCION_RPA` |
| CU-32 | `GET /api/rpa/informes` · `GET /api/rpa/informes/{id}` · `GET …/{id}/pdf` | `CONSULTAR_RPA`, `REVISAR_RPA` o `AUTORIZAR_ENVIO_RPA` |
| CU-32 | `PUT …/{id}/borrador` · `POST …/{id}/devolucion` | `REVISAR_RPA` o `AUTORIZAR_ENVIO_RPA` |
| CU-32 | `POST …/{id}/aprobacion` (aprueba, autoriza y envía) · `POST …/{id}/reintento-envio` | `AUTORIZAR_ENVIO_RPA` |
| CU-33 | `GET /api/consultas/correspondencia` · `GET /api/consultas/tramites` | `CONSULTAR_FILTROS` |
| — | `GET /api/notificaciones` (avisos de la cabecera) | autenticado |

### Flujo RPA

1. **Inicio (CU-31).** El Apoyo Profesional elige el periodo, el tipo de correspondencia, el revisor y los destinatarios.
   - El backend crea `ejecucion_rpa` en `PENDIENTE`.
   - Envía a `rpa-worker` (`POST /interno/rpa/ejecuciones`, con `X-Service-Key`) solo los **oficios autorizados** del periodo y la plantilla vigente.
2. **Trabajador.** Sigue estos pasos:
   1. Valida campos obligatorios, duplicados y fechas.
   2. Consolida y clasifica por fecha, tipo, estado y dependencia.
   3. Aplica la plantilla (Jinja2 en sandbox, con autoescape).
   4. Verifica el render en Chromium.
   5. Genera el PDF con Playwright en el volumen.
   6. Arma el borrador de correo.

   Devuelve el resumen, las incidencias y las evidencias de cada paso.
3. **Registro.** El backend comprueba el hash del PDF y crea:
   - el informe en `PENDIENTE_REVISION`, con su detalle;
   - el correo en `BORRADOR`;
   - las incidencias.

   Luego notifica al revisor en la cabecera.
4. **Revisión (CU-32).** El revisor ve la vista previa del PDF y del borrador. Puede editar el borrador, **devolverlo** o **aprobar y autorizar** (esto último requiere `AUTORIZAR_ENVIO_RPA`).
   - Al aprobar se guardan `id_usuario_autoriza` y `fecha_autorizacion`, y el backend envía por SMTP/TLS.
   - El envío se hace con la fila bloqueada y es idempotente: nunca sale dos veces.
5. **Reejecución (CU-37).** Solo se permite sobre una ejecución `FALLIDA` o con el informe `DEVUELTO`, que no haya sido enviada ni reejecutada antes.
   - La nueva ejecución apunta a la anterior en `id_ejecucion_origen`, y las incidencias anteriores quedan cerradas con `id_reejecucion`.
   - Nunca coexisten dos informes vigentes del mismo periodo.
6. **Tiempo máximo.** Por defecto son 5 minutos (`proceso_rpa.tiempo_max_segundos`). Si se supera, la ejecución queda `FALLIDA` con la incidencia `TIEMPO_EXCEDIDO`.
7. **Métricas.** El historial muestra la duración, los registros procesados, la tasa de éxito y los errores, comparados con el tiempo manual de referencia (`proceso_rpa.tiempo_manual_minutos`).

Si el trabajador RPA no está disponible, la ejecución queda `FALLIDA` con la incidencia `SERVICIO_NO_DISPONIBLE` y el resto del sistema sigue funcionando.

## Endpoints de la iteración 4

| CU | Método y ruta | Permiso |
|---|---|---|
| CU-13 | `GET /api/periodos` · `GET …/{id}/resumen` | consulta académica |
| CU-13 | `POST/PUT /api/periodos` · `PATCH …/{id}/estado` · `POST …/{id}/copia-grupos` | `GESTIONAR_PLANIFICACION` |
| CU-14 | `GET /api/materias` · `GET /api/grupos?idPeriodo=` · `GET /api/grupos/{id}/inscripciones` | consulta académica |
| CU-14 | `POST/PUT /api/materias` · `POST/PUT /api/grupos` · `POST /api/grupos/{id}/inscripciones` · `POST /api/inscripciones/{id}/retiro` · `POST …/{id}/cambio-grupo` | `GESTIONAR_MATERIAS_GRUPOS` |
| CU-15 | `GET/POST /api/migraciones` | `GESTIONAR_MIGRACIONES` (consulta académica para GET) |
| CU-16 | `GET /api/docentes` · `GET /api/docentes/{id}/antecedentes` · `GET /api/asignaciones` | consulta académica |
| CU-16 | `POST /api/asignaciones` · `POST /api/asignaciones/{id}/finalizacion` | `GESTIONAR_CARGA_DOCENTE` |
| CU-17 | `GET /api/titulados` · `POST/PUT /api/titulados` | `GESTIONAR_TITULADOS` (consulta institucional para GET) |
| CU-18 | `GET /api/trabajos-grado` · `POST/PUT /api/trabajos-grado` · `POST …/{id}/archivo` | `GESTIONAR_TRABAJOS_GRADO` (consulta institucional para GET) |
| CU-19 | `GET /api/poa` · `GET /api/poa/{gestion}` · `GET /api/actividades-poa/{id}/seguimientos` | `CONSULTAR_POA` |
| CU-19 | `POST/PUT /api/poa` · `POST /api/poa/{id}/actividades` · `PUT /api/actividades-poa/{id}` · `POST …/{id}/seguimientos` | `GESTIONAR_POA` |
| CU-20 | `GET /api/eventos` · `POST/PUT /api/eventos` | `GESTIONAR_EVENTOS` (consulta para GET) |
| CU-04 | `GET/POST /api/transferencias-autoridad` · `POST …/{id}/revocacion` | `TRANSFERIR_AUTORIDAD` (solo el Director titular) |

Reglas principales:
- **Inscripciones:**
  - Exigen el periodo `ABIERTO` y el plan del estudiante igual al de la materia.
  - Solo admiten estudiantes `REGULAR` o `CONDICIONAL`, sin repetir la materia y con cupo.
  - El grupo se bloquea al inscribir para que dos inscripciones simultáneas no superen el cupo.
  - Después del cierre de inscripciones solo se admiten casos especiales respaldados por un trámite.
- **Carga docente:**
  - Cada grupo tiene un solo docente responsable.
  - Las coberturas exigen registrar el antecedente.
  - Límite semestral por dedicación: 480 h a tiempo completo, 360 h a medio tiempo y 280 h por horario.
- **POA:** el estado de cada actividad (PENDIENTE, EN_CURSO, CONCLUIDA o RETRASADA) y el avance global se recalculan. Bajar un avance exige una justificación.
- **Transferencia de autoridad (CU-04):**
  - Mientras está vigente, el usuario designado actúa con el rol y los permisos de `DIRECTOR_CARRERA`. Esto se calcula en cada petición y su rol propio no cambia.
  - No puede volver a delegarla, y un trabajo diario la finaliza al vencer.

## Endpoints de la iteración 5

| CU | Método y ruta | Permiso |
|---|---|---|
| CU-21/22 | `GET /api/ocr/documentos` · `POST …/{idArchivo}/procesamiento` · `PUT …/{idArchivo}/validacion` | `PROCESAR_OCR` / `VALIDAR_OCR` |
| CU-23 | `GET /api/busqueda?texto=&tipos=&desde=&hasta=` | `BUSQUEDA_AVANZADA` |
| CU-24 | `GET /api/busqueda/semantica?consulta=` · `POST /api/busqueda/indice` | `BUSQUEDA_SEMANTICA` |
| CU-25 | `POST /api/asistente` | `USAR_ASISTENTE` |
| CU-26 | `GET /api/dashboard` | `VER_DASHBOARD` |
| CU-27 | `GET /api/reportes/{tramites\|correspondencia\|carga-docente\|poa\|rpa}?formato=pdf\|xlsx` | `GENERAR_REPORTES` |
| CU-28 | `GET /api/alertas` · `GET /api/alertas/resumen` | `CONSULTAR_ALERTAS` |
| CU-29 | `POST /api/prediccion/analisis` | `ANALISIS_PREDICTIVO` |
| CU-30 | `PATCH /api/alertas/{id}/atencion` · `POST /api/alertas/generacion` | `ATENDER_ALERTAS` |

Servicios inteligentes (interfaz reemplazable por cada grupo):
- **OCR:** `POST /interno/ocr {ruta}` devuelve el texto y la confianza. El texto queda con `validado=false` hasta que una persona lo revisa. Reprocesar un texto ya validado exige confirmación.
- **Búsqueda semántica:**
  - El backend envía el corpus a `POST /interno/busqueda/indice`. El índice se regenera cada noche, y también solo si está vacío.
  - `POST /interno/busqueda` devuelve los ids con su relevancia, y **el backend filtra por permisos** antes de mostrarlos.
  - Si el modelo no está disponible, el servicio usa TF-IDF (`SEMANTICA_MOTOR=tfidf`).
- **Predicción:** se entrena con los trámites cerrados del histórico, sin datos personales y sin variables que filtren la respuesta. Una probabilidad de 0,6 o más genera una alerta `PREDICCION` de nivel MEDIA; 0,8 o más, de nivel ALTA. No se duplican.
- **Alertas de plazo:** un job diario (06:00) crea una alerta ALTA si el trámite venció y MEDIA si vence en 2 días o menos. Las alertas de trámites ya resueltos se cierran solas.
- **Asistente:** `POST /interno/asistente` devuelve la intención y las entidades. El backend ejecuta la consulta autorizada para el usuario y redacta la respuesta.
- **Disponibilidad:** si `servicios-ia` no responde, estas funciones responden 503 con un mensaje claro y el resto del sistema sigue funcionando.

## Iteración 6: transición

### Respaldos diarios
- `scripts/respaldo.sh`:
  - Hace `pg_dump -Fc` de la base dentro del contenedor y verifica el volcado con `pg_restore --list`.
  - Empaqueta el volumen `documentos` con un contenedor temporal.
  - Escribe un manifiesto SHA-256 y elimina los respaldos con más de `RETENCION_DIAS` días (14 por defecto).
  - El destino es `RESPALDO_DIR` (por defecto `./respaldos`).
- Programación diaria (crontab del servidor):
  ```
  30 1 * * * cd /opt/proyecto25 && ./scripts/respaldo.sh >> /var/log/proyecto25-respaldo.log 2>&1
  ```
- `scripts/restaurar.sh <fecha, p. ej. 2026-11-16_0130> [--si]` (lee de `RESPALDO_DIR`):
  - Comprueba las sumas SHA-256 y pide confirmación.
  - Detiene el backend, restaura la base (`pg_restore --clean`) y el volumen, y vuelve a levantar el backend.

### Pruebas de aceptación
```bash
pip install playwright && python -m playwright install chromium
# Flujo completo por la interfaz, con capturas en ./evidencias/
python scripts/prueba_e2e_ui.py --url http://localhost --mailhog http://localhost:8025 --desde 2026-09-01 --hasta 2026-09-30
# 20 usuarios concurrentes con consultas filtradas; falla si el p95 supera 3 s
python scripts/prueba_carga.py http://localhost 20 60
```

`prueba_e2e_ui.py` recorre el flujo completo y verifica cada paso:
1. Apoyo ejecuta el RPA.
2. Se comprueba que **no** salió ningún correo.
3. El Director abre la notificación, revisa y aprueba.
4. Se comprueba que llegó **exactamente un** correo con el PDF adjunto.
5. La auditoría muestra `RPA_INICIAR`, `RPA_EJECUCION_COMPLETADA`, `APROBAR_INFORME`, `AUTORIZAR_ENVIO` y `CORREO_ENVIADO`.

Cada periodo admite un solo informe vigente, así que cada corrida necesita un mes sin informe.

### Operación
- **Registros:** el backend escribe JSON estructurado (logstash) en la salida estándar; se consultan con `docker compose logs -f backend`. Cada operación auditada deja también la línea `auditoria accion=… entidad=… usuario=… ip=…`.
- **Salud:** `GET /api/actuator/health`. Docker Compose espera a que PostgreSQL esté sano antes de iniciar el backend.
- **Disponibilidad parcial:** si `rpa-worker` o `servicios-ia` caen, el resto del sistema sigue funcionando y esas funciones responden con un mensaje claro.
- **Auditoría bajo carga:** la bitácora se escribe con un pool de conexiones propio (`EscritorAuditoria`). Así, la auditoría nunca compite por conexiones con las transacciones de negocio (lo comprueba `iniciosDeSesionConcurrentesNoBloqueanElPool`).
- **Producción:** use `nginx/nginx.prod.conf` (HTTPS en 443), un SMTP real con `MAIL_STARTTLS=true` y `MAIL_AUTH=true`, y secretos propios en `.env`.

## Criterios de terminado

- [x] `docker compose up` levanta todo y este README explica los 3 usuarios de prueba.
- [x] Los 37 CU tienen endpoint, permiso, pantalla y prueba ([docs/TRAZABILIDAD.md](docs/TRAZABILIDAD.md)).
- [x] OpenAPI en `/api/docs`.
- [x] El flujo RPA de punta a punta funciona con el seed y cumple las 3 verificaciones (a), (b) y (c).

## Pruebas

```bash
cd backend && mvn test                     # unitarias + integración/seguridad (Testcontainers)
cd frontend && npm test                    # menú por permisos
cd rpa-worker && pip install -r requirements-dev.txt && pytest   # trabajador RPA (duplicados, incompletos, falla de plantilla, tiempo)
cd servicios-ia && SEMANTICA_MOTOR=tfidf pytest                  # OCR, búsqueda, predicción y asistente
python scripts/prueba_e2e_rpa.py http://localhost   # punta a punta por la API
python scripts/prueba_e2e_ui.py                     # punta a punta por la interfaz (navegador)
```

Verificaciones obligatorias:
- **(a) El RPA no tiene credenciales de la base de datos.** Lo comprueban `ArquitecturaTest` (sobre `docker-compose.yml`) y `test_trabajador_no_tiene_acceso_a_base_de_datos`.
- **(b) No se envía ningún correo sin aprobación.** Lo comprueban `RpaIntegracionTest.flujoCompletoSinEnvioHastaLaAprobacion` y `ArquitecturaTest.soloElServicioDeEnvioUsaElCorreo`. Además, la base de datos lo exige con un CHECK.
- **(c) Una reejecución no duplica informes ni envíos.** Lo comprueba `RpaIntegracionTest.reejecucionNoDuplicaInformesNiEnvios`.

Las pruebas de integración levantan PostgreSQL 16 con Testcontainers, lo que requiere Docker. Sin Docker se pueden ejecutar contra una base vacía:

```bash
PRUEBAS_DB_URL=jdbc:postgresql://localhost:5432/p25test PRUEBAS_DB_USER=postgres PRUEBAS_DB_PASSWORD=... mvn test
```

## Estructura

```
proyecto25/
├── frontend/      React + Vite + TS (Dockerfile también construye la imagen de Nginx)
├── backend/       Spring Boot (seguridad, comun, academico, documental, tramites, rpa, institucional, ocr, busqueda, reportes)
├── rpa-worker/    FastAPI + Playwright + Jinja2 (sandbox)
├── servicios-ia/  FastAPI: OCR, búsqueda semántica, predicción y asistente
├── nginx/         nginx.conf (desarrollo, 80) y nginx.prod.conf (producción, 443)
├── db/            scripts SQL de referencia
├── docs/          manual de usuario y matriz de trazabilidad
├── scripts/       respaldo, restauración, prueba de carga y pruebas E2E
└── docker-compose.yml
```
