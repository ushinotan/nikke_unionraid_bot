# コーディング規約

このリポジトリでコードを書くときのルールです。人間も AI エージェントも同じものに従います。
既存のコードから読み取れる書き方をもとにしており、コードと規約が食い違ったら規約に合わせて直します（直すのは触ったついでで構いません）。

- [general.md](general.md): 全体共通（言語、ブランチ、コミット、PR、秘密情報）
- [backend.md](backend.md): `kotlin/`（Spring Boot + JDA + Komapper）
- [frontend.md](frontend.md): `web/`（Next.js 16 + React 19 + Tailwind CSS 4）
- [database.md](database.md): `init.sql` と `migrations/`
- [testing.md](testing.md): テストの書き方と実行方法

## 用語

- **必須**: 守らないとレビューで差し戻す。
- **推奨**: 理由があれば外れてよい。外れるときは PR かコメントに理由を書く。

## 規約を変えるとき

規約の変更は単独の PR（`docs📝(conventions): ...`）で出します。既存コードの一括書き換えが必要なら、規約の PR とは分けます。
