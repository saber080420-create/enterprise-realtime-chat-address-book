CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS announcement_embedding (
    announcement_id INTEGER NOT NULL,
    model_version VARCHAR(150) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    chunk_index INTEGER NOT NULL,
    embedding vector(1024) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (announcement_id, model_version, chunk_index)
);
-- Exact cosine search after permission filtering for the first 200-document implementation.
-- No ANN index yet: benchmark recall/filter behaviour before adding HNSW.
CREATE TABLE IF NOT EXISTS document_embedding (
    document_id INTEGER NOT NULL,
    model_version VARCHAR(150) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    chunk_index INTEGER NOT NULL,
    embedding vector(1024) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (document_id, model_version, chunk_index)
);
