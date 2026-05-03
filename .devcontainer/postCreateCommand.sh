#!/usr/bin/env bash
set -euo pipefail

cd /workspaces/nikke_unionraid_bot

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
  gradle --no-daemon tasks >/dev/null || true
  cd /workspaces/nikke_unionraid_bot
fi

echo "[postCreate] Tool versions"
python3 --version || true
go version || true
java -version || true
kotlinc -version || true
gradle --version || true
