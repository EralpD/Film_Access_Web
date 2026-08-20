UPDATE films
SET type = UPPER(type)
WHERE type IS NOT NULL;