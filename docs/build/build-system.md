# Build System

`shared-build/` holds every dependency version, every plugin version, and every
build convention. The app's `pom.xml` declares what it needs and never says at
what version.

One version of each library reaches the JAR. A version change happens in one
file. A new module inherits the compiler settings, the formatter and the
dependency checks by naming nothing.

`shared-build/` ships no code and contributes nothing to any artifact.

## The chain

Four POMs, each inheriting from the one above:

```
shared-build/catalog/thirdparty/pom.xml    versions for outside libraries
        ↓
shared-build/catalog/internal/pom.xml      versions for our own artifacts
        ↓
shared-build/pom.xml                       compiler, plugins, conventions
        ↓
pom.xml                                    the app
```

Each link uses `<relativePath>`. Nothing needs installing first. Clone the repo
and `mvn clean package` works.

## Why inheritance, not scope=import

Maven resolves imported BOMs as artifacts and ignores `relativePath`. An
imported catalog would need `mvn install` before the app could build.

Inheritance honours `relativePath`, so the layout stays clone-and-go.

## Where things live

| What | File |
|---|---|
| Third-party versions | `shared-build/catalog/thirdparty/pom.xml` |
| Internal and forked versions | `shared-build/catalog/internal/pom.xml` |
| Java version, plugin versions and config | `shared-build/pom.xml` |
| What the app depends on | `pom.xml` |

Build tooling sits apart from library versions. A Maven plugin upgrade and a
Jackson upgrade are different decisions, so they live in different files.

## The catalog is a menu

Listing a library pins its version and makes it available. It adds nothing to
the classpath. The app gets what it declares and nothing else.

A catalog can grow without making any JAR bigger.

## Moving shared-build to its own repository

The layout already matches the target.

1. Move `shared-build/` into its own repo, cloned as a sibling of this one
2. Change the parent `<relativePath>` in `pom.xml` to `../shared-build/pom.xml`
3. Nothing else. The catalog chain uses paths relative to itself

To resolve from a repository instead of disk, publish the three POMs and drop
`<relativePath>`. Every consuming repo then needs the artifacts available.

## Related

- [Adding a dependency](./adding-a-dependency.md) — the two-step process
- [Build conventions](./build-conventions.md) — plugins and enforcer rules
- [Build issues](../troubleshooting/build-issues.md) — when the build fails
