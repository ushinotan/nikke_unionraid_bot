# CodeBase マップ

NIKKE のユニオンレイドを管理する Discord ボットと Web ダッシュボードのコード地図です。
factory プラグイン（PO / DEV / CR）がコードを開く前に読み、DEV の doc-sync が変更に合わせて更新します。

- [overview.md](overview.md): スタック、構成、共通の約束事
- backend（`kotlin/`）
  - [entities.md](backend/entities.md): テーブルとエンティティ
  - [routes-map.md](backend/routes-map.md): REST API と Discord のスラッシュコマンド・コンポーネント
  - [services-map.md](backend/services-map.md): サービス、リポジトリ、ドメインエラー
  - [jobs-map.md](backend/jobs-map.md): 通知と自動終了のスケジューラー
- frontend（`web/`）
  - [pages.md](frontend/pages.md): 画面
  - [routes-map.md](frontend/routes-map.md): API Route（BFF）と proxy
  - [services-map.md](frontend/services-map.md): `lib/` のセッション・権限・バックエンド呼び出し
  - [components-map.md](frontend/components-map.md): 共通 UI とクライアント側ユーティリティ
