# MVC layering, enforced by the build

## Intent

A new team member should learn this framework fast. A well-known architecture does that.
Today the packages are grouped by function. Handlers, processors and `tableapi/` reach the
database directly. Nothing in the build checks layering.

The goal is a layer map any Java developer already knows, with the build keeping it true.

## Goals

- Every package belongs to exactly one layer: Controller, Service, Model or View.
- The build fails when code crosses a layer the wrong way.
- Existing violations are listed, not hidden. The list shrinks as code moves.
- Package names say the layer, once the moves land.

## Non-goals

- No rewrite of behaviour. Moves change packages, not logic.
- No new framework. No Spring, no template engine.
- No change to the Maven module split (`common`, `core`, `scheduler`, `web`).

## Anti-goals

- A big-bang rename that compiles and fails at runtime.
- A rule set so strict that people add exemptions to get green.
- A layer doc that drifts from the tests.

## Layer map

| Layer | Holds | Packages today |
|---|---|---|
| Controller | Takes the request, calls a service, picks the response | `handler`, `processor`, `route`, `servlet` |
| Service | Business logic and orchestration | `tableapi`, `storage`, `scheduler`, `datasource`, `extlib`, `mongo`, `script`, `module` |
| Model | Rows, records, data access | `record`, `repository`, `config`, `common` `model` |
| View | Shapes the response: JSON bodies and static pages | JSON building now inside handlers; `static/` |

In this API, the View is the code that renders a response body. Controllers do not
hand-build JSON.

## Rules

Controller depends on Service and View. It does not depend on `record`, `Database` or jOOQ.

Service depends on Model. It does not depend on Controller or View.

Model depends on nothing above it.

View depends on Model types only.

`util`, `exception` and `security` are shared. Any layer may use them.

## Enforcement

ArchUnit tests in the `web` module. `web` sees every other module, so one test class
covers all layers.

Existing violations go into a frozen baseline (ArchUnit `FreezingArchRule`). A new
violation fails the build. Fixing one shrinks the baseline file.

The ArchUnit version is pinned in `shared-build/catalog/thirdparty/pom.xml`. The `web`
`pom.xml` declares it without a version.

## Approaches considered

- **A. Doc and tests, no moves.** Cheap and safe. Package names still do not say MVC.
- **B. Rename everything now.** Self-teaching layout. About 60 files move at once, and
  handler names are strings that fail at runtime when stale.
- **C. A first, then move one layer at a time.** Chosen. Rules guard each move, and the
  rename risk spreads over small steps.

## Rollout

1. **Rules, doc and baseline.** Add ArchUnit, the layer tests, the frozen baseline and a
   layer doc under `docs/architecture/`. No code moves.
2. **Controller out of data access.** Controllers stop touching `Database` and
   `TableRecord`. They call services. Move extracted logic into services.
3. **Response shaping into View.** Move hand-built JSON out of controllers.
4. **Package renames.** `handler` and `processor` become `controller`. Each rename ships
   an edit to the handler names in `V3__routes.sql`.
5. **Retire the baseline.** When it is empty, delete it and keep the plain rules.

Each step is its own change, reviewed on its own.

## Constraints

- `routes` rows store simple Java class names for `handler` and `processor` types. A class
  rename edits `V3__routes.sql`, and an existing database must be recreated.
- Handler and processor names are registered twice, in `RouteRegistry` and
  `RouteDispatcher`. Update both together.
- Classes scripts can reach are whitelisted in `ScriptSecurityManager`. Moving one means
  updating the whitelist and `types/servlet-example.d.ts`.
- `record/` holds `TableRecord`, the one data-access layer. It stays in Model.
- Docs follow `.claude/rules/documentation.md`: under 250 lines, one concept per file.

## Calls I made

- Tests live in `web`, not per module, so one class sees all layers.
- `FreezingArchRule` handles the baseline. It is the standard ArchUnit tool for this.
- `config` (`Database`, `DbConfig`) sits in Model, since it is data access plumbing.
- `handler` becomes `controller` in step 4. The name matches the layer.
- Step 1 lists violations only. It fixes none.

## Left to the builder

- The exact ArchUnit rule wording and the package globs per layer.
- Which services to extract first in step 2, ordered by violation count.
- The name and format of the layer doc.
