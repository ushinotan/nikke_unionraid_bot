# backend（`kotlin/`）

Kotlin 2.3 + Spring Boot 4 + JDA 6 + Komapper 6、Java 21。

## パッケージ構成

ルートパッケージは `gg.nikke.raid.bot`。層ごとにパッケージを分けます。

| パッケージ | 置くもの | 依存してよい先 |
| --- | --- | --- |
| `bot` | JDA のリスナー、コマンド登録、スケジューラー | `service`, `repository`, `config`, `util` |
| `controller` | REST コントローラー | `dto`, `repository`, `service` |
| `dto` | API のレスポンス型と、エンティティからの変換拡張関数 | `entity` |
| `service` | ドメインロジック、ドメインエラー、ドメインの enum | `repository`, `entity`, `util` |
| `repository` | Komapper のクエリ | `entity` |
| `entity` | `@KomapperEntity` のデータクラス | なし |
| `config` | Spring の設定、フィルター | なし |
| `util` | 状態を持たない共通処理（`TimeUtils` など） | なし |

- **必須**: `entity` と `repository` から `bot` / `controller` / `service` を参照しない。
- **必須**: 書き込みを伴う業務ルール（期間の下限、進行中レイドの重複防止、順位とパーセンテージの排他など）は `service` に置く。`bot` や `controller` からリポジトリの書き込みメソッドを直接呼んで業務ルールを迂回しない。
- **推奨**: 読み取りだけのコントローラーはリポジトリを直接呼んでよい（`RaidApiController` の現状）。

## 命名

- クラス: `PascalCase`。役割を接尾辞で表す（`*Service`, `*Repository`, `*Controller`, `*EventListener`, `*Scheduler`, `*Dto`, `*Response`）。
- 関数・プロパティ: `camelCase`。定数は `companion object` 内の `private const val UPPER_SNAKE_CASE`（`const` にできない値は `private val UPPER_SNAKE_CASE`）。
- リポジトリのメソッドは `find*` / `insert*` / `save*` / `update*` / `delete*` で始め、対象と条件を名前に入れる（例: `findActiveUnionRaidByGuildId`）。
- テーブル名は `@KomapperTable("union_raids")` で明示する。プロパティ名は `camelCase`、カラム名は `snake_case`（Komapper の既定の変換に任せる）。

## 書式

- インデント 4 スペース。1 行は 120 文字を目安にする。
- **必須**: 引数・プロパティを複数行に分けるときは末尾カンマを付ける。
- **必須**: ワイルドカード import（`import foo.*`）を使わない。
- **推奨**: 名前付き引数を使う。引数が 3 つ以上ある呼び出しや、同じ型が並ぶ呼び出しでは必須。
- **推奨**: 1 つの式で返せる関数は式本体（`fun f(): T = ...`）にする。
- 現状フォーマッターは入れていないので、IntelliJ の Kotlin 公式スタイル（Kotlin coding conventions）に合わせる。

## Kotlin の書き方

- **必須**: `!!` を使わない。null にならない理由があるなら `?: error("理由")` や `requireNotNull(x) { "理由" }` で理由を残す。
- **必須**: 値の変更が必要ない限り `val` を使う。エンティティと DTO は `data class` で、プロパティはすべて `val`。
- **推奨**: 状態の分岐は `when` と sealed class / enum で網羅させる。
- **推奨**: 文字列で表すドメイン値は enum に寄せる（例: `Difficulty`。DB には `value` を保存し、画面には `label` を出す）。

## エラー処理

- **必須**: 想定内の失敗（入力不正、状態不正、対象なし）は `service` から `Result<T>` で返す。中身は `RaidDomainError`（sealed class）のサブクラスにし、メッセージは日本語でユーザーにそのまま見せられる文にする。
- **必須**: 新しい失敗パターンを足すときは `RaidDomainError` にサブクラスを足す。汎用の `Exception` や文字列で返さない。
- **必須**: 想定外の例外は握りつぶさない。`catch` したら `logger.error(..., e)` で例外ごと記録し、ユーザーには汎用のエラーメッセージを返す。
- REST API では `@ExceptionHandler` で `ErrorResponse(error = "UPPER_SNAKE_CASE", message = "...")` に変換する。`error` は `BAD_REQUEST` / `NOT_FOUND` / `INTERNAL_SERVER_ERROR` などの固定値。

## Spring

- **必須**: 依存はコンストラクタ注入で `private val` として受け取る。フィールドに `@Autowired` を付けない。
- **必須**: 設定値は `application.properties` に `${ENV_NAME:既定値}` で定義し、`AppConfig` などの設定クラス経由で読む。コード内で `System.getenv` を直接呼ばない。
- **必須**: `/health` 以外の HTTP エンドポイントは `/api/**` の下に置く（`InternalApiTokenFilter` の保護範囲に入れるため）。
- REST API を足したり変えたりしたら `kotlin/docs/API.md` を直す。

## JDA（Discord）

- **必須**: インタラクションには 3 秒以内に応答する。重い処理の前に `deferReply()` を呼び、続きは `event.hook` で送る。
- **必須**: `queue()` には失敗時のコールバックを渡すか、失敗してもよい理由をコメントで残す。`complete()` はイベントスレッドで使わない。
- **必須**: エラーや本人にしか関係ない応答は `setEphemeral(true)` にする。
- **必須**: スラッシュコマンドを足したら `CommandRegistrar` に登録し、`docs/codebase/backend/routes-map.md` に追記する。
- **推奨**: コンポーネント ID（ボタン、モーダル、セレクト）は `snake_case` の接頭辞 + パラメーター（例: `raid_start_modal_$duration`）にし、接頭辞は定数にまとめる。
- **推奨**: Embed の色やメッセージ文言など繰り返し使う値は定数にする。

## ログ

- **必須**: ロガーは `private val logger = LoggerFactory.getLogger(<クラス>::class.java)` で作る。
- レベルの目安: `info` は状態の変化（スケジュール登録、通知送信、起動・停止）、`warn` は処理を続けられる失敗（Discord への送信失敗など）、`error` は想定外の例外、`debug` は調査用の詳細。
- **必須**: トークンやセッションの中身をログに出さない。

## KDoc

- **必須**: `service` と `repository` の public 関数、REST コントローラーのハンドラーには KDoc を書く。`@param` と `@return` を付け、`Result` を返すなら失敗時のエラーも書く。
- **推奨**: 文体は「〜する。」で揃える（既存の「〜します。」は触ったときに直す）。
- **推奨**: 行コメントは「なぜそうしたか」を書く。コードを読めば分かることは書かない。
