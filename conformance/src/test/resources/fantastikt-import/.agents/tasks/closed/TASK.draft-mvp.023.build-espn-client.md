---
kind: task
schema: loom.agent/v1
ref: TASK.draft-mvp.023
roadmap: ROADMAP.draft-mvp.001
effort: HIGH
impact: HIGH
depends:
  - TASK.draft-mvp.008
  - TASK.draft-mvp.009
  - TASK.draft-mvp.010
  - TASK.draft-mvp.017
  - TASK.draft-mvp.019
---

# TASK.draft-mvp.023: Build a credential-safe ESPN client

## Description

Implement separate typed ESPN v3 view requests, cookie authentication, pagination/filter capture, raw response retention, and sanitized contract fixtures.

## Requirements

- Own `etl-espn` HTTP/client DTO foundation and fixture sanitization tests.
- Fetch `mSettings`, `mTeam`, `mRoster`, `kona_player_info`, the verified 2026 player-list view/resource, and `mDraftDetail` separately with `ignoreUnknownKeys` behavior; the specification says `player_wl` while current reference clients say `players_wl`, so a captured fixture/request must decide the literal rather than memory.
- Read SWID/S2 only from environment or local secret store and redact Cookie, Set-Cookie, ESPN_S2, and SWID from logs, artifacts, errors, and fixtures; sanitize private league names and discard account identifiers not required by a contract assertion before committing fixtures.

## Deliverables

- [x] Golden fixtures cover every required view, unknown-field compatibility, and missing required-field failure.
- [x] Sanitization integration tests inject sentinel cookies, private league labels, and unnecessary account IDs and find zero occurrence in all committed fixtures and captured logs/errors/artifact metadata.
- [x] Player-pool fetching demonstrates more than the default truncated result set and complete pagination using a sanitized, inspectable filter configuration.

## Closure

```yaml
closed_at: 2026-09-03T05:11:38.928493Z
closed_by: Codex
summary: Implemented a credential-safe typed ESPN v3 client with separate view requests, environment/local-store cookie loading, sanitized raw artifact retention, strict contract decoding, complete player-pool pagination, and inspected 2026 player-list request evidence.
verification:
  - .\gradlew.bat clean verify => BUILD SUCCESSFUL in 1m 1s; 67 actions (66 executed, 1 up-to-date); 163 tests, 0 failures/errors/skips; 59 product files and 11 modules verified.
  - EspnClientTest parses sanitized golden mSettings, mTeam, mRoster, kona_player_info, players_wl, and mDraftDetail fixtures with unknown fields, while a missing required player id fails visibly after sanitized raw retention.
  - Sentinel SWID/S2 cookies, Set-Cookie values, private league label, account ID, and league ID produce zero occurrences in captured logs/errors/artifact metadata and all committed ESPN fixtures; static fixture scan found 0 cookie/auth/account/owner/private sentinel terms.
  - A 1,075-player synthetic pool completes in three inspected kona_player_info pages at offsets 0/500/1000 with page size 500 (> default 50), retained per-page filter metadata, no duplicate IDs, and an explicit final-short-page completion rule.
  - On 2026-09-02 the public 2026 /players resource returned HTTP 200 and 2,627 active entries for both aliases; current espn-api commit cec2935d9d94a3ab88dd6eff0cf5a4fdc0e80d2f uses players_wl, recorded as the canonical sanitized request literal while player_wl remains an observed compatibility alias.
follow_ups:
  - none
```
