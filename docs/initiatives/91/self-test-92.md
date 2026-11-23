## Self-test result (#92)

### ビルド / 起動チェック
- backend (kotlin): `./gradlew build` ✅ BUILD SUCCESSFUL（テスト結果 XML 集計: 190 件 / skipped 1 / failures 0 / errors 0。formal suite は参考値）
- frontend (web): 変更なしのため対象外
- `init.sql` / `migrations/` に変更なし ✅
- 3 メソッド（reportThreeTurn / cancelThreeTurnReports / cancelParticipation）に `@Transactional(rollbackFor = [Exception::class])` ✅
- listener は `RaidDomainService` 経由のみ（GuildRepository / UnionRaidRepository の参照のみで、参加者・報告リポジトリを直接呼ばない）✅
- DB に統合テストの残骸（guild_id 70_092_000..999）なし ✅

### 手動確認チェックリスト（本物の Discord。AC1/AC2）
- [ ] AC1: ボタンで 3T 報告すると、`GET /api/raids/{raidId}` の `participants` に自分（スコア 0）が出る
- [ ] 既に参加者（スコア入力済み）の人が報告しても、スコア・username・joined_at が上書きされない
- [ ] 同じ報告を重複して行うと「報告済み」の文言になる
- [ ] AC2: 報告をすべて取り消すと `participants` から消え、メッセージに参加者から外れた旨（スコアも消える旨）が出る
- [ ] 報告を一部だけ取り消した場合は参加者に残る
- [ ] 終了済みレイドでは報告・取り消しが拒否される
- [ ] AC3/AC4: 参加取り消し（ドメインメソッド）は自動テストで確認（API は未作成のため手動不可）

### RBAC / スコーピング
- userId は `event.user.idLong` から取得（要コードで確認済みの範囲外は手動で確認）
- 本人・特権の確認と終了済み判定は呼び出し側（web）の責務
