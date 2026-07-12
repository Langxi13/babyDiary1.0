# Changelog

## 2026-07-12

- Isolated frontend API caches by account and added a client session generation boundary so logout or account switching immediately clears protected state and rejects stale responses or refresh results.
- Added cross-tab authentication synchronization, current-account-only offline queue counts, Pinia reset guards, and a two-account Chromium/Firefox/WebKit regression for anniversary privacy.
- Replaced the SPA fallback health false-positive with an exact Nginx proxy to the loopback Actuator endpoint, and made release checks require a top-level `status=UP` JSON response.
- Added a systemd `PrivateTmp` deployment drop-in, host `/tmp` permission governance, and shared-system-directory guards so media permission setup cannot alter `/tmp` or other top-level runtime directories.
- Moved Nginx validation ahead of backend shutdown and added script regressions for deployment ordering, Actuator `DOWN`, private temporary directories, and unsafe media roots.
- Replaced mutable GitHub Action tags with verified full commit SHAs, upgraded Trivy Action to the safe v0.36.0 release, and forced the development-only `glob` chain to patched v13.0.6.

## 2026-07-11

- Fixed the production album-group query failure caused by incompatible collations on legacy image paths, with a V13 normalization migration and real MySQL 8.4 upgrade tests.
- Reorganized desktop navigation into five primary destinations plus a complete "More" menu, and added an inline retry state for album loading failures.
- Added layered CI for script governance, backend and frontend coverage, Chromium/Firefox/WebKit E2E, synthetic AI tests, supply-chain scanning, packaged ZAP checks, and scheduled k6 performance tests.
- Added a resource-bounded staging stack, non-root Nginx frontend image, security headers, privacy-safe test fixtures, and a complete release acceptance and rollback guide.
- Corrected Spring Boot 3 Redis configuration to use `spring.data.redis`, preventing packaged deployments from connecting to container-local Redis by mistake.
- Added personal/shared diary spaces with invitations, roles, private entries, password step-up locks, revisions, trash, comments, Emoji reactions, notifications, and Web Push.
- Added rotating 30-day refresh sessions, 15-minute access tokens, device management, verified email recovery, one-time recovery codes, and login-page recovery flows.
- Made email-verification and password-reset tokens transactionally single-use under concurrent requests, and tightened private-share expiration validation.
- Added offline diary queues, incremental pull/push sync, conflict handling, Chinese full-text search, templates, reminders, and yearly insights.
- Added space-scoped AI weekly, monthly, and annual reports with schedules while excluding locked diary content from prompts and derived data.
- Added private local or S3-compatible rich-media storage, signed media URLs, quota tracking, processing jobs, and image/audio/video metadata.
- Added expiring password-protected shares, ZIP v2 import/export with media, PDF/EPUB diary books, extraction limits, and deterministic import IDs.
- Added Flyway migrations V9-V13 for spaces, sessions, collaboration, sync, search, media, sharing, templates, reminder delivery guards, and legacy media-path collation normalization.
- Serialized shared-space role changes and member removal so concurrent requests cannot remove the final owner.
- Hardened locked-diary redaction across V2 and legacy lists, keyword filters, drafts, albums, photos, exports, notifications, search, sync, insights, AI, and media import/upload; V2 missing resources now return HTTP 404.
- Kept legacy in-memory image uploads capped at 10 MB while allowing larger V2 streamed media, preventing oversized images from exhausting the JVM heap.
- Upgraded jose4j to 0.9.6 to address published JWE denial-of-service and cryptographic validation advisories in older releases.
- Upgraded MinIO Java Client to 8.6.0 to prevent XML value substitution from exposing environment or system properties.
- Updated vulnerable transitive runtime dependencies: Logback 1.5.35, Bouncy Castle 1.84, Commons Lang 3.18.0, and AsyncHttpClient 2.15.0.
- Updated the Jackson BOM to 2.21.5 to fix the case-insensitive per-property deserialization bypass in 2.21.4.
- Forced MySQL sessions to the `+08:00` offset without requiring MySQL timezone tables, isolated V2 media from the legacy public image directory, and disabled SpringDoc in production by default.
- Corrected missing static resources, including disabled SpringDoc routes, to return HTTP 404 instead of HTTP 500.
- Configured Maven Surefire to preload the Byte Buddy test agent so Mockito remains reliable when JVM self-attachment is restricted or disabled.
- Split collaborative interactions from diary lifecycle logic and extracted profile styles to reduce high-change source files.
- Reworked the phone UI across authentication, home, diaries, drafts, timeline, calendar, albums, anniversaries, AI reports, and profile pages.
- Added a shared mobile foundation for safe-area gutters, 44px touch controls, narrow-screen overflow protection, dialogs, messages, date pickers, and upload targets.
- Refined the mobile app shell with a compact edge-to-edge tab bar, icon-based secondary navigation, route preloading, scroll-locked sheets, and automatic tab-bar hiding while the software keyboard is open.
- Replaced the diary list's mobile range calendar with two independent date fields and corrected the calendar page's duplicated month control and wrapped navigation buttons.
- Consolidated login and registration styling, improved mobile autocomplete and input sizing, and removed their duplicated page CSS.
- Corrected new anniversary dates to use the local calendar day instead of UTC truncation.
- Added mobile layout regression tests and multi-viewport browser checks covering 320px through 768px layouts.

## 2026-07-10

- Prepared a privacy-safe public source tree with no production credentials, runtime data, private domains, or server-specific paths.
- Required database, invitation, JWT, and AI encryption secrets to be supplied through environment variables.
- Added local Compose services, public setup documentation, contribution and security policies, CI, and dependency update configuration.
- Made operational scripts resolve the repository root dynamically and load health-check targets from private runtime configuration.

Earlier private deployment notes are intentionally not included in the public repository because they contain environment-specific operational details.
