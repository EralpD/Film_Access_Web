CREATE EXTENSION IF NOT EXISTS vector; -- Activating pgvector properties for database

ALTER TABLE films
    ADD COLUMN embedding vector(1536); -- For now on, films table includes vectors size of 1536 (required dimenstion for model)