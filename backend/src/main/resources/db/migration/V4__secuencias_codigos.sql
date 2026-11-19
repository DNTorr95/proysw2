-- =====================================================================
-- V4: secuencias para los códigos únicos generados por el sistema.
-- No agrega tablas ni columnas (el modelo sigue con 44 tablas).
--   tramite.numero              -> TRM-<gestión>-<nnnn>
--   correspondencia.codigo      -> DCP-REC|EMI-<gestión>-<nnnn>
--   expediente.codigo           -> EXP-<gestión>-<nnnn>
-- Las secuencias continúan después de los datos del seed.
-- =====================================================================

CREATE SEQUENCE seq_tramite_numero;
CREATE SEQUENCE seq_correspondencia_codigo;
CREATE SEQUENCE seq_expediente_codigo;

SELECT setval('seq_tramite_numero',         COALESCE((SELECT MAX(id_tramite)         FROM tramite), 0) + 1, false);
SELECT setval('seq_correspondencia_codigo', COALESCE((SELECT MAX(id_correspondencia) FROM correspondencia), 0) + 1, false);
SELECT setval('seq_expediente_codigo',      COALESCE((SELECT MAX(id_expediente)      FROM expediente), 0) + 1, false);
