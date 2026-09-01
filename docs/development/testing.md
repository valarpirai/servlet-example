# Testing

JUnit 5 and Mockito, run by surefire.

```bash
mvn test                                   # everything
mvn test -Dtest=RouteRegistryTest          # one class
mvn test -Dtest=Route*Test                 # a pattern
mvn test -Dtest=*Storage*Test,AttachmentManagerTest
```

## Layout mirrors source

Test packages match source packages. `route/RouteRegistryTest` tests
`route/RouteRegistry`. To find a class's tests, look in the same package under
`src/test/java`.

## Useful subsets

| Area | Command |
|---|---|
| Routing | `mvn test -Dtest=Route*Test` |
| Storage | `mvn test -Dtest=*Storage*Test,AttachmentManagerTest` |
| Script sandbox | `mvn test -Dtest=ScriptProcessorSecurityTest` |
| Data sources | `mvn test -Dtest=DataSource*Test` |
| Handlers | `mvn test -Dtest=*HandlerTest` |

## The security suite is a contract

`ScriptProcessorSecurityTest` holds 26 tests covering blocked classes, allowed
classes, timeout enforcement and the memory ceiling. A failure means the sandbox
changed. Investigate it as a security regression, never as flakiness.

## Reading results

Surefire writes XML per class to `target/surefire-reports/`. When output is long,
those files are easier than scrollback:

```bash
grep -l 'failures="[1-9]\|errors="[1-9]' target/surefire-reports/*.xml
```

## What is not covered

`MongoHandler.handleConnect` has no test. It calls the real
`MongoExtLibManager`, which downloads driver jars over the network.
`MongoHandlerTest` covers the other Mongo endpoints against a mock.

`DataBrowserHandlerTest` does not cover PostgreSQL's reconnect branch in
`handleSelectDatabase`.

Both gaps are noted in the class Javadoc. Neither is a reason to skip the rest.

## Failing tests block the build

Surefire fails the build on any error, which stops `verify` before packaging.
Use `-Dmaven.test.failure.ignore=true` to see packaging behaviour despite a
failure, and never to hide one.

## Related

- [Definition of done](./definition-of-done.md) — per-area checks
- [Build conventions](../build/build-conventions.md) — how surefire is configured
- [Sandbox policy](../scripting/sandbox-policy.md) — what the security suite guards
