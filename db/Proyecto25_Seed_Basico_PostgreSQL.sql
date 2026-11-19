-- =====================================================================
-- Proyecto 25 - Seed básico de desarrollo (PostgreSQL 16)  -> Flyway V3
-- TODOS LOS DATOS SON FICTICIOS.
-- Requiere: V1 (34 tablas), V2 (complementario) y V2_1 (ajustes).
-- Los 3 roles y los estados/transiciones ya los inserta el complementario.
--
-- Usuarios de prueba (contraseña en BCrypt):
--   director     / Director2026*     -> DIRECTOR_CARRERA
--   coordinador  / Coordinador2026*  -> COORDINADOR_CARRERA
--   apoyo        / Apoyo2026*        -> APOYO_PROFESIONAL
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================================
-- 1. PERMISOS Y ASIGNACIÓN A ROLES
-- =====================================================================
INSERT INTO permiso (codigo, modulo, descripcion) VALUES
 -- Seguridad
 ('GESTIONAR_USUARIOS',          'SEGURIDAD',     'Crear, editar, bloquear y desbloquear usuarios'),
 ('GESTIONAR_ROLES_PERMISOS',    'SEGURIDAD',     'Asignar permisos a los roles institucionales'),
 ('CONSULTAR_AUDITORIA',         'SEGURIDAD',     'Consultar la bitácora de auditoría'),
 ('TRANSFERIR_AUTORIDAD',        'SEGURIDAD',     'Transferir la autoridad de Dirección'),
 -- Documental
 ('GESTIONAR_CORRESPONDENCIA',   'DOCUMENTAL',    'Registrar y editar correspondencia institucional'),
 ('SEGUIMIENTO_CORRESPONDENCIA', 'DOCUMENTAL',    'Registrar el seguimiento de la correspondencia'),
 ('CONSULTAR_CORRESPONDENCIA',   'DOCUMENTAL',    'Consultar correspondencia y su seguimiento'),
 ('GESTIONAR_DOCUMENTOS',        'DOCUMENTAL',    'Cargar y administrar documentos digitales'),
 ('GESTIONAR_EXPEDIENTES',       'DOCUMENTAL',    'Abrir, actualizar y cerrar expedientes administrativos'),
 ('CONSULTAR_EXPEDIENTES',       'DOCUMENTAL',    'Consultar expedientes administrativos'),
 -- Trámites
 ('REGISTRAR_TRAMITE',           'TRAMITES',      'Registrar trámites o solicitudes estudiantiles'),
 ('GESTIONAR_REQUISITOS',        'TRAMITES',      'Registrar y verificar requisitos del trámite'),
 ('EJECUTAR_ACTUACION_TRAMITE',  'TRAMITES',      'Derivar, observar, subsanar y archivar trámites'),
 ('APROBAR_RECHAZAR_TRAMITE',    'TRAMITES',      'Aprobar o rechazar trámites'),
 ('CONSULTAR_TRAMITES',          'TRAMITES',      'Consultar seguimiento e historial de trámites'),
 ('GESTIONAR_TIPOS_TRAMITE',     'TRAMITES',      'Configurar tipos de trámite, requisitos y plazos'),
 -- Académico
 ('GESTIONAR_PLANIFICACION',     'ACADEMICO',     'Gestionar la planificación académica semestral'),
 ('GESTIONAR_MATERIAS_GRUPOS',   'ACADEMICO',     'Gestionar materias, grupos y cupos'),
 ('GESTIONAR_MIGRACIONES',       'ACADEMICO',     'Gestionar migraciones entre planes 148-1 y 148-2'),
 ('GESTIONAR_CARGA_DOCENTE',     'ACADEMICO',     'Gestionar carga docente y antecedentes de cobertura'),
 ('CONSULTAR_ACADEMICO',         'ACADEMICO',     'Consultar información académica'),
 -- Institucional
 ('GESTIONAR_TITULADOS',         'INSTITUCIONAL', 'Gestionar registros de titulados'),
 ('GESTIONAR_TRABAJOS_GRADO',    'INSTITUCIONAL', 'Gestionar investigaciones y trabajos de grado'),
 ('GESTIONAR_POA',               'INSTITUCIONAL', 'Gestionar el seguimiento del POA'),
 ('CONSULTAR_POA',               'INSTITUCIONAL', 'Consultar el POA y su avance'),
 ('GESTIONAR_EVENTOS',           'INSTITUCIONAL', 'Gestionar eventos y talleres institucionales'),
 ('CONSULTAR_INSTITUCIONAL',     'INSTITUCIONAL', 'Consultar titulados, trabajos de grado y eventos'),
 -- OCR
 ('PROCESAR_OCR',                'OCR',           'Procesar documentos mediante OCR'),
 ('VALIDAR_OCR',                 'OCR',           'Revisar y validar texto extraído por OCR'),
 -- Búsqueda
 ('BUSQUEDA_AVANZADA',           'BUSQUEDA',      'Realizar búsqueda avanzada'),
 ('BUSQUEDA_SEMANTICA',          'BUSQUEDA',      'Realizar búsqueda semántica'),
 ('USAR_ASISTENTE',              'BUSQUEDA',      'Consultar el asistente inteligente'),
 ('CONSULTAR_FILTROS',           'BUSQUEDA',      'Consultar registros mediante filtros convencionales'),
 -- Reportes
 ('VER_DASHBOARD',               'REPORTES',      'Consultar el dashboard institucional'),
 ('GENERAR_REPORTES',            'REPORTES',      'Generar y exportar reportes PDF/XLSX'),
 ('CONSULTAR_ALERTAS',           'REPORTES',      'Consultar alertas administrativas y predictivas'),
 ('ATENDER_ALERTAS',             'REPORTES',      'Registrar la atención de alertas'),
 ('ANALISIS_PREDICTIVO',         'REPORTES',      'Realizar análisis predictivo'),
 -- RPA
 ('EJECUTAR_RPA',                'RPA',           'Iniciar ejecuciones RPA de consolidación'),
 ('CONSULTAR_RPA',               'RPA',           'Consultar resultados, métricas e incidencias RPA'),
 ('REVISAR_RPA',                 'RPA',           'Revisar resultados RPA y devolverlos con observaciones'),
 ('AUTORIZAR_ENVIO_RPA',         'RPA',           'Aprobar informes RPA y autorizar el envío del correo'),
 ('GESTIONAR_REEJECUCION_RPA',   'RPA',           'Gestionar incidencias y reejecuciones RPA');

-- DIRECTOR_CARRERA (id_rol 1)
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT 1, id_permiso FROM permiso WHERE codigo IN (
 'GESTIONAR_USUARIOS', 'GESTIONAR_ROLES_PERMISOS', 'CONSULTAR_AUDITORIA', 'TRANSFERIR_AUTORIDAD',
 'CONSULTAR_CORRESPONDENCIA', 'CONSULTAR_EXPEDIENTES',
 'APROBAR_RECHAZAR_TRAMITE', 'CONSULTAR_TRAMITES', 'GESTIONAR_TIPOS_TRAMITE',
 'CONSULTAR_ACADEMICO', 'GESTIONAR_POA', 'CONSULTAR_POA', 'CONSULTAR_INSTITUCIONAL',
 'BUSQUEDA_AVANZADA', 'BUSQUEDA_SEMANTICA', 'USAR_ASISTENTE', 'CONSULTAR_FILTROS',
 'VER_DASHBOARD', 'GENERAR_REPORTES', 'CONSULTAR_ALERTAS', 'ANALISIS_PREDICTIVO',
 'CONSULTAR_RPA', 'REVISAR_RPA', 'AUTORIZAR_ENVIO_RPA');

-- COORDINADOR_CARRERA (id_rol 2)
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT 2, id_permiso FROM permiso WHERE codigo IN (
 'CONSULTAR_CORRESPONDENCIA', 'GESTIONAR_EXPEDIENTES', 'CONSULTAR_EXPEDIENTES',
 'EJECUTAR_ACTUACION_TRAMITE', 'CONSULTAR_TRAMITES',
 'GESTIONAR_PLANIFICACION', 'GESTIONAR_MATERIAS_GRUPOS', 'GESTIONAR_MIGRACIONES',
 'GESTIONAR_CARGA_DOCENTE', 'CONSULTAR_ACADEMICO',
 'CONSULTAR_POA', 'GESTIONAR_EVENTOS', 'CONSULTAR_INSTITUCIONAL',
 'BUSQUEDA_AVANZADA', 'BUSQUEDA_SEMANTICA', 'USAR_ASISTENTE', 'CONSULTAR_FILTROS',
 'VER_DASHBOARD', 'GENERAR_REPORTES', 'CONSULTAR_ALERTAS', 'ATENDER_ALERTAS',
 'CONSULTAR_RPA', 'REVISAR_RPA', 'GESTIONAR_REEJECUCION_RPA');

-- APOYO_PROFESIONAL (id_rol 3)
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT 3, id_permiso FROM permiso WHERE codigo IN (
 'GESTIONAR_CORRESPONDENCIA', 'SEGUIMIENTO_CORRESPONDENCIA', 'CONSULTAR_CORRESPONDENCIA',
 'GESTIONAR_DOCUMENTOS', 'CONSULTAR_EXPEDIENTES',
 'REGISTRAR_TRAMITE', 'GESTIONAR_REQUISITOS', 'CONSULTAR_TRAMITES',
 'CONSULTAR_ACADEMICO', 'GESTIONAR_TITULADOS', 'GESTIONAR_TRABAJOS_GRADO', 'CONSULTAR_INSTITUCIONAL',
 'PROCESAR_OCR', 'VALIDAR_OCR', 'CONSULTAR_FILTROS',
 'EJECUTAR_RPA', 'CONSULTAR_RPA');

-- =====================================================================
-- 2. PERSONAS, USUARIOS Y PERSONAL ADMINISTRATIVO
--    id_persona 1-3 personal, 4-9 docentes, 10-49 estudiantes
-- =====================================================================
INSERT INTO persona (id_persona, ci, nombres, apellidos, correo, telefono) VALUES
 (1, '7000001', 'Mariela',  'Rosales Vaca',    'director.psicologia@ejemplo.edu.bo',    '70000001'),
 (2, '7000002', 'Fernando', 'Quiroga Antelo',  'coordinador.psicologia@ejemplo.edu.bo', '70000002'),
 (3, '7000003', 'Daniela',  'Suárez Justiniano','apoyo.psicologia@ejemplo.edu.bo',      '70000003');

INSERT INTO usuario (id_usuario, id_persona, id_rol, nombre_usuario, contrasena_hash, estado) VALUES
 (1, 1, 1, 'director',    crypt('Director2026*',    gen_salt('bf', 10)), 'ACTIVO'),
 (2, 2, 2, 'coordinador', crypt('Coordinador2026*', gen_salt('bf', 10)), 'ACTIVO'),
 (3, 3, 3, 'apoyo',       crypt('Apoyo2026*',       gen_salt('bf', 10)), 'ACTIVO');

INSERT INTO personal_administrativo (id_persona, cargo, area, fecha_designacion, resolucion_designacion) VALUES
 (1, 'Directora de Carrera',   'Dirección de Carrera de Psicología', '2025-02-03', 'RES-FHCS-0012/2025'),
 (2, 'Coordinador de Carrera', 'Coordinación Académica',             '2025-02-10', 'RES-DC-0004/2025'),
 (3, 'Apoyo Profesional',      'Secretaría de Dirección',            '2025-03-01', 'MEM-DC-0021/2025');

-- Docentes (ficticios)
INSERT INTO persona (id_persona, ci, nombres, apellidos, correo, telefono) VALUES
 (4, '6100004', 'Ricardo',  'Méndez Paz',      'rmendez@ejemplo.edu.bo',  '71000004'),
 (5, '6100005', 'Carla',    'Ortiz Melgar',    'cortiz@ejemplo.edu.bo',   '71000005'),
 (6, '6100006', 'Jorge',    'Saucedo Flores',  'jsaucedo@ejemplo.edu.bo', '71000006'),
 (7, '6100007', 'Lucía',    'Peña Barba',      'lpena@ejemplo.edu.bo',    '71000007'),
 (8, '6100008', 'Andrés',   'Velasco Rivero',  'avelasco@ejemplo.edu.bo', '71000008'),
 (9, '6100009', 'Patricia', 'Gutiérrez Ribera','pgutierrez@ejemplo.edu.bo','71000009');

INSERT INTO docente (id_persona, codigo_docente, categoria, dedicacion, especialidad) VALUES
 (4, 'DOC-0104', 'A',        'TIEMPO_COMPLETO', 'Psicología Clínica'),
 (5, 'DOC-0105', 'B',        'TIEMPO_COMPLETO', 'Psicología Educativa'),
 (6, 'DOC-0106', 'B',        'MEDIO_TIEMPO',    'Neuropsicología'),
 (7, 'DOC-0107', 'C',        'HORARIO',         'Psicología Social'),
 (8, 'DOC-0108', 'A',        'TIEMPO_COMPLETO', 'Metodología de la Investigación'),
 (9, 'DOC-0109', 'INTERINO', 'HORARIO',         'Psicología Organizacional');

-- 40 estudiantes ficticios (id_persona 10..49); los pares en 148-2, los impares en 148-1
INSERT INTO persona (id_persona, ci, nombres, apellidos, correo, telefono)
SELECT 9 + i,
       '8' || lpad(i::text, 6, '0'),
       (ARRAY['Ana','Luis','María','Carlos','Sofía','Diego','Valeria','Javier','Camila','Mateo',
              'Paola','Rodrigo','Gabriela','Sebastián','Natalia','Pablo','Andrea','Miguel','Lorena','Tomás'])[1 + (i - 1) % 20],
       (ARRAY['Rojas','Vargas','Céspedes','Arteaga','Justiniano','Moreno','Chávez','Salvatierra'])[1 + (i - 1) % 8]
         || ' ' ||
       (ARRAY['Pérez','Áñez','Soliz','Romero','Cuéllar','Durán','Ribera','Terceros','Montaño','Ibáñez'])[1 + (i * 3) % 10],
       'estudiante' || lpad(i::text, 2, '0') || '@ejemplo.edu.bo',
       '76' || lpad(i::text, 6, '0')
FROM generate_series(1, 40) AS i;

INSERT INTO estudiante (id_persona, registro, plan, estado)
SELECT 9 + i,
       '2' || (19 + i % 6)::text || lpad((1000 + i * 37)::text, 5, '0'),
       CASE WHEN i % 2 = 0 THEN '148-2' ELSE '148-1' END,
       CASE WHEN i IN (7, 23) THEN 'LICENCIA' WHEN i IN (38, 39, 40) THEN 'EGRESADO' ELSE 'REGULAR' END
FROM generate_series(1, 40) AS i;

-- =====================================================================
-- 3. TIPOS DE TRÁMITE, REQUISITOS Y PLAZOS
-- =====================================================================
INSERT INTO tipo_tramite (id_tipo_tramite, nombre, descripcion, plazo_referencia_dias, requiere_dictamen) VALUES
 (1, 'Adición de materia fuera de fecha', 'Inscripción de una materia después del cierre de inscripciones', 5,  FALSE),
 (2, 'Cambio de grupo',                   'Traslado del estudiante a otro grupo de la misma materia',      3,  FALSE),
 (3, 'Reincorporación',                   'Retorno del estudiante tras abandono o licencia',               10, TRUE),
 (4, 'Traspaso',                          'Traspaso desde otra carrera o universidad',                     15, TRUE),
 (5, 'Récord académico',                  'Emisión de récord académico certificado',                       2,  FALSE),
 (6, 'Licencia temporal',                 'Suspensión temporal de estudios por un periodo',                7,  TRUE);

INSERT INTO requisito_tipo_tramite (id_tipo_tramite, descripcion, obligatorio) VALUES
 (1, 'Carta de solicitud dirigida a la Dirección de Carrera', TRUE),
 (1, 'Boleta de inscripción del periodo vigente', TRUE),
 (1, 'Justificativo del retraso', TRUE),
 (2, 'Carta de solicitud de cambio de grupo', TRUE),
 (2, 'Boleta de inscripción del periodo vigente', TRUE),
 (2, 'Justificativo laboral o de salud', FALSE),
 (3, 'Carta de solicitud de reincorporación', TRUE),
 (3, 'Fotocopia de carnet de identidad', TRUE),
 (3, 'Récord académico actualizado', TRUE),
 (3, 'Comprobante de pago de matrícula', TRUE),
 (4, 'Carta de solicitud de traspaso', TRUE),
 (4, 'Certificado de notas de la carrera de origen', TRUE),
 (4, 'Programas analíticos legalizados', TRUE),
 (4, 'Fotocopia de carnet de identidad', TRUE),
 (5, 'Formulario de solicitud de récord', TRUE),
 (5, 'Comprobante de pago de valores', TRUE),
 (6, 'Carta de solicitud de licencia', TRUE),
 (6, 'Documento de respaldo (médico, laboral u otro)', TRUE),
 (6, 'Boleta de inscripción del último periodo', FALSE);

-- =====================================================================
-- 4. PERIODOS, MATERIAS, GRUPOS, CARGA DOCENTE E INSCRIPCIONES
-- =====================================================================
INSERT INTO periodo_academico (id_periodo, gestion, semestre, fecha_inicio, fecha_fin, fecha_cierre_inscripcion, estado) VALUES
 (1, 2026, 1, '2026-02-16', '2026-07-03', '2026-03-06', 'CERRADO'),
 (2, 2026, 2, '2026-08-03', '2026-12-18', '2026-08-21', 'ABIERTO');

INSERT INTO materia (id_materia, sigla, nombre, plan, nivel, horas) VALUES
 (1,  'PSI-101', 'Introducción a la Psicología',      '148-1', 1, 80),
 (2,  'PSI-102', 'Psicología General',                '148-1', 1, 80),
 (3,  'PSI-103', 'Biología Humana',                   '148-1', 1, 64),
 (4,  'PSI-104', 'Filosofía y Epistemología',         '148-1', 1, 64),
 (5,  'PSI-201', 'Psicología del Desarrollo I',       '148-1', 2, 80),
 (6,  'PSI-202', 'Estadística Aplicada',              '148-1', 2, 64),
 (7,  'PSI-203', 'Neuropsicología',                   '148-1', 2, 80),
 (8,  'PSI-204', 'Psicología Social',                 '148-1', 2, 80),
 (9,  'PSI-301', 'Psicopatología I',                  '148-1', 3, 80),
 (10, 'PSI-302', 'Psicometría',                       '148-1', 3, 80),
 (11, 'PSI-101', 'Fundamentos de la Psicología',      '148-2', 1, 96),
 (12, 'PSI-105', 'Procesos Psicológicos Básicos',     '148-2', 1, 96),
 (13, 'PSI-106', 'Bases Biológicas del Comportamiento','148-2', 1, 64),
 (14, 'PSI-205', 'Desarrollo Humano',                 '148-2', 2, 96),
 (15, 'PSI-206', 'Estadística en Psicología',         '148-2', 2, 64),
 (16, 'PSI-303', 'Psicología Educativa',              '148-2', 3, 80),
 (17, 'PSI-304', 'Psicología Organizacional',         '148-2', 3, 80),
 (18, 'PSI-305', 'Metodología de la Investigación',   '148-2', 3, 80),
 (19, 'PSI-401', 'Evaluación Psicológica',            '148-2', 4, 96),
 (20, 'PSI-405', 'Psicología Comunitaria',            '148-2', 4, 80);

-- Un grupo por materia en el periodo 2026-2 (id_grupo = id_materia)
INSERT INTO grupo (id_grupo, id_materia, id_periodo, codigo, cupo, turno, horario, aula)
SELECT m, m, 2, 'Z' || (1 + m % 3)::text, 35 + (m % 3) * 5,
       (ARRAY['MANANA','TARDE','NOCHE'])[1 + m % 3],
       (ARRAY['Lun-Mié 07:00-09:15','Mar-Jue 14:00-16:15','Lun-Vie 19:00-20:30'])[1 + m % 3],
       'Aula ' || (200 + m)::text
FROM generate_series(1, 20) AS m;

INSERT INTO asignacion_docente (id_docente, id_grupo, tipo, horas_asignadas, fecha_inicio, fecha_fin, observacion)
SELECT 4 + g % 6, g,
       CASE WHEN g % 6 = 5 THEN 'INTERINO' WHEN g % 7 = 0 THEN 'COBERTURA' ELSE 'TITULAR' END,
       CASE WHEN g <= 10 THEN 80 ELSE 96 END, '2026-08-03', '2026-12-18',
       CASE WHEN g % 7 = 0 THEN 'Cobertura por licencia del docente titular' END
FROM generate_series(1, 20) AS g;

-- Cada estudiante en 3 grupos de su plan
INSERT INTO inscripcion (id_estudiante, id_grupo, fecha_inscripcion, estado, es_caso_especial)
SELECT 9 + i,
       CASE WHEN i % 2 = 0 THEN 10 ELSE 0 END + 1 + ((i + k * 3) % 10),
       DATE '2026-08-10' + (i % 10),
       'INSCRITO',
       (k = 2 AND i % 9 = 0)
FROM generate_series(1, 40) AS i, generate_series(0, 2) AS k;

UPDATE grupo g SET inscritos = (SELECT COUNT(*) FROM inscripcion ins WHERE ins.id_grupo = g.id_grupo);

-- =====================================================================
-- 5. EXPEDIENTES, TRÁMITES HISTÓRICOS E HISTORIAL DE ESTADOS
--    60 trámites (marzo-octubre 2026) para el modelo de predicción.
-- =====================================================================
INSERT INTO expediente (id_expediente, codigo, id_estudiante, titulo, descripcion, estado, fecha_apertura)
SELECT i, 'EXP-2026-' || lpad(i::text, 4, '0'), 9 + i,
       'Expediente académico-administrativo del estudiante ' || (9 + i),
       'Agrupa los trámites y documentos del estudiante', 'ABIERTO', DATE '2026-03-01'
FROM generate_series(1, 10) AS i;

DO $$
DECLARE
    i            INTEGER;
    v_tipo       INTEGER;
    v_plazo      INTEGER;
    v_est        INTEGER;
    v_recep      TIMESTAMP;
    v_fin        VARCHAR(30);
    v_dias       INTEGER;
    v_t          TIMESTAMP;
    v_observado  BOOLEAN;
    v_rechazado  BOOLEAN;
    v_usr_dir    CONSTANT INTEGER := 1;
    v_usr_coord  CONSTANT INTEGER := 2;
    v_usr_apoyo  CONSTANT INTEGER := 3;
BEGIN
    FOR i IN 1..60 LOOP
        v_tipo  := 1 + (i - 1) % 6;
        SELECT plazo_referencia_dias INTO v_plazo FROM tipo_tramite WHERE id_tipo_tramite = v_tipo;
        v_est   := 10 + (i * 7) % 40;
        v_recep := TIMESTAMP '2026-03-02 08:30' + ((i - 1) * 4) * INTERVAL '1 day' + (i % 5) * INTERVAL '1 hour';
        -- Duración total: alrededor del plazo, con trámites demorados de forma sistemática
        -- (traspaso y reincorporación con más demora; observados también).
        v_observado := (i % 4 = 0);
        v_rechazado := (i % 7 = 0);
        v_dias := v_plazo + ((i * 7) % 9) - 7
                  + CASE WHEN v_observado THEN 4 ELSE 0 END
                  + CASE WHEN v_tipo IN (3, 4) THEN 3 ELSE 0 END;
        IF v_dias < 1 THEN v_dias := 1; END IF;

        v_fin := CASE
                    WHEN i <= 40 THEN 'ARCHIVADO'
                    WHEN i <= 48 THEN 'EN_REVISION'
                    WHEN i <= 54 THEN 'OBSERVADO'
                    ELSE 'RECEPCIONADO'
                 END;
        IF i > 40 THEN v_observado := (v_fin = 'OBSERVADO'); END IF;

        INSERT INTO tramite (id_tramite, numero, id_tipo_tramite, id_estudiante, id_expediente, id_usuario_registro,
                             fecha_recepcion, fecha_limite, fecha_cierre, estado_actual, prioridad, observaciones)
        VALUES (i, 'TRM-2026-' || lpad(i::text, 4, '0'), v_tipo, v_est,
                CASE WHEN v_est BETWEEN 10 AND 19 THEN v_est - 9 END,
                v_usr_apoyo, v_recep, (v_recep + v_plazo * INTERVAL '1 day')::date,
                CASE WHEN v_fin = 'ARCHIVADO' THEN v_recep + (v_dias + 1) * INTERVAL '1 day' END,
                v_fin,
                (ARRAY['ALTA','MEDIA','BAJA'])[1 + i % 3],
                'Solicitud presentada por el estudiante (dato ficticio)');

        -- Registro inicial
        INSERT INTO historial_estado (id_tramite, id_transicion, estado_origen, estado_destino, evento, id_usuario, fecha_hora, observacion)
        VALUES (i, NULL, NULL, 'RECEPCIONADO', 'registrar', v_usr_apoyo, v_recep, 'Trámite recepcionado');

        -- Requisitos del tipo de trámite
        INSERT INTO documento_tramite (id_tramite, id_requisito, es_requisito, presentado, verificado)
        SELECT i, r.id_requisito, TRUE, TRUE, (v_fin <> 'RECEPCIONADO')
        FROM requisito_tipo_tramite r WHERE r.id_tipo_tramite = v_tipo;

        IF v_fin = 'RECEPCIONADO' THEN CONTINUE; END IF;

        v_t := v_recep + INTERVAL '1 day';
        INSERT INTO historial_estado (id_tramite, id_transicion, estado_origen, estado_destino, evento, id_usuario, fecha_hora, observacion)
        SELECT i, t.id_transicion, 'RECEPCIONADO', 'EN_REVISION', 'derivar', v_usr_coord, v_t, 'Requisitos registrados, se deriva a revisión'
        FROM transicion_estado t WHERE t.evento = 'derivar';

        IF v_observado THEN
            v_t := v_t + INTERVAL '1 day';
            INSERT INTO historial_estado (id_tramite, id_transicion, estado_origen, estado_destino, evento, id_usuario, fecha_hora, observacion)
            SELECT i, t.id_transicion, 'EN_REVISION', 'OBSERVADO', 'observar', v_usr_coord, v_t, 'Falta documento de respaldo legible'
            FROM transicion_estado t WHERE t.evento = 'observar';
            IF v_fin = 'OBSERVADO' THEN CONTINUE; END IF;
            v_t := v_t + INTERVAL '3 day';
            INSERT INTO historial_estado (id_tramite, id_transicion, estado_origen, estado_destino, evento, id_usuario, fecha_hora, observacion)
            SELECT i, t.id_transicion, 'OBSERVADO', 'EN_REVISION', 'subsanar', v_usr_coord, v_t, 'Documentación completada por el estudiante'
            FROM transicion_estado t WHERE t.evento = 'subsanar';
        END IF;

        IF v_fin = 'EN_REVISION' THEN CONTINUE; END IF;

        -- Decisión del Director
        v_t := v_recep + v_dias * INTERVAL '1 day';
        IF v_rechazado THEN
            INSERT INTO historial_estado (id_tramite, id_transicion, estado_origen, estado_destino, evento, id_usuario, fecha_hora, observacion)
            SELECT i, t.id_transicion, 'EN_REVISION', 'RECHAZADO', 'rechazar', v_usr_dir, v_t, 'No cumple el reglamento vigente'
            FROM transicion_estado t WHERE t.evento = 'rechazar';
            INSERT INTO historial_estado (id_tramite, id_transicion, estado_origen, estado_destino, evento, id_usuario, fecha_hora, observacion)
            SELECT i, t.id_transicion, 'RECHAZADO', 'ARCHIVADO', 'archivar', v_usr_coord, v_t + INTERVAL '1 day', 'Archivado'
            FROM transicion_estado t JOIN estado_tramite e ON e.id_estado = t.id_estado_origen
            WHERE t.evento = 'archivar' AND e.nombre = 'RECHAZADO';
        ELSE
            INSERT INTO historial_estado (id_tramite, id_transicion, estado_origen, estado_destino, evento, id_usuario, fecha_hora, observacion)
            SELECT i, t.id_transicion, 'EN_REVISION', 'APROBADO', 'aprobar', v_usr_dir, v_t, 'Aprobado por Dirección'
            FROM transicion_estado t WHERE t.evento = 'aprobar';
            INSERT INTO historial_estado (id_tramite, id_transicion, estado_origen, estado_destino, evento, id_usuario, fecha_hora, observacion)
            SELECT i, t.id_transicion, 'APROBADO', 'ARCHIVADO', 'archivar', v_usr_coord, v_t + INTERVAL '1 day', 'Archivado'
            FROM transicion_estado t JOIN estado_tramite e ON e.id_estado = t.id_estado_origen
            WHERE t.evento = 'archivar' AND e.nombre = 'APROBADO';
        END IF;

        IF v_tipo IN (3, 4, 6) THEN
            INSERT INTO dictamen (id_tramite, fecha, resultado, fundamento)
            VALUES (i, (v_t - INTERVAL '1 day')::date,
                    CASE WHEN v_rechazado THEN 'DESFAVORABLE' ELSE 'FAVORABLE' END,
                    'Dictamen técnico de la Coordinación según reglamento (ficticio)');
        END IF;
    END LOOP;
END $$;

-- =====================================================================
-- 6. CORRESPONDENCIA: 150 oficios de 3 meses (agosto-octubre 2026)
-- =====================================================================
INSERT INTO destinatario (id_destinatario, nombre, cargo, unidad, institucion, correo) VALUES
 (1,  'Hugo Arandia Lemaitre',  'Decano',                  'Decanato FHCS',                    'UAGRM', 'decanato.demo@ejemplo.edu.bo'),
 (2,  'Silvia Roca Antelo',     'Vicedecana',              'Vicedecanato FHCS',                'UAGRM', 'vicedecanato.demo@ejemplo.edu.bo'),
 (3,  'Marcelo Paz Hurtado',    'Jefe de Unidad',          'Registro y Kardex',                'UAGRM', 'kardex.demo@ejemplo.edu.bo'),
 (4,  'Elena Toledo Vaca',      'Directora',               'Dirección Universitaria de Investigación', 'UAGRM', 'investigacion.demo@ejemplo.edu.bo'),
 (5,  'Óscar Lino Suárez',      'Jefe de Unidad',          'Planificación Académica',          'UAGRM', 'planificacion.demo@ejemplo.edu.bo'),
 (6,  'Rosa Mercado Égüez',     'Responsable',             'Bienestar Estudiantil',            'UAGRM', 'bienestar.demo@ejemplo.edu.bo'),
 (7,  'Iván Coimbra Rivero',    'Director',                'Carrera de Trabajo Social',        'UAGRM', 'trabajosocial.demo@ejemplo.edu.bo'),
 (8,  'Nora Saavedra Paz',      'Presidenta',              'Centro de Estudiantes de Psicología','UAGRM', 'cep.demo@ejemplo.edu.bo'),
 (9,  'Gustavo Rengel Áñez',    'Jefe de Unidad',          'Recursos Humanos',                 'UAGRM', 'rrhh.demo@ejemplo.edu.bo'),
 (10, 'Claudia Ferrufino Soria','Coordinadora',            'Colegio de Psicólogos (externo)',  'Externa', 'colegio.demo@ejemplo.org');

INSERT INTO correspondencia (id_correspondencia, codigo, tipo, asunto, descripcion, fecha_documento,
                             fecha_registro, id_destinatario, estado, autorizado, id_usuario_registro)
SELECT i,
       CASE WHEN i % 3 = 0 THEN 'DCP-EMI-2026-' ELSE 'DCP-REC-2026-' END || lpad(i::text, 4, '0'),
       CASE WHEN i % 3 = 0 THEN 'EMITIDA' ELSE 'RECIBIDA' END,
       (ARRAY['Solicitud de aulas para el periodo 2026-2',
              'Remisión de planillas de calificaciones',
              'Invitación a taller de salud mental',
              'Solicitud de designación de tribunal de grado',
              'Informe de avance del POA',
              'Convocatoria a reunión de Consejo de Carrera',
              'Remisión de nómina de estudiantes habilitados',
              'Solicitud de cobertura docente',
              'Comunicación de cronograma de defensas',
              'Requerimiento de material de escritorio',
              'Solicitud de convenio de prácticas preprofesionales',
              'Remisión de récord académico'])[1 + i % 12],
       'Oficio de prueba generado para el seed (dato ficticio)',
       DATE '2026-08-01' + ((i - 1) * 91 / 150),
       (DATE '2026-08-01' + ((i - 1) * 91 / 150))::timestamp + INTERVAL '9 hour' + (i % 8) * INTERVAL '1 hour',
       1 + (i * 7) % 10,
       (ARRAY['REGISTRADA','DERIVADA','EN_TRAMITE','RESPONDIDA','ARCHIVADA'])[1 + i % 5],
       (i % 17 <> 0),     -- unos pocos sin autorizar: no deben entrar al RPA
       3
FROM generate_series(1, 150) AS i;

INSERT INTO seguimiento_correspondencia (id_correspondencia, fecha, estado, observacion)
SELECT c.id_correspondencia, c.fecha_registro + INTERVAL '1 day', c.estado,
       'Seguimiento registrado: ' || lower(c.estado)
FROM correspondencia c WHERE c.estado <> 'REGISTRADA';

-- =====================================================================
-- 7. PLANTILLA INSTITUCIONAL Y PROCESO RPA
-- =====================================================================
INSERT INTO plantilla_documento (id_plantilla, nombre, tipo, version, contenido_html, vigente) VALUES
 (1, 'Informe mensual de correspondencia', 'INFORME_RPA', '1.0',
$html$<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>{{ titulo }}</title>
<style>
  @page { size: Letter; margin: 2cm; }
  body { font-family: Arial, sans-serif; font-size: 11pt; color: #222; }
  header { border-bottom: 2px solid #1f3a68; margin-bottom: 16px; }
  header h1 { font-size: 14pt; margin: 0; color: #1f3a68; }
  header p { margin: 2px 0; font-size: 9pt; }
  table { width: 100%; border-collapse: collapse; margin: 10px 0 18px; }
  th, td { border: 1px solid #999; padding: 4px 6px; text-align: left; }
  th { background: #e8edf5; }
  footer { font-size: 8pt; color: #666; margin-top: 24px; }
</style>
</head>
<body>
<header>
  <h1>Universidad Autónoma Gabriel René Moreno</h1>
  <p>Facultad de Humanidades y Ciencias de la Comunicación</p>
  <p>Dirección de Carrera de Psicología</p>
</header>
<h2>{{ titulo }}</h2>
<p><strong>Periodo:</strong> {{ periodo_desde }} al {{ periodo_hasta }}</p>
<p><strong>Total recibidos:</strong> {{ total_recibidos }} &nbsp; <strong>Total emitidos:</strong> {{ total_emitidos }}</p>
{{ secciones }}
<footer>Documento generado automáticamente por el proceso RPA (ejecución {{ id_ejecucion }}) el {{ fecha_generacion }}.
Pendiente de revisión y autorización de la Dirección de Carrera.</footer>
</body>
</html>$html$, TRUE);

INSERT INTO proceso_rpa (id_proceso, nombre, descripcion, version, tipo_proceso, estado, tiempo_max_segundos, tiempo_manual_minutos) VALUES
 (1, 'Informe mensual de correspondencia',
  'Consolida la correspondencia autorizada de un periodo, genera el informe PDF con la plantilla institucional y prepara el borrador de correo para revisión.',
  '1.0', 'CONSOLIDACION_CORRESPONDENCIA', 'HABILITADO', 300, 240);

-- =====================================================================
-- 8. POA 2026, ACTIVIDADES, EVENTOS Y SEGUIMIENTO
-- =====================================================================
INSERT INTO poa (id_poa, gestion, objetivo, estado) VALUES
 (1, 2026, 'Fortalecer la gestión académica y administrativa de la Carrera de Psicología mediante la digitalización y automatización de procesos.', 'EN_EJECUCION');

INSERT INTO actividad_poa (id_actividad, id_poa, descripcion, meta, responsable, fecha_inicio, fecha_fin, avance, estado) VALUES
 (1, 1, 'Digitalizar el archivo de correspondencia 2024-2026',      '100% de oficios digitalizados',       'Apoyo Profesional', '2026-02-02', '2026-11-30', 70.00, 'EN_CURSO'),
 (2, 1, 'Implementar el informe mensual de correspondencia con RPA','12 informes mensuales automatizados',  'Dirección de Carrera', '2026-03-01', '2026-12-15', 40.00, 'EN_CURSO'),
 (3, 1, 'Realizar talleres de salud mental para estudiantes',       '4 talleres en la gestión',            'Coordinación',      '2026-03-01', '2026-11-30', 50.00, 'EN_CURSO'),
 (4, 1, 'Actualizar la planificación académica del plan 148-2',     'Plan 148-2 aprobado y socializado',   'Coordinación',      '2026-02-02', '2026-06-30', 100.00,'CONCLUIDA'),
 (5, 1, 'Organizar el seminario de investigación en psicología',    '1 seminario con 100 participantes',   'Dirección de Carrera', '2026-08-01', '2026-10-31', 20.00, 'RETRASADA'),
 (6, 1, 'Reducir el tiempo de atención de trámites estudiantiles',  'Tiempo promedio menor a 7 días',      'Dirección de Carrera', '2026-02-02', '2026-12-15', 55.00, 'EN_CURSO');

INSERT INTO evento (id_evento, nombre, tipo, fecha, responsable, id_actividad) VALUES
 (1, 'Taller: Manejo del estrés académico',       'TALLER',    '2026-04-15', 'Lic. Carla Ortiz Melgar',   3),
 (2, 'Taller: Primeros auxilios psicológicos',    'TALLER',    '2026-09-10', 'Lic. Ricardo Méndez Paz',   3),
 (3, 'Seminario de Investigación en Psicología',  'SEMINARIO', '2026-11-27', 'Lic. Andrés Velasco Rivero', 5),
 (4, 'Bienvenida a estudiantes nuevos 2026-2',    'EVENTO',    '2026-08-05', 'Coordinación de Carrera',   NULL);

INSERT INTO seguimiento_actividad (id_actividad, fecha, avance, observacion, evidencia, id_evento) VALUES
 (1, '2026-05-30', 35.00, 'Digitalizados los oficios de 2024',                 'Acta de entrega 01/2026', NULL),
 (1, '2026-09-30', 70.00, 'Digitalizados los oficios de 2025',                 'Acta de entrega 02/2026', NULL),
 (2, '2026-09-15', 40.00, 'Prototipo del proceso RPA validado',                'Informe técnico 03/2026', NULL),
 (3, '2026-04-15', 25.00, 'Primer taller ejecutado',                           'Lista de asistencia',     1),
 (3, '2026-09-10', 50.00, 'Segundo taller ejecutado',                          'Lista de asistencia',     2),
 (4, '2026-06-30', 100.00,'Plan 148-2 aprobado por Consejo de Carrera',        'Resolución CC-015/2026',  NULL),
 (5, '2026-10-15', 20.00, 'Retraso en la confirmación de expositores',         NULL,                      NULL),
 (6, '2026-07-31', 55.00, 'Tiempo promedio de atención actual: 8,4 días',      'Reporte del sistema',     NULL);

-- =====================================================================
-- 9. TRABAJOS DE GRADO, TITULADOS Y MIGRACIONES (CU-15, CU-17, CU-18)
-- =====================================================================
INSERT INTO trabajo_grado (id_trabajo, id_estudiante, titulo, tutor, gestion, palabras_clave) VALUES
 (1, 47, 'Ansiedad académica en estudiantes de primer año de una universidad pública', 'Lic. Ricardo Méndez Paz',    2025, 'ansiedad, estudiantes, rendimiento'),
 (2, 48, 'Clima organizacional y satisfacción laboral en una empresa de servicios',    'Lic. Patricia Gutiérrez Ribera', 2025, 'clima organizacional, satisfacción'),
 (3, 49, 'Estrategias de afrontamiento en cuidadores de personas con discapacidad',    'Lic. Carla Ortiz Melgar',    2026, 'afrontamiento, cuidadores'),
 (4, 45, 'Uso de redes sociales y autoestima en adolescentes',                         'Lic. Lucía Peña Barba',      2026, 'redes sociales, autoestima, adolescencia');

INSERT INTO titulado (id_estudiante, id_trabajo, gestion, modalidad, fecha_defensa, nro_diploma) VALUES
 (47, 1, 2025, 'TESIS',          '2025-11-20', 'DIP-PSI-2025-031'),
 (48, 2, 2025, 'TRABAJO_DIRIGIDO','2025-12-04', 'DIP-PSI-2025-034');

INSERT INTO registro_migracion (id_estudiante, plan_origen, plan_destino, fecha, observacion) VALUES
 (12, '148-1', '148-2', '2026-02-20', 'Migración voluntaria con convalidación de 6 materias'),
 (16, '148-1', '148-2', '2026-02-21', 'Migración voluntaria con convalidación de 4 materias'),
 (20, '148-1', '148-2', '2026-08-12', 'Migración por cierre progresivo del plan 148-1');

-- =====================================================================
-- 10. BITÁCORA INICIAL
-- =====================================================================
INSERT INTO bitacora_auditoria (id_usuario, accion, entidad, id_entidad, direccion_ip, detalle)
VALUES (NULL, 'CARGA_SEED', 'SISTEMA', NULL, '127.0.0.1', 'Carga inicial de datos ficticios de desarrollo (Flyway V3)');

-- =====================================================================
-- 11. SINCRONIZAR SECUENCIAS DE IDENTIDAD (se insertaron ids explícitos)
-- =====================================================================
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN
        SELECT c.table_name, c.column_name
        FROM information_schema.columns c
        WHERE c.table_schema = current_schema() AND c.is_identity = 'YES'
    LOOP
        EXECUTE format(
            'SELECT setval(pg_get_serial_sequence(%L, %L), COALESCE((SELECT MAX(%I) FROM %I), 0) + 1, false)',
            r.table_name, r.column_name, r.column_name, r.table_name);
    END LOOP;
END $$;
