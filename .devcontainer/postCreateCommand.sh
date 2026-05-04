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

if [ -f requirements.txt ]; then
  echo "[postCreate] Installing Python dependencies..."
  pip3 install --user -r requirements.txt
fi

if [ -f go/go.mod ]; then
  echo "[postCreate] Downloading Go modules..."
  cd go
  go mod download
  cd /workspaces/nikke_unionraid_bot
fi

if [ -f kotlin/build.gradle.kts ]; then
  echo "[postCreate] Checking Gradle project..."
  cd kotlin
  if [ -f gradle/wrapper/gradle-wrapper.jar ]; then
    ./gradlew --no-daemon tasks >/dev/null || true
  else
    gradle --no-daemon tasks >/dev/null || true
  fi
  cd /workspaces/nikke_unionraid_bot
fi

echo "[postCreate] Tool versions"
python3 --version || true
go version || true
java -version || true
kotlinc -version || true
if [ -f /workspaces/nikke_unionraid_bot/kotlin/gradle/wrapper/gradle-wrapper.jar ]; then
  ./kotlin/gradlew --version || true
else
  gradle --version || true
fi
