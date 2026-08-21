ALTER TABLE films
    ADD COLUMN embedding_model VARCHAR(100),
    ADD COLUMN embedding_content_hash VARCHAR(64),
    ADD COLUMN embedding_updated_at TIMESTAMPTZ;

-- Eski vektörlerin hangi model ve içerikle üretildiği bilinmiyor.
-- Yanlış vektör uzaylarını karıştırmamak için yalnızca türetilmiş veri
-- geçersiz kılınır; film kataloğu verileri korunur.
UPDATE films
SET embedding = NULL
WHERE embedding IS NOT NULL;
