---
# ════════════════════ FACTORY CONFIG — nikke_unionraid_bot ════════════════════
project:   nikke-unionraid-bot
company:   ushinotan            # GitHub owner (no separate organization)
language:  ja                   # README, commits and issues are all Japanese (confirmed)

tracker:
  driver:   github              # confirmed
  repo:     ushinotan/nikke_unionraid_bot
  status:                       # stage labels do not exist yet; the github driver creates them on first transition
    backlog:     { state: open }
    in_progress: { state: open, label: in-progress }
    qa_gate:     { state: open, label: ready-for-qa }
    in_qa:       { state: open, label: in-qa }
    done:        { state: closed }
  labels:
    approved:    approved       # not in the repo yet — create it
    upstream_bug: bug           # existing label

issue:    { prefix: "#", id_regex: '^#?(\d+)$' }

git:
  base_branch: origin/develop   # origin/HEAD → develop; all PRs target develop
  dev_base:    origin/develop   # same as base_branch (develop → master only for releases)
  protected:   [develop, master]
  branch_prefix: { story: 'feature/', bug: 'bugfix/' }   # confirmed — matches .github/PULL_REQUEST_TEMPLATE/{feature,bugfix}.md
  commit_format: '{type}{gitmoji}({scope}): {message}'   # message in Japanese; e.g. "feat✨(web): ...", "fix🐛(web): ...", "chore🔒(ci): ..." (git log)
  remote: origin

workspace:
  root:   .
  layout: single                # one repo, two apps side by side (no submodules)
  repos:
    backend:  { path: kotlin }
    frontend: { path: web }

stack:
  backend:
    framework: "Spring Boot 4 (Kotlin 2.3, JDA 6 Discord bot)"   # confirmed — kotlin/build.gradle.kts
    orm:   Komapper             # confirmed — komapper-spring-boot-starter-jdbc + KSP processor
    db:    PostgreSQL 16        # docker-compose.yml (postgres:16-alpine), init.sql + migrations/
    pkg:   gradle
    test:  "JUnit 5 (spring-boot-starter-test + kotlin-test)"     # build.gradle.kts useJUnitPlatform, CI ./gradlew test
    build: 'cd kotlin && ./gradlew build'
  frontend:
    framework: "Next.js 16 (App Router, React 19)"
    ui:    "Tailwind CSS 4"
    e2e:   TBD                  # no evidence — no cypress/playwright; web has only node:test unit tests (web/__tests__)
    build: 'cd web && npm run build'
    lint:  'cd web && npm run lint'
  arch_notes_doc: docs/codebase/overview.md   # REST API details: kotlin/docs/API.md

env:
  app_url: http://localhost:3000   # docker-compose.yml web
  api_url: http://localhost:8080   # docker-compose.yml bot (127.0.0.1 only, X-Internal-Api-Token required under /api)

docker:
  compose:   docker-compose.yml   # used as `docker compose -f <file>`
  ensure_up: true               # confirmed — needs a .env with a real DISCORD_TOKEN and INTERNAL_API_TOKEN
  run:       'set -a && . ./.env && set +a && cd kotlin && ./gradlew test --no-daemon'   # backend; loads POSTGRES_* from .env (app default password is empty) and uses the compose DB (init.sql applied by the bot container)
  run_frontend: 'cd web && npm test'

evidence:
  mode_var: EVIDENCE_MODE
  slowmo_var: SLOW_MO
  slowmo: 500
  resolution: 1920x1080
  artifacts:
    videos:          TBD        # no E2E tool yet
    screenshots:     TBD        # no E2E tool yet
    backend_result:  kotlin/build/test-results/test
    frontend_result: TBD        # node:test has no result file configured

tests:
  dir: .
  layout:
    backend:  'kotlin/src/test/kotlin/gg/nikke/raid/bot/{bot,config,controller,repository,service,util}/*Test.kt'
    frontend: 'web/__tests__/*.test.ts'
  seeder: init.sql

docs_map:
  codebase:    docs/codebase
  conventions: docs/conventions

product:
  domain: "Discord bot + web dashboard for managing NIKKE Union Raid participation, 3T reports and rankings"
  personas:                     # confirmed
    - "ギルドメンバー: Discord で 3凸報告し、Web で自分やギルドの進み具合を見る"
    - "特権ユーザー: サーバーのオーナー・管理者。レイドの作成・終了、順位の登録、代理登録をする（lib/permissions.ts の isPrivileged）"
    - "ボットの導入者: 自分のギルドにボットを入れて運用を始める"
  competitors: []
  compliance:  []

dev:
  developer_split: true         # backend (kotlin) and frontend (web) are separate apps
  doc_sync_order:  after_self_test
  retries:         2
  commit: { by: doc_sync, push: mr }   # confirmed — DEV opens a PR to develop at the end

models:                         # omitted roles use the plugin defaults (CONTRACT.md "Models per role")
  dev.tech_lead: opus
  dev.code_reviewer: opus
  dev.developer: sonnet         # heavy tasks: pass --model dev.developer=opus
  qa.planner: opus
  qa.backend: sonnet
  qa.frontend: sonnet
  qa.runner: haiku
  po.blueprint: opus
  po.researcher: sonnet
  po.codemap: sonnet
  po.tasks: haiku
  cr.security: opus
---

# Project notes
- Two apps in one repo: `kotlin/` (Spring Boot API + JDA bot, port 8080) and `web/` (Next.js BFF, port 3000). Web → bot calls go through `BACKEND_URL` with the `X-Internal-Api-Token` header.
- `web/` uses Next.js 16 with breaking changes: read `web/node_modules/next/dist/docs/` before writing code (see `web/AGENTS.md`). `middleware.ts` is `proxy.ts`.
- Schema: `init.sql` is the base; incremental changes go in `migrations/YYYYMMDD_*.sql`. The bot container applies the schema with psql on startup.
- Backend tests need a running PostgreSQL with `init.sql` applied (see `.github/workflows/pr-test.yml`).
- `.env` is not copied into worktrees: symlink it from the main checkout. Compose uses the folder name as the project name, but `container_name` is fixed (`nikke_unionraid_*`), so only one worktree can run the stack at a time.
- `master` is the release branch (develop → master release PRs, then back-merge into develop).
- Status/approval labels (`in-progress`, `ready-for-qa`, `in-qa`, `approved`) do not exist yet in the repo.
