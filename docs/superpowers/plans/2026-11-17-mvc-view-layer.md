# View Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Response shaping lives in a `com.example.view` package, the View layer. Controllers stop writing JSON bodies themselves.

**Architecture:** `ResponseHelper` moves from shared `util` to `view` and gains two small writers. Three duplicated private writers in handlers are replaced by it. The health, metrics and scheduler-stats bodies get their own view classes. View classes depend only on the Model and shared code.

**Tech Stack:** Java 17, JUnit 5, Mockito, ArchUnit (in place), Maven.

**Spec:** `docs/superpowers/specs/2026-11-17-mvc-layering-design.md`, rollout step 3. Rules in `docs/architecture/layers.md`.

## Global Constraints

- Response bytes do not change. Same status, content type, charset, key order and key names.
- View classes use only Model types and shared code. They never call a controller, a service or data access.
- Handler and processor names stay registered in both `RouteRegistry` and `RouteDispatcher`. No handler is renamed here.
- Script-driven responses stay where they are: `ApiHandler.sendResponse`, `ScriptProcessor`, `RouteDispatcher` and `ApiScriptProcessor.createErrorResponse` relay what a script or a route produced. They move in a later step.
- Run `mvn spotless:apply` before each commit. Commit messages end with the attribution line the session gives.
- Changing `Layers.java` needs no baseline change, because the layer rule has a stable name. Run the guard tests after each change anyway.

## Review Focus

- A JSON body that contains non-ASCII text is written as UTF-8.
- The health body is byte-identical to today: `{"status":"UP","timestamp":<n>,"uptime":"<n> ms"}`, keys in that order.
- The scheduler stats body keeps three shapes: disabled, enabled, and enabled with a failed table count (status 500, `table.error` set).
- The table API error body stays `{"error": message, "status": status}`. It differs from `JsonUtil.errorResponse`, and clients depend on it.
- `ResponseHelper` is not reachable from `util` after the move. Nothing in shared code imports it.

---

### Task 1: Create the View package

**Files:**
- Move: `web/src/main/java/com/example/util/ResponseHelper.java` to `web/src/main/java/com/example/view/ResponseHelper.java`
- Move: `web/src/test/java/com/example/util/ResponseHelperTest.java` to `web/src/test/java/com/example/view/ResponseHelperTest.java`
- Modify: every importer found by `grep -rn "ResponseHelper" web core scheduler --include='*.java'`
- Modify: `web/src/test/java/com/example/architecture/Layers.java` (`VIEW_PACKAGES`)
- Test: `web/src/test/java/com/example/view/ResponseHelperTest.java`

**Interfaces:**
- Produces: in `com.example.view.ResponseHelper`, `public static void sendJson(HttpServletResponse response, int status, Object body) throws IOException` (status, `application/json`, charset `UTF-8`, `JsonUtil.toJson(body)`, flush) and `public static void sendJsonText(HttpServletResponse response, int status, String json) throws IOException` (the same, with the string written as it is). All existing methods keep their names and signatures.

- [ ] **Step 1: Write the failing tests.** In `ResponseHelperTest`, with a Mockito `HttpServletResponse` and a `StringWriter`: `sendJsonWritesStatusTypeCharsetAndBody` (a `LinkedHashMap` with keys `b` then `a` is written in that order), `sendJsonTextWritesTheStringUnchanged`, `sendJsonWritesNonAsciiText` (a value `"é"` appears in the output). Add a layer test in `LayersTest`: `viewPackageIsAClaimedLayer` (a class in `com.example.view` has layer `View`).
- [ ] **Step 2: Run them and see them fail.** `mvn -q test -pl web -am -Dtest='ResponseHelperTest,LayersTest' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: compile error or failure, the methods and layer are missing.
- [ ] **Step 3: Move the class and its test with `git mv`.** Change the package lines. Fix every importer from the grep.
- [ ] **Step 4: Implement `sendJson` and `sendJsonText`.** Set `VIEW_PACKAGES` to `{"com.example.view.."}` in `Layers.java`.
- [ ] **Step 5: Run the architecture tests.** `mvn -q test -pl web -am -Dtest='ArchitectureTest,BaselineGuardTest,LayersTest,ResponseHelperTest' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS with the baseline unchanged. A new violation here means `ResponseHelper` reaches a layer View may not.
- [ ] **Step 6: Commit.** `git commit -m "refactor: ResponseHelper moves to the new view layer"`.

### Task 2: Handlers use the shared writers

**Files:**
- Modify: `web/src/main/java/com/example/handler/TableApiHandler.java` (`write`, `sendError`)
- Modify: `web/src/main/java/com/example/handler/DataBrowserHandler.java` (`writeJson`)
- Modify: `web/src/main/java/com/example/handler/MongoHandler.java` (`writeJson`)
- Test: existing `DataBrowserHandlerTest`, `MongoHandlerTest`; add `web/src/test/java/com/example/view/ResponseHelperTest.java` case `tableApiErrorShapeIsKept`

**Interfaces:**
- Consumes: `ResponseHelper.sendJson`, `ResponseHelper.sendJsonText` from Task 1.
- Produces: `public static void sendError(HttpServletResponse response, int status, String message) throws IOException` on `ResponseHelper`, writing `{"error": message, "status": status}` through `sendJson`.

- [ ] **Step 1: Write the failing test.** `tableApiErrorShapeIsKept`: `sendError(response, 404, "No such table")` writes status 404 and the body `{"error":"No such table","status":404}`.
- [ ] **Step 2: Run it and see it fail.** Expected: compile error, `sendError` is missing.
- [ ] **Step 3: Implement `sendError` on `ResponseHelper`.**
- [ ] **Step 4: Replace the private writers.** `TableApiHandler.write` becomes `ResponseHelper.sendJson`, and its `sendError` becomes `ResponseHelper.sendError`. `DataBrowserHandler.writeJson` and `MongoHandler.writeJson` become `ResponseHelper.sendJsonText`. Delete the private methods.
- [ ] **Step 5: Run the handler tests.** `mvn -q test -pl web -am -Dtest='*HandlerTest,ResponseHelperTest' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS.
- [ ] **Step 6: Commit.** `git commit -m "refactor: handlers write JSON through the view layer"`.

### Task 3: Health and metrics bodies

**Files:**
- Create: `web/src/main/java/com/example/view/HealthView.java`, `web/src/main/java/com/example/view/MetricsView.java`
- Modify: `web/src/main/java/com/example/servlet/RouterServlet.java` (`handleHealth`, `handleMetrics`)
- Test: `web/src/test/java/com/example/view/HealthViewTest.java`, `MetricsViewTest.java`

**Interfaces:**
- Produces: `HealthView.json(String status, long timestampMillis, long uptimeMillis) -> String` and `MetricsView.json(Object snapshot) -> String`. Both are `public static`.

- [ ] **Step 1: Write the failing tests.** `HealthViewTest.bodyIsByteIdentical`: `json("UP", 1700000000000L, 12)` equals `{"status":"UP","timestamp":1700000000000,"uptime":"12 ms"}`. `HealthViewTest.downStatusIsKept`: the same call with `"DOWN"`. `MetricsViewTest.wrapsTheSnapshotUnderMetrics`: a map `{requests: 3}` gives `{"metrics":{"requests":3}}` as `JsonUtil.toJson` writes it.
- [ ] **Step 2: Run them and see them fail.** Expected: compile error.
- [ ] **Step 3: Implement both classes.** `HealthView` holds the `String.format` call that `RouterServlet.handleHealth` has today. `MetricsView` holds the `JsonUtil.toJson(Map.of("metrics", ...))` call.
- [ ] **Step 4: Use them in `RouterServlet`.** `handleHealth` and `handleMetrics` print the view's string. Status handling stays in the servlet.
- [ ] **Step 5: Run the tests.** `mvn -q test -pl web -am -Dtest='HealthViewTest,MetricsViewTest,RouterServlet*Test' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS.
- [ ] **Step 6: Commit.** `git commit -m "refactor: health and metrics bodies come from the view layer"`.

### Task 4: Scheduler stats body

**Files:**
- Create: `web/src/main/java/com/example/view/SchedulerStatsView.java`
- Modify: `web/src/main/java/com/example/handler/SchedulerHandler.java` (`handleStats`)
- Test: `web/src/test/java/com/example/view/SchedulerStatsViewTest.java`

**Interfaces:**
- Consumes: `ResponseHelper.sendJson` from Task 1.
- Produces: `SchedulerStatsView.disabled() -> Map<String, Object>` (`{"enabled": false}`), `enabled(Object node, Object table) -> Map<String, Object>` (keys `enabled`, `node`, `table`, in that order), `tableFailed(Object node, String message) -> Map<String, Object>` (keys `enabled`, `node`, `table` with `{"error": message}`). All `public static`.

- [ ] **Step 1: Write the failing tests.** `disabledHasOnlyEnabledFalse`, `enabledKeepsKeyOrder` (assert `new ArrayList<>(body.keySet())` equals `[enabled, node, table]`), `tableFailureCarriesTheMessage` (`table` is `{"error": "boom"}`), `nullMessageBecomesTheTextNull` (matches the current `String.valueOf(e.getMessage())`).
- [ ] **Step 2: Run them and see them fail.** Expected: compile error.
- [ ] **Step 3: Implement `SchedulerStatsView`.**
- [ ] **Step 4: Use it in `SchedulerHandler.handleStats`.** The handler keeps the scheduler calls and the 500 status, and writes with `ResponseHelper.sendJson`. It no longer builds a `LinkedHashMap`.
- [ ] **Step 5: Run the tests.** `mvn -q test -pl web -am -Dtest='SchedulerStatsViewTest,*HandlerTest' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS.
- [ ] **Step 6: Commit.** `git commit -m "refactor: scheduler stats body comes from the view layer"`.

### Task 5: Docs and the full build

**Files:**
- Modify: `docs/architecture/layers.md` (the layer table and "What View means here")
- Modify: `docs/architecture/package-map.md` (add `view/`, remove `ResponseHelper` from `util/`)
- Modify: `docs/architecture/modules.md` if it names `ResponseHelper` under `util`

- [ ] **Step 1: Update the docs.** View is no longer "none yet". List its package, its classes and the rule it keeps. Name the exempt script-driven responses and say they move later.
- [ ] **Step 2: Check claims against code.** Every class named in the docs exists in `view/`. Run `grep -rn "util/ResponseHelper\|util.ResponseHelper" docs web core`. Expected: no match.
- [ ] **Step 3: Run the whole build.** `mvn clean verify`. Expected: BUILD SUCCESS with the layer baseline no larger than before.
- [ ] **Step 4: Commit.** `git commit -m "docs: describe the view layer"`.
