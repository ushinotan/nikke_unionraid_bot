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
    e2e:   "Playwright (Chromium) — web/e2e/*.spec.ts, `cd web && npm run test:e2e` (mock backend on :18080, web on :3100; no bot/DB/Discord)"   # web/playwright.config.ts; unit tests stay on node:test (web/__tests__)
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
    videos:          web/test-results   # Playwright outputDir; video always on with EVIDENCE_MODE, else retain-on-failure (gitignored)
    screenshots:     web/test-results   # always on with EVIDENCE_MODE, else only-on-failure
    backend_result:  kotlin/build/test-results/test
    frontend_result: web/e2e-results/junit.xml   # Playwright junit reporter (E2E only; node:test has no result file)

tests:
  dir: .
  layout:
    backend:  'kotlin/src/test/kotlin/gg/nikke/raid/bot/{bot,config,controller,repository,service,util}/*Test.kt'
    frontend: 'web/__tests__/*.test.ts'
    frontend_e2e: 'web/e2e/*.spec.ts'   # helpers/mock backend in web/e2e/support/
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
- E2E (Playwright) needs no bot, DB or real Discord: `web/e2e/support/mock-backend.ts` stands in for the bot API. Details and ports: `docs/conventions/testing.md` → E2E. CI job: `web-e2e` in `.github/workflows/pr-test.yml`.
- Backend tests need a running PostgreSQL with `init.sql` applied (see `.github/workflows/pr-test.yml`).
- `.env` is not copied into worktrees: symlink it from the main checkout. Compose uses the folder name as the project name, but `container_name` is fixed (`nikke_unionraid_*`), so only one worktree can run the stack at a time.
- `master` is the release branch (develop → master release PRs, then back-merge into develop).
- Status/approval labels (`in-progress`, `ready-for-qa`, `in-qa`, `approved`) do not exist yet in the repo.

# DEV review hardening (orchestrator instructions)
Added after #109 / PR #127, where the factory review approved the diff but the PR review then found about 20 issues (bypasses and false positives in a guard test).
- **Stack reviewers after the factory code-review.** The `dev-code-reviewer` agent cannot spawn sub-agents, so the orchestrator does it. When the code-reviewer returns `APPROVED` or `APPROVED_WITH_NOTES`, run the reviewers from the user's CLAUDE.md on the uncommitted diff, in parallel: `typescript-reviewer` (`web/**/*.ts`), `react-reviewer` (`*.tsx`), `kotlin-reviewer` (`*.kt`), `database-reviewer` (`init.sql`, `migrations/`, DB access), and `security-reviewer` when the task touches auth, sessions, permissions, API routes or secrets. Merge the findings into `{DIR}/code-review-{id}-{attempt}.md`. A critical or high finding counts as `REJECTED` (back to the developer, consumes a retry). Medium and low findings go into the final report as 🟡.
- **Safety-net code** (guard tests, static checks, permission helpers) follows `docs/conventions/testing.md` → "静的検査・ガードテスト". The tech-lead plans it fail-closed and AST-based, and lists concrete bypass cases and must-pass cases in the plan checklist. The developer runs those probes. The code-reviewer runs them again and reports each result. "Accepted as a limitation in the plan" is not a reason to skip a probe.
- **Narrowed scope.** When the tech-lead narrows the issue's acceptance criteria, ask the human before the developer starts. The PR body then says which criteria moved to which issue.
