# Changelog

## 2026-08-23

- Removed album-cover authorization N+1 reads, fixed the indexed home-favorite protection lookup, cached bounded diary summaries and home projections with write-driven invalidation, added an 8 MiB/30-second Redis-outage fallback, replaced racy asynchronous media responses with constant-memory resource streaming, added a bounded signed-media proxy cache, updated Tomcat, and corrected the staging EPUB step-up flow.
- Restored green CI by approving the reviewed application icons and documented `/srv/baby-diary` deployment root, narrowly filtering WebKit's unsupported `interactive-widget` viewport diagnostic, updating vulnerable transitive `nanoid`, `brace-expansion`, and `undici` releases, and making the staging performance fixture use independent MySQL temporary digit tables with a regression gate.
- Fixed home favorite photos when only `ORIGINAL/source` exists by exposing the original profile and keeping a signed original fallback; reused the persisted space ID to overlap home and space-list requests on returning sessions.
- Added bounded diary summaries, lazy timeline month pages, one-request home projection, consolidated album reads and context-aware signed-media hydration to reduce SQL/request amplification and browser payloads.
- Added Redis single-flight read fallback, account/space projection caches, post-commit invalidation, SQL request metrics and cache payload observability while preserving step-up and elevated-response no-store rules.
- Streamed portable archive manifests and media, bounded diary-book rendering, serialized export concurrency, and added temporary-file cleanup and explicit 413/429 export responses.
- Added worker empty-poll backoff and non-overlap locks, Flyway V7 read-path indexes, JVM/container/database/Redis resource budgets, immutable frontend asset caching and Vite manifest-based bundle budgets.
- Added a deterministic 10,000-diary/20,000-media staging fixture, k6 read-path and Redis-outage scenarios, SQL `EXPLAIN` capture, and the [性能优化与压测指南](document/性能优化与压测指南.md). Full k6 staging execution remains a release-host task; this change was validated with automated tests and build gates.

## 2026-08-02

- Released `1.0.0-beta.8` (`versionCode 8`) as the final privacy-scanned Android/Web artifact after normalizing non-production URL placeholders.
- Released `1.0.0-beta.7` (`versionCode 7`) so installed Beta 6 clients receive the final native staging-file cleanup fix through the immutable upgrade path.
- Released `1.0.0-beta.6` (`versionCode 6`) with an Android transport split: native HTTP for refresh cookies, Axios/Bearer for JSON, and official File Transfer streaming for media and large exports.
- Fixed Android post-login failures by allowing every emitted client/version/idempotency header in trusted native CORS preflights and added the same preflight to production health checks.
- Preserved HEIC/HEIF originals, raised the unified image limit to 25 MB, staged native selections in private app storage, and added UUID media upload idempotency without changing existing assets.
- Added native network/foreground/back-button/system-bar handling, offline status UI, merged startup requests, retryable initialization, native export save sheets, 48 px Android touch targets, and bounded staging cleanup.
- Added frontend native transfer/lifecycle/offline/error tests, backend CORS/HEIC/idempotency/migration tests, Android plugin governance, and a disk-gated maintenance script for requeuing only temporary-space media failures.

## 2026-07-31

- Added Flyway V5 adaptive image derivatives while preserving immutable `ORIGINAL/source` objects and their checksums; image assets now expose `THUMBNAIL/compact` and `PREVIEW/screen` under one media contract.
- Replaced Thumbnailator with a bounded libvips, cwebp and FFmpeg pipeline: 800-pixel list images, 2048-pixel screen previews, adaptive SSIM-gated WebP, lossless graphics, metadata stripping, no upscaling, animation fallback and a 10% minimum saving rule.
- Added an explicit “查看原图” viewer action, stable compact-to-screen-to-source fallback, serial background backfill and real-tool/MySQL regressions covering quality, transparency, animation, migration and original-data preservation.
- Prevented retained failed derivative jobs from occupying subsequent backfill candidate batches while preserving bounded retries and auditable failure state.
- Extended deployment governance regressions to cover both available and missing media processing tools in isolated CI environments.
- Restored authorization-aware covers for non-empty all-photo, favorite, custom and AI albums by falling back to their first visible photo when no usable persisted cover exists.
- Switched Web album and diary image viewers from original files to the existing 1280-pixel compressed representation with original fallback, and stopped media processing from enlarging small images while recording derived dimensions accurately.
- Unified the current runtime naming around `/api/v3`, database `baby_diary`, neutral environment variables, `Baby-Diary.jar`, canonical frontend model modules, and product version `1.0.0-beta.5` from one tracked release file.
- Added encrypted format-3 backups for database, media objects, private runtime configuration and Android signing material, with wrong-passphrase and tamper regressions; used separately verified encrypted archives during the one-time runtime cutover.
- Completed a rollback-safe one-time cutover that verified row counts, deterministic data hashes, Flyway state, media representations and space boundaries before retiring historical databases, files and narrowly matched Redis keys; removed the migration utility after completion.
- Closed cross-relation protected-media leaks: shares, portable archives, media ZIP exports, album catalogs, avatars, comments and AI album proposals now re-evaluate whether an asset is referenced by any locked diary before every sensitive read or state transition.
- Made diary revision restore validate both the current diary and target snapshot lock state, and made saved AI album proposals discard diaries or media that later became locked, private, deleted or inaccessible.
- Prevented elevated diary, album, anniversary and media responses from entering browser or Redis caches; locked diary records persisted for offline reading are reduced to metadata-only placeholders.
- Bounded large UUID lookups to 500 items per SQL batch, preserved caller order, and capped portable exports at 9,999 media entries plus the manifest.
- Removed API dependencies on repository projection types, split album reads from writes and portable archive format/export/import responsibilities, and eliminated redundant read-after-write list scans.
- Standardized malformed request handling with RFC 9457 responses for missing multipart fields, unsupported methods/media types, unacceptable response types, upload limits and uncaught unique-key races instead of accidental 500 responses.
- Added MySQL 8.4 V3-to-V4 migration coverage and verified V4 against an isolated copy of the current production V3 data without changing row counts.

## 2026-07-30

- Added Flyway V4 UUID diary revisions, private sync visibility projections, AI report history indexes and creator-scoped server pagination while preserving existing V3 data.
- Unified embedded media responses across diaries, shares, avatars, members, comments and AI album proposals; removed active frontend aliases for retired media fields and unsupported IndexedDB v1 data.
- Restored generated year albums with existing-photo covers and bounded details, added diary image ZIP export, completed timeline cursor traversal and removed repeated total-count queries.
- Split profile credentials from public projections, archive import/export/format responsibilities and album query/write services; added API governance, migration, privacy and offline-media regressions.
- Added account/space-scoped Redis read caching for tags, diary aggregates and sanitized album metadata, with post-commit version invalidation, database fallback, a short circuit breaker and deferred invalidation replay.
- Added bounded Outbox delivery, idempotent shared-diary notifications, optional VAPID Web Push, timezone-aware writing reminders, scheduled weekly/monthly/annual AI reports and catch-up for missed period boundaries.
- Added Flyway V3 retention infrastructure, 30-day diary trash purging, explicit permanent deletion, 90-day sync baselines/reset responses, expired operational-data cleanup and media ownership transfer between active space members.
- Updated the frontend for annual reports, permanent trash deletion and stale offline cursor resets; updated current API/performance/AI smoke scripts and removed public documentation of production data counts.
- Removed the frontend's fabricated V3 success envelopes and legacy model aliases; Web and Capacitor transports now expose the same raw response body, API methods require an explicit space, and UI state uses canonical `id`, `diaryDate`, `mood`, and `representations` fields.
- Consolidated personal and shared diary API behavior, media presentation helpers, the Emoji mood picker, and the diary media gallery; corrected shared-space cursor pagination, invitation acceptance, notifications, templates, comments, shares, and account session consumers that still expected retired response fields.
- Replaced the remaining application-to-MyBatis dependencies with explicit repository/gateway ports and infrastructure adapters across AI albums, schedules, discovery, templates, interactions, recovery, media authorization, notifications, shares, transfers and background jobs.
- Added ArchUnit rules preventing application dependencies on infrastructure or MyBatis, and added a pinned Spotless/Google Java Format gate after normalizing the full backend source tree.
- Removed the dormant legacy Java/API/Mapper/migration runtime and promoted the UUID architecture to the sole `BabyDiaryApplication`, canonical configuration, Mapper and Flyway locations while preserving the applied V1/V2 migration checksums.
- Activated Android minimum-version enforcement with RFC 9457 HTTP 426 responses and CORS support for native version headers, and activated privacy-safe slow-request observation for `/api/v3/**`.
- Removed the retired one-time data migration CLI and obsolete run instructions; corrected the security documentation so `/images/**` is explicitly forbidden rather than described as public compatibility behavior.
- Replaced whole-project backups with permission-hardened minimal recovery bundles containing compressed database, object storage, runtime configuration, release metadata and checksums; added a 5 GiB deployment disk gate.
- Unified V3 media storage, representations, provider-aware reads, profile selection, signed access contexts, Range/HEAD/ETag delivery, and diary/signed-media regression coverage. Migrated diary images now retain their real `ORIGINAL/source` and `THUMBNAIL/default` profiles across diary, share, album cover, avatar, comment avatar, direct-media, and public-media responses.
- Rejected old profile-less media signatures instead of retaining an ambiguous compatibility path; every new URL binds variant type, actual profile, expiry, and an HMAC-protected access context ticket.
- Added real MIME/header and image-dimension validation, single-threaded idempotent media processing, unique derived-object keys, `DELETE_PENDING`/`DELETED` lifecycle states, reference checks, quota-safe storage GC, and MySQL migration V2 indexes/dedupe constraints.
- Added account-scoped IndexedDB v2 migration/quarantine tests, strict Service Worker shell-only caching, protected-media step-up tests, shared-space LINKED/SPACE authorization tests, Range/HEAD/304/416 tests, and generic mobile MIME handling.
- Aligned deployment backups with the V3 datasource credentials and JDBC target so a retained legacy datasource cannot cause the current V3 database export to fail.
- Promoted the V3 UUID-based runtime to production with an isolated `baby_diary_v3` database and a verified migration of the existing accounts, spaces, diaries, media, albums, favorites, avatars, anniversaries, templates, and AI history.
- Made `/api/v3` the only documented business API and switched local Compose, staging, Docker, systemd, health checks, media policy, and native bootstrap documentation to the V3 runtime.
- Added the V3 migration CLI with read-only preflight, empty-target enforcement, semantic verification, media checksum validation, space-isolation checks, and explicit migration confirmation.
- Removed the obsolete V15 media migration shell entry point and the production Nginx legacy `/images/` alias; V3 media is served only through short-lived signed URLs.
- Consolidated the architecture, deployment, feature, API, testing, and rollback documentation around the V3 baseline and added the V3 migration runbook.
- Moved custom, favorites, and all-photos album detail loading to bounded server-side pagination with accurate totals while preserving manual and favorite ordering and automatic cover fallback.
- Pinned secure PostCSS, tar, and brace-expansion dependency versions and advanced the Capacitor CLI patch release; production and development npm audits now report zero vulnerabilities.
- Revalidated the release with `mvn verify`, frontend tests/build, script governance, Chromium E2E, production health checks, and post-cutover database counts.

## 2026-07-17

- Corrected the About and Updates page's small-text contrast so the authenticated critical-page accessibility gate meets WCAG 2 AA, and advanced the immutable Android release line to `1.0.0-beta.3` (`versionCode=3`).
- Added an authenticated About and Updates page showing the installed client version/build, server/API compatibility, release notes, and APK checksum without embedding a production server in the app.
- Extended the public client bootstrap contract with validated Android release metadata; incomplete, cleartext, traversal, or malformed direct-download configurations are disabled instead of being exposed to clients.
- Added non-blocking native update discovery with a compact mobile banner and explicit Android system-confirmed installation through the Capacitor Browser plugin.
- Guarded update state with a server-generation boundary so a delayed response from a previous private server cannot overwrite the newly selected server state.
- Advanced the permanent Android release line to `1.0.0-beta.2` (`versionCode=2`) so the signed Beta 1 to Beta 2 in-place upgrade path can be tested.
- Added a resource-light self-hosted APK publishing script that re-verifies the pinned signature, publishes immutable checksum-protected same-origin downloads, preserves releases across web deployments, and backs up the generated update configuration.

## 2026-07-13

- Established a permanent Android release identity outside Git, pinned its public certificate fingerprint, synchronized encrypted GitHub Actions secrets, and added checked backup coverage for signing recovery.
- Added a resource-bounded local signed Release builder and artifact verifier covering APK/AAB signatures, package/version metadata, SDK levels, backup and cleartext policy, and server-free Capacitor configuration.
- Extended the Android workflow to publish immutable, named APK/AAB beta releases with SHA-256 checksums, and added an Android 13 themed monochrome diary icon.
- Reworked the phone diary list around a persistent search field and compact date, tag, and mood disclosures, keeping advanced filters collapsed until requested and preserving a stable narrow-screen layout.
- Kept diary titles, dates, and always-visible edit/delete actions on one mobile card header row, reduced preview density, and added direct deletion to the diary detail action bar.
- Added router scroll restoration rules so newly opened diary details start at the top while browser back navigation still restores the previous list position.
- Extended component and three-browser regressions for filter disclosures, card action alignment, top-of-detail navigation, and 320px mobile visual coverage.
- Fixed diary image replacement so removing every existing image while adding new uploads clears the old files on the first update, with frontend FormData and backend storage regressions.
- Removed the obsolete PWA and Android system-share receivers now that the native client can open the gallery and camera directly inside every image upload surface.
- Added a permanent Chromium phone-layout matrix for 320px, 390px, and 430px widths, and corrected narrow-screen diary actions, workspace filters, home cards, AI report summaries, text wrapping, and spacing.
- Replaced the WireMock E2E container with a repository-local loopback Node AI provider, removing an external image dependency while retaining model-list and report-generation coverage.
- Fixed the AI report browser regression to use the same deterministic week as its synthetic diary fixture, preventing date-dependent failures when the test run crosses a calendar week.
- Revalidated the release candidate across backend, frontend, Chromium, Firefox, WebKit, Android lint/unit/build, script governance, privacy scanning, and reviewed public-asset checks.
- Fixed GitHub Android jobs to invoke `sdkmanager` from the hosted SDK root, and synchronized the Java 17 baseline test with optional Maven mirror arguments.
- Completed npm audit, Trivy, and Gitleaks scans against current dependency data, the source snapshot, and all reachable Git history with no blocking findings.

## 2026-07-12

- Added a Capacitor 8 Android client that reuses the Vue application, validates a user-supplied HTTPS server, keeps refresh sessions in native HttpOnly cookies, and preserves the existing Web/PWA transport path.
- Added native gallery and camera actions for diaries, avatars, anniversary covers, and shared spaces, plus Android single/multiple image share intents with bounded private cache, HEIC/HEIF-to-JPEG handling, per-file rejection, and 24-hour cleanup.
- Added Android launcher and splash resources, backup/data-transfer denial, narrowed FileProvider paths, resource-aware single-worker builds, CI Debug APKs, and a manually triggered signed APK/AAB workflow with signature verification.
- Added the public native compatibility bootstrap endpoint, explicit Capacitor CORS origins, path-scoped Nginx cross-origin media policy, production health coverage, and native deployment/test documentation.
- Replaced the generic password prompt with a reusable identity-verification dialog for sensitive actions, including responsive desktop/mobile layouts, safe-area spacing, password visibility, inline validation errors, loading locks, and session-reset cleanup.
- Isolated frontend API caches by account and added a client session generation boundary so logout or account switching immediately clears protected state and rejects stale responses or refresh results.
- Added cross-tab authentication synchronization, current-account-only offline queue counts, Pinia reset guards, and a two-account Chromium/Firefox/WebKit regression for anniversary privacy.
- Replaced the SPA fallback health false-positive with an exact Nginx proxy to the loopback Actuator endpoint, and made release checks require a top-level `status=UP` JSON response.
- Added a systemd `PrivateTmp` deployment drop-in, host `/tmp` permission governance, and shared-system-directory guards so media permission setup cannot alter `/tmp` or other top-level runtime directories.
- Moved Nginx validation ahead of backend shutdown and added script regressions for deployment ordering, Actuator `DOWN`, private temporary directories, and unsafe media roots.
- Replaced mutable GitHub Action tags with verified full commit SHAs, upgraded Trivy Action to the safe v0.36.0 release, and forced the development-only `glob` chain to patched v13.0.6.
- Added a redacting open-source privacy gate for current files, Git metadata, and all public branch/tag/note/PR refs, covering private hosts, email and IP addresses, server paths, personal identifiers, sensitive filenames, and full-history Gitleaks scans.
- Added checksum enforcement for visually reviewed public images and other non-text assets, including every historical version reachable through public refs.

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
