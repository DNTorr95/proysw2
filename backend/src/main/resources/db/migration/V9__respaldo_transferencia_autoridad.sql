-- =====================================================================
-- V9: documento de respaldo de la transferencia de autoridad (CU-04).
-- Hasta ahora el respaldo era solo el número de resolución (texto). Se
-- agrega una referencia opcional al archivo escaneado (PDF/JPG/PNG/DOCX)
-- guardado en el volumen documental. No agrega tablas (sigue con 44).
-- =====================================================================
ALTER TABLE transferencia_autoridad
    ADD COLUMN id_archivo_respaldo INTEGER,
    ADD CONSTRAINT fk_transferencia_archivo_respaldo
        FOREIGN KEY (id_archivo_respaldo) REFERENCES archivo_adjunto (id_archivo);

CREATE INDEX ix_transferencia_archivo_respaldo ON transferencia_autoridad (id_archivo_respaldo);
