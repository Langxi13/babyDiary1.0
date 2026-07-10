# Web Diary Detail Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Web users reliably enter the existing diary detail page from diary cards and calendar days after list previews became height-limited.

**Architecture:** Reuse the existing `/diaries/:id` route and `DiaryDetail.vue`. Keep the diary card click target as the primary Web interaction, add a lightweight detail button for discoverability, and let calendar day clicks open the first diary directly when there is only one diary for that day.

**Tech Stack:** Vue 3, Vue Router, Element Plus, Node test runner.

---

### Task 1: Diary List Detail Entry

**Files:**
- Modify: `frontend/src/views/diary/DiaryList.test.js`
- Modify: `frontend/src/views/diary/DiaryList.vue`

- [x] Add a failing source test asserting that `.diary-card` keeps `@click="openDiary(diary.diaryId)"`, a `查看详情` button calls `openDiary(diary.diaryId)` with `.stop`, and the action area lays out safely.
- [x] Run `cd frontend && node --test src/views/diary/DiaryList.test.js` and confirm the new test fails because the button does not exist.
- [x] Add a small `查看详情` text button to the diary card action area before edit/delete, using the existing `openDiary` function and `@click.stop`.
- [x] Run `cd frontend && node --test src/views/diary/DiaryList.test.js` and confirm it passes.

### Task 2: Calendar Direct Detail Entry

**Files:**
- Modify: `frontend/src/views/diary/Calendar.test.js`
- Modify: `frontend/src/views/diary/Calendar.vue`

- [x] Add a failing source test asserting that `openDay()` routes to `/diaries/${item.firstDiaryId}` when `item.count === 1`, and otherwise keeps routing to `/diaries?date=${day}`.
- [x] Run `cd frontend && node --test src/views/diary/Calendar.test.js` and confirm the new test fails because the direct detail branch does not exist.
- [x] Update `openDay()` to return early for empty days, open the detail route for one diary, and keep the filtered list route for multiple diaries.
- [x] Run `cd frontend && node --test src/views/diary/Calendar.test.js` and confirm it passes.

### Task 3: Verification, Docs, Deploy

**Files:**
- Modify: `document/维护记录.md`

- [x] Run focused frontend tests: `cd frontend && node --test src/views/diary/DiaryList.test.js src/views/diary/Calendar.test.js src/components/mobile/mobileNavigation.test.js`.
- [x] Run `git diff --check`.
- [x] Run `scripts/verify.sh`.
- [x] Update `document/维护记录.md` with the Web diary detail entry change and verification commands.
- [x] Run `scripts/backup.sh`, `scripts/deploy.sh`, and `scripts/health-check.sh`.
- [x] Commit all source and document changes.
