# レイド読み取り API ドキュメント

## 概要

Spring Boot で実装された REST API（読み取り＋参加者・3T 報告の書き込み）。
Next.js BFF からの内部呼び出しを想定している。

## ⚠️ セキュリティ / 認証に関する重要な注意事項

**このAPIは内部向け（Internal-Only）として設計されています。**

- **公開禁止**: これらのエンドポイントを直接インターネットに公開しないでください
- **想定用途**: Next.js BFF または信頼されたネットワーク内からのアクセスのみ
- **内部トークン認証**: `/api` 配下は `X-Internal-Api-Token` ヘッダーが環境変数 `INTERNAL_API_TOKEN` と一致する場合のみ許可します（不一致・ヘッダー無しは `401`）
- **fail-closed**: Backend 側に `INTERNAL_API_TOKEN` が未設定の場合、`/api` 配下は全リクエストを `401` で拒否します（`/health` は対象外）
- **認可（書き込み API）**: bot が確認するのは `X-Internal-Api-Token` だけ。呼び出し元ユーザーの権限や、`userId` が操作者本人かどうかは確認しない。BFF が `checkParticipantEdit`（`web/lib/permissions.ts`）で権限を判定し、`userId` はセッションから取ること（代理登録は #94 のメンバー一覧から選ばせる）
- **ユーザー認証**: Discord OAuth によるユーザー認証・ギルド単位の認可は Next.js BFF が担当します（[issue #42](https://github.com/ushinotan/nikke_unionraid_bot/issues/42)）

### 推奨される構成

```
[ブラウザ] → [Next.js BFF (認証あり)] → [Spring Boot API (内部向け)] → [DB]
```

Next.js BFF が認証を担当し、Spring Boot API は信頼されたバックエンド間の通信としてのみ利用してください。

## エンドポイント

### 1. ギルド一覧取得

**GET** `/api/guilds`

登録されている全てのギルド情報を取得する。

#### レスポンス例

```json
{
  "guilds": [
    {
      "guildId": "123456789012345678",
      "createdAt": "2024-01-15T12:00:00Z"
    },
    {
      "guildId": "987654321098765432",
      "createdAt": "2024-01-20T15:30:00Z"
    }
  ]
}
```

**注**: Discord Snowflake ID (`guildId`) は文字列として返されます。JavaScript の Number 型では精度が失われる可能性があるためです。

---

### 2. ギルドのレイド一覧取得

**GET** `/api/guilds/{guildId}/raids`

指定されたギルドに紐づく全てのレイド情報を取得する。
作成日時の降順でソートされる。

#### パスパラメータ

| パラメータ | 型 | 説明 |
|-----------|-----|------|
| guildId | Long | ギルドID |

#### レスポンス例

```json
{
  "raids": [
    {
      "id": 1,
      "guildId": "123456789012345678",
      "raidName": "レイド2024-01",
      "startTime": "2024-01-20T10:00:00Z",
      "endTime": "2024-01-21T10:00:00Z",
      "notifyTime": "2024-01-20T09:00:00Z",
      "channelId": "111222333444555666",
      "ranking": 5,
      "percentage": 1.23,
      "finishedAt": "2024-01-21T10:05:00Z",
      "createdAt": "2024-01-15T12:00:00Z"
    },
    {
      "id": 2,
      "guildId": "123456789012345678",
      "raidName": "レイド2024-02",
      "startTime": "2024-02-01T10:00:00Z",
      "endTime": "2024-02-02T10:00:00Z",
      "notifyTime": null,
      "channelId": "111222333444555666",
      "ranking": null,
      "percentage": null,
      "finishedAt": null,
      "createdAt": "2024-01-25T14:30:00Z"
    }
  ]
}
```

**注**: `guildId` と `channelId` は文字列として返されます。

#### エラーレスポンス

**404 Not Found** - ギルドが存在しない場合

---

### 3. レイド詳細取得

**GET** `/api/raids/{raidId}`

指定されたレイドの詳細情報を取得する。
参加者リストとレポートリストを含む。

#### パスパラメータ

| パラメータ | 型 | 説明 |
|-----------|-----|------|
| raidId | Int | レイドID |

#### レスポンス例

```json
{
  "raid": {
    "id": 1,
    "guildId": "123456789012345678",
    "raidName": "レイド2024-01",
    "startTime": "2024-01-20T10:00:00Z",
    "endTime": "2024-01-21T10:00:00Z",
    "notifyTime": "2024-01-20T09:00:00Z",
    "channelId": "111222333444555666",
    "ranking": 5,
    "percentage": 1.23,
    "finishedAt": "2024-01-21T10:05:00Z",
    "createdAt": "2024-01-15T12:00:00Z"
  },
  "participants": [
    {
      "id": 1,
      "userId": "100000000000000001",
      "username": "ユーザー1",
      "score": 1500000,
      "joinedAt": "2024-01-20T10:05:00Z"
    },
    {
      "id": 2,
      "userId": "100000000000000002",
      "username": "ユーザー2",
      "score": 1200000,
      "joinedAt": "2024-01-20T10:10:00Z"
    }
  ],
  "reports": [
    {
      "id": 1,
      "userId": "100000000000000001",
      "username": "ユーザー1",
      "difficulty": "hard",
      "threeT": 1,
      "reportedAt": "2024-01-20T15:00:00Z"
    },
    {
      "id": 2,
      "userId": "100000000000000002",
      "username": "ユーザー2",
      "difficulty": "normal",
      "threeT": 1,
      "reportedAt": "2024-01-20T16:00:00Z"
    }
  ]
}
```

**注**: `guildId`, `channelId`, `userId` は全て文字列として返されます。

#### エラーレスポンス

**404 Not Found** - レイドが存在しない場合

---

### 4. ギルドメンバー一覧取得

**GET** `/api/guilds/{guildId}/members`

bot が参加しているギルドのメンバー一覧を取得する（bot / system ユーザーは除外）。Server Members Intent が ON であることが前提（DEPLOYMENT.md 参照）。

#### パスパラメータ

| 名前 | 型 | 説明 |
|---|---|---|
| guildId | Long | ギルドID |

#### クエリパラメータ

| 名前 | 型 | 必須 | 説明 |
|---|---|---|---|
| query | string | 任意 | 表示名（ニックネーム・グローバル名・ユーザー名）の部分一致検索（大文字小文字を区別しない）。100文字以内（前後の空白を除去する**前**の生の値で判定）。除去後に空なら絞り込まない |
| limit | int | 任意 | 最大件数。1〜100、既定100 |

#### レスポンス例

```json
{
  "members": [
    {
      "userId": "123456789012345678",
      "displayName": "Alice",
      "avatarUrl": "https://cdn.discordapp.com/avatars/123456789012345678/abc.png"
    }
  ],
  "truncated": false
}
```

- `displayName` は ニックネーム → グローバル名 → ユーザー名 の順で、空白のみでない最初の値。
- 並び順は `displayName`、同値なら `userId` の昇順。
- `limit` を超える該当があった場合、先頭 `limit` 件を返し `truncated` が `true` になる。
- **注**: `userId` は文字列として返されます。

#### エラーレスポンス

| ステータス | error | 条件 |
|---|---|---|
| 400 Bad Request | `BAD_REQUEST` | `query` が100文字超、`limit` が範囲外、`guildId` / `limit` の型不正 |
| 401 Unauthorized | - | `X-Internal-Api-Token` が不正（フィルターが返す） |
| 404 Not Found | `NOT_FOUND` | bot がそのギルドに参加していない |
| 503 Service Unavailable | `SERVICE_UNAVAILABLE` | メンバー一覧を取得できない（bot 未接続・シャットダウン中・取得タイムアウト10秒など） |
| 500 Internal Server Error | `INTERNAL_SERVER_ERROR` | 予期しないエラー |

---

## 書き込みエンドポイント

共通の挙動:

- 認可は BFF の責務（上記「認可（書き込み API）」参照）。bot はトークンしか見ない。
- 入力検証（400）はレイド存在確認（404）より先に行う。存在しない `raidId` でも不正な入力なら 400。
- ボディは JSON オブジェクト。未知のフィールド・型違い（`"100"`、`1.5` など）は 400、`Content-Type` が JSON でなければ 415。
- `userId` はボディでは文字列（数字 1〜20 桁、1 以上の 64bit）、パスでは 1 以上の整数。`username` は trim 後 1〜100 文字、制御文字不可。`score` は 0 以上の Int。
- 終了済みレイドも API から書き込める（可否の判断は BFF）。
- Web からの操作は Discord の Embed を更新しない（次に誰かが Discord から報告したときに作り直される）。
- エラーは `{ "error", "message" }`（400 `BAD_REQUEST` / 404 `NOT_FOUND` / 409 `CONFLICT` / 500）。

### 5. 参加者の登録

**POST** `/api/raids/{raidId}/participants`

ボディ: `{ "userId": "123456789012345678", "username": "名前", "score": 0 }`

- `201`: 登録した参加者（`id`, `userId`, `username`, `score`, `joinedAt`）
- `400`: 入力不正 / `404`: レイドが無い / `409`: 既に参加者（何も変更しない）

### 6. 参加者のスコア更新

**PATCH** `/api/raids/{raidId}/participants/{userId}`

ボディ: `{ "score": 100 }`

- スコアは絶対値での上書き（last-write-wins）。`username` と `joinedAt` は変えない。
- `200`: 更新後の参加者 / `400` / `404`: レイドまたは参加者が無い

### 7. 参加の取り消し

**DELETE** `/api/raids/{raidId}/participants/{userId}`

参加者の行と全難易度の 3T 報告を削除する。

- `200`: `{ "participantRemoved": true, "removedReportCount": 2 }`
- `400` / `404`: レイドが無い、または参加者の行も報告も無い

### 8. 3T 報告の記録

**POST** `/api/raids/{raidId}/reports`

ボディ: `{ "userId": "123456789012345678", "username": "名前", "difficulty": "normal" }`（`difficulty` は `normal` | `hard`）

未参加なら score 0 で参加者にも登録する。既存参加者の `username`・`score`・`joinedAt` は変えない。

- `200`: `{ "alreadyReported": false, "participantRegistered": true }`（報告済みなら `alreadyReported: true`）
- `400` / `404`: レイドが無い

### 9. 3T 報告の取り消し

**DELETE** `/api/raids/{raidId}/reports/{userId}/{difficulty}`

- 最後の 3T 報告を取り消すと参加者も削除される（手動登録・スコアも消える）。レスポンスの `participantRemoved` で判別する。
- `200`: `{ "cancelledDifficulties": ["normal"], "participantRemoved": false }`
- `400` / `404`: レイドが無い、またはその難易度の報告が無い

---

## エラーレスポンス形式

予期しないエラーが発生した場合は、以下の形式でエラー情報が返される。

```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "エラーメッセージの詳細",
  "timestamp": "2024-01-20T12:00:00Z"
}
```

## データ型の説明

### 難易度 (difficulty)

- `normal`: ノーマル難易度
- `hard`: ハード難易度

### threeT

3ターン撃破フラグ。

- `1`: 3ターン撃破済み
- `0`: 未達成

### 参加者スコアのソート順

参加者リストは `score` の降順でソートされている。

## データ型に関する重要な注意

### Snowflake ID の取り扱い

Discord の Snowflake ID (`guildId`, `userId`, `channelId`) は以下の理由で**文字列**として返されます:

- JavaScript の `Number` 型は 53 ビットまでしか正確に表現できない
- Discord Snowflake ID は 64 ビット整数であり、精度が失われる可能性がある
- 文字列として扱うことで精度を保証

フロントエンドでは、これらの ID を文字列として扱い、数値演算を行わないでください。

### パスパラメータ vs レスポンス JSON

**重要**: パスパラメータと JSON レスポンスで ID の型が異なります。

#### パスパラメータ（リクエスト）
- 数値型として送信できます（例: `/api/guilds/123456789012345678`）
- Spring Boot が Long として自動バインドします
- BFF 実装者は文字列を数値に変換せず、そのまま URL パスに埋め込んでください

#### JSON レスポンス
- Snowflake ID は**文字列**として返されます
- JavaScript で安全に扱うため、精度損失を防ぎます

**例**:
```javascript
// BFF から Spring API を呼び出す場合
const guildId = "123456789012345678"; // 文字列として保持
const response = await fetch(`http://spring-api/api/guilds/${guildId}/raids`, { // そのまま埋め込む
  headers: { "X-Internal-Api-Token": process.env.INTERNAL_API_TOKEN! },
});
const data = await response.json();
console.log(data.raids[0].guildId); // "123456789012345678" (文字列)
```

## 実装メモ

- **内部向け API として設計** (Next.js BFF または信頼されたネットワークからの呼び出しのみ)
- **公開 Web アクセスは禁止**: このエンドポイントを直接インターネットに公開しないでください
- BFF → Backend 間は `X-Internal-Api-Token` ヘッダーによる共有シークレット認証（定数時間比較）
- 書き込み API は `RaidDomainService` 経由（レイド行を `FOR UPDATE` でロックしてから更新）。リクエストボディの検証は `controller/RaidWriteRequests.kt`
