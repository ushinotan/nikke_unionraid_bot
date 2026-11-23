## Self-test result (#93)

### ビルド / 起動チェック
- backend: `./gradlew build` ✅、`./gradlew test --rerun-tasks` ✅（258 件、失敗 0、エラー 0、スキップ 1）
- frontend: 対象外（変更なし）
- 起動チェック（bootRun）: スキップ。既存コンテナが別 worktree 由来で、Discord に接続するため
- 新しい成果物: `RaidApiController` に 5 ルート（POST/PATCH/DELETE participants、POST/DELETE reports）を確認 ✅
- `init.sql` / `migrations/` の変更なし ✅

### 手動確認チェックリスト（受け入れ条件由来）
- [ ] 5 本のエンドポイントが期待どおり動く（201/200）。`userId` はレスポンスでも文字列
- [ ] 不正入力は 400（score が負・小数・文字列・INT 超過、difficulty 不正、壊れた JSON、空ボディ）、Content-Type 違いは 415
- [ ] 存在しないレイド・参加者・報告は 404、参加登録の重複は 409
- [ ] 3T 報告で未参加なら自動参加、最後の報告の取り消しで参加者も削除
- [ ] 終了済みレイドでも 5 本とも成功。Discord 経由は従来どおり RaidAlreadyFinished
- [ ] トークン無しは 401
- [ ] Discord の Embed を Web 操作では更新しない（コントローラーは Scheduler/JDA 非依存）
