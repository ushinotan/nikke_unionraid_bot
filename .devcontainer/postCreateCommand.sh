#!/usr/bin/env bash
set -euo pipefail

cd /workspaces/nikke_unionraid_bot

# ホストのgit設定をdevcontainerに引き継ぐ
if [ -f /root/.gitconfig ] && [ ! -f ~/.gitconfig ]; then
  cp /root/.gitconfig ~/.gitconfig
fi
if command -v git >/dev/null && [ -z "$(git config --global user.email 2>/dev/null)" ]; then
  HOST_EMAIL=$(git config --system user.email 2>/dev/null || true)
  HOST_NAME=$(git config --system user.name 2>/dev/null || true)
  [ -n "$HOST_EMAIL" ] && git config --global user.email "$HOST_EMAIL"
  [ -n "$HOST_NAME" ] && git config --global user.name "$HOST_NAME"
fi

if [ -f kotlin/build.gradle.kts ]; then
  echo "[postCreate] Checking Gradle project..."
  cd kotlin
  ./gradlew --no-daemon tasks >/dev/null || true
  cd /workspaces/nikke_unionraid_bot
fi

echo "[postCreate] Tool versions"
java -version || true
kotlinc -version || true
./kotlin/gradlew --version || true

# Ensure DB schema (devcontainerではファイルbindが効かないためここで適用)
echo "[postCreate] Ensuring database schema..."
if docker exec nikke_unionraid_dev_db psql -U postgres -d nikke_unionraid -c '\dt' >/dev/null 2>&1; then
  cat /workspaces/nikke_unionraid_bot/init.sql | docker exec -i nikke_unionraid_dev_db psql -U postgres -d nikke_unionraid -v ON_ERROR_STOP=1 || echo "[postCreate] Schema apply finished (errors ignored if tables existed)"
else
  echo "[postCreate] Dev DB not reachable yet, skipping schema init (will be applied on first bot run if using main compose)"
fi
