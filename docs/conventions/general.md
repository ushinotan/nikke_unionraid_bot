# 全体共通

## 言語

- **必須**: ドキュメント、コメント、KDoc / JSDoc、コミットメッセージ、PR、Issue は日本語で書く。
- **必須**: ユーザー（Discord・Web 画面）に見せる文言は日本語。
- **必須**: 識別子（クラス名、関数名、変数名、ファイル名）は英語。ただしテストメソッド名とテストケース名は日本語でよい（[testing.md](testing.md)）。
- **推奨**: ログメッセージは日本語。キーと値は `raidId=$raidId guildId=$guildId` の形で後ろに並べる。
- 例外: REST API のエラーレスポンス（`error` / `message`）と web のサーバーログの接頭辞（`[Backend]` など）は英語のままでよい。既存の API 契約に合わせるため。

## ブランチ

- `develop` が開発の基点。PR はすべて `develop` 向けに出す。
- `master` はリリース用。`develop` → `master` のリリース PR のあと、`master` を `develop` にバックマージする。
- **必須**: `develop` と `master` に直接 push しない。
- **推奨**: ブランチ名は `<type>/<短い説明>`（例: `feat/raid-ranking-edit`、`fix/notify-resume`）。AI エージェントのブランチは `claude/*`、依存更新は `dependabot/*` のままでよい。

## コミットメッセージ

形式は `<type><gitmoji>(<scope>): <日本語の要約>` です。

```
feat✨(web): 書き込み系 API Route 用のサーバー側権限チェックを追加
fix🐛(web): permissions が10進数の非負整数のときだけ権限を判定する
docs📝: factory プラグイン用の CodeBase マップ docs/codebase を追加
chore🔒(ci): pr-test の GITHUB_TOKEN 権限を contents: read に制限
```

| type | gitmoji | 用途 |
| --- | --- | --- |
| `feat` | ✨ | 機能追加 |
| `fix` | 🐛 | バグ修正 |
| `refactor` | ♻️ | 振る舞いを変えない整理 |
| `test` | ✅ | テストの追加・修正 |
| `docs` | 📝 | ドキュメント |
| `chore` | 🔧 / 🔒 / ⬆️ | 設定、セキュリティ、依存更新など |

- scope は `kotlin` / `web` / `db` / `ci` / `docker` / `conventions` など変更した場所。複数にまたがるなら省略してよい。
- **必須**: 要約は「何をしたか」が分かる一文にする。末尾に句点は付けない。
- **推奨**: 1 コミットには 1 つの目的だけを入れる。

## PR

- **必須**: `.github/pull_request_template.md`（機能は `feature.md`、バグ修正は `bugfix.md`）を埋める。
- **必須**: CI（`.github/workflows/pr-test.yml`）が通ってからマージする。
- **必須**: DB スキーマ、環境変数、REST API の形を変えたら、PR の「影響範囲」に書く。
- **必須**: `docs/codebase/` の地図と食い違う変更をしたら、同じ PR で地図も直す。

## 秘密情報と設定

- **必須**: トークン、パスワード、セッション鍵をコード・テスト・ログに書かない。`.env` はコミットしない。
- **必須**: 新しい環境変数を足したら `env.example` に追記し、`DEPLOYMENT.md` の該当箇所も直す。
- **必須**: 認証や権限の設定がないときは拒否する側に倒す（fail-closed）。例: `INTERNAL_API_TOKEN` が未設定なら `/api/**` は全拒否。

## 時刻

- **必須**: 時刻は UTC で保存・計算し、表示するときだけ `DEFAULT_TIMEZONE_HOURS`（既定 9 = JST）でずらす。
- **必須**: 「現在時刻」を使う関数は `now` を引数で受け取れるようにし、テストから固定できるようにする（例: `RaidDomainService.createRaid(..., now = TimeUtils.utcNow())`）。

## Discord の ID

- **必須**: Discord の snowflake ID（ギルド、ユーザー、チャンネル、メッセージ）は Kotlin では `Long`、JSON と TypeScript では `string` で扱う。JavaScript の `number` は 53bit を超える値で桁が落ちるため。
