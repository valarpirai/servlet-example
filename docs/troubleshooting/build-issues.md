# Build Issues

## Spotless check failed

```bash
mvn spotless:apply
```

`spotless:check` runs at `verify`. The pre-commit hook applies formatting,
re-stages, and exits non-zero, so the first commit attempt failing is the hook
working. Review and commit again.

See [code formatting](../development/code-formatting.md).

## Enforcer: requireUpperBoundDeps failed

**Cause.** Something in the dependency graph needs a newer version than the one
resolved.

The failure names the artifact and the version it wants. Pin that newer version
in `shared-build/catalog/thirdparty/pom.xml`.

```bash
mvn dependency:tree
```

See [adding a dependency](../build/adding-a-dependency.md).

## Enforcer: banDuplicatePomDependencyVersions failed

The same dependency is declared twice in one POM. Remove one. Search the
catalogs before adding anything, since a duplicate usually means it was already
pinned.

## Parent POM not found

**Symptom.** Maven cannot resolve `shared-build-parent` or a catalog.

**Cause.** The `<relativePath>` no longer matches the layout. The app's parent is
`shared-build/pom.xml`, and each catalog points at the next by relative path.

The chain resolves from disk, so nothing needs installing. A broken path is a
path problem, not a repository problem. See
[build system](../build/build-system.md).

## A dependency resolves to an unexpected version

```bash
mvn dependency:tree
mvn help:effective-pom
```

Check whether a version was hardcoded in `pom.xml`. A direct declaration beats
`dependencyManagement`, so the catalog loses. `pom.xml` should carry no
versions.

## Tests fail and packaging never runs

Surefire fails the build at `test`, before `package`.

```bash
mvn clean verify -Dmaven.test.failure.ignore=true
```

Use that to inspect packaging behaviour, never to land a change.

## Stale build output

```bash
mvn clean verify
```

`dependency-reduced-pom.xml` is shade output and regenerates on every package.
It is gitignored; do not commit it.

## Related

- [Build system](../build/build-system.md) — the catalog chain
- [Build conventions](../build/build-conventions.md) — enforcer rules
- [Testing](../development/testing.md) — running subsets
