-- 既存DB向け: union_raids に通知メッセージID(notify_message_id)を追加する
-- Bot 再起動後も通知メッセージ(報告ボタン/Embed)を DB の message ID から再取得できるようにする
-- 新規環境は init.sql を参照すること(init.sql にも同じ ALTER が含まれるため、Bot 起動時に自動適用される)

ALTER TABLE union_raids
    ADD COLUMN IF NOT EXISTS notify_message_id BIGINT;
