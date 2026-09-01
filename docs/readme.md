# Documentation

Start here. Each file covers one concept.

## Architecture

How the app is put together.

- [Startup flow](./architecture/startup-flow.md) — JVM start to first request
- [Request flow](./architecture/request-flow.md) — one request, end to end
- [Component responsibilities](./architecture/component-responsibilities.md) — who owns what
- [Package map](./architecture/package-map.md) — every package and its purpose
- [Configuration](./architecture/configuration.md) — properties and precedence

## Routing

35 routes in YAML, matched and dispatched by reflection.

- [Route registry](./routing/route-registry.md) — matching, validation, dispatch
- [Route configuration](./routing/route-configuration.md) — the routes.yml format
- [Adding a route](./routing/adding-a-route.md) — the checklist

## Storage

Files stream in fixed-size chunks and never load whole.

- [Memory guarantee](./storage/memory-guarantee.md) — heap scales with concurrency
- [Chunked streaming](./storage/chunked-streaming.md) — the read and write path
- [Storage backends](./storage/storage-backends.md) — database and filesystem

## Scripting

Server-side JavaScript on Rhino, sandboxed.

- [Script sandbox](./scripting/script-sandbox.md) — the four controls
- [Sandbox policy](./scripting/sandbox-policy.md) — allow and block lists
- [Script execution](./scripting/script-execution.md) — executor, scope, modules
- [API scripting](./scripting/api-scripting.md) — endpoints written in JavaScript
- [Script modules](./scripting/script-modules.md) — require() and imports

## Data browser

Browse PostgreSQL, MySQL, Snowflake or MongoDB from the browser.

- [Data browser](./data-browser/data-browser.md) — the feature
- [DataSource strategies](./data-browser/datasource-strategies.md) — adding a database
- [Driver loading](./data-browser/driver-loading.md) — runtime JDBC drivers
- [Database switching](./data-browser/database-switching.md) — catalog switch or reconnect
- [MongoDB mode](./data-browser/mongodb-mode.md) — the reflection path
- [Choosing an integration](./data-browser/choosing-an-integration.md) — JDBC, HTTP, or reflection
- [API endpoints](./data-browser/data-browser-api.md) — the HTTP surface

## Observability

- [Structured logging](./observability/structured-logging.md) — logging with context
- [Correlation IDs](./observability/correlation-ids.md) — tracing a request
- [Log format](./observability/log-format.md) — the line format and queries

## Build

Versions and conventions live in `shared-build/`.

- [Build system](./build/build-system.md) — the catalog chain
- [Adding a dependency](./build/adding-a-dependency.md) — the two-step process
- [Build conventions](./build/build-conventions.md) — plugins and enforcer rules

## Development

- [Definition of done](./development/definition-of-done.md) — before you commit
- [Workflows](./development/workflows.md) — feature, bug, dev server
- [Testing](./development/testing.md) — subsets and coverage
- [Code formatting](./development/code-formatting.md) — spotless
- [Lombok](./development/lombok.md) — annotations and IDE setup
- [Debugging](./development/debugging.md) — logs, debugger, profiling

## Troubleshooting

- [JSON and serialization](./troubleshooting/json-and-serialization.md)
- [Routing issues](./troubleshooting/routing-issues.md)
- [Script issues](./troubleshooting/script-issues.md)
- [Build issues](./troubleshooting/build-issues.md)
- [Runtime issues](./troubleshooting/runtime-issues.md)
- [Database issues](./troubleshooting/database-issues.md)

## Writing docs

Rules for these files live in
[.claude/rules/documentation.md](../.claude/rules/documentation.md).

## Related

- [Project rules](../CLAUDE.md) — repository guidance for Claude Code
- [README](../README.md) — features and quick start
