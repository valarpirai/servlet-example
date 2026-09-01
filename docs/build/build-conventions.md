# Build Conventions

Some plugins apply to every project. Others wait to be asked.

## Automatic

Every child gets these without declaring anything.

| Plugin | What it does |
|---|---|
| `maven-compiler-plugin` | Java 17, UTF-8 |
| `maven-surefire-plugin` | JUnit 5 test run |
| `maven-enforcer-plugin` | Dependency checks at `validate` |
| `spotless-maven-plugin` | google-java-format check at `verify` |

Compiler and surefire run because they are part of the default lifecycle, so
`pluginManagement` configuration reaches them with no declaration. Enforcer and
spotless run because `shared-build/pom.xml` lists them under `<build><plugins>`.

## Opt-in

A child activates these by naming the plugin, with no version and no
configuration.

| Plugin | What it does |
|---|---|
| `maven-shade-plugin` | Executable JAR with a `Main-Class` |
| `exec-maven-plugin` | `mvn -PappRun` dev server |

Both read `${app.mainClass}`, which the app sets once:

```xml
<properties>
    <app.mainClass>com.example.Main</app.mainClass>
</properties>
```

A library leaves it unset and gets no runnable manifest. That is correct for a
library.

## Enforcer rules

Two rules run at `validate`, before compile.

`requireUpperBoundDeps` fails when a transitive dependency needs a newer version
than the one actually resolved. This is the guard against declaring one version
and running another.

`banDuplicatePomDependencyVersions` fails when the same dependency is declared
twice.

When `requireUpperBoundDeps` fails, read which artifact wants the newer version,
then pin that newer version in the third-party catalog.

## What ends up in the JAR

| Source | In the JAR? |
|---|---|
| Compiled classes from `src/main/java` | Yes |
| Dependencies declared in `pom.xml` | Yes |
| `META-INF/services` entries from resources | Yes |
| Plugin and convention configuration | No, build time only |
| `test` scoped dependencies | No |
| `provided` scoped dependencies, such as Lombok | No |
| Catalog entries the app never declares | No |

## Adding a convention

Add the plugin to `pluginManagement` in `shared-build/pom.xml` with a version
and its configuration. Then decide:

- Every project should get it, so add it to `<build><plugins>` too
- Only some projects need it, so leave it opt-in and let them name it

## Commands

```bash
mvn clean verify                 # build, test, format check, enforcer
mvn -PappRun                     # dev server
mvn dependency:tree              # what resolved, and from where
mvn help:effective-pom           # the full inherited POM
mvn enforcer:enforce             # dependency checks alone
```

## Related

- [Build system](./build-system.md) — the catalog chain
- [Adding a dependency](./adding-a-dependency.md) — the two-step process
- [Code formatting](../development/code-formatting.md) — spotless in practice
- [Testing](../development/testing.md) — running the suite
