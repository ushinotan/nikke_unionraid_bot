# レイド読み取り API ドキュメント

## 概要

Spring Boot で実装された読み取り専用の REST API。
Next.js BFF からの内部呼び出しを想定している。

## ⚠️ セキュリティ / 認証に関する重要な注意事項

**このAPIは内部向け（Internal-Only）として設計されています。**

- **公開禁止**: これらのエンドポイントを直接インターネットに公開しないでください
- **想定用途**: Next.js BFF または信頼されたネットワーク内からのアクセスのみ
- **認証未実装**: 本 PR では認証機能を実装していません
- **本格的な認証**: Discord OAuth を含む認証機能は [issue #42](https://github.com/ushinotan/nikke_unionraid_bot/issues/42) で実装予定

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
const response = await fetch(`http://spring-api/api/guilds/${guildId}/raids`); // そのまま埋め込む
const data = await response.json();
console.log(data.raids[0].guildId); // "123456789012345678" (文字列)
```

## 実装メモ

- **内部向け API として設計** (Next.js BFF または信頼されたネットワークからの呼び出しのみ)
- **公開 Web アクセスは禁止**: このエンドポイントを直接インターネットに公開しないでください
- 認証機能の実装は別 Issue (#42) で対応予定
- Discord bot の既存機能には影響を与えない読み取り専用 API
