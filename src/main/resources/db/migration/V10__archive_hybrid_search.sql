CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;

CREATE FUNCTION archive_normalize_title(value text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT btrim(regexp_replace(lower(translate(coalesce(value, ''),
        'İIıÇçĞğÖöŞşÜüÁáÀàÂâÄäÉéÈèÊêËëÍíÌìÎîÏïÓóÒòÔôÚúÙùÛûÑñ',
        'iiiCcGgOoSsUuAaAaAaAaEeEeEeEeIiIiIiIiOoOoOoUuUuUuNn')),
        '[^[:alnum:]]+', ' ', 'g'))
$$;

-- Keep this serialization in sync with FilmEmbeddingService.buildSearchText.
CREATE FUNCTION archive_embedding_field(label text, value text, separator text)
RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT CASE WHEN value IS NULL
        OR btrim(value, E' \t\n\r\f\013') = ''
        OR upper(btrim(value, E' \t\n\r\f\013')) = 'N/A'
        THEN '' ELSE label || ': ' || btrim(value, E' \t\n\r\f\013') || separator END
$$;

CREATE FUNCTION archive_embedding_text(title text, year_text text, film_type text,
    genres text, director text, actors text, plot text, separator text)
RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT archive_embedding_field('Title', title, separator)
        || archive_embedding_field('Year', year_text, separator)
        || archive_embedding_field('Type', film_type, separator)
        || archive_embedding_field('Genres', genres, separator)
        || archive_embedding_field('Director', director, separator)
        || archive_embedding_field('Actors', actors, separator)
        || archive_embedding_field('Plot', plot, separator)
$$;

-- The encoding is fixed, so the digest is deterministic within this database.
-- Wrapping convert_to avoids its generic STABLE volatility in generated columns.
CREATE FUNCTION archive_embedding_hash(value text) RETURNS text
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
BEGIN
    RETURN encode(sha256(convert_to(value, 'UTF8')), 'hex');
END
$$;

ALTER TABLE films
    ADD COLUMN search_title text GENERATED ALWAYS AS (archive_normalize_title(title)) STORED,
    ADD COLUMN search_content_hash text GENERATED ALWAYS AS (
        archive_embedding_hash(archive_embedding_text(title, year_text, type,
            genres_text, director, actors, plot, E'\n'))) STORED,
    ADD COLUMN embedding_retry_count integer NOT NULL DEFAULT 0,
    ADD COLUMN embedding_retry_after timestamptz,
    ADD COLUMN embedding_retry_model text,
    ADD COLUMN embedding_retry_hash text;

-- Adopt valid Windows-generated hashes without replacing their vectors.
UPDATE films SET embedding_content_hash = search_content_hash
WHERE embedding IS NOT NULL AND embedding_content_hash = encode(sha256(convert_to(
    archive_embedding_text(title, year_text, type, genres_text, director, actors, plot,
        E'\r\n'), 'UTF8')), 'hex');

CREATE INDEX idx_films_search_title_trgm ON films USING gin (search_title gin_trgm_ops);
CREATE INDEX idx_films_search_title_exact ON films (search_title);
