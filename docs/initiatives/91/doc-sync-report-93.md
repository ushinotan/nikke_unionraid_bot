# doc-sync report #93

- 対象プロジェクト: backend (`kotlin/`)
- 変更コード: RaidApiController, RaidWriteRequests(新規), ApiResponses, DtoMappers, RaidParticipantRepository, Difficulty, RaidDomainError, RaidDomainService（+テスト）
- 更新した docs:
  - `kotlin/docs/API.md`: 概要、認可（書き込み API）、新節「書き込みエンドポイント」(5〜9)、実装メモ。レビュー依頼の6点（認可モデル / 400→404 の順 / 409・終了済み書き込み可 / PATCH 上書き / 最後の報告削除で participantRemoved / Embed 非更新）を記載
  - `docs/codebase/backend/routes-map.md`: 新ルート5件、書き込み API の注意
  - `docs/codebase/backend/services-map.md`: registerParticipant / updateParticipantScore、allowFinished、エラー一覧、引き継ぎ欄
  - `docs/conventions/backend.md`: 変更なし（該当する記述の変更が不要と判断）
- alerts: なし。`RaidDomainService.registerParticipant` の KDoc は username「255 文字以内」だが、API は 100 文字で検証（矛盾ではないが差あり）。
