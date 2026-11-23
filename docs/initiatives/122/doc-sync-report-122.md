# Doc Sync レポート #122

- 検出プロジェクト: web (frontend) + CI
- 変更コードファイル: .github/workflows/pr-test.yml, web/{.dockerignore,.gitignore,eslint.config.mjs,package.json,package-lock.json,playwright.config.ts}, web/e2e/**
- 更新したドキュメント:
  - docs/factory.config.md: stack.frontend.e2e, evidence.artifacts.{videos,screenshots,frontend_result}, tests.layout.frontend_e2e, Project notes(E2E 追記)
  - docs/conventions/testing.md: 「E2E（Playwright）」節を追加
  - docs/codebase/overview.md, docs/codebase/frontend/services-map.md: E2E のテスト配置を追記
- アラート: なし。evidence の値は web/playwright.config.ts(outputDir=test-results, junit=e2e-results/junit.xml)と照合済み
