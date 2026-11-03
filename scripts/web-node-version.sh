#!/bin/sh
# web/Dockerfile の base ステージから Node.js のバージョン (例: 22.23.4) を出力する。
# CI はこの値を使い、Node.js のバージョンを Dockerfile の 1 箇所だけで管理する。
set -eu

dockerfile="${1:-$(dirname "$0")/../web/Dockerfile}"
version=$(sed -n 's/^FROM node:\([0-9][0-9.]*\)-alpine AS base$/\1/p' "$dockerfile")

if [ -z "$version" ]; then
  echo "::error file=web/Dockerfile::'FROM node:<version>-alpine AS base' の行が見つかりません" >&2
  exit 1
fi

echo "$version"
