-- =====================================================================
-- V6: índices de trigramas (pg_trgm) para las búsquedas por texto.
-- La búsqueda avanzada, las consultas con filtros y los listados usan
-- lower(columna) LIKE '%texto%'. Un índice B-tree no sirve para un patrón
-- que empieza con %, así que PostgreSQL recorría las tablas completas.
-- Un índice GIN con gin_trgm_ops sí puede resolver LIKE '%…%'.
-- Cada índice reproduce exactamente la expresión que genera Hibernate
-- (por ejemplo lower(coalesce(descripcion,''))), si no el planificador no lo usa.
-- No agrega tablas ni columnas (el modelo sigue con 44 tablas).
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Correspondencia y dependencias
CREATE INDEX ix_trgm_correspondencia_codigo      ON correspondencia USING gin (lower(codigo) gin_trgm_ops);
CREATE INDEX ix_trgm_correspondencia_asunto      ON correspondencia USING gin (lower(asunto) gin_trgm_ops);
CREATE INDEX ix_trgm_correspondencia_descripcion ON correspondencia USING gin (lower(coalesce(descripcion, '')) gin_trgm_ops);
CREATE INDEX ix_trgm_destinatario_unidad         ON destinatario    USING gin (lower(unidad) gin_trgm_ops);
CREATE INDEX ix_trgm_destinatario_nombre         ON destinatario    USING gin (lower(nombre) gin_trgm_ops);

-- Trámites, estudiantes y expedientes
CREATE INDEX ix_trgm_tramite_numero              ON tramite         USING gin (lower(numero) gin_trgm_ops);
CREATE INDEX ix_trgm_tramite_observaciones       ON tramite         USING gin (lower(coalesce(observaciones, '')) gin_trgm_ops);
CREATE INDEX ix_trgm_persona_nombre_completo     ON persona         USING gin (lower((nombres || ' ' || apellidos)) gin_trgm_ops);
CREATE INDEX ix_trgm_estudiante_registro         ON estudiante      USING gin (registro gin_trgm_ops);
CREATE INDEX ix_trgm_expediente_codigo           ON expediente      USING gin (lower(codigo) gin_trgm_ops);
CREATE INDEX ix_trgm_expediente_titulo           ON expediente      USING gin (lower(titulo) gin_trgm_ops);

-- Documentos y texto OCR
CREATE INDEX ix_trgm_archivo_nombre              ON archivo_adjunto USING gin (lower(nombre_archivo) gin_trgm_ops);
CREATE INDEX ix_trgm_archivo_descripcion         ON archivo_adjunto USING gin (lower(coalesce(descripcion, '')) gin_trgm_ops);
CREATE INDEX ix_trgm_ocr_texto                   ON extraccion_ocr  USING gin (lower(texto_extraido) gin_trgm_ops);

-- Trabajos de grado
CREATE INDEX ix_trgm_trabajo_titulo              ON trabajo_grado   USING gin (lower(titulo) gin_trgm_ops);
CREATE INDEX ix_trgm_trabajo_palabras_clave      ON trabajo_grado   USING gin (lower(coalesce(palabras_clave, '')) gin_trgm_ops);
CREATE INDEX ix_trgm_trabajo_tutor               ON trabajo_grado   USING gin (lower(coalesce(tutor, '')) gin_trgm_ops);
