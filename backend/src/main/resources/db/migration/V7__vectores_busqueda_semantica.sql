-- =====================================================================
-- V7: vectores de la búsqueda semántica (CU-24) con pgvector.
-- servicios-ia calcula los vectores (SentenceTransformers multilingüe, 384
-- dimensiones, normalizados) y el backend los guarda aquí; la consulta se
-- resuelve en PostgreSQL con distancia coseno y un índice HNSW. Así el índice
-- sobrevive a los reinicios y solo se recalculan los textos que cambiaron
-- (se compara el SHA-256 del texto).
-- Requiere la imagen pgvector/pgvector:pg16 (docker-compose.yml).
-- Agrega 1 tabla. No modifica las existentes.
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE embedding_documento (
    clave               VARCHAR(50)  NOT NULL,            -- CORR-<id>, TRAM-<id>, DOC-<id>, TRAB-<id>
    hash_texto          VARCHAR(64)  NOT NULL,            -- SHA-256 del texto indexado
    motor               VARCHAR(120) NOT NULL,            -- modelo que generó el vector
    embedding           vector(384)  NOT NULL,
    fecha_actualizacion TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_embedding_documento PRIMARY KEY (clave),
    CONSTRAINT ck_embedding_documento_clave CHECK (clave ~ '^(CORR|TRAM|DOC|TRAB)-[0-9]+$')
);

-- Vecinos más cercanos por distancia coseno (operador <=>)
CREATE INDEX ix_embedding_documento_hnsw ON embedding_documento USING hnsw (embedding vector_cosine_ops);
