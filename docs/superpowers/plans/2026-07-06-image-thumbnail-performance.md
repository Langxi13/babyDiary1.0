# Image Thumbnail Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Speed up album and multi-image diary views by using generated thumbnails for grid/list rendering while keeping originals for previews and export.

**Architecture:** Store 480px thumbnails under `data/images/thumbs/480/` and continue serving all media through the existing Nginx `/images/**` alias. Backend upload/update/delete paths maintain thumbnails, a backfill script generates thumbnails for existing images, and frontend image helpers choose thumbnail URLs for small previews and original URLs for full preview lists.

**Tech Stack:** Spring Boot 3.5, Thumbnailator, MyBatis, Vue 3, Element Plus, Nginx static files.

---

### Task 1: Backend Thumbnail Generator

**Files:**
- Create: `backend/src/main/java/com/langxi/babydiary/util/ThumbnailGenerator.java`
- Create: `backend/src/test/java/com/langxi/babydiary/util/ThumbnailGeneratorTest.java`

- [x] Write tests proving thumbnails are generated at `thumbs/480/{filename}`, do not upscale small images, and can be deleted with the original.
- [x] Implement `ThumbnailGenerator` with safe path resolution under the upload root.
- [x] Run `cd backend && mvn -q -Dtest=ThumbnailGeneratorTest test`.

### Task 2: Diary Upload/Delete Integration

**Files:**
- Modify: `backend/src/main/java/com/langxi/babydiary/service/DiaryService.java`
- Modify: `backend/src/test/java/com/langxi/babydiary/service/DiaryServiceTest.java`

- [x] Add tests that update/delete removes thumbnails for removed images.
- [x] Generate thumbnails after saving new diary image files.
- [x] Delete thumbnails when image files are removed.
- [x] Run `cd backend && mvn -q -Dtest=DiaryServiceTest test`.

### Task 3: Existing Image Backfill Script

**Files:**
- Create: `backend/src/main/java/com/langxi/babydiary/tools/ThumbnailBackfillTool.java`
- Create: `scripts/generate-thumbnails.sh`
- Create: `scripts/generate-thumbnails.test.sh`
- Modify: `scripts/verify.sh`

- [x] Add a shell test proving the script compiles backend classes and invokes the backfill main class with `IMAGE_DIR`.
- [x] Implement the script and Java tool.
- [x] Run the script against production `data/images`.

### Task 4: Frontend Thumbnail Usage

**Files:**
- Create: `frontend/src/utils/imageUrl.js`
- Create: `frontend/src/utils/imageUrl.test.js`
- Modify: `frontend/src/views/diary/Album.vue`
- Modify: `frontend/src/views/diary/AlbumDetail.vue`
- Modify: `frontend/src/views/diary/DiaryList.vue`
- Modify: `frontend/src/views/diary/DiaryDetail.vue`
- Modify: `frontend/src/views/home/Home.vue`
- Modify: `frontend/src/views/diary/Timeline.vue`
- Modify tests under `frontend/src/views/diary/*.test.js`

- [x] Add URL helper tests for original and thumbnail URLs.
- [x] Use thumbnail URLs for covers, grids, strips, and list previews.
- [x] Keep original URLs in `preview-src-list`.
- [x] Add lazy image loading where native `<img>` is used.
- [x] Run relevant frontend tests.

### Task 5: Verification, Docs, Deploy

**Files:**
- Modify: `document/维护记录.md`
- Modify: `document/部署文档.md`
- Modify: `document/技术架构文档.md`
- Modify: `document/API接口文档.md`

- [x] Run `scripts/verify.sh`, frontend build, and `git diff --check`.
- [x] Run `scripts/backup.sh`, `scripts/deploy.sh`, `scripts/health-check.sh`.
- [x] Verify representative thumbnail URLs return 200.
- [x] Commit all source and document changes.
