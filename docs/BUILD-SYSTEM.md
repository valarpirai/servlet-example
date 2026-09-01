# Build System

**Last updated**: 2026-09-01

## What this does

`shared-build/` holds every dependency version, every plugin version, and every
build convention for this repo. The app's `pom.xml` declares what it needs. It
never says at what version.

This means one version of each library reaches the JAR. It means a version
change happens in one file. It means a new module inherits the compiler
settings, the formatter, and the dependency checks by naming nothing.

`shared-build/` ships no code. It contributes nothing to any artifact.

## The chain

Four POMs, each inheriting from the one above:

```
shared-build/catalog/thirdparty/pom.xml    versions for outside libraries
        ↓
shared-build/catalog/internal/pom.xml      versions for our own artifacts
        ↓
shared-build/pom.xml                       compiler, plugins, conventions
        ↓
pom.xml                                    the app: what it needs, no versions
```

Each link uses `<relativePath>`. Nothing needs installing first. Clone the repo
and `mvn clean package` works.

## Where things live

| What | File |
|---|---|
| Third-party versions (gson, tomcat, jOOQ, Flyway) | `shared-build/catalog/thirdparty/pom.xml` |
| Internal and forked artifact versions | `shared-build/catalog/internal/pom.xml` |
| Java version, plugin versions, plugin config | `shared-build/pom.xml` |
| What the app depends on | `pom.xml` |

Build tooling sits apart from library versions. A Maven plugin upgrade and a
Jackson upgrade are different decisions, so they live in different files.

## The catalog is a menu

Listing a library in a catalog pins its version and makes it available. It adds
nothing to the classpath. The app gets what it orders and nothing else.

So a catalog can grow without making any JAR bigger.

## Adding a dependency

Search the catalogs first:

```bash
grep -rn "com.google.code.gson" shared-build/catalog/
```

If it is missing, add a version property and a managed dependency to
`shared-build/catalog/thirdparty/pom.xml`:

```xml
<properties>
    <caffeine.version>3.1.8</caffeine.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
            <version>${caffeine.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then declare it in `pom.xml` with no version:

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

Scope stays in `pom.xml`. Version stays in the catalog. The catalog decides
which version. The project decides whether it is `test`, `provided`, or
compile.

## Changing a version

Edit the property in the catalog. Run `mvn clean verify`. The change reaches
every project that declares the library.

## What a hardcoded version does

Nothing good. Maven's `dependencyManagement` wins over transitive versions but
loses to a version written directly in `pom.xml`. So a hardcoded version does
take effect, silently, and now the repo has two places that decide versions.

The enforcer will not catch it. Code review is the gate. Keep `pom.xml`
version-free.

## Conventions

Some plugins apply to every project. Others wait to be asked.

**Automatic.** Every child gets these without declaring anything:

| Plugin | What it does |
|---|---|
| `maven-compiler-plugin` | Java 17, UTF-8 |
| `maven-surefire-plugin` | JUnit 5 test run |
| `maven-enforcer-plugin` | Dependency checks at `validate` |
| `spotless-maven-plugin` | google-java-format check at `verify` |

Compiler and surefire run because they are part of the default lifecycle.
Enforcer and spotless run because `shared-build/pom.xml` lists them under
`<build><plugins>`.

**Opt-in.** A child activates these by naming the plugin, with no version and
no configuration:

| Plugin | What it does |
|---|---|
| `maven-shade-plugin` | Executable JAR with a `Main-Class` |
| `exec-maven-plugin` | `mvn -PappRun` dev server |

Both read `${app.mainClass}`. The app sets it once:

```xml
<properties>
    <app.mainClass>com.example.Main</app.mainClass>
</properties>
```

A library leaves it unset and gets no runnable manifest. That is correct for a
library.

## Enforcer rules

Two rules run at `validate`, before compile:

- `requireUpperBoundDeps` fails when a transitive dependency needs a newer
  version than the one actually resolved. This is the guard against declaring
  one version and running another.
- `banDuplicatePomDependencyVersions` fails on the same dependency declared
  twice.

When `requireUpperBoundDeps` fails, read which artifact wants the newer
version, then pin that newer version in the third-party catalog.

## What ends up in the JAR

| Source | In the JAR? |
|---|---|
| Compiled classes from `src/main/java` | Yes |
| Dependencies declared in `pom.xml` | Yes |
| `META-INF/services` entries from `src/main/resources` | Yes |
| Plugin and convention configuration | No, build time only |
| `test` scoped dependencies | No |
| `provided` scoped dependencies, such as Lombok | No |
| Catalog entries the app never declares | No |

## Moving shared-build to its own repository

The layout already matches the target. Three steps:

1. Move `shared-build/` into its own repo. Clone it as a sibling of this one.
2. In `pom.xml`, change the parent `<relativePath>` from `shared-build/pom.xml`
   to `../shared-build/pom.xml`.
3. Nothing else. The catalog chain uses paths relative to itself.

To resolve from a repository instead of disk, publish the three POMs and drop
`<relativePath>` from `pom.xml`. Then every repo needs `mvn install` on
shared-build, or access to the artifact repository.

## Commands

```bash
mvn clean verify                 # build, test, format check, enforcer
mvn -PappRun                     # dev server
mvn dependency:tree              # what resolved, and from where
mvn help:effective-pom           # the full inherited POM
mvn enforcer:enforce             # dependency checks alone
```
