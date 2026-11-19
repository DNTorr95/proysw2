-- =====================================================================
-- Proyecto 25 - Script de ajustes (PostgreSQL 16)  -> Flyway V2_1
-- Se ejecuta DESPUÉS del complementario y ANTES del seed.
-- No crea tablas: el modelo sigue teniendo 44 tablas.
--
-- 1) Agrega las 6 claves foráneas de conexión entre las 34 tablas base
--    y las 10 tablas del complementario.
-- 2) Sincroniza las secuencias de identidad (el complementario inserta
--    id_rol explícitos y las transiciones suponen id_estado 1..6).
-- 3) Alinea las guardas de transicion_estado con la especificación.
-- 4) Agrega restricciones de integridad a las tablas del complementario.
-- =====================================================================

-- ---------------- 1) Seis claves foráneas de conexión ----------------
ALTER TABLE tramite
    ADD CONSTRAINT fk_tramite_estado
    FOREIGN KEY (estado_actual) REFERENCES estado_tramite (nombre);

ALTER TABLE historial_estado
    ADD CONSTRAINT fk_historial_estado_origen
    FOREIGN KEY (estado_origen) REFERENCES estado_tramite (nombre);

ALTER TABLE historial_estado
    ADD CONSTRAINT fk_historial_estado_destino
    FOREIGN KEY (estado_destino) REFERENCES estado_tramite (nombre);

ALTER TABLE historial_estado
    ADD CONSTRAINT fk_historial_transicion
    FOREIGN KEY (id_transicion) REFERENCES transicion_estado (id_transicion);

ALTER TABLE titulado
    ADD CONSTRAINT fk_titulado_trabajo
    FOREIGN KEY (id_trabajo) REFERENCES trabajo_grado (id_trabajo);

ALTER TABLE seguimiento_actividad
    ADD CONSTRAINT fk_seguimiento_evento
    FOREIGN KEY (id_evento) REFERENCES evento (id_evento);

-- ---------------- 2) Secuencias de identidad ----------------
SELECT setval(pg_get_serial_sequence('rol', 'id_rol'),
              (SELECT COALESCE(MAX(id_rol), 0) + 1 FROM rol), false);
SELECT setval(pg_get_serial_sequence('estado_tramite', 'id_estado'),
              (SELECT COALESCE(MAX(id_estado), 0) + 1 FROM estado_tramite), false);
SELECT setval(pg_get_serial_sequence('transicion_estado', 'id_transicion'),
              (SELECT COALESCE(MAX(id_transicion), 0) + 1 FROM transicion_estado), false);

-- ---------------- 3) Guardas según la especificación ----------------
UPDATE transicion_estado SET guarda = 'justificación obligatoria'
 WHERE evento = 'observar';
UPDATE transicion_estado SET guarda = 'usuario con rol DIRECTOR_CARRERA y justificación obligatoria'
 WHERE evento = 'rechazar';

-- ---------------- 4) Integridad adicional ----------------
-- Un mismo evento no puede salir dos veces del mismo estado.
ALTER TABLE transicion_estado
    ADD CONSTRAINT uq_transicion_origen_evento UNIQUE (id_estado_origen, evento);

ALTER TABLE alerta
    ADD CONSTRAINT ck_alerta_origen CHECK (origen IN ('PLAZO', 'PREDICCION')),
    ADD CONSTRAINT ck_alerta_nivel  CHECK (nivel IN ('ALTA', 'MEDIA', 'BAJA')),
    ADD CONSTRAINT ck_alerta_estado CHECK (estado IN ('ABIERTA', 'EN_ATENCION', 'ATENDIDA'));

ALTER TABLE incidencia_rpa
    ADD CONSTRAINT ck_incidencia_tipo CHECK (tipo IN (
        'CAMPO_OBLIGATORIO', 'DUPLICADO', 'FECHA_INVALIDA', 'FALLA_PLANTILLA',
        'TIEMPO_EXCEDIDO', 'SERVICIO_NO_DISPONIBLE', 'ERROR_INTERNO')),
    ADD CONSTRAINT ck_incidencia_estado CHECK (estado IN ('ABIERTA', 'EN_REEJECUCION', 'CERRADA'));

ALTER TABLE evento
    ADD CONSTRAINT ck_evento_tipo CHECK (tipo IN ('TALLER', 'SEMINARIO', 'EVENTO'));

ALTER TABLE registro_migracion
    ADD CONSTRAINT ck_migracion_planes CHECK (plan_origen <> plan_destino);
