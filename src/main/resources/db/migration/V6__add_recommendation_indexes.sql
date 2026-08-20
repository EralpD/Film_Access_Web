CREATE INDEX IF NOT EXISTS
    idx_user_films_film_user
ON user_films (
    film_id,
    user_id
);

CREATE INDEX IF NOT EXISTS
    idx_films_embedding_hnsw
ON films
USING hnsw (
    embedding vector_cosine_ops
)
WHERE embedding IS NOT NULL;