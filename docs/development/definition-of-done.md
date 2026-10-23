# Definition of Done

Run this before committing.

## Any change

- [ ] `mvn clean verify` passes, which covers compile, tests, format and enforcer
- [ ] No debug leftovers: no `System.out.println`, no commented-out code
- [ ] Manual check if user-facing: curl or browser
- [ ] Docs updated if behaviour changed

`mvn clean verify` is the single gate. It runs the enforcer at `validate`,
surefire at `test`, and spotless at `verify`.

```bash
mvn clean verify
git status          # nothing unintended
```

## Changing storage

- [ ] No `byte[]` sized from file length, no `Files.readAllBytes`
- [ ] Metadata uses `JsonUtil`, never `new Gson()`
- [ ] `mvn test -Dtest=*Storage*Test,AttachmentManagerTest`
- [ ] Upload at the configured max, confirm `/metrics` heap barely moves
- [ ] Round-trip a file and compare with `sha256sum`

See [memory guarantee](../storage/memory-guarantee.md).

## Changing routing

- [ ] Route added to the `routes` table, with a `sort_order` below any wildcard that would shadow it
- [ ] Name registered in **both** `RouteRegistry` and `RouteDispatcher`
- [ ] `mvn test -Dtest=Route*Test`
- [ ] `curl` the new route and get the expected status

Both registration points are required. See
[adding a route](../routing/adding-a-route.md).

## Changing the script sandbox

- [ ] All 26 tests pass: `mvn test -Dtest=ScriptProcessorSecurityTest`
- [ ] No class added to the allow list without review
- [ ] Timeout and memory limits still enforced
- [ ] `java.lang.System.exit()` still blocked
- [ ] [Sandbox policy](../scripting/sandbox-policy.md) updated if the lists changed

Treat a failure here as a security regression.

## Changing a data source

- [ ] Strategy implements every interface method, with a public no-arg constructor
- [ ] Fully qualified name added to
      `META-INF/services/com.example.datasource.DataSourceStrategy`
- [ ] `mvn test -Dtest=DataSource*Test`
- [ ] Connect through the data browser UI

Registration is the services file, not the registry constructor. See
[DataSource strategies](../data-browser/datasource-strategies.md).

## Commit messages

Conventional Commits: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`,
`build:`.

Describe the current state and why it changed. Migration narratives belong here,
not in `docs/`.

## Related

- [Testing](./testing.md) — running subsets
- [Code formatting](./code-formatting.md) — spotless and the pre-commit hook
- [Workflows](./workflows.md) — the loop for a feature or a bug
