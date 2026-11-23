## Self-test result

- frontend build (next build): OK
- lint (eslint): OK (警告なし)
- npm test: 139/139 pass(参考)
- npm audit: 0 vulnerabilities
- 解決版: zod 4.6.7 / typescript-eslint 8.73.0 / minimatch 11.0.9 (hoisted)

### 手動確認チェックリスト
- [ ] `npm ci` が lockfile 通りに成功する
- [ ] `npm audit` で zod / minimatch の指摘が出ない
- [ ] `npm run build` / `npm run lint` が通る
- [ ] 本番依存(dependencies)に差分がない
