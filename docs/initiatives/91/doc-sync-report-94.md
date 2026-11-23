# Doc Sync Report - 94

- Project: backend (kotlin)
- Changed code: DiscordBot, ApiResponses, DtoMappers, RaidDomainError, JdaGuildMemberSource, GuildMemberApiController, GuildMember, GuildMemberService, GuildMemberSource (+ tests)
- Updated docs:
  - DEPLOYMENT.md (section 2 step 5, troubleshooting row for 4014)
  - README.md (Discord Bot の必要権限 > Privileged Gateway Intents)
  - kotlin/docs/API.md (4. ギルドメンバー一覧取得)
  - docs/codebase/backend/routes-map.md (new route)
  - docs/codebase/backend/services-map.md (GuildMember* services, errors, DiscordBot)
- Alerts: none. API.md の概要は「レイド読み取り API」のままだが、メンバー一覧も読み取り専用のため整合。
