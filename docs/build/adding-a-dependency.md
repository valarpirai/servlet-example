# Adding a Dependency

Two steps. The version goes in a catalog. The app declares the library without
one.

## 1. Search first

```bash
grep -rn "com.google.code.gson" shared-build/catalog/
```

It may already be pinned. Two entries for one artifact is exactly what the
enforcer's duplicate check exists to catch.

## 2. Pin the version

In `shared-build/catalog/thirdparty/pom.xml`:

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

Use `catalog/internal/pom.xml` instead for an artifact built inside the
organization, or a patched fork of an upstream library.

## 3. Declare it

In `pom.xml`, with no version:

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

Scope stays here. Version stays in the catalog. The catalog decides which
version; the project decides whether it is `test`, `provided` or compile.

## 4. Verify

```bash
mvn clean verify
```

## Changing a version

Edit the property in the catalog. Run `mvn clean verify`. The change reaches
every project that declares the library.

## Never hardcode a version

A version written directly in `pom.xml` takes effect. That is the problem.
`dependencyManagement` wins over transitive versions but loses to a direct
declaration, so now two files decide versions and they can disagree.

The enforcer will not catch it. `requireUpperBoundDeps` checks resolved versions
against transitive requirements, not where a version was written. Code review is
the only gate.

Keep `pom.xml` version-free.

## Multi-artifact families

Prefer an imported BOM over pinning each artifact. The catalog imports
`junit-bom` and `mockito-bom` this way, which keeps the members consistent with
each other automatically.

## Related

- [Build system](./build-system.md) — the catalog chain
- [Build conventions](./build-conventions.md) — what the enforcer checks
- [Build issues](../troubleshooting/build-issues.md) — enforcer failures
