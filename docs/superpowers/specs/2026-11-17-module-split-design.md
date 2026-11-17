# Module split: design

Split the single app into four Maven modules. This is a design spec. It is a
working record, not a current-state doc.

## Why

- Several web apps, each scoped, will run in one server. They must share the
  database layer, properties, logging and scripting without copying them.
- One module per concern makes each layer testable on its own.
- Dependency cycles between packages block any split today. The split removes them.

## Goals

- Four modules: `common`, `core`, `scheduler`, `web`.
- Dependencies flow one way. No cycles.
- `web` depends on all three others.
- A second web app can reuse `common`, `core` and `scheduler` unchanged.
- Behavior stays identical. No route, table or script API changes.

## Non-goals

- Publishing modules as separately versioned libraries.
- Moving `storage`, `datasource`, `extlib`, `mongo` or `tableapi` out of `web`.
- Running a second web app. This spec only keeps the door open.
- Per-module Flyway histories.

## Module graph

```
common  ←  core  ←  scheduler  ←  web
  ↑          ↑                     │
  └──────────┴─────────────────────┘   web also uses common and core directly
```

## Module contents

### common

No database. No configuration. No servlet types.

| Package | Classes |
|---|---|
| `exception/` | All |
| `model/` | All |
| `util/` | `JsonUtil`, `JsonConverter`, and other helpers with no config or repository import |

### core

Everything that needs the database, properties or the script sandbox.

| Package | Classes |
|---|---|
| `config/` | `Database`, `DbConfig`, `DbPropertiesLoader` |
| `repository/` | `PropertyRepository` |
| `record/` | `TableRecord`, business rules |
| `script/` | Rhino execution internals, except `ScriptScopeBuilder` |
| `module/` | `ModuleManager` and module loading |
| `security/` | `ScriptSecurityManager` |
| `processor/` | `ApiScriptProcessor` only |
| `util/` | `StructuredLogger`, `LoggingConfig`, `PropertiesUtil` |
| resources | `db/V*.sql` migrations, `application.yml` |

`ApiScriptProcessor` has no servlet import. It runs a script from `api_scripts`.
Scheduler needs it, so it lives in core.

### scheduler

| Package | Classes |
|---|---|
| `scheduler/` | All 16 classes, including `ScriptJobHandler` |
| `repository/` | `JobRepository`, `SqlJobRepository` |

### web

| Package | Classes |
|---|---|
| `Main` | Tomcat bootstrap |
| `servlet/`, `route/`, `handler/` | Request path |
| `processor/` | `IRequestProcessor`, `ScriptProcessor`, `FileUploadProcessor` |
| `storage/`, `datasource/`, `extlib/`, `mongo/`, `tableapi/` | Feature code |
| `processor/` | also `ScriptScopeBuilder`, moved from `script/` because it takes an `HttpServletRequest` |
| `util/` | `CorrelationIdFilter`, `RequestHelper`, `ResponseHelper`, `RuntimeMetrics` (reports scheduler stats) |
| resources | `static/`, `META-INF/services/` |

Servlet-aware utilities stay in `web`. Core and scheduler never import
`jakarta.servlet`.

## Cycles removed

| Cycle today | Cut |
|---|---|
| `util` imports `config`, `repository` | Pure helpers to `common`. The rest to `core`. |
| `repository` imports `scheduler` | Job repositories move to `scheduler`. |
| `scheduler` imports `processor` | `ApiScriptProcessor` moves to `core`. |
| `record`, `module`, `script` import each other | All three sit in `core`. No cut needed. |

## Decisions

- **One Flyway history.** All migrations stay in `core`. Scheduler tables ship with
  core. Simple. A scheduler-less app still gets the tables.
- **Build chain.** Each module takes `shared-build-parent` as its parent. A new
  root aggregator POM lists the four modules. Versions stay in
  `shared-build/catalog/`. See `docs/build/build-system.md`.
- **Version.** All modules share `major.minor.<6-char commit>`. `build.sh` sets it. See `docs/build/versioning.md`.
- **Packaging.** `common`, `core` and `scheduler` build plain JARs. `web` builds the
  shaded runnable JAR.
- **Package names.** Names stay `com.example.*`. `core` also publishes a test-jar for shared test helpers. Moving a class changes its module,
  not its package. This keeps reflection strings valid.
- **Reflection strings.** `RouteRegistry` and `RouteDispatcher` name handlers by
  string. `web` holds all handlers, so those strings do not change.
- **ClassGraph scan.** `JobHandlerScanner` scans for `@JobName`. It must scan
  the web app's classpath too, not only the scheduler JAR.

## Configuration

Core ships `application-default.yml`. A web app or top-level app supplies
`application.yml`. `PropertiesUtil` merges it over the defaults, key by key.

## Scoped web apps

Future web apps run in the same server. The split supports that:

- Each web app is its own module, depending on `common`, `core` and `scheduler`.
- The scheduler starts once per server, not once per web app.
- Properties and tables stay shared. A scope field on routes and scripts
  is a later design.

Open for that later design: how a request picks its web app, and whether each app
gets its own `routes` rows or its own table.

## Approaches considered

| Approach | Verdict |
|---|---|
| Four modules, one-way graph (chosen) | Matches the stated layering. Removes every cycle. |
| Three modules: core, features, web | Less isolation. Mixes scheduler with feature code. |
| Plugin-style `ServiceLoader` modules | Best isolation. Most design work. Deferred. |

## Migration order

Each step builds and passes `mvn clean verify` before the next.

1. Add the aggregator POM. Move current code into `web` unchanged.
2. Extract `common`. Split `util` first.
3. Extract `core`. Split `repository`. Move `ApiScriptProcessor`.
4. Extract `scheduler`.
5. Update docs.

## Docs to update when each step lands

Docs describe the current state. Update them in the same commit as the step.

- `docs/architecture/package-map.md`: add the module column.
- `docs/build/build-system.md`: add the aggregator and module parents.
- `docs/development/workflows.md`: per-module build commands.
- `CLAUDE.md`: "Where to Look" paths and the Commands block.
- `docs/readme.md`: link this spec.

## Risks

- Tests mirror source packages. Each test moves with its class.
- `StorageTestHelper` wipes `data/attachments`. Run from the `web` module only.
- Shaded JAR must still merge `META-INF/services`.
- `dependency-reduced-pom.xml` is generated per shaded module. Keep it ignored.

## Related

- [Package map](../../architecture/package-map.md) — current layout
- [Build system](../../build/build-system.md) — the POM chain
- [Table records and business rules](../../scripting/table-records-and-rules.md) — `record/` in core
- [Scheduler: adding a job](../../scheduler/adding-a-job.md) — `scheduler/` module
