CREATE INDEX IF NOT EXISTS
    idx_user_films_user_added_at_desc
ON user_films (
    user_id, 
    added_at DESC
)