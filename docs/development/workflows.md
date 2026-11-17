# Workflows

The loops for a feature, a bug, and a dev server.

## Running the app

```bash
mvn -PappRun                      # dev server on 8080, from the repo root
SERVER_PORT=9090 mvn -PappRun     # different port
curl localhost:8080/health        # confirm it is up
```

Packaged run:

```bash
./build.sh package          # jars at major.minor.<commit>; mvn clean package gives 1.0.dev
java -jar web/target/servlet-example-*.jar
```

## Adding a feature

```bash
git checkout -b feat/my-feature

# 1. Write a failing test first
mvn test -Dtest=MyProcessorTest

# 2. Implement until it passes
mvn test -Dtest=MyProcessorTest

# 3. Full gate
mvn clean verify

git commit
```

Test first is worth it here because the reflective dispatch means a wrong name
compiles fine and fails at runtime. A test catches it immediately.

## Fixing a bug

```bash
# 1. Reproduce it as a failing test
mvn test -Dtest=FailingTest

# 2. Fix

# 3. Confirm the test passes
mvn test -Dtest=FailingTest

# 4. Confirm nothing else broke
mvn clean verify
```

The reproduction test stays. It is the only thing preventing the bug from
returning.

## Adding an HTTP endpoint

The short version:

1. Add the route to the `routes` table, in a migration
2. Write the handler or processor
3. Register the name in `RouteRegistry` and `RouteDispatcher`
4. Test it

Full detail in [adding a route](../routing/adding-a-route.md). No servlet change
is needed.

## Changing a property default

Properties live in `app_properties`, not `application.yml`. Change a running
value with SQL or `TableRecord`. It takes effect within 5 seconds. Change the shipped default by adding a
migration under `core/src/main/resources/db/`.

Editing `V1__app_properties.sql` does nothing on an existing database.
Flyway will not re-run an applied migration.

## Branch and commit

Branch off `main`. Use Conventional Commits. Keep a mechanical rename in its own
commit, separate from behaviour changes, so review can skip it.

## Related

- [Definition of done](./definition-of-done.md) — the gate before committing
- [Testing](./testing.md) — subsets and coverage
- [Debugging](./debugging.md) — logs, remote debugger, profiling
- [Configuration](../architecture/configuration.md) — where properties live
