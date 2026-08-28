ALTER TABLE films ADD COLUMN search_genres text[] GENERATED ALWAYS AS
    (regexp_split_to_array(lower(coalesce(genres_text, '')), '\s*,\s*')) STORED;

CREATE INDEX idx_films_search_genres ON films USING gin (search_genres);
