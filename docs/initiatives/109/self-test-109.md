## Self-test result

- frontend build (`npm run build`): OK
- frontend lint (`npm run lint`): OK (警告・エラーなし)
- backend: 対象外（変更なし）
- npm test (参考): 104 pass / 0 fail

### 手動確認チェックリスト
- [ ] write-route-guard.test.ts が書き込み系 API ルート（未認証/権限なし）を拒否することを検証している
- [ ] raid-authorization.test.ts が既存の権限判定を維持している
- [ ] 本番コードに差分がない（web/__tests__ のみ）
