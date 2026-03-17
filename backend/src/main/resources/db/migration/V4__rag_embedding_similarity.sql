CREATE TABLE IF NOT EXISTS doc_chunk_embeddings (
    chunk_id VARCHAR(80) PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    dimension INT NOT NULL,
    embedding_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_doc_chunk_embeddings_chunk FOREIGN KEY (chunk_id) REFERENCES doc_chunks(chunk_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_doc_chunk_embeddings_status ON doc_chunk_embeddings(status);
CREATE INDEX IF NOT EXISTS idx_doc_chunk_embeddings_updated_at ON doc_chunk_embeddings(updated_at);
