# Android Share Target Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Android users add diary photos by sharing images from the system gallery to the installed Baby Diary PWA, avoiding the Android app-shell file picker limitation.

**Architecture:** Register a Web Share Target in `manifest.webmanifest` with `POST` + `multipart/form-data`. The service worker intercepts `/share-target`, validates image files, stores them in a dedicated Cache API namespace, redirects to `/diaries/create?shared=1`, and `DiaryForm.vue` consumes the cached files into the existing `fileList` upload pipeline.

**Tech Stack:** Vue 3, Vue Router, Service Worker, Web App Manifest `share_target`, Cache API, Element Plus, Node test runner.

---

### Task 1: Manifest and Service Worker Share Target

**Files:**
- Modify: `frontend/public/manifest.webmanifest`
- Modify: `frontend/public/sw.js`
- Modify: `frontend/public/pwaMetadata.test.js`

- [x] Add failing tests asserting `manifest.share_target.action === '/share-target'`, `method === 'POST'`, `enctype === 'multipart/form-data'`, and `files[0].name === 'photos'`.
- [x] Add failing tests asserting `sw.js` bumps the shell cache version, intercepts `POST /share-target`, reads `request.formData()`, stores shared images in `baby-diary-share-target-v1`, redirects to `/diaries/create?shared=1`, and ignores non-image files.
- [x] Run `cd frontend && node --test public/pwaMetadata.test.js` and confirm the new assertions fail.
- [x] Implement the manifest `share_target`.
- [x] Implement service worker share target handling with Cache API storage and redirect.
- [x] Run `cd frontend && node --test public/pwaMetadata.test.js` and confirm it passes.

### Task 2: Shared File Consumer

**Files:**
- Create: `frontend/src/utils/shareTargetFiles.js`
- Create: `frontend/src/utils/shareTargetFiles.test.js`

- [x] Add tests for `isShareTargetEntryRoute()`, `toSharedUploadItem()`, and `consumeSharedImageFiles()` using a fake Cache API.
- [x] Run `cd frontend && node --test src/utils/shareTargetFiles.test.js` and confirm it fails because the module is missing.
- [x] Implement `shareTargetFiles.js` with constants shared with the service worker cache names, route query detection, upload item conversion, cache metadata reading, file reconstruction, and cache cleanup.
- [x] Run `cd frontend && node --test src/utils/shareTargetFiles.test.js` and confirm it passes.

### Task 3: Diary Form Integration

**Files:**
- Modify: `frontend/src/views/diary/DiaryForm.vue`
- Modify: `frontend/src/views/diary/DiaryForm.test.js`

- [x] Add failing tests asserting `DiaryForm.vue` imports the shared file utilities, calls `loadSharedImages()` on create routes with `shared=1`, appends accepted shared files to `fileList`, displays a success message, and removes the `shared` query via `router.replace`.
- [x] Run `cd frontend && node --test src/views/diary/DiaryForm.test.js` and confirm the new assertions fail.
- [x] Implement `loadSharedImages()` and call it during `onMounted()` after initial diary/draft loading.
- [x] Run `cd frontend && node --test src/views/diary/DiaryForm.test.js` and confirm it passes.

### Task 4: Verification, Docs, Deploy

**Files:**
- Modify: `scripts/verify.sh`
- Modify: `scripts/verify.test.sh`
- Modify: `document/维护记录.md`
- Modify: `document/技术架构文档.md`
- Modify: `document/部署文档.md`

- [x] Add `src/utils/shareTargetFiles.test.js` to the unified frontend test list and update `scripts/verify.test.sh`.
- [x] Run focused tests: `cd frontend && node --test public/pwaMetadata.test.js src/utils/shareTargetFiles.test.js src/views/diary/DiaryForm.test.js`.
- [x] Run `bash scripts/verify.test.sh`, `git diff --check`, and `scripts/verify.sh`.
- [x] Update docs with Android gallery share-to-Baby-Diary behavior and limitations.
- [x] Run `scripts/backup.sh`, `scripts/deploy.sh`, and `scripts/health-check.sh`.
- [x] Verify the deployed manifest and service worker contain the share target and bumped cache version.
- [x] Commit all source and document changes.
