## Self-test result

- frontend build (`npm run build`): ✅
- lint (`npm run lint`): ✅ 警告・エラーなし
- 単体テスト (`npm test`): ✅ 139 pass / 0 fail(参考)
- E2E (`npm run test:e2e`, 3100/18080): ✅ 14 passed
- docker build (web/, 使い捨てタグ、削除済み): ✅ .dockerignore で壊れない
- backend: 変更なし(スキップ)。既存スタックは別 worktree のため未操作

### 手動確認チェックリスト
- [ ] CI(pr-test.yml)で E2E ジョブが実行され、成功する
- [ ] 未ログインで /raids → 307 リダイレクト、/api/* → 401
- [ ] 非所属ギルドのレイド一覧/詳細/選択が 403
- [ ] ログイン済みで一覧・詳細が表示され、bot API エラー時に再読み込みボタンが出る
- [ ] E2E 成果物(test-results / playwright-report)が git 管理外
