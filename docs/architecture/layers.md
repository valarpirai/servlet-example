# MVC Layers

Every package under `com.example` belongs to one layer. The build checks the layers.

A new team member can read this page, then read any package knowing its role.

## The layers

| Layer | Job | Packages |
|---|---|---|
| Controller | Takes the request, calls a service, picks the response | `handler`, `processor`, `route`, `servlet` |
| Service | Business logic and orchestration | `tableapi`, `storage`, `scheduler`, `datasource`, `extlib`, `mongo`, `script`, `module` |
| Model | Rows, records, data access | `record`, `repository`, `config`, `model` |
| View | Shapes the response body | none yet |

`util`, `exception`, `security` and the root `Main` class sit outside the layers.
Any layer may use them.

## What View means here

This app returns JSON and static pages. It has no template engine.

View is the code that renders a response body. A controller passes data to it. A
controller does not build JSON by hand.

No View package exists yet. The layer is optional in the rules, so an empty View
raises no error.

## The rules

- A controller calls services and views. It does not touch `TableRecord`, `Database`
  or jOOQ.
- A service calls other services and the model. It does not call a controller or a view.
- A model class calls only other model classes and shared code.
- A view calls only the model.
- Nothing calls a controller.

The first rule is `CONTROLLERS_AVOID_DATA_ACCESS`. The rest are the `LAYERS` rule.
Both live in `ArchitectureTest`.

## Where the checks live

All checks are tests in the `web` module, under `web/src/test/java/com/example/architecture/`.
`web` sees every other module, so one test class covers all layers.

| File | Does |
|---|---|
| `Layers.java` | Maps each package to a layer. The one place to edit. |
| `LayersTest.java` | Fails when a class sits in a package no layer claims. |
| `ArchitectureTest.java` | Holds the two ArchUnit rules and a proof test. |
| `MainClassesOnly.java` | Keeps test classes out of the analysis, including the packaged `core` test jar. |

The proof test runs the data-access rule against `ViolatingFixture`, a controller that
holds a `TableRecord`. It fails if the rule stops catching that.

## The baseline

The code does not meet every rule today. Existing violations sit in a frozen baseline,
`web/src/test/resources/archunit-baseline/`.

- A new violation fails the build.
- A fixed violation drops out of the baseline on the next run.
- A missing baseline fails the build. The test never recreates it.
  `archunit.properties` sets `freeze.store.default.allowStoreCreation=false`.

To read what is left, open the files in the baseline folder. `stored.rules` names the
file for each rule. Each line in a rule file is one violation.

Review the baseline in a pull request. A baseline that grows means someone broke a rule.

## Adding a package

Add its glob to the right array in `Layers.java`. Use the form `com.example.name..`.

Skip this and `LayersTest` fails. The message names the classes with no layer.

Put shared code in `OUTSIDE_PACKAGES` only when no layer owns it.

## Fixing a violation

1. Find the line in the baseline.
2. Move the logic to the layer that owns it. A controller that reads a table calls a
   service. The service uses `TableRecord`.
3. Run `mvn test -pl web -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false`.
4. Commit the shrunken baseline with the fix.

Do not add a class to the baseline by hand. Fix the code.

## Related

- [Package map](./package-map.md) — every package and its purpose
- [Modules](./modules.md) — the four Maven modules
- [Request flow](./request-flow.md) — how a request crosses the layers
- [Data access rules](../../.claude/rules/data-access.md) — why tables go through `TableRecord`
- [Project rules](../../CLAUDE.md) — repository-wide guidance
