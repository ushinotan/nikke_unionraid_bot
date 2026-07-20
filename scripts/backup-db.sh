#!/bin/bash
set -euo pipefail

# 一ヶ月ごとの PostgreSQL 自動バックアップスクリプト
# 使い方:
#   1. 実行権限を付与: chmod +x scripts/backup-db.sh
#   2. ホストの crontab に登録 (毎月1日 4:00 に実行例):
#      0 4 1 * * /path/to/nikke_unionraid_bot/scripts/backup-db.sh >> /var/log/nikke-backup.log 2>&1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${PROJECT_ROOT}/backups"

mkdir -p "${BACKUP_DIR}"

DATE=$(date +%Y-%m-%d)
BACKUP_FILE="${BACKUP_DIR}/backup-${DATE}.sql.gz"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] バックアップ開始: ${BACKUP_FILE}"

# docker compose (v2) 推奨。古い環境は docker-compose に置き換え可
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DOCKER_COMPOSE="docker-compose"
else
  echo "ERROR: docker compose が見つかりません"
  exit 1
fi

cd "${PROJECT_ROOT}"

# -T で TTY を無効化してパイプが安全に動くようにする
${DOCKER_COMPOSE} exec -T db pg_dump -U postgres nikke_unionraid | gzip > "${BACKUP_FILE}"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] バックアップ完了: ${BACKUP_FILE} ($(du -h "${BACKUP_FILE}" | cut -f1))"

# 古いバックアップを削除 (13ヶ月以上前)
# 月1回なので基本的に直近13個を残すイメージ
find "${BACKUP_DIR}" -name 'backup-*.sql.gz' -mtime +395 -delete 2>/dev/null || true

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 古いバックアップのクリーンアップ完了"
