# Modules

The app is four Maven modules. Each one owns a layer.

Dependencies run one way: `common ← core ← scheduler ← web`. `web` uses all
three. No module imports a module above it.

```
common     exception/, model/, JsonUtil, JsonConverter
core       config/, record/, script/, module/, security/, logging, migrations
scheduler  scheduler/, job repositories
web        Main, servlet/, route/, handler/, processor/, storage/, and the rest
```

## common

Plain Java and Gson. No database, no configuration, no servlet types.

Holds `exception/`, `model/`, `util/JsonUtil` and `util/JsonConverter`.

## core

Everything that needs the database, the properties or the script sandbox.

| Area | Classes |
|---|---|
| Database | `config/Database`, `DbConfig`, `DbPropertiesLoader` |
| Properties | `repository/PropertyRepository`, `util/PropertiesUtil` |
| Tables | `record/TableRecord`, `BusinessRuleRunner` |
| Scripting | `script/`, `module/`, `security/`, `processor/ApiScriptProcessor` |
| Logging | `util/StructuredLogger`, `util/LoggingConfig` |
| Resources | `db/V*.sql`, `application-default.yml`, `logback.xml` |

Core imports no servlet type. A web app overrides the defaults with its own
`application.yml`. All Flyway migrations live here, including the
scheduler tables. One schema history covers every module.

## scheduler

`scheduler/` and the job repositories, `JobRepository` and `SqlJobRepository`.
`ScriptJobHandler` runs an `api_scripts` row through core's `ApiScriptProcessor`.
`Jobs` manages jobs from Java and scripts. It reaches scripts through core's
[script globals](../scripting/script-globals.md).

## web

The HTTP layer and the feature code: `Main`, `servlet/`, `route/`, `handler/`,
`processor/`, `storage/`, `datasource/`, `extlib/`, `mongo/`, `tableapi/`.

Servlet-aware helpers stay here: `CorrelationIdFilter`, `RequestHelper`,
`RuntimeMetrics`, `script/ScriptScopeBuilder` and the `view/` package. This module builds
the shaded `servlet-example.jar`.

## Adding code

| You add | It goes in |
|---|---|
| A type with no database or servlet use | `common` |
| Database, property or script code | `core` |
| A job handler that needs only core | `scheduler` |
| A route, handler or servlet type | `web` |

If code needs a type from a module above it, the code is in the wrong module.
Move the code up, or pass the value in.

## Tests

Each module tests its own classes. Test packages match source packages inside
the module. `core` also publishes a test-jar, so `web` tests can reuse
`FakeScriptDb`.

```bash
mvn test -pl core -am        # one module and what it needs
mvn test -Dtest=Route*Test   # one class or pattern, from the root
```

## Related

- [Package map](./package-map.md) — every package and class
- [Build system](../build/build-system.md) — how the module POMs inherit
- [Startup flow](./startup-flow.md) — what runs at boot
