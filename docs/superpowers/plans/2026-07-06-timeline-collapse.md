# Timeline Collapse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add year, month, and week collapsing to the timeline so dense months can be browsed without a long flat list.

**Architecture:** Keep the backend timeline API unchanged. Add a focused frontend grouping helper that converts API month groups into year/month/week view models, then update `Timeline.vue` to render collapsible sections with sensible default expansion.

**Tech Stack:** Vue 3 Composition API, Element Plus, Node test runner, existing diary API and date/image helpers.

---

### Task 1: Timeline Grouping Helper

**Files:**
- Create: `frontend/src/utils/timelineGroups.js`
- Create: `frontend/src/utils/timelineGroups.test.js`

- [x] **Step 1: Write failing tests**

Create tests that call `buildTimelineTree()` with month groups across multiple years and dense months. Assert year counts, month counts, photo counts, week grouping for months with at least 8 diary entries, and direct diary display for sparse months.

- [x] **Step 2: Run red test**

Run: `node --test frontend/src/utils/timelineGroups.test.js`
Expected: FAIL because `timelineGroups.js` does not exist.

- [x] **Step 3: Implement helper**

Implement `buildTimelineTree(groups, { weeklyThreshold = 8 })`, `timelineKey()`, and `initialExpandedTimelineKeys(tree)`. Use local calendar dates, month-level grouping, and week-of-month labels.

- [x] **Step 4: Run green test**

Run: `node --test frontend/src/utils/timelineGroups.test.js`
Expected: PASS.

### Task 2: Timeline UI

**Files:**
- Modify: `frontend/src/views/diary/Timeline.vue`
- Add tests to existing or new timeline test file.

- [x] **Step 1: Write failing source-level tests**

Add tests that require `Timeline.vue` to import the grouping helper, render year/month/week toggle buttons, and apply responsive spacing classes.

- [x] **Step 2: Run red test**

Run the timeline test and confirm it fails before component edits.

- [x] **Step 3: Update component**

Use a computed tree from `groups`, render year blocks with month blocks inside, show week blocks only when a month is dense, and preserve collapsed state in a `Set`. Default expand the newest year and newest month after data loads.

- [x] **Step 4: Run green tests and build**

Run: `node --test frontend/src/utils/timelineGroups.test.js frontend/src/views/diary/Timeline.test.js`
Run: `npm --prefix frontend run build`
Expected: PASS and successful build.

### Task 3: Docs, Deployment, Commit

**Files:**
- Modify: `document/系统功能文档.md`
- Modify: `document/维护记录.md`

- [x] **Step 1: Update docs**

Document the timeline hierarchy and default expansion behavior.

- [x] **Step 2: Full verification**

Run: `git diff --check`
Run: `scripts/verify.sh`
Run: `scripts/backup.sh`
Run: `scripts/deploy.sh`
Run: `scripts/health-check.sh`

- [x] **Step 3: Commit**

Commit all repository changes with a concise message.
