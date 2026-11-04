# Weibo Daily Automation Implementation Plan

> **For agentic workers:** Use subagent-driven-development for the independent browser and UI slices. The root integrates and verifies the database queue and scheduling.

**Goal:** Automatically execute persistent, staggered daily quotas for every enabled Weibo topic.

**Architecture:** Store immutable per-topic daily targets and individual action rows in MySQL. A serialized polling worker executes one due action through an account-isolated browser adapter; Vue reads persisted progress and manages per-topic templates.

**Tech Stack:** Java 8, Spring Boot 2.7, MyBatis-Plus, MySQL, Playwright 1.35, Vue 3, Element Plus.

## Global Constraints

- Asia/Shanghai 08:00–22:00; sign=1, post=1, forward=2–4, reply=4–6, view=40–50 per account/topic/day, inclusive random bounds.
- Preserve unrelated dirty files and unresolved README merge; no commits of user work or live platform mutations during validation.
- Repeat trigger/restart never increases targets; old pending work expires. Unknown submissions never automatically replay.
- User-scoped endpoints; account-isolated browser contexts; login/captcha pauses the whole account until explicit recovery.
- At most one global action per 5-second polling tick; adaptive 5–60-second cooldown based on remaining time and workload, normally 60 seconds. Report remaining work honestly when actual platform throughput is insufficient. No multi-minute schedule sleeps.

### Task 1: Persist and plan daily work (root)

**Files:** new `activity` Java package, append new tables to `backend/src/main/resources/schema.sql`, new tests under `backend/src/test/java/com/autosign/activity/`.

- [x] Write deterministic generator tests for inclusive ranges, each topic including topic six, randomized bucket times in the window, and no generation after 22:00. Verify failure before implementation.
- [x] Add daily plan, action, template and account-pause tables with unique natural keys. Create plans and action rows in one Spring transaction, using insert-if-absent to keep targets immutable.
- [x] Add mapper operations for scoped read, atomic claim, recovery, expiry, progress, cooldown, target reservation and retry. Add real H2 MySQL-mode persistence tests where compatible.
- [x] Implement generation using `Clock`/`Random` injection; templates resolve `{topic}`, values saved in each action.

### Task 2: Account-isolated browser actions (Executor A)

**Files:** `backend/src/main/java/com/autosign/adapter/WeiboActivityAdapter.java`, `WeiboActivityResult.java`, related adapter tests. Existing adapters remain unchanged.

**Interface:** `execute(PlatformAccount account, WeiboSuperTopic topic, String action, String content, String reservedPostId, Set<String> excludedPostIds, Consumer<String> reservePostId)` returns status `SUCCESS`, `FAILED`, `NEED_LOGIN`, `NEED_VERIFICATION`, or `UNKNOWN`, with message and optional post ID. `checkLogin(PlatformAccount)` returns the same result type, detecting login and verification separately.

- [x] Test canonical post-ID selection, exclusions, context ownership and uncertain submission classification before implementation.
- [x] Sign delegates verified existing sign method. Other actions use `BrowserService.executeSerialized` and `loadWeiboContext(account)` with context cleanup.
- [x] Discover and randomly choose distinct feed posts using bounded scrolling. Persist ID with callback before opening/submitting. Safe retries reuse their reserved ID; unknown submissions retain reservations and never replay. Browse one detail per action.
- [x] Scope controls to visible composer/dialog and require positive platform confirmation after mutations. Clear pre-submit failures may retry; any uncertain post-submit outcome is UNKNOWN.

### Task 3: Daily dashboard and templates (Executor B)

**Files:** `frontend/src/views/TaskControl.vue`, optional presentation helpers and tests.

**Interfaces:** relative paths (Axios already has `/api` base): `GET /task/today`, `GET /task/history`, `POST /task/all`, `POST /task/{sign,post,reply,forward,view}`, `GET/PUT /task/templates/{topicId}`, `POST /task/account/{accountId}/resume`.

Today DTO: `{id,accountId,accountName,topicId,topicName,taskDate,targets:{sign,post,reply,forward,view},completed:{sign,post,reply,forward,view},states:{STATUS:count},nextRunAt,lastMessage,paused}`. History DTO: `{id,topicName,actionType,status,dueAt,finishedAt,message,postId}`. Templates: `{postTemplates,replyTemplates}` as newline-separated strings supporting `{topic}`. Mutation response: `{message}`.

- [x] Render server-derived quota/progress and status; unknown, failed, expired and paused never count as completed.
- [x] Add 15-second polling with unmount cleanup, error/stale display, template editor and account resume.
- [x] Replace misleading immediate-execution copy and fix duplicate `/api` prefixes. Run `npm run build`.

### Task 4: Integrate worker and authenticated endpoints (root)

**Files:** new activity service/controller tests; replace Weibo handlers in `SignScheduler.java` and `TaskController.java`; preserve Tieba behavior.

- [x] Write worker tests for CAS rejection, one due action, cooldown, safe retry, login/captcha pause, uncertain results, disabled topics, expiry and cross-user endpoint access. Observe expected failure before implementing.
- [x] Serialize workers with a MySQL connection-held named lock, release in finally; do not hold action data transactions across browser work.
- [x] Recover stale running actions as UNKNOWN, expire old pending work, enforce date/window and persisted cooldown. Reserve selected targets before side effects. Record per-topic sign results.
- [x] Retryable pre-submit failures transition RUNNING back to PENDING with five-minute delay and at most three attempts; terminal failures stay FAILED. Login/captcha moves current action and pending account work to PAUSED, or current action to UNKNOWN when submission is uncertain.
- [x] Replace all old Weibo cron/batch operations with queue polling. Manual endpoints only ensure existing daily plans; scope all endpoints to authenticated user.
- [x] Template save changes future pending contents. Resume verifies current login/captcha, clears durable account pause, ensures missing today's plans, retains existing quota and spreads paused work over remaining window.

### Task 5: Verification and operating notes (root)

- [x] Review task handoffs for spec compliance and code quality; fix material issues.
- [x] Run `mvn test` in backend and `npm run build` in frontend, inspect full exit codes and summaries.
- [x] Check diff for unrelated edits and secret data; add deployment/operation notes describing initialization, windows, pending/unknown recovery and server requirements.
- [x] Report implementation and exact validation, distinguishing local test/build success from unperformed live Weibo validation.
