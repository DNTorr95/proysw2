# Matriz de trazabilidad: casos de uso → endpoint, permiso, pantalla y prueba

Cada uno de los 37 casos de uso tiene su endpoint REST (documentado en `/api/docs`), el permiso que lo protege, la pantalla del frontend y al menos una prueba automatizada.

Abreviaturas de pruebas:
- **SEG** `SeguridadIntegracionTest` (incluye la matriz de acceso por rol `matrizDeAcceso`)
- **TRA** `TramitesIntegracionTest` · **MAQ** `MaquinaEstadosTramiteTest`
- **DOC** `DocumentalIntegracionTest` · **ALM** `AlmacenamientoServiceTest`
- **RPA** `RpaIntegracionTest` · **ARQ** `ArquitecturaTest` · **W** `rpa-worker/tests/test_rpa.py`
- **ACA** `AcademicoIntegracionTest` · **INS** `InstitucionalIntegracionTest` · **REG** `ReglasInstitucionalesTest`
- **IA** `InteligenciaIntegracionTest` · **PY-IA** `servicios-ia/tests/test_ia.py`
- **E2E** `scripts/prueba_e2e_rpa.py` (API) y `scripts/prueba_e2e_ui.py` (navegador)

| CU | Nombre | Endpoint principal | Permiso | Pantalla | Pruebas |
|---|---|---|---|---|---|
| CU-01 | Autenticar y gestionar usuarios | `POST /api/auth/login`, `/api/usuarios` | público / `GESTIONAR_USUARIOS` | `/login`, `/cuenta`, `/usuarios` | SEG `loginDevuelvePerfilConPermisosDelRol`, `bloqueoTrasCincoIntentosYDesbloqueoPorDirector`, `iniciosDeSesionConcurrentesNoBloqueanElPool`; `JwtServiceTest`; `UsuarioBloqueoTest` |
| CU-02 | Gestionar roles y permisos | `/api/roles`, `/api/permisos` | `GESTIONAR_ROLES_PERMISOS` | `/roles` | SEG `cambioDePermisosRigeDeInmediato`, `directorNoPuedeQuitarseLaAdministracion`; `RolServiceTest` |
| CU-03 | Consultar bitácora de auditoría | `GET /api/auditoria` | `CONSULTAR_AUDITORIA` | `/auditoria` | SEG `auditoriaFiltraYNoAdmiteEscritura` |
| CU-04 | Transferir autoridad de Dirección | `/api/transferencias-autoridad` | `TRANSFERIR_AUTORIDAD` | `/autoridad` | INS `transferenciaDeAutoridad`; REG `transferenciaActivaSoloDentroDeSusFechasYMientrasEsteVigente` |
| CU-05 | Registrar correspondencia | `/api/correspondencia`, `/api/destinatarios` | `GESTIONAR_CORRESPONDENCIA` | `/correspondencia` | DOC `correspondenciaConCodigoUnicoAdjuntoYSeguimiento`, `permisosDeCorrespondenciaYDocumentos` |
| CU-06 | Seguimiento de correspondencia | `POST /api/correspondencia/{id}/seguimiento` | `SEGUIMIENTO_CORRESPONDENCIA` | `/correspondencia` (detalle) | DOC `correspondenciaConCodigoUnicoAdjuntoYSeguimiento` |
| CU-07 | Gestionar documentos digitales | `/api/documentos` | `GESTIONAR_DOCUMENTOS` | `/documentos` | DOC `documentoDigitalSeSubeDescargaYElimina`; ALM (7 pruebas) |
| CU-08 | Gestionar expedientes | `/api/expedientes` | `GESTIONAR_EXPEDIENTES` | `/expedientes` | DOC `expedienteAgrupaTramitesYDocumentos` |
| CU-09 | Registrar trámite estudiantil | `POST /api/tramites` | `REGISTRAR_TRAMITE` | `/tramites/nuevo` | TRA `flujoCompletoConGuardasPermisosHistorialYAuditoria` |
| CU-10 | Gestionar requisitos del trámite | `PATCH /api/tramites/{id}/documentos/{idDoc}` | `GESTIONAR_REQUISITOS` | `/tramites/:id` | TRA `requisitoAjenoAlTipoEsRechazado`; MAQ `derivarSinRequisitosEsRechazado` |
| CU-11 | Ejecutar actuaciones (máquina de estados) | `POST /api/tramites/{id}/eventos/{evento}` | `EJECUTAR_ACTUACION_TRAMITE` / `APROBAR_RECHAZAR_TRAMITE` | `/tramites/:id` | MAQ (9 pruebas); TRA `rechazoRequiereJustificacionDelDirector`, `accionesDisponiblesDependenDelPermiso` |
| CU-12 | Seguimiento de trámites | `GET /api/tramites` | `CONSULTAR_TRAMITES` | `/tramites` | TRA `filtrosDeSeguimiento` |
| CU-13 | Planificación semestral | `/api/periodos` | `GESTIONAR_PLANIFICACION` | `/academico` (Planificación) | ACA `planificacionSemestral` |
| CU-14 | Materias, grupos y cupos | `/api/materias`, `/api/grupos`, `/api/inscripciones` | `GESTIONAR_MATERIAS_GRUPOS` | `/academico` (Grupos y cupos) | ACA `gruposCuposEInscripciones` |
| CU-15 | Migraciones entre planes | `/api/migraciones` | `GESTIONAR_MIGRACIONES` | `/academico` (Migraciones) | ACA `migracionEntrePlanes` |
| CU-16 | Carga docente y coberturas | `/api/asignaciones`, `/api/docentes` | `GESTIONAR_CARGA_DOCENTE` | `/academico` (Carga docente) | ACA `cargaDocenteYCoberturas` |
| CU-17 | Registrar titulados | `/api/titulados` | `GESTIONAR_TITULADOS` | `/institucional` (Titulados) | INS `trabajosDeGradoYTitulados` |
| CU-18 | Gestionar trabajos de grado | `/api/trabajos-grado` | `GESTIONAR_TRABAJOS_GRADO` | `/institucional` (Trabajos de grado) | INS `trabajosDeGradoYTitulados` |
| CU-19 | Seguimiento del POA | `/api/poa`, `/api/actividades-poa` | `GESTIONAR_POA` / `CONSULTAR_POA` | `/poa` | INS `seguimientoDelPoaYEventos`; REG `estadoDeActividadSegunAvanceYFechas`, `avanceDelPoaEsElPromedioDeSusActividades` |
| CU-20 | Eventos y talleres | `/api/eventos` | `GESTIONAR_EVENTOS` | `/eventos` | INS `seguimientoDelPoaYEventos` |
| CU-21 | Procesar documentos con OCR | `POST /api/ocr/documentos/{id}/procesamiento` | `PROCESAR_OCR` | `/ocr` | IA `ocrValidacionYBusquedaAvanzadaPorPermisos`; PY-IA `test_ocr_impide_salir_del_volumen` |
| CU-22 | Validar texto extraído | `PUT /api/ocr/documentos/{id}/validacion` | `VALIDAR_OCR` | `/ocr` | IA `ocrValidacionYBusquedaAvanzadaPorPermisos` |
| CU-23 | Búsqueda avanzada | `GET /api/busqueda` | `BUSQUEDA_AVANZADA` | `/busqueda` | IA `ocrValidacionYBusquedaAvanzadaPorPermisos` |
| CU-24 | Búsqueda semántica | `GET /api/busqueda/semantica` | `BUSQUEDA_SEMANTICA` | `/busqueda` | IA `busquedaSemanticaFiltraPorPermisos`; PY-IA `test_busqueda_devuelve_lo_relevante_primero` |
| CU-25 | Asistente inteligente | `POST /api/asistente` | `USAR_ASISTENTE` | `/busqueda` (Asistente) | IA `asistente`; PY-IA `test_asistente_reconoce_intenciones`, `test_asistente_extrae_entidades` |
| CU-26 | Dashboard de indicadores | `GET /api/dashboard` | `VER_DASHBOARD` | `/` (Inicio) | IA `dashboardYReportes` |
| CU-27 | Generar reportes PDF/XLSX | `GET /api/reportes/{tipo}?formato=` | `GENERAR_REPORTES` | `/reportes` | IA `dashboardYReportes` |
| CU-28 | Consultar alertas | `GET /api/alertas` | `CONSULTAR_ALERTAS` | `/alertas` | IA `alertasPorPlazoYPrediccion` |
| CU-29 | Análisis predictivo | `POST /api/prediccion/analisis` | `ANALISIS_PREDICTIVO` | `/alertas` (Análisis predictivo) | IA `alertasPorPlazoYPrediccion`; PY-IA `test_prediccion_aprende_el_patron`, `test_prediccion_valida_el_historico` |
| CU-30 | Atender alertas | `PATCH /api/alertas/{id}/atencion` | `ATENDER_ALERTAS` | `/alertas` | IA `alertasPorPlazoYPrediccion` |
| CU-31 | Ejecutar consolidación RPA | `POST /api/rpa/ejecuciones` | `EJECUTAR_RPA` | `/rpa/ejecutar` | RPA `flujoCompletoSinEnvioHastaLaAprobacion`, `validacionesYPermisosDeInicio`; W `test_ejecucion_completa_genera_pdf_y_borrador`, `test_detecta_duplicados_incompletos_y_fechas_invalidas`; E2E |
| CU-32 | Revisar y autorizar el envío | `POST /api/rpa/informes/{id}/aprobacion`, `…/devolucion`, `PUT …/borrador` | `REVISAR_RPA` / `AUTORIZAR_ENVIO_RPA` | `/rpa/revision/:id` | RPA `flujoCompletoSinEnvioHastaLaAprobacion`; ARQ `soloElServicioDeEnvioUsaElCorreo`; E2E |
| CU-33 | Consultas con filtros | `GET /api/consultas/{correspondencia\|tramites}` | `CONSULTAR_FILTROS` | `/consultas` | SEG `matrizDeAcceso`; carga `scripts/prueba_carga.py` (p95 < 3 s) |
| CU-34 | Generar informe mensual (incluido en CU-31) | resultado de `POST /api/rpa/ejecuciones`; `GET /api/rpa/informes/{id}/pdf` | `EJECUTAR_RPA` / `CONSULTAR_RPA` | `/rpa/ejecutar`, `/rpa/revision/:id` | W `test_falla_de_plantilla_por_variables_faltantes`, `test_falla_de_plantilla_por_sintaxis`, `test_plantilla_no_puede_ejecutar_codigo`, `test_periodo_vacio_completa_con_informe_sin_registros`; RPA `trabajadorCaidoOFallaDePlantillaNoAfectanAlResto` |
| CU-35 | Preparar notificación por correo (incluido en CU-31) | borrador en `GET /api/rpa/informes/{id}`; `POST …/reintento-envio` | `EJECUTAR_RPA` / `AUTORIZAR_ENVIO_RPA` | `/rpa/revision/:id` | RPA `flujoCompletoSinEnvioHastaLaAprobacion`, `reejecucionNoDuplicaInformesNiEnvios`; E2E (correo en MailHog) |
| CU-36 | Historial y métricas RPA | `GET /api/rpa/ejecuciones`, `GET /api/rpa/metricas` | `CONSULTAR_RPA` | `/rpa/historial` | RPA `tiempoExcedidoYReejecucion`; W `test_tiempo_excedido` |
| CU-37 | Reejecución e incidencias RPA | `POST /api/rpa/ejecuciones/{id}/reejecucion`, `PATCH /api/rpa/incidencias/{id}/cierre` | `GESTIONAR_REEJECUCION_RPA` | `/rpa/historial` | RPA `reejecucionNoDuplicaInformesNiEnvios`, `tiempoExcedidoYReejecucion` |

## Verificaciones obligatorias del punto 12

| Verificación | Dónde se comprueba |
|---|---|
| (a) El RPA no tiene credenciales de la BD | ARQ `rpaYServiciosInteligentesNoTienenCredencialesDeBaseDeDatos` (inspecciona `docker-compose.yml`); W `test_trabajador_no_tiene_acceso_a_base_de_datos`; PY-IA `test_servicio_no_tiene_acceso_a_base_de_datos` |
| (b) Ningún correo sale sin aprobación | RPA `flujoCompletoSinEnvioHastaLaAprobacion`; ARQ `soloElServicioDeEnvioUsaElCorreo`; E2E (cuenta los correos antes y después de aprobar) |
| (c) Reejecutar no duplica informes ni envíos | RPA `reejecucionNoDuplicaInformesNiEnvios`; índices únicos de V2_1 |
| Servicios internos no expuestos | ARQ `serviciosInternosNoSePublicanAlExterior`; W/PY-IA `test_api_exige_clave_*` |
| Seguridad por rol | SEG `matrizDeAcceso` (todas las rutas × 3 roles) |
