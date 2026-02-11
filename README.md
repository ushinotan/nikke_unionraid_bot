# Docker環境でのセットアップ

## 前提条件
- Docker
- Docker Compose

## セットアップ手順

### 1. 環境変数の設定
`.env.example`をコピーして`.env`ファイルを作成:
```bash
cp .env.example .env
```

`.env`ファイルを編集して、必要な値を設定:
- `DISCORD_TOKEN`: DiscordボットのトークンW
- `POSTGRES_PASSWORD`: PostgreSQLのパスワード

### 2. Dockerコンテナの起動
```bash
docker-compose up -d
```

### 3. ログの確認
```bash
docker-compose logs -f bot
```

### 4. データベースへの接続確認
```bash
docker-compose exec db psql -U postgres -d nikke_unionraid
```

## よく使うコマンド

### コンテナの停止
```bash
docker-compose down
```

### コンテナの再起動
```bash
docker-compose restart
```

### データベースのバックアップ
```bash
docker-compose exec db pg_dump -U postgres nikke_unionraid > backup.sql
```

### データベースのリストア
```bash
docker-compose exec -T db psql -U postgres nikke_unionraid < backup.sql
```

### ボットコンテナに入る
```bash
docker-compose exec bot /bin/bash
```

### データベースコンテナに入る
```bash
docker-compose exec db /bin/sh
```

### ログの確認
```bash
# すべてのログ
docker-compose logs -f

# ボットのログのみ
docker-compose logs -f bot

# データベースのログのみ
docker-compose logs -f db
```

## トラブルシューティング

### ボットが起動しない場合
1. `.env`ファイルの設定を確認
2. ログを確認: `docker-compose logs bot`
3. データベースの接続を確認

### データベースに接続できない場合
1. データベースコンテナが起動しているか確認: `docker-compose ps`
2. ヘルスチェックの状態を確認: `docker-compose ps db`
3. データベースのログを確認: `docker-compose logs db`