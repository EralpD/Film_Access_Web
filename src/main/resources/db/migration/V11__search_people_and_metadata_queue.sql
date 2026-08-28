-- Search projections, not a second source of truth for people or credits.
CREATE TABLE film_search_people (
    film_id bigint NOT NULL REFERENCES films(id) ON DELETE CASCADE,
    role text NOT NULL CHECK (role IN ('actor', 'director')),
    name text NOT NULL,
    words text[] GENERATED ALWAYS AS (string_to_array(name, ' ')) STORED,
    PRIMARY KEY (film_id, role, name)
);
CREATE INDEX idx_search_people_name ON film_search_people (name, role, film_id);
CREATE INDEX idx_search_people_words ON film_search_people USING gin (words);
CREATE INDEX idx_search_people_fuzzy ON film_search_people USING gist (name gist_trgm_ops);
CREATE INDEX idx_films_search_title_nearest ON films USING gist (search_title gist_trgm_ops);

CREATE FUNCTION archive_sync_people() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.director IS NOT DISTINCT FROM OLD.director
        AND NEW.actors IS NOT DISTINCT FROM OLD.actors THEN RETURN NEW; END IF;
    DELETE FROM film_search_people WHERE film_id = NEW.id;
    INSERT INTO film_search_people(film_id, role, name)
    SELECT DISTINCT NEW.id, source.role, archive_normalize_title(person)
    FROM (VALUES ('director', NEW.director), ('actor', NEW.actors)) source(role, names)
    CROSS JOIN LATERAL regexp_split_to_table(source.names, ',') person
    WHERE btrim(person) <> '' AND upper(btrim(person)) <> 'N/A'
      AND archive_normalize_title(person) <> '';
    RETURN NEW;
END
$$;
CREATE TRIGGER films_sync_people AFTER INSERT OR UPDATE OF director, actors ON films
    FOR EACH ROW EXECUTE FUNCTION archive_sync_people();

INSERT INTO film_search_people(film_id, role, name)
SELECT DISTINCT f.id, source.role, archive_normalize_title(person)
FROM films f CROSS JOIN LATERAL
    (VALUES ('director', f.director), ('actor', f.actors)) source(role, names)
CROSS JOIN LATERAL regexp_split_to_table(source.names, ',') person
WHERE btrim(person) <> '' AND upper(btrim(person)) <> 'N/A'
  AND archive_normalize_title(person) <> '';

-- A durable, bounded-worker queue: details are refreshed after the page request.
CREATE TABLE film_metadata_refresh (
    film_id bigint PRIMARY KEY REFERENCES films(id) ON DELETE CASCADE,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO film_metadata_refresh(film_id)
SELECT f.id FROM films f WHERE EXISTS (SELECT 1 FROM user_films uf WHERE uf.film_id = f.id)
    AND (coalesce(btrim(f.actors), '') IN ('', 'N/A')
         OR coalesce(btrim(f.director), '') IN ('', 'N/A'));

-- Viewed films can be indexed without blocking their detail page, even if not saved.
ALTER TABLE films ADD COLUMN embedding_requested boolean NOT NULL DEFAULT false;
