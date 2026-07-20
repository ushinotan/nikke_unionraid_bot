-- 既存DB向け: union_raids に finished_at を追加し、終了済み行を埋める
-- 新規環境は init.sql を参照すること

ALTER TABLE union_raids
    ADD COLUMN IF NOT EXISTS finished_at TIMESTAMPTZ;

-- 既存の「終了済み」（end_time が過去）を finished 扱いにする
UPDATE union_raids
SET finished_at = end_time
WHERE finished_at IS NULL
  AND end_time <= now();
